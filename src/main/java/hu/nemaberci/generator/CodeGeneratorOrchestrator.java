package hu.nemaberci.generator;

import static hu.nemaberci.generator.regex.dfa.util.DFAUtils.extractAllNodes;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.data.DFAParseResult;
import hu.nemaberci.generator.regex.dfa.minimizer.DFAMinimizer;
import hu.nemaberci.regex.api.RegexParser;
import hu.nemaberci.regex.container.RegexParserContainer;
import hu.nemaberci.regex.data.ParseResult;
import hu.nemaberci.regex.data.ParseResultMatch;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.Modifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.CharUtils;

@Slf4j
public class CodeGeneratorOrchestrator {

    public static final String CURR_CHAR = "currentCharacter";
    public static final String CURR_INDEX = "currentIndex";
    public static final String FUNCTION_INPUT_VARIABLE_NAME = "inputString";
    public static final String CURR_STATE = "currentState";
    public static final String FOUND = "found";
    public static final String MATCH_STARTED_AT = "currentMatchStartedAt";
    public static final String LAST_SUCCESSFUL_MATCH_AT = "lastSuccessfulMatchAt";
    private static final String INSTANCE_NAME = "instance";

    private static void calculateDistanceFromStartAndAddParents(DFANode startNode) {
        startNode.setDistanceFromStart(0);
        List<DFANode> queue = new ArrayList<>();
        queue.add(startNode);

        while (!queue.isEmpty()) {
            var curr = queue.get(0);
            queue.remove(0);
            curr.getTransitions().values().stream().filter(Objects::nonNull).forEach(
                node -> {
                    if (!node.getParents().contains(curr)) {
                        node.getParents().add(curr);
                        if (node.getDistanceFromStart() < curr.getDistanceFromStart()) {
                            node.setDistanceFromStart(curr.getDistanceFromStart() + 1);
                            queue.add(node);
                        }
                    }
                }
            );
        }

    }

    private String getClassName(String originalClassName) {
        return originalClassName + "_impl";
    }

    private CodeBlock matchesFunctionImplementation(DFAParseResult parseResult) {
        var codeBlockBuilder = CodeBlock.builder();
        handleEmptyInputWithBooleanOutput(codeBlockBuilder);
        initStartingVariables(parseResult.getStartingNode(), codeBlockBuilder);
        addMainWhileLoopForMatches(parseResult, codeBlockBuilder);
        checkIfStateIsAcceptingAndReturn(parseResult.getStartingNode(), codeBlockBuilder);
        return codeBlockBuilder.build();
    }

    private static void addAlternateEdges(DFANode startingNode) {

        for (var node : extractAllNodes(startingNode)) {
            node.getTransitions().keySet().stream()
                .filter(CharUtils::isAsciiPrintable)
                .forEach(c -> {
                    char otherCaseChar = Character.isUpperCase(c) ?
                        Character.toLowerCase(c) :
                        Character.toUpperCase(c);
                    if (!node.getTransitions().containsKey(otherCaseChar)) {
                        node.getTransitions().put(
                            otherCaseChar,
                            node.getTransitions().get(c)
                        );
                    }
                });
        }

    }

    private static void checkIfStateIsAcceptingAndReturn(DFANode startingNode,
        Builder codeBlockBuilder
    ) {
        final var allNodes = extractAllNodes(startingNode);
        codeBlockBuilder.beginControlFlow("switch ($L)", CURR_STATE);

        for (var node : allNodes) {

            if (node.isAccepting()) {
                codeBlockBuilder
                    .beginControlFlow("case $L:", node.getId())
                    .addStatement("return true")
                    .endControlFlow();
            }

        }
        codeBlockBuilder
            .beginControlFlow("default :")
            .addStatement("return false")
            .endControlFlow()
            .endControlFlow();
    }

    private CodeBlock findMatchesFunctionImplementation(DFAParseResult parseResult) {
        var codeBlockBuilder = CodeBlock.builder();
        handleEmptyInputWithParseResultOutput(codeBlockBuilder);
        initStartingVariables(parseResult.getStartingNode(), codeBlockBuilder);
        initFindMatchesVariables(codeBlockBuilder);
        addMainWhileLoopForFindMatches(parseResult, codeBlockBuilder);
        checkIfMatchWasFound(parseResult.getStartingNode(), codeBlockBuilder);
        returnDefaultValueWithParseResultOutput(codeBlockBuilder);
        return codeBlockBuilder.build();
    }

    private static void checkIfMatchWasFound(DFANode startingNode, Builder codeBlockBuilder) {
        final var allNodes = extractAllNodes(startingNode);

        codeBlockBuilder.beginControlFlow("switch ($L)", CURR_STATE);

        for (var node : allNodes) {

            if (node.isAccepting()) {
                codeBlockBuilder
                    .beginControlFlow("case $L:", node.getId())
                    .addStatement(
                        "$L = $L.length()", LAST_SUCCESSFUL_MATCH_AT, FUNCTION_INPUT_VARIABLE_NAME)
                    .endControlFlow();
            }

        }

        codeBlockBuilder
            .endControlFlow()
            .beginControlFlow("if ($L > $L)", LAST_SUCCESSFUL_MATCH_AT, MATCH_STARTED_AT)
            .addStatement(
                "$L.add(new $T($L, $L))", FOUND, ParseResultMatch.class, MATCH_STARTED_AT,
                CURR_INDEX
            )
            .endControlFlow();
    }

    private static void initFindMatchesVariables(Builder codeBlockBuilder) {
        codeBlockBuilder
            .addStatement(
                "$T<$T> $L = new $T<>()", ArrayDeque.class, ParseResultMatch.class, FOUND,
                ArrayDeque.class
            )
            .addStatement("$T $L = 0", int.class, LAST_SUCCESSFUL_MATCH_AT)
            .addStatement("$T $L = 0", int.class, MATCH_STARTED_AT);
    }

    private static void returnDefaultValueWithParseResultOutput(Builder codeBlockBuilder) {
        codeBlockBuilder
            .addStatement("return new $T($L)", ParseResult.class, FOUND);
    }

    private static void initStartingVariables(DFANode finiteAutomata, Builder codeBlockBuilder) {
        codeBlockBuilder
            .addStatement("int $L = 0", CURR_INDEX)
            .addStatement("int $L = $L", CURR_STATE, finiteAutomata.getId());
    }

    private static void handleEmptyInputWithParseResultOutput(Builder codeBlockBuilder) {
        codeBlockBuilder
            .beginControlFlow("if ($L.length() == 0)", FUNCTION_INPUT_VARIABLE_NAME)
            .addStatement("return new $T($T.emptyList())", ParseResult.class, Collections.class)
            .endControlFlow();
    }

    private static void handleEmptyInputWithBooleanOutput(Builder codeBlockBuilder) {
        codeBlockBuilder
            .beginControlFlow("if ($L.length() == 0)", FUNCTION_INPUT_VARIABLE_NAME)
            .addStatement("return false")
            .endControlFlow();
    }

    private static void addMainWhileLoopForMatches(DFAParseResult parseResult,
        Builder codeBlockBuilder
    ) {
        // Start while
        codeBlockBuilder
            .beginControlFlow("while ($L < $L.length())", CURR_INDEX, FUNCTION_INPUT_VARIABLE_NAME);

        codeBlockBuilder
            .addStatement(
                "char $L = $L.charAt($L)", CURR_CHAR, FUNCTION_INPUT_VARIABLE_NAME, CURR_INDEX);
        addStateSwitchForMatches(parseResult, codeBlockBuilder);

        codeBlockBuilder
            .addStatement("$L++", CURR_INDEX);
        // End while
        codeBlockBuilder
            .endControlFlow();
    }

    private static void addMainWhileLoopForFindMatches(DFAParseResult parseResult,
        Builder codeBlockBuilder
    ) {
        codeBlockBuilder
            .beginControlFlow("while ($L < $L.length())", CURR_INDEX, FUNCTION_INPUT_VARIABLE_NAME)
            .addStatement(
                "char $L = $L.charAt($L)", CURR_CHAR, FUNCTION_INPUT_VARIABLE_NAME, CURR_INDEX);

        addStateSwitchForFindMatches(parseResult, codeBlockBuilder);

        codeBlockBuilder
            .addStatement("$L++", CURR_INDEX)
            .endControlFlow();
    }

    private static void addDFANodeCaseForFindMatches(
        DFAParseResult parseResult, Builder codeBlockBuilder
    ) {
        Set<DFANode> done = new HashSet<>();
        Set<DFANode> queue = new HashSet<>();
        queue.add(parseResult.getStartingNode());
        while (!queue.isEmpty()) {
            var curr = queue.iterator().next();
            queue.remove(curr);
            if (done.contains(curr) || curr == null) {
                continue;
            }
            done.add(curr);

            codeBlockBuilder.beginControlFlow("case $L:", curr.getId());

            addCurrentDFANodeTransitionsForFindMatches(
                curr, parseResult.getStartingNode(), codeBlockBuilder, parseResult.getFlags());

            codeBlockBuilder.endControlFlow();

            queue.addAll(curr.getTransitions().values());
            if (curr.getDefaultTransition() != null) {
                queue.add(curr.getDefaultTransition());
            }

        }
    }

    private static String formatCharacter(char c) {
        if (c == '\'') {
            return "\\'";
        } else if (c == '\\') {
            return "\\\\";
        } else {
            return Character.toString(c);
        }
    }

    private static void addCurrentDFANodeTransitionsForMatches(DFANode curr, DFANode defaultNode,
        Builder codeBlockBuilder, Collection<RegexFlag> flags
    ) {
        if (curr.isAccepting() && !flags.contains(RegexFlag.END_OF_STRING)) {
            codeBlockBuilder.addStatement("return true");
        } else {
            codeBlockBuilder
                .beginControlFlow("switch ($L)", CURR_CHAR);

            curr.getTransitions().entrySet().forEach(
                edge -> caseOfEdge(curr, defaultNode, codeBlockBuilder, edge, false)
            );

            addDefaultNextStateNavigation(curr, defaultNode, codeBlockBuilder, flags);

            codeBlockBuilder
                .endControlFlow();

            codeBlockBuilder.addStatement("break");
        }
    }

    private static void addDefaultNextStateNavigation(DFANode curr, DFANode defaultNode,
        Builder codeBlockBuilder, Collection<RegexFlag> flags
    ) {

        // Can there be more than one negated edges?

        // Assume that there are none.

        if (flags.contains(RegexFlag.START_OF_STRING)) {

            codeBlockBuilder
                .beginControlFlow("default:")
                .addStatement("return false")
                .endControlFlow();

        } else {

            if (curr.getDefaultTransition() != null) {

                codeBlockBuilder
                    .beginControlFlow("default: ")
                    .addStatement(
                        "$L = $L",
                        CURR_STATE,
                        curr.getDefaultTransition().getId()
                    )
                    .addStatement("break")
                    .endControlFlow();

            } else {

                // TODO: FIND BETTER PLACE TO JUMP BACK TO
                codeBlockBuilder
                    .beginControlFlow("default: ")
                    .addStatement("$L = $L", CURR_STATE, defaultNode.getId())
                    .addStatement("$L -= $L", CURR_INDEX, curr.getDistanceFromStart())
                    .addStatement("break")
                    .endControlFlow();

            }

        }
    }

    private static void addCurrentDFANodeTransitionsForFindMatches(DFANode curr,
        DFANode defaultNode,
        Builder codeBlockBuilder,
        Collection<RegexFlag> flags
    ) {
        if (curr.isAccepting()) {
            if (curr.getTransitions().isEmpty()) {
                codeBlockBuilder
                    .addStatement(
                        "$L.add(new $T($L, $L))", FOUND, ParseResultMatch.class, MATCH_STARTED_AT,
                        CURR_INDEX
                    )
                    .addStatement("$L--", CURR_INDEX)
                    .addStatement("$L = $L  + 1", MATCH_STARTED_AT, CURR_INDEX)
                    .addStatement("$L = $L", LAST_SUCCESSFUL_MATCH_AT, CURR_INDEX)
                    .addStatement("$L = $L", CURR_STATE, defaultNode.getId());
            } else {
                if (flags.contains(RegexFlag.LAZY)) {
                    codeBlockBuilder.addStatement("$L = $L", LAST_SUCCESSFUL_MATCH_AT, CURR_INDEX);

                    lazyCharSwitch(curr, defaultNode, codeBlockBuilder, flags);

                } else {
                    if (!flags.contains(RegexFlag.END_OF_STRING) && curr.getDefaultTransition() == null) {
                        addMatchSuccess(codeBlockBuilder);
                    }
                    addReturnToDefaultNode(curr, defaultNode, codeBlockBuilder, flags);
                }
            }
        } else {
            addNextStateNavigation(curr, defaultNode, codeBlockBuilder, flags);
        }
        codeBlockBuilder.addStatement("break");
    }

    private static void lazyCharSwitch(DFANode curr, DFANode defaultNode, Builder codeBlockBuilder,
        Collection<RegexFlag> flags
    ) {
        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_CHAR);

        curr.getTransitions().entrySet().forEach(
            edge -> caseOfEdge(curr, defaultNode, codeBlockBuilder, edge, true)
        );

        defaultLazyCase(curr, defaultNode, codeBlockBuilder, flags);

        codeBlockBuilder
            .endControlFlow();
    }

    private static void defaultLazyCase(DFANode curr, DFANode defaultNode, Builder codeBlockBuilder,
        Collection<RegexFlag> flags
    ) {
        codeBlockBuilder
            .beginControlFlow("default: ");

        if (!flags.contains(RegexFlag.LAZY) && curr.getDefaultTransition() == null) {
            addMatchSuccess(codeBlockBuilder);
        }
        addReturnToDefaultNode(curr, defaultNode, codeBlockBuilder, flags);

        codeBlockBuilder
            .endControlFlow();
    }

    private static void addNextStateNavigation(DFANode curr, DFANode defaultNode,
        Builder codeBlockBuilder, Collection<RegexFlag> flags
    ) {

        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_CHAR);

        curr.getTransitions().entrySet().forEach(
            edge -> caseOfEdge(curr, defaultNode, codeBlockBuilder, edge, true)
        );

        codeBlockBuilder
            .beginControlFlow("default:");

        if (curr.getDefaultTransition() == null) {

            codeBlockBuilder
                .beginControlFlow("if ($L > $L)", LAST_SUCCESSFUL_MATCH_AT, MATCH_STARTED_AT);

            addMatchSuccess(codeBlockBuilder);

            codeBlockBuilder.endControlFlow();

        }

        addReturnToDefaultNode(curr, defaultNode, codeBlockBuilder, flags);

        codeBlockBuilder
            .addStatement("break")
            .endControlFlow();

        codeBlockBuilder
            .endControlFlow();

    }

    private static void caseOfEdge(DFANode curr, DFANode defaultNode, Builder codeBlockBuilder,
        Entry<Character, DFANode> edge, boolean resetMatchStartedAt
    ) {
        if (edge.getValue() != null) {

            codeBlockBuilder
                .beginControlFlow("case '$L':", formatCharacter(edge.getKey()))
                .addStatement("$L = $L", CURR_STATE, edge.getValue().getId())
                .addStatement("break")
                .endControlFlow();

        } else {

            codeBlockBuilder
                .beginControlFlow("case '$L':", formatCharacter(edge.getKey()))
                .addStatement("$L -= $L", CURR_INDEX, curr.getDistanceFromStart());

            if (resetMatchStartedAt) {
                codeBlockBuilder.addStatement("$L = $L + 1", MATCH_STARTED_AT, CURR_INDEX);
            }

            codeBlockBuilder
                .addStatement("$L = $L", CURR_STATE, defaultNode.getId())
                .addStatement("break")
                .endControlFlow();

        }
    }

    private static void addMatchSuccess(Builder codeBlockBuilder) {
        codeBlockBuilder
            .addStatement(
                "$L.add(new $T($L, $L))", FOUND, ParseResultMatch.class, MATCH_STARTED_AT,
                CURR_INDEX
            )
            .addStatement("$L = $L + 1", MATCH_STARTED_AT, CURR_INDEX);
    }

    private static void addReturnToDefaultNode(DFANode curr, DFANode defaultNode,
        Builder codeBlockBuilder, Collection<RegexFlag> flags
    ) {

        if (flags.contains(RegexFlag.START_OF_STRING)) {

            // If the string has to match from the start and there is match in the DFA,
            // we move to an impossible state.
            codeBlockBuilder.addStatement("$L = $L", CURR_STATE, -1);

        } else {

            if (curr.getDefaultTransition() != null) {

                codeBlockBuilder
                    .addStatement(
                        "$L = $L",
                        CURR_STATE,
                        curr.getDefaultTransition().getId()
                    );

            } else {

                codeBlockBuilder
                    .addStatement("$L -= $L", CURR_INDEX, curr.getDistanceFromStart())
                    .addStatement("$L = $L", CURR_STATE, defaultNode.getId());

            }

        }

    }

    private static void addStateSwitchForMatches(DFAParseResult parseResult,
        Builder codeBlockBuilder
    ) {
        // Start switch
        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_STATE);

        addDFANodeCaseForMatches(parseResult, codeBlockBuilder);

        // End switch
        codeBlockBuilder
            .endControlFlow();
    }

    private static void addDFANodeCaseForMatches(DFAParseResult parseResult,
        Builder codeBlockBuilder
    ) {
        Set<DFANode> done = new HashSet<>();
        Set<DFANode> queue = new HashSet<>();
        queue.add(parseResult.getStartingNode());
        while (!queue.isEmpty()) {
            var curr = queue.iterator().next();
            queue.remove(curr);
            if (done.contains(curr) || curr == null) {
                continue;
            }
            done.add(curr);

            codeBlockBuilder.beginControlFlow("case $L:", curr.getId());

            addCurrentDFANodeTransitionsForMatches(
                curr, parseResult.getStartingNode(), codeBlockBuilder, parseResult.getFlags());

            codeBlockBuilder.endControlFlow();

            queue.addAll(curr.getTransitions().values());

        }
    }

    private static void addStateSwitchForFindMatches(DFAParseResult parseResult,
        Builder codeBlockBuilder
    ) {
        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_STATE);

        addDFANodeCaseForFindMatches(parseResult, codeBlockBuilder);

        codeBlockBuilder
            .beginControlFlow("default: ")
            .addStatement("return new $T($L)", ParseResult.class, FOUND)
            .endControlFlow();

        codeBlockBuilder
            .endControlFlow();
    }

    private boolean isRegexParser(JavaAnnotation javaAnnotation) {
        return javaAnnotation.getType().isA("hu.nemaberci.regex.annotation.RegularExpression");
    }

    public void generateParser(File sourceLocation, File targetLocation) {

        try {
            final var parser = new JavaProjectBuilder();
            parser.addSource(sourceLocation);
            final var parsedClass = parser.getClasses().stream().findFirst().orElseThrow();
            if (parsedClass.getAnnotations().stream().noneMatch(this::isRegexParser)) {
                return;
            }

            Files.createDirectories(targetLocation.getParentFile().toPath());

            final var classImplementationName = getClassName(parsedClass.getName());
            var classImplBuilder = TypeSpec.classBuilder(classImplementationName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(RegexParser.class);
            final var annotation = parsedClass.getAnnotations().stream()
                .filter(this::isRegexParser)
                .findFirst().orElseThrow();
            final var regexValueStr = annotation.getNamedParameter("value").toString()
                .replace("\\\\", "\\")
                .replace("\\\"", "\"");
            final var regexValue = regexValueStr.substring(1, regexValueStr.length() - 1);

            classImplBuilder
                .addField(
                    FieldSpec
                        .builder(
                            RegexParser.class, INSTANCE_NAME, Modifier.STATIC, Modifier.PRIVATE,
                            Modifier.FINAL
                        )
                        .initializer("new $L()", classImplementationName)
                        .build()
                );

            classImplBuilder.addStaticBlock(
                CodeBlock.builder()
                    .addStatement(
                        "$T.registerParser($S, $L)", RegexParserContainer.class,
                        regexValue, INSTANCE_NAME
                    )
                    .build()
            );

            var parseResult = new DFAMinimizer().parseAndConvertAndMinimize(regexValue);
            calculateDistanceFromStartAndAddParents(parseResult.getStartingNode());
            if (parseResult.getFlags().contains(RegexFlag.CASE_INDEPENDENT_ASCII)) {
                addAlternateEdges(parseResult.getStartingNode());
            }

            var matchesMethodBuilder = MethodSpec.methodBuilder("matches")
                .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class);

            matchesMethodBuilder.addCode(matchesFunctionImplementation(parseResult));

            var findMatchesMethodBuilder = MethodSpec.methodBuilder("findMatches")
                .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParseResult.class);

            findMatchesMethodBuilder.addCode(findMatchesFunctionImplementation(parseResult));

            classImplBuilder
                .addMethod(matchesMethodBuilder.build())
                .addMethod(findMatchesMethodBuilder.build());

            var javaFileBuilder = JavaFile.builder(
                "hu.nemaberci.regex.generated",
                classImplBuilder.build()
            );
            javaFileBuilder.build().writeTo(targetLocation);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}

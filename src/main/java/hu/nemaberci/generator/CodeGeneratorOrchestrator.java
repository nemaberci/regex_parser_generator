package hu.nemaberci.generator;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.minimizer.DFAMinimizer;
import hu.nemaberci.regex.api.RegexParser;
import hu.nemaberci.regex.container.RegexParserContainer;
import hu.nemaberci.regex.data.ParseResult;
import hu.nemaberci.regex.data.ParseResultMatch;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import lombok.extern.slf4j.Slf4j;

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

    private static void walkTreeAndAllNeighbours(List<DFANode> nodes, DFANode curr) {
        curr.getTransitions().forEach(
            (character, otherNode) -> {
                if (!nodes.contains(otherNode)) {
                    nodes.add(otherNode);
                    walkTreeAndAllNeighbours(nodes, otherNode);
                }
            }
        );
    }

    private static List<DFANode> extractAllNodes(DFANode startNode) {
        List<DFANode> allNodes = new ArrayList<>();
        List<DFANode> queue = new ArrayList<>();
        allNodes.add(startNode);
        queue.add(startNode);

        while (!queue.isEmpty()) {
            var otherNode = queue.get(0);
            queue.remove(0);
            if (allNodes.contains(otherNode)) {
                continue;
            }
            allNodes.add(otherNode);
            queue.addAll(otherNode.getTransitions().values());
        }

        walkTreeAndAllNeighbours(allNodes, startNode);
        return allNodes;
    }

    private static void calculateDistanceFromStart(DFANode startNode) {
        startNode.setDistanceFromStart(0);
        List<DFANode> queue = new ArrayList<>();
        queue.add(startNode);

        while (!queue.isEmpty()) {
            var curr = queue.get(0);
            queue.remove(0);
            curr.getTransitions().values().forEach(
                other -> {
                    if (!queue.contains(other) &&
                        (
                            other.getDistanceFromStart() == -1 ||
                                other.getDistanceFromStart() > curr.getDistanceFromStart()
                        )) {
                        other.setDistanceFromStart(curr.getDistanceFromStart() + 1);
                        queue.add(other);
                    }
                }
            );
        }

    }

    private String getClassName(String originalClassName) {
        return originalClassName + "_impl";
    }

    private CodeBlock matchesFunctionImplementation(String regex) {
        var startingNode = new DFAMinimizer().parseAndConvertAndMinimize(regex);
        calculateDistanceFromStart(startingNode);
        var codeBlockBuilder = CodeBlock.builder();
        handleEmptyInputWithBooleanOutput(codeBlockBuilder);
        initStartingVariables(startingNode, codeBlockBuilder);
        addMainWhileLoopForMatches(startingNode, codeBlockBuilder);
        checkIfStateIsAcceptingAndReturn(startingNode, codeBlockBuilder);
        return codeBlockBuilder.build();
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

    private CodeBlock findMatchesFunctionImplementation(String regex, boolean lazy) {
        var startingNode = new DFAMinimizer().parseAndConvertAndMinimize(regex);
        calculateDistanceFromStart(startingNode);
        var codeBlockBuilder = CodeBlock.builder();
        handleEmptyInputWithParseResultOutput(codeBlockBuilder);
        initStartingVariables(startingNode, codeBlockBuilder);
        initFindMatchesVariables(codeBlockBuilder);
        addMainWhileLoopForFindMatches(startingNode, codeBlockBuilder, lazy);
        checkIfMatchWasFound(startingNode, codeBlockBuilder);
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
                "$T<$T> $L = new $T<>()", List.class, ParseResultMatch.class, FOUND,
                ArrayList.class
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

    private static void addMainWhileLoopForMatches(DFANode startingNode, Builder codeBlockBuilder) {
        // Start while
        codeBlockBuilder
            .beginControlFlow("while ($L < $L.length())", CURR_INDEX, FUNCTION_INPUT_VARIABLE_NAME);

        codeBlockBuilder
            .addStatement(
                "char $L = $L.charAt($L)", CURR_CHAR, FUNCTION_INPUT_VARIABLE_NAME, CURR_INDEX);
        addStateSwitchForMatches(startingNode, codeBlockBuilder);

        codeBlockBuilder
            .addStatement("$L++", CURR_INDEX);
        // End while
        codeBlockBuilder
            .endControlFlow();
    }

    private static void addMainWhileLoopForFindMatches(DFANode startingNode,
        Builder codeBlockBuilder, boolean lazy
    ) {
        // Start while
        codeBlockBuilder
            .beginControlFlow("while ($L < $L.length())", CURR_INDEX, FUNCTION_INPUT_VARIABLE_NAME);

        codeBlockBuilder
            .addStatement(
                "char $L = $L.charAt($L)", CURR_CHAR, FUNCTION_INPUT_VARIABLE_NAME, CURR_INDEX);
        addStateSwitchForFindMatches(startingNode, codeBlockBuilder, lazy);

        codeBlockBuilder
            .addStatement("$L++", CURR_INDEX);
        // End while
        codeBlockBuilder
            .endControlFlow();
    }

    private static void addDFANodeCaseRecursivelyForMatches(Set<DFANode> done, DFANode curr,
        DFANode defaultNode, Builder codeBlockBuilder
    ) {
        if (done.contains(curr)) {
            return;
        }
        done.add(curr);

        codeBlockBuilder
            .beginControlFlow("case $L:", curr.getId());

        addCurrentDFANodeTransitionsForMatches(curr, defaultNode, codeBlockBuilder);

        codeBlockBuilder.endControlFlow();

        curr.getTransitions().forEach(
            (character, dfaNode) -> addDFANodeCaseRecursivelyForMatches(
                done, dfaNode, defaultNode, codeBlockBuilder)
        );
    }

    private static void addDFANodeCaseForFindMatches(
        DFANode startingNode, Builder codeBlockBuilder, boolean lazy
    ) {
        Set<DFANode> done = new HashSet<>();
        Set<DFANode> queue = new HashSet<>();
        queue.add(startingNode);
        while (!queue.isEmpty()) {
            var curr = queue.iterator().next();
            queue.remove(curr);
            if (done.contains(curr)) {
                continue;
            }
            done.add(curr);

            codeBlockBuilder.beginControlFlow("case $L:", curr.getId());

            addCurrentDFANodeTransitionsForFindMatches(curr, startingNode, codeBlockBuilder, lazy);

            codeBlockBuilder.endControlFlow();

            curr.getTransitions().forEach(
                (unused, dfaNode) -> queue.add(dfaNode)
            );

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
        Builder codeBlockBuilder
    ) {
        if (curr.isAccepting()) {
            codeBlockBuilder.addStatement("return true");
        } else {
            codeBlockBuilder
                .beginControlFlow("switch ($L)", CURR_CHAR);

            curr.getTransitions().forEach(
                (character, dfaNode) ->
                    codeBlockBuilder
                        .beginControlFlow("case '$L':", formatCharacter(character))
                        .addStatement("$L = $L", CURR_STATE, dfaNode.getId())
                        .addStatement("break")
                        .endControlFlow()
            );

            codeBlockBuilder
                .beginControlFlow("default: ")
                .addStatement("$L = $L", CURR_STATE, defaultNode.getId())
                .addStatement("$L -= $L", CURR_INDEX, curr.getDistanceFromStart())
                .addStatement("break")
                .endControlFlow();

            codeBlockBuilder
                .endControlFlow();

            codeBlockBuilder.addStatement("break");
        }
    }

    private static void addCurrentDFANodeTransitionsForFindMatches(DFANode curr,
        DFANode defaultNode,
        Builder codeBlockBuilder, boolean lazy
    ) {
        if (curr.isAccepting()) {
            if (lazy) {
                codeBlockBuilder
                    .addStatement("$L = $L", LAST_SUCCESSFUL_MATCH_AT, CURR_INDEX);

                codeBlockBuilder
                    .beginControlFlow("switch ($L)", CURR_CHAR);

                curr.getTransitions().forEach(
                    (character, dfaNode) ->
                        codeBlockBuilder
                            .beginControlFlow("case '$L':", formatCharacter(character))
                            .addStatement("$L = $L", CURR_STATE, dfaNode.getId())
                            .addStatement("break")
                            .endControlFlow()
                );

                codeBlockBuilder
                    .beginControlFlow("default: ");

                addMatchSuccess(codeBlockBuilder);
                addReturnToDefaultNode(defaultNode, codeBlockBuilder);

                codeBlockBuilder
                    .endControlFlow();

                codeBlockBuilder
                    .endControlFlow();

            } else {
                addMatchSuccess(codeBlockBuilder);
                addReturnToDefaultNode(defaultNode, codeBlockBuilder);
            }
        } else {
            addNextStateNavigation(curr, defaultNode, codeBlockBuilder);
        }
        codeBlockBuilder.addStatement("break");
    }

    private static void addNextStateNavigation(DFANode curr, DFANode defaultNode,
        Builder codeBlockBuilder
    ) {
        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_CHAR);

        curr.getTransitions().forEach(
            (character, dfaNode) ->
                codeBlockBuilder
                    .beginControlFlow("case '$L':", formatCharacter(character))
                    .addStatement("$L = $L", CURR_STATE, dfaNode.getId())
                    .addStatement("break")
                    .endControlFlow()
        );

        codeBlockBuilder
            .beginControlFlow("default: ")
            .beginControlFlow("if ($L > $L)", LAST_SUCCESSFUL_MATCH_AT, MATCH_STARTED_AT);

        addMatchSuccess(codeBlockBuilder);

        codeBlockBuilder.endControlFlow();

        addReturnToDefaultNode(defaultNode, codeBlockBuilder);
        if (curr.getDistanceFromStart() > 0) {
            codeBlockBuilder
                .addStatement("$L -= $L", CURR_INDEX, curr.getDistanceFromStart());
        }

        codeBlockBuilder
            .addStatement("break")
            .endControlFlow();

        codeBlockBuilder
            .endControlFlow();

    }

    private static void addMatchSuccess(Builder codeBlockBuilder) {
        codeBlockBuilder
            .addStatement(
                "$L.add(new $T($L, $L))", FOUND, ParseResultMatch.class, MATCH_STARTED_AT,
                CURR_INDEX
            );
    }

    private static void addReturnToDefaultNode(DFANode defaultNode,
        Builder codeBlockBuilder
    ) {
        codeBlockBuilder
            .addStatement("$L = $L + 1", MATCH_STARTED_AT, CURR_INDEX)
            .addStatement("$L = $L", CURR_STATE, defaultNode.getId());
    }

    private static void addStateSwitchForMatches(DFANode startingNode, Builder codeBlockBuilder) {
        // Start switch
        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_STATE);

        addDFANodeCaseForMatches(startingNode, codeBlockBuilder);

        // End switch
        codeBlockBuilder
            .endControlFlow();
    }

    private static void addDFANodeCaseForMatches(DFANode startingNode, Builder codeBlockBuilder) {
        Set<DFANode> done = new HashSet<>();
        Set<DFANode> queue = new HashSet<>();
        queue.add(startingNode);
        while (!queue.isEmpty()) {
            var curr = queue.iterator().next();
            queue.remove(curr);
            if (done.contains(curr)) {
                continue;
            }
            done.add(curr);

            codeBlockBuilder.beginControlFlow("case $L:", curr.getId());

            addCurrentDFANodeTransitionsForMatches(curr, startingNode, codeBlockBuilder);

            codeBlockBuilder.endControlFlow();

            curr.getTransitions().forEach(
                (unused, dfaNode) -> queue.add(dfaNode)
            );

        }
    }

    private static void addStateSwitchForFindMatches(DFANode startingNode, Builder codeBlockBuilder,
        boolean lazy
    ) {
        // Start switch
        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_STATE);

        addDFANodeCaseForFindMatches(startingNode, codeBlockBuilder, lazy);

        // End switch
        codeBlockBuilder
            .endControlFlow();
    }

    private boolean isRegexParser(JavaAnnotation javaAnnotation) {
        return javaAnnotation.getType().isA("hu.nemaberci.regex.annotation.RegularExpression");
    }

    public void generateParser(File sourceLocation, File targetLocation, boolean lazy) {

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
            final var regexValueStr = parsedClass.getAnnotations().stream()
                .filter(this::isRegexParser)
                .findFirst().orElseThrow().getNamedParameter("value").toString();
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

            var matchesMethodBuilder = MethodSpec.methodBuilder("matches")
                .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class);

            matchesMethodBuilder.addCode(matchesFunctionImplementation(regexValue));

            var findMatchesMethodBuilder = MethodSpec.methodBuilder("findMatches")
                .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParseResult.class);

            findMatchesMethodBuilder.addCode(findMatchesFunctionImplementation(regexValue, lazy));

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

    public void generateParser(File sourceLocation, File targetLocation) {

        this.generateParser(sourceLocation, targetLocation, true);

    }

    public static void main(String[] args) {
        new CodeGeneratorOrchestrator().generateParser(
            new File(
                "C:\\Work\\bme\\7_felev\\szakdoga\\test\\src\\main\\java\\hu\\nemaberci\\api\\TestRegexParser.java"),
            new File("C:\\Work\\bme\\7_felev\\szakdoga\\qwe123\\")
        );
    }

}

package hu.nemaberci.generator.generator;

import static hu.nemaberci.generator.annotationprocessor.RegularExpressionAnnotationProcessor.GENERATED_FILE_PACKAGE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CHARS;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_CHAR;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_INDEX;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_STATE_HANDLER;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.FOUND;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.FUNCTION_INPUT_VARIABLE_NAME;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.IMPOSSIBLE_STATE_ID;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.INPUT_STRING_LENGTH;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.LAST_SUCCESSFUL_MATCH_AT;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.MATCH_STARTED_AT;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.STATES_PER_FILE_LOG_2;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.stateHandlerPartName;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.regex.annotation.RegularExpressionParserImplementation;
import hu.nemaberci.regex.api.RegexParser;
import hu.nemaberci.regex.data.ParseResult;
import hu.nemaberci.regex.data.ParseResultMatch;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;

public class ParserFileGenerator {

    private static void initStartingVariables(DFANode startingNode, Builder codeBlockBuilder) {
        codeBlockBuilder
            .addStatement(
                "$L = $L",
                CURR_INDEX,
                0
            )
            .addStatement(
                "$L = $L",
                MATCH_STARTED_AT,
                0
            )
            .addStatement(
                "$L = $L",
                LAST_SUCCESSFUL_MATCH_AT,
                0
            )
            .addStatement(
                "$L = $L",
                CURR_STATE,
                startingNode.getId() % (1 << STATES_PER_FILE_LOG_2)
            )
            .addStatement(
                "$L = $L",
                CURR_STATE_HANDLER,
                startingNode.getId() / (1 << STATES_PER_FILE_LOG_2)
            )
            .addStatement("$L = $L.toCharArray()", CHARS, FUNCTION_INPUT_VARIABLE_NAME)
            .addStatement(
                "final int $L = $L.length()", INPUT_STRING_LENGTH, FUNCTION_INPUT_VARIABLE_NAME);
    }

    private static CodeBlock matchesFunctionImplementation(DFANode startingNode,
        List<DFANode> dfaNodes, Collection<RegexFlag> flags, String className
    ) {
        var codeBlockBuilder = CodeBlock.builder();
        initStartingVariables(startingNode, codeBlockBuilder);
        handleEmptyInputWithBooleanOutput(codeBlockBuilder);
        addMainWhileLoopForMatches(startingNode, dfaNodes, codeBlockBuilder, className);
        checkIfStateIsAcceptingAndReturnBoolean(codeBlockBuilder, dfaNodes, flags, className);
        return codeBlockBuilder.build();
    }

    private static void handleEmptyInputWithBooleanOutput(Builder codeBlockBuilder) {
        codeBlockBuilder
            .beginControlFlow("if ($L == 0)", INPUT_STRING_LENGTH)
            .addStatement("return false")
            .endControlFlow();
    }

    private static void checkIfStateIsAcceptingAndReturnBoolean(Builder codeBlockBuilder,
        List<DFANode> allNodes, Collection<RegexFlag> flags, String className
    ) {

        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_STATE_HANDLER);

        for (int i = 0; i <= allNodes.size() >> STATES_PER_FILE_LOG_2; i++) {

            codeBlockBuilder
                .beginControlFlow("case $L:", i)
                .addStatement(
                    "$L.runEmpty()", stateHandlerPartName(className, i)
                )
                .addStatement("break")
                .endControlFlow();

        }

        codeBlockBuilder
            .endControlFlow();

        codeBlockBuilder.beginControlFlow("switch ($L)", CURR_STATE_HANDLER);
        boolean[] included = new boolean[(1 << STATES_PER_FILE_LOG_2)];

        for (int i = 0; i < allNodes.size(); i++) {
            var handler = i / (1 << STATES_PER_FILE_LOG_2);
            var indexInHandler = i % (1 << STATES_PER_FILE_LOG_2);
            var node = allNodes.get(i);
            if (node.isAccepting()) {
                if (!included[handler]) {
                    codeBlockBuilder
                        .beginControlFlow("case $L:", handler)
                        .beginControlFlow("switch ($L)", CURR_STATE);
                }
                codeBlockBuilder
                    .beginControlFlow("case $L:", indexInHandler)
                    .addStatement("$L = $L - 1", LAST_SUCCESSFUL_MATCH_AT, INPUT_STRING_LENGTH)
                    .endControlFlow();
                included[handler] = true;
            }
            if ((i == allNodes.size() - 1 || indexInHandler == (1 << STATES_PER_FILE_LOG_2) - 1)
                && included[handler]) {
                codeBlockBuilder
                    .endControlFlow()
                    .endControlFlow();
            }
        }

        codeBlockBuilder.endControlFlow();

        if (flags.contains(RegexFlag.END_OF_STRING)) {
            codeBlockBuilder
                .beginControlFlow(
                    "if ($L == $L - 1)",
                    LAST_SUCCESSFUL_MATCH_AT,
                    INPUT_STRING_LENGTH
                );
        } else {
            codeBlockBuilder
                .beginControlFlow(
                    "if ($L > $L)",
                    LAST_SUCCESSFUL_MATCH_AT,
                    MATCH_STARTED_AT
                );
        }

        codeBlockBuilder
            .addStatement("return true")
            .endControlFlow()
            .addStatement("return false");
    }

    private static void addMainWhileLoopForMatches(DFANode startingNode,
        Collection<DFANode> dfaNodes, Builder codeBlockBuilder, String className
    ) {
        codeBlockBuilder
            .beginControlFlow(
                "while ($L < $L)", CURR_INDEX,
                INPUT_STRING_LENGTH
            )
            .beginControlFlow(
                "if ($L == $L)",
                CURR_STATE,
                IMPOSSIBLE_STATE_ID
            )
            .addStatement("return false")
            .endControlFlow()
            .addStatement(
                "$L = $L[$L]", CURR_CHAR, CHARS, CURR_INDEX
                // "$L = $L.charAt($L)", CURR_CHAR, FUNCTION_INPUT_VARIABLE_NAME, CURR_INDEX
            )
            .beginControlFlow("switch ($L)", CURR_STATE_HANDLER);

        for (int i = 0; i <= dfaNodes.size() >> STATES_PER_FILE_LOG_2; i++) {

            codeBlockBuilder
                .beginControlFlow("case $L:", i)
                .addStatement(
                    "$L.run()", stateHandlerPartName(className, i)
                )
                .addStatement("break")
                .endControlFlow();

        }

        codeBlockBuilder
            .endControlFlow()
            .beginControlFlow(
                "if ($L > $L)",
                LAST_SUCCESSFUL_MATCH_AT,
                MATCH_STARTED_AT
            )
            .addStatement("return true")
            .endControlFlow()
            .beginControlFlow(
                "if ($L == $L)",
                CURR_STATE,
                startingNode.getId()
            )
            .addStatement(
                "$L = $L + 1",
                MATCH_STARTED_AT,
                CURR_INDEX
            )
            .endControlFlow()
            .addStatement("$L++", CURR_INDEX)
            .endControlFlow();
    }

    private static CodeBlock findMatchesFunctionImplementation(DFANode startingNode,
        List<DFANode> dfaNodes, Collection<RegexFlag> flags, String className
    ) {
        var codeBlockBuilder = CodeBlock.builder();
        initStartingVariables(startingNode, codeBlockBuilder);
        codeBlockBuilder.addStatement(
            "$L = new $T<>()", FOUND,
            ArrayDeque.class
        );
        handleEmptyInputWithParseResultOutput(codeBlockBuilder);
        addMainWhileLoopForFindMatches(codeBlockBuilder, dfaNodes, className);
        checkIfStateIsAcceptingAndReturnParseResult(codeBlockBuilder, dfaNodes, flags, className);
        return codeBlockBuilder.build();
    }

    private static void checkIfStateIsAcceptingAndReturnParseResult(Builder codeBlockBuilder,
        List<DFANode> allNodes, Collection<RegexFlag> flags, String className
    ) {

        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_STATE_HANDLER);

        for (int i = 0; i <= allNodes.size() >> STATES_PER_FILE_LOG_2; i++) {

            codeBlockBuilder
                .beginControlFlow("case $L:", i)
                .addStatement(
                    "$L.runEmpty()", stateHandlerPartName(className, i)
                )
                .addStatement("break")
                .endControlFlow();

        }

        codeBlockBuilder
            .endControlFlow();

        codeBlockBuilder.beginControlFlow("switch ($L)", CURR_STATE_HANDLER);
        boolean[] included = new boolean[(1 << STATES_PER_FILE_LOG_2)];

        for (int i = 0; i < allNodes.size(); i++) {
            var handler = i / (1 << STATES_PER_FILE_LOG_2);
            var indexInHandler = i % (1 << STATES_PER_FILE_LOG_2);
            var node = allNodes.get(i);
            if (node.isAccepting()) {
                if (!included[handler]) {
                    codeBlockBuilder
                        .beginControlFlow("case $L:", handler)
                        .beginControlFlow("switch ($L)", CURR_STATE);
                }
                codeBlockBuilder
                    .beginControlFlow("case $L:", indexInHandler)
                    .addStatement("$L = $L - 1", LAST_SUCCESSFUL_MATCH_AT, INPUT_STRING_LENGTH)
                    .endControlFlow();
                included[handler] = true;
            }
            if ((i == allNodes.size() - 1 || indexInHandler == (1 << STATES_PER_FILE_LOG_2) - 1)
                && included[handler]) {
                codeBlockBuilder
                    .endControlFlow()
                    .endControlFlow();
            }
        }

        codeBlockBuilder.endControlFlow();

        if (flags.contains(RegexFlag.END_OF_STRING)) {
            codeBlockBuilder
                .beginControlFlow(
                    "if ($L == $L - 1)",
                    LAST_SUCCESSFUL_MATCH_AT,
                    INPUT_STRING_LENGTH
                );
        } else {
            codeBlockBuilder
                .beginControlFlow(
                    "if ($L > $L)",
                    LAST_SUCCESSFUL_MATCH_AT,
                    MATCH_STARTED_AT
                );
        }
        codeBlockBuilder
            .addStatement(
                "$L.add(new $T($L, $L))",
                FOUND,
                ParseResultMatch.class,
                MATCH_STARTED_AT,
                LAST_SUCCESSFUL_MATCH_AT
            )
            .endControlFlow();

        codeBlockBuilder
            .addStatement("return new $T($L)", ParseResult.class, FOUND);
    }

    private static void handleEmptyInputWithParseResultOutput(Builder codeBlockBuilder) {
        codeBlockBuilder
            .beginControlFlow("if ($L == 0)", INPUT_STRING_LENGTH)
            .addStatement("return new $T($T.emptyList())", ParseResult.class, Collections.class)
            .endControlFlow();
    }

    private static void addMainWhileLoopForFindMatches(
        Builder codeBlockBuilder, Collection<DFANode> dfaNodes, String className
    ) {
        codeBlockBuilder
            .beginControlFlow(
                "while ($L < $L)", CURR_INDEX,
                INPUT_STRING_LENGTH
            )
            .beginControlFlow(
                "if ($L < $L)",
                CURR_STATE,
                0
            )
            .addStatement("return new $T($L)", ParseResult.class, FOUND)
            .endControlFlow()
            .addStatement(
                "$L = $L[$L]", CURR_CHAR, CHARS, CURR_INDEX
            )
            .beginControlFlow("switch ($L)", CURR_STATE_HANDLER);

        for (int i = 0; i <= dfaNodes.size() >> STATES_PER_FILE_LOG_2; i++) {

            codeBlockBuilder
                .beginControlFlow("case $L:", i)
                .addStatement(
                    "$L.run()", stateHandlerPartName(className, i)
                )
                .addStatement("break")
                .endControlFlow();

        }

        codeBlockBuilder
            .endControlFlow()
            .addStatement("$L++", CURR_INDEX)
            .endControlFlow();
    }

    private static CodeBlock addResultFunctionImplementation() {
        return CodeBlock.builder()
            .beginControlFlow(
                "if ($L > $L)",
                LAST_SUCCESSFUL_MATCH_AT,
                MATCH_STARTED_AT
            )
            .addStatement(
                "$L.add(new $T($L, $L))",
                FOUND,
                ParseResultMatch.class,
                MATCH_STARTED_AT,
                LAST_SUCCESSFUL_MATCH_AT
            )
            .endControlFlow()
            .addStatement(
                "$L = $L > $L ? $L - 1 : $L",
                CURR_INDEX,
                LAST_SUCCESSFUL_MATCH_AT,
                MATCH_STARTED_AT,
                LAST_SUCCESSFUL_MATCH_AT,
                MATCH_STARTED_AT
            )
            .addStatement(
                "$L = $L + 1",
                MATCH_STARTED_AT,
                CURR_INDEX
            )
            .build();
    }

    public static void createMainParserFile(
        List<DFANode> dfaNodes,
        DFANode startingNode,
        Collection<RegexFlag> flags,
        String className,
        String regex,
        Writer targetLocation
    ) {

        var classImplBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(RegexParser.class)
            .addAnnotation(
                AnnotationSpec.builder(Generated.class)
                    .addMember("value", "$S", "hu.nemaberci.generator")
                    .addMember("date", "$S", Instant.now().toString())
                    .build()
            )
            .addAnnotation(
                AnnotationSpec.builder(RegularExpressionParserImplementation.class)
                    .addMember("value", "$S", regex)
                    .build()
            )
            .addField(
                FieldSpec
                    .builder(
                        ParameterizedTypeName.get(ArrayDeque.class, ParseResultMatch.class), FOUND,
                        Modifier.PRIVATE, Modifier.STATIC
                    )
                    .build()
            )
            .addField(
                FieldSpec.builder(char[].class, CHARS, Modifier.PUBLIC, Modifier.STATIC).build()
            )
            .addField(
                FieldSpec.builder(char.class, CURR_CHAR, Modifier.PUBLIC, Modifier.STATIC).build()
            )
            .addField(
                FieldSpec.builder(int.class, CURR_INDEX, Modifier.PUBLIC, Modifier.STATIC).build()
            )
            .addField(
                FieldSpec.builder(int.class, MATCH_STARTED_AT, Modifier.PUBLIC, Modifier.STATIC)
                    .build()
            )
            .addField(
                FieldSpec.builder(
                    int.class, LAST_SUCCESSFUL_MATCH_AT, Modifier.PUBLIC, Modifier.STATIC).build()
            )
            .addField(
                FieldSpec.builder(int.class, CURR_STATE, Modifier.PUBLIC, Modifier.STATIC).build()
            )
            .addField(
                FieldSpec.builder(int.class, CURR_STATE_HANDLER, Modifier.PUBLIC, Modifier.STATIC)
                    .build()
            );

        var matchesMethodBuilder = MethodSpec.methodBuilder("staticMatches")
            .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(boolean.class)
            .addCode(matchesFunctionImplementation(startingNode, dfaNodes, flags, className));

        var findMatchesMethodBuilder = MethodSpec.methodBuilder("staticFindMatches")
            .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(ParseResult.class)
            .addCode(findMatchesFunctionImplementation(startingNode, dfaNodes, flags, className));

        var addResultMethodBuilder = MethodSpec.methodBuilder("addResult")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addCode(addResultFunctionImplementation())
            .returns(void.class);

        classImplBuilder
            .addMethod(matchesMethodBuilder.build())
            .addMethod(findMatchesMethodBuilder.build())
            .addMethod(addResultMethodBuilder.build())
            .addMethod(
                MethodSpec.methodBuilder("matches")
                    .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(boolean.class)
                    .addCode(
                        "return $L.staticMatches($L);", className, FUNCTION_INPUT_VARIABLE_NAME)
                    .build()
            )
            .addMethod(
                MethodSpec.methodBuilder("findMatches")
                    .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParseResult.class)
                    .addCode(
                        "return $L.staticFindMatches($L);", className, FUNCTION_INPUT_VARIABLE_NAME)
                    .build()
            );

        var javaFileBuilder = JavaFile.builder(
            GENERATED_FILE_PACKAGE,
            classImplBuilder.build()
        );
        try {
            javaFileBuilder.build().writeTo(targetLocation);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}

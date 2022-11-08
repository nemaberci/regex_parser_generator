package hu.nemaberci.generator.generator;

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
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.regex.annotation.RegularExpressionParserImplementation;
import hu.nemaberci.regex.api.AbstractParserPart;
import hu.nemaberci.regex.api.RegexParser;
import hu.nemaberci.regex.data.ParseResult;
import hu.nemaberci.regex.data.ParseResultMatch;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
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
                startingNode.getId()
            )
            .addStatement(
                "$L = $L",
                CURR_STATE_HANDLER,
                getPartVariableName(startingNode.getId() / (1 << STATES_PER_FILE_LOG_2))
            )
            .addStatement("$L = $L.toCharArray()", CHARS, FUNCTION_INPUT_VARIABLE_NAME)
            .addStatement(
                "final int $L = $L.length()", INPUT_STRING_LENGTH, FUNCTION_INPUT_VARIABLE_NAME);
    }

    private static CodeBlock matchesFunctionImplementation(DFANode startingNode) {
        var codeBlockBuilder = CodeBlock.builder();
        initStartingVariables(startingNode, codeBlockBuilder);
        handleEmptyInputWithBooleanOutput(codeBlockBuilder);
        addMainWhileLoopForMatches(startingNode, codeBlockBuilder);
        checkIfStateIsAcceptingAndReturnBoolean(codeBlockBuilder);
        return codeBlockBuilder.build();
    }

    private static void handleEmptyInputWithBooleanOutput(Builder codeBlockBuilder) {
        codeBlockBuilder
            .beginControlFlow("if ($L == 0)", INPUT_STRING_LENGTH)
            .addStatement("return false")
            .endControlFlow();
    }

    private static void checkIfStateIsAcceptingAndReturnBoolean(Builder codeBlockBuilder) {

        codeBlockBuilder
            .beginControlFlow(
                "if ($L > $L)",
                LAST_SUCCESSFUL_MATCH_AT,
                MATCH_STARTED_AT
            )
            .addStatement("return true")
            .endControlFlow()
            .addStatement("return false");
    }

    private static void addMainWhileLoopForMatches(DFANode startingNode, Builder codeBlockBuilder) {
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
            )
            .addStatement(
                "$L.run()", CURR_STATE_HANDLER
            )
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

    private static CodeBlock findMatchesFunctionImplementation(DFANode startingNode) {
        var codeBlockBuilder = CodeBlock.builder();
        initStartingVariables(startingNode, codeBlockBuilder);
        codeBlockBuilder.addStatement(
            "$L = new $T<>()", FOUND,
            ArrayDeque.class
        );
        handleEmptyInputWithParseResultOutput(codeBlockBuilder);
        addMainWhileLoopForFindMatches(codeBlockBuilder);
        checkIfStateIsAcceptingAndReturnParseResult(codeBlockBuilder);
        return codeBlockBuilder.build();
    }

    private static void checkIfStateIsAcceptingAndReturnParseResult(Builder codeBlockBuilder) {

        codeBlockBuilder
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
            .addStatement("return new $T($L)", ParseResult.class, FOUND);
    }

    private static void handleEmptyInputWithParseResultOutput(Builder codeBlockBuilder) {
        codeBlockBuilder
            .beginControlFlow("if ($L == 0)", INPUT_STRING_LENGTH)
            .addStatement("return new $T($T.emptyList())", ParseResult.class, Collections.class)
            .endControlFlow();
    }

    private static void addMainWhileLoopForFindMatches(
        Builder codeBlockBuilder
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
            .addStatement(
                "$L.run()", CURR_STATE_HANDLER
            )
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
        Collection<DFANode> dfaNodes,
        DFANode startingNode,
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
                        Modifier.PRIVATE
                    )
                    .build()
            )
            .addField(
                FieldSpec.builder(char[].class, CHARS, Modifier.PUBLIC).build()
            )
            .addField(
                FieldSpec.builder(char.class, CURR_CHAR, Modifier.PUBLIC).build()
            )
            .addField(
                FieldSpec.builder(int.class, CURR_INDEX, Modifier.PUBLIC).build()
            )
            .addField(
                FieldSpec.builder(int.class, MATCH_STARTED_AT, Modifier.PUBLIC).build()
            )
            .addField(
                FieldSpec.builder(int.class, LAST_SUCCESSFUL_MATCH_AT, Modifier.PUBLIC).build()
            )
            .addField(
                FieldSpec.builder(int.class, CURR_STATE, Modifier.PUBLIC).build()
            )
            .addField(
                FieldSpec.builder(AbstractParserPart.class, CURR_STATE_HANDLER, Modifier.PUBLIC)
                    .build()
            );

        var constructorBuilder = CodeBlock.builder();

        for (int i = 0;
            i < (dfaNodes.size() + (1 << STATES_PER_FILE_LOG_2) - 1) / (1 << STATES_PER_FILE_LOG_2);
            i++) {

            classImplBuilder.addField(
                FieldSpec.builder(
                        ClassName.get(
                            "hu.nemaberci.regex.generated", stateHandlerPartName(className, i)),
                        getPartVariableName(i),
                        Modifier.PUBLIC,
                        Modifier.FINAL
                    )
                    .build()
            );

            constructorBuilder.addStatement(
                "$L = new $L(this)",
                getPartVariableName(i),
                stateHandlerPartName(className, i)
            );

        }

        classImplBuilder.addMethod(
            MethodSpec.constructorBuilder()
                .addCode(constructorBuilder.build())
                .addModifiers(Modifier.PUBLIC)
                .build()
        );

        var matchesMethodBuilder = MethodSpec.methodBuilder("matches")
            .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(boolean.class)
            .addCode(matchesFunctionImplementation(startingNode));

        var findMatchesMethodBuilder = MethodSpec.methodBuilder("findMatches")
            .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParseResult.class)
            .addCode(findMatchesFunctionImplementation(startingNode));

        var addResultMethodBuilder = MethodSpec.methodBuilder("addResult")
            .addModifiers(Modifier.PUBLIC)
            .addCode(addResultFunctionImplementation())
            .returns(void.class);

        classImplBuilder
            .addMethod(matchesMethodBuilder.build())
            .addMethod(findMatchesMethodBuilder.build())
            .addMethod(addResultMethodBuilder.build());

        var javaFileBuilder = JavaFile.builder(
            "hu.nemaberci.regex.generated",
            classImplBuilder.build()
        );
        try {
            javaFileBuilder.build().writeTo(targetLocation);
            targetLocation.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static String getPartVariableName(int i) {
        return "part_" + i;
    }

}

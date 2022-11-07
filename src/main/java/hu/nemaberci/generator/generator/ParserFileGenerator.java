package hu.nemaberci.generator.generator;

import static hu.nemaberci.generator.annotationprocessor.RegularExpressionAnnotationProcessor.GENERATED_FILE_PACKAGE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.*;
import static hu.nemaberci.generator.regex.dfa.util.DFAUtils.extractAllNodes;

import com.ibm.icu.impl.RuleCharacterIterator.Position;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.data.DFAParseResult;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;

public class ParserFileGenerator {

    private static void initStartingVariables(DFANode startingNode, Builder codeBlockBuilder) {
        codeBlockBuilder
            .addStatement("$L[$L] = $L",
                CURRENT_STATE_ARRAY, CURR_INDEX_POSITION,
                0
            )
            .addStatement("$L[$L] = $L",
                CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION,
                0
            )
            .addStatement("$L[$L] = $L",
                CURRENT_STATE_ARRAY, LAST_SUCCESSFUL_MATCH_AT_POSITION,
                0
            )
            .addStatement("$L[$L] = $L",
                CURRENT_STATE_ARRAY, CURR_STATE_POSITION,
                startingNode.getId()
            )
            .addStatement("final char[] $L = $L.toCharArray()", CHARS, FUNCTION_INPUT_VARIABLE_NAME)
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
                "if ($L[$L] > $L[$L])",
                CURRENT_STATE_ARRAY, LAST_SUCCESSFUL_MATCH_AT_POSITION,
                CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION
            )
            .addStatement("return true")
            .endControlFlow()
            .addStatement("return false");
    }

    private static void addMainWhileLoopForMatches(DFANode startingNode, Builder codeBlockBuilder) {
        codeBlockBuilder
            .beginControlFlow("while ($L[$L] < $L)", CURRENT_STATE_ARRAY, CURR_INDEX_POSITION, INPUT_STRING_LENGTH)
            .beginControlFlow(
                "if ($L[$L] == $L)",
                CURRENT_STATE_ARRAY, CURR_STATE_POSITION,
                IMPOSSIBLE_STATE_ID
            )
                .addStatement("return false")
            .endControlFlow()
            .addStatement("final char $L = $L[$L[$L]]", CURR_CHAR, CHARS, CURRENT_STATE_ARRAY, CURR_INDEX_POSITION)
            .addStatement("$L[$L[$L] / $L].accept($L, $L)", STATE_HANDLERS, CURRENT_STATE_ARRAY, CURR_STATE_POSITION, STATES_PER_FILE, CURR_CHAR, CURRENT_STATE_ARRAY)
            .beginControlFlow(
                "if ($L[$L] > $L[$L])",
                CURRENT_STATE_ARRAY, LAST_SUCCESSFUL_MATCH_AT_POSITION,
                CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION
            )
                .addStatement("return true")
            .endControlFlow()
            .beginControlFlow(
                "if ($L[$L] == $L)",
                CURRENT_STATE_ARRAY, CURR_STATE_POSITION,
                startingNode.getId()
            )
                .addStatement(
                    "$L[$L] = $L[$L] + 1",
                    CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION,
                    CURRENT_STATE_ARRAY, CURR_INDEX_POSITION
                )
            .endControlFlow()
            .addStatement("$L[$L]++", CURRENT_STATE_ARRAY, CURR_INDEX_POSITION)
            .endControlFlow();
    }

    private static CodeBlock findMatchesFunctionImplementation(DFANode startingNode) {
        var codeBlockBuilder = CodeBlock.builder();
        initStartingVariables(startingNode, codeBlockBuilder);
        codeBlockBuilder.addStatement(
            "final $T<$T> $L = new $T<>()", ArrayDeque.class, ParseResultMatch.class, FOUND,
            ArrayDeque.class
        );
        handleEmptyInputWithParseResultOutput(codeBlockBuilder);
        addMainWhileLoopForFindMatches(startingNode, codeBlockBuilder);
        checkIfStateIsAcceptingAndReturnParseResult(codeBlockBuilder);
        return codeBlockBuilder.build();
    }

    private static void checkIfStateIsAcceptingAndReturnParseResult(Builder codeBlockBuilder) {

        codeBlockBuilder
            .beginControlFlow(
                "if ($L[$L] > $L[$L])",
                CURRENT_STATE_ARRAY, LAST_SUCCESSFUL_MATCH_AT_POSITION,
                CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION
            )
            .addStatement(
                "$L.add(new $T($L[$L], $L[$L]))",
                FOUND,
                ParseResultMatch.class,
                CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION,
                CURRENT_STATE_ARRAY, LAST_SUCCESSFUL_MATCH_AT_POSITION
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

    private static void addMainWhileLoopForFindMatches(DFANode startingNode,
        Builder codeBlockBuilder
    ) {
        codeBlockBuilder
            .beginControlFlow("while ($L[$L] < $L)", CURRENT_STATE_ARRAY, CURR_INDEX_POSITION, INPUT_STRING_LENGTH)
            .beginControlFlow(
                "if ($L[$L] == $L)",
                CURRENT_STATE_ARRAY, CURR_STATE_POSITION,
                IMPOSSIBLE_STATE_ID
            )
                .addStatement("return new $T($L)", ParseResult.class, FOUND)
            .endControlFlow()
            .addStatement("final char $L = $L[$L[$L]]", CURR_CHAR, CHARS, CURRENT_STATE_ARRAY, CURR_INDEX_POSITION)
            .addStatement("$L[$L[$L] / $L].accept($L, $L)", STATE_HANDLERS, CURRENT_STATE_ARRAY, CURR_STATE_POSITION, STATES_PER_FILE, CURR_CHAR, CURRENT_STATE_ARRAY)
            .beginControlFlow(
                "if ($L[$L] > $L[$L])",
                CURRENT_STATE_ARRAY, LAST_SUCCESSFUL_MATCH_AT_POSITION,
                CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION
            )
                .addStatement(
                    "$L.add(new $T($L[$L], $L[$L]))",
                    FOUND,
                    ParseResultMatch.class,
                    CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION,
                    CURRENT_STATE_ARRAY, LAST_SUCCESSFUL_MATCH_AT_POSITION
                )
            .endControlFlow()
            .beginControlFlow(
                "if ($L[$L] == $L)",
                CURRENT_STATE_ARRAY, CURR_STATE_POSITION,
                startingNode.getId()
            )
                .addStatement(
                    "$L[$L] = $L[$L] > $L[$L] ? $L[$L] - 1 : $L[$L]",
                    CURRENT_STATE_ARRAY, CURR_INDEX_POSITION,
                    CURRENT_STATE_ARRAY, LAST_SUCCESSFUL_MATCH_AT_POSITION,
                    CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION,
                    CURRENT_STATE_ARRAY, LAST_SUCCESSFUL_MATCH_AT_POSITION,
                    CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION
                )
                .addStatement(
                    "$L[$L] = $L[$L] + 1",
                    CURRENT_STATE_ARRAY, MATCH_STARTED_AT_POSITION,
                    CURRENT_STATE_ARRAY, CURR_INDEX_POSITION
                )
            .endControlFlow()
            .addStatement("$L[$L]++", CURRENT_STATE_ARRAY, CURR_INDEX_POSITION)
            .endControlFlow();
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
            );

        var stateHandlersType = ArrayTypeName.of(ParameterizedTypeName.get(ClassName.get(BiConsumer.class), TypeName.CHAR.box(), ArrayTypeName.of(TypeName.INT)));

        classImplBuilder
            .addField(
                FieldSpec
                    .builder(int[].class, CURRENT_STATE_ARRAY, Modifier.PRIVATE,
                        Modifier.FINAL)
                    .initializer("new int[]{$L, $L, $L, $L}", 0, startingNode.getId(), 0, 0)
                    .build()
            )
            .addField(
                FieldSpec
                    .builder(stateHandlersType, STATE_HANDLERS, Modifier.FINAL, Modifier.PRIVATE)
                    .initializer("new $T[$L]", BiConsumer.class, (dfaNodes.size() + STATES_PER_FILE - 1) / STATES_PER_FILE)
                    .build()
            );

        var constructorBuilder = CodeBlock.builder();

        for (int i = 0; i < (dfaNodes.size() + STATES_PER_FILE - 1) / STATES_PER_FILE; i++) {

            constructorBuilder.addStatement(
                "$L[$L] = new $L()",
                STATE_HANDLERS,
                i,
                className + "_part_" + i
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
            .returns(boolean.class);

        matchesMethodBuilder.addCode(matchesFunctionImplementation(startingNode));

        var findMatchesMethodBuilder = MethodSpec.methodBuilder("findMatches")
            .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParseResult.class);

        findMatchesMethodBuilder.addCode(findMatchesFunctionImplementation(startingNode));

        classImplBuilder
            .addMethod(matchesMethodBuilder.build())
            .addMethod(findMatchesMethodBuilder.build());

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

}

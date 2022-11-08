package hu.nemaberci.generator.generator;

import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_CHAR;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.IMPOSSIBLE_STATE_ID;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.PARENT_PARSER;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.UTIL;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.nameOfFunctionThatAddsResultFound;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.nameOfFunctionThatLeadsToState;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.nameOfFunctionThatRestartsSearch;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.regex.api.AbstractParserPart;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.Collection;
import java.util.Map.Entry;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;

public class IndividualStateHandlerGenerator {

    private static void addCurrentDFANodeTransitionsForFindMatches(DFANode curr,
        DFANode defaultNode,
        Builder codeBlockBuilder,
        Collection<RegexFlag> flags
    ) {
        if (curr.isAccepting()) {
            codeBlockBuilder
                .addStatement(
                    "$L.$L.$L()",
                    PARENT_PARSER, UTIL, nameOfFunctionThatAddsResultFound()
                );
            if (curr.getTransitions().isEmpty()) {
                codeBlockBuilder
                    .addStatement(
                        "$L.$L.$L()",
                        PARENT_PARSER, UTIL, nameOfFunctionThatRestartsSearch()
                    );
            } else {
                lazyCharSwitch(curr, defaultNode, codeBlockBuilder, flags);
            }
        } else {
            addNextStateNavigation(curr, defaultNode, codeBlockBuilder, flags);
        }
    }

    private static void lazyCharSwitch(DFANode curr, DFANode defaultNode, Builder codeBlockBuilder,
        Collection<RegexFlag> flags
    ) {
        codeBlockBuilder
            .beginControlFlow("switch ($L.$L)", PARENT_PARSER, CURR_CHAR);

        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(curr, defaultNode, codeBlockBuilder, edge)
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

        addReturnToDefaultNode(curr, defaultNode, codeBlockBuilder, flags);

        codeBlockBuilder
            .endControlFlow();
    }

    private static void addNextStateNavigation(DFANode curr, DFANode defaultNode,
        Builder codeBlockBuilder, Collection<RegexFlag> flags
    ) {

        codeBlockBuilder
            .beginControlFlow("switch ($L.$L)", PARENT_PARSER, CURR_CHAR);

        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(curr, defaultNode, codeBlockBuilder, edge)
        );

        codeBlockBuilder
            .beginControlFlow("default:");

        addReturnToDefaultNode(curr, defaultNode, codeBlockBuilder, flags);

        codeBlockBuilder
            .addStatement("break")
            .endControlFlow();

        codeBlockBuilder
            .endControlFlow();

    }

    private static void caseOfEdge(DFANode curr, DFANode defaultNode, Builder codeBlockBuilder,
        Entry<Character, DFANode> edge
    ) {
        if (edge.getValue() != null) {

            codeBlockBuilder
                .beginControlFlow("case $L:", (int) edge.getKey())
                .addStatement(
                    "$L.$L.$L()",
                    PARENT_PARSER, UTIL, nameOfFunctionThatLeadsToState(edge.getValue().getId())
                )
                .addStatement("break")
                .endControlFlow();

        } else {

            codeBlockBuilder
                .beginControlFlow("case $L:", (int) edge.getKey())
                .addStatement(
                    "$L.$L.$L()",
                    PARENT_PARSER, UTIL, nameOfFunctionThatRestartsSearch()
                )
                .addStatement("break")
                .endControlFlow();

        }
    }

    private static void addReturnToDefaultNode(DFANode curr, DFANode defaultNode,
        Builder codeBlockBuilder, Collection<RegexFlag> flags
    ) {

        if (flags.contains(RegexFlag.START_OF_STRING)) {

            // If the string has to match from the start and there is match in the DFA,
            // we move to an impossible state.
            codeBlockBuilder.addStatement("$L.$L = $L",
                PARENT_PARSER, CURR_STATE,
                IMPOSSIBLE_STATE_ID
            );

        } else {

            if (curr.getDefaultTransition() != null) {

                codeBlockBuilder
                    .addStatement(
                        "$L.$L.$L()",
                        PARENT_PARSER, UTIL,
                        nameOfFunctionThatLeadsToState(curr.getDefaultTransition().getId())
                    );

            } else {

                codeBlockBuilder
                    .addStatement("$L.$L.$L()",
                        PARENT_PARSER, UTIL, nameOfFunctionThatRestartsSearch()
                    );

            }

        }

    }

    private static void handleStates(Builder codeBlockBuilder, DFANode node,
        Collection<RegexFlag> flags, DFANode startingNode
    ) {

        addCurrentDFANodeTransitionsForFindMatches(
            node, startingNode, codeBlockBuilder, flags);

    }

    public static void createIndividualStateHandler(
        DFANode node,
        Collection<RegexFlag> flags,
        DFANode startingNode,
        String className,
        String parentClassName,
        Writer targetLocation
    ) {

        var classImplBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(
                AnnotationSpec.builder(Generated.class)
                    .addMember("value", "$S", "hu.nemaberci.generator")
                    .addMember("date", "$S", Instant.now().toString())
                    .build()
            )
            .superclass(AbstractParserPart.class)
            .addField(
                FieldSpec.builder(
                    ClassName.get("hu.nemaberci.regex.generated", parentClassName),
                    PARENT_PARSER,
                    Modifier.PRIVATE
                ).build()
            )
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addParameter(
                        ClassName.get("hu.nemaberci.regex.generated", parentClassName), "parent")
                    .addCode("$L = $L;", PARENT_PARSER, "parent")
                    .addModifiers(Modifier.PUBLIC)
                    .build()
            );
        var codeBlockBuilder = CodeBlock.builder();
        handleStates(codeBlockBuilder, node, flags, startingNode);

        classImplBuilder.addMethod(
            MethodSpec.methodBuilder("run")
                .returns(void.class)
                .addCode(codeBlockBuilder.build())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .build()
        );

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

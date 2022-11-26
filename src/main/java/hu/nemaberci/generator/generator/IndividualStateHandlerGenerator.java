package hu.nemaberci.generator.generator;

import static hu.nemaberci.generator.annotationprocessor.RegularExpressionAnnotationProcessor.GENERATED_FILE_PACKAGE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_CHAR;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.IMPOSSIBLE_STATE_ID;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.nameOfFunctionThatAddsResultFound;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.nameOfFunctionThatLeadsToState;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.nameOfFunctionThatRestartsSearch;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.utilName;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.Collection;
import java.util.Map.Entry;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;

public class IndividualStateHandlerGenerator {

    private static void addCurrentDFANodeTransitionsForFindMatches(DFANode curr,
        Builder codeBlockBuilder,
        Collection<RegexFlag> flags,
        String parentClassName
    ) {
        if (curr.isAccepting()) {
            codeBlockBuilder
                .addStatement(
                    "$T.$L()",
                    ClassName.get(GENERATED_FILE_PACKAGE, utilName(parentClassName)),
                    nameOfFunctionThatAddsResultFound()
                );
            if (curr.getTransitions().isEmpty()) {
                codeBlockBuilder
                    .addStatement(
                        "$T.$L()",
                        ClassName.get(GENERATED_FILE_PACKAGE, utilName(parentClassName)),
                        nameOfFunctionThatRestartsSearch()
                    );
            } else {
                lazyCharSwitch(curr, codeBlockBuilder, flags, parentClassName);
            }
        } else {
            addNextStateNavigation(curr, codeBlockBuilder, flags, parentClassName);
        }
    }

    private static void lazyCharSwitch(DFANode curr, Builder codeBlockBuilder,
        Collection<RegexFlag> flags, String parentClassName
    ) {
        codeBlockBuilder
            .beginControlFlow(
                "switch ($T.$L)", ClassName.get(GENERATED_FILE_PACKAGE, parentClassName),
                CURR_CHAR
            );

        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(codeBlockBuilder, edge, parentClassName)
        );

        defaultLazyCase(curr, codeBlockBuilder, flags, parentClassName);

        codeBlockBuilder
            .endControlFlow();
    }

    private static void defaultLazyCase(DFANode curr, Builder codeBlockBuilder,
        Collection<RegexFlag> flags, String parentClassName
    ) {
        codeBlockBuilder
            .beginControlFlow("default: ");

        addReturnToDefaultNode(curr, codeBlockBuilder, flags, parentClassName);

        codeBlockBuilder
            .endControlFlow();
    }

    private static void addNextStateNavigation(DFANode curr,
        Builder codeBlockBuilder, Collection<RegexFlag> flags, String parentClassName
    ) {

        codeBlockBuilder
            .beginControlFlow(
                "switch ($T.$L)", ClassName.get(GENERATED_FILE_PACKAGE, parentClassName),
                CURR_CHAR
            );

        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(codeBlockBuilder, edge, parentClassName)
        );

        codeBlockBuilder
            .beginControlFlow("default:");

        addReturnToDefaultNode(curr, codeBlockBuilder, flags, parentClassName);

        codeBlockBuilder
            .addStatement("break")
            .endControlFlow();

        codeBlockBuilder
            .endControlFlow();

    }

    private static void caseOfEdge(Builder codeBlockBuilder,
        Entry<Character, DFANode> edge, String parentClassName
    ) {
        if (edge.getValue() != null) {

            codeBlockBuilder
                .beginControlFlow("case $L:", (int) edge.getKey())
                .addStatement(
                    "$T.$L()",
                    ClassName.get(GENERATED_FILE_PACKAGE, utilName(parentClassName)),
                    nameOfFunctionThatLeadsToState(edge.getValue().getId())
                )
                .addStatement("break")
                .endControlFlow();

        } else {

            codeBlockBuilder
                .beginControlFlow("case $L:", (int) edge.getKey())
                .addStatement(
                    "$T.$L()",
                    ClassName.get(GENERATED_FILE_PACKAGE, utilName(parentClassName)),
                    nameOfFunctionThatRestartsSearch()
                )
                .addStatement("break")
                .endControlFlow();

        }
    }

    private static void addReturnToDefaultNode(DFANode curr,
        Builder codeBlockBuilder, Collection<RegexFlag> flags, String parentClassName
    ) {

        if (flags.contains(RegexFlag.START_OF_STRING)) {

            // If the string has to match from the start and there is match in the DFA,
            // we move to an impossible state.
            codeBlockBuilder.addStatement("$T.$L = $L",
                ClassName.get(GENERATED_FILE_PACKAGE, parentClassName), CURR_STATE,
                IMPOSSIBLE_STATE_ID
            );

        } else {

            if (curr.getDefaultTransition() != null) {

                codeBlockBuilder
                    .addStatement(
                        "$T.$L()",
                        ClassName.get(GENERATED_FILE_PACKAGE, utilName(parentClassName)),
                        nameOfFunctionThatLeadsToState(curr.getDefaultTransition().getId())
                    );

            } else {

                codeBlockBuilder
                    .addStatement(
                        "$T.$L()",
                        ClassName.get(GENERATED_FILE_PACKAGE, utilName(parentClassName)),
                        nameOfFunctionThatRestartsSearch()
                    );

            }

        }

    }

    private static void handleStates(Builder codeBlockBuilder, DFANode node,
        Collection<RegexFlag> flags, String parentClassName
    ) {

        addCurrentDFANodeTransitionsForFindMatches(
            node, codeBlockBuilder, flags, parentClassName);

    }

    private static void createEmptyRun(Builder codeBlockBuilder, DFANode node,
        String parentClassName
    ) {

        if (node.isAccepting()) {
            codeBlockBuilder
                .addStatement(
                    "$T.$L()",
                    ClassName.get(GENERATED_FILE_PACKAGE, utilName(parentClassName)),
                    nameOfFunctionThatAddsResultFound()
                );
        }

    }

    public static void createIndividualStateHandler(
        DFANode node,
        Collection<RegexFlag> flags,
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
            );
        var codeBlockBuilder = CodeBlock.builder();
        handleStates(codeBlockBuilder, node, flags, parentClassName);

        classImplBuilder.addMethod(
            MethodSpec.methodBuilder("run")
                .returns(void.class)
                .addCode(codeBlockBuilder.build())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .build()
        );

        codeBlockBuilder = CodeBlock.builder();
        createEmptyRun(codeBlockBuilder, node, parentClassName);
        classImplBuilder.addMethod(
            MethodSpec.methodBuilder("runEmpty")
                .returns(void.class)
                .addCode(codeBlockBuilder.build())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
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

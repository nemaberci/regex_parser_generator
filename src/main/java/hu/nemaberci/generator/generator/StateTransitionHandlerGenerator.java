package hu.nemaberci.generator.generator;

import static hu.nemaberci.generator.annotationprocessor.RegularExpressionAnnotationProcessor.GENERATED_FILE_PACKAGE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_INDEX;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_STATE_HANDLER;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.LAST_SUCCESSFUL_MATCH_AT;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.STATES_PER_FILE_LOG_2;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.nameOfFunctionThatAddsResultFound;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.nameOfFunctionThatLeadsToState;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.nameOfFunctionThatRestartsSearch;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;

public class StateTransitionHandlerGenerator {

    public static void createStateTransitionHandlerUtil(
        int states,
        Writer targetLocation,
        DFANode defaultNode,
        String className,
        String parentClassName
    ) {

        var classImplBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(
                AnnotationSpec.builder(Generated.class)
                    .addMember("value", "$S", "hu.nemaberci.generator")
                    .addMember("date", "$S", Instant.now().toString())
                    .build()
            );

        for (int i = 0; i < states; i++) {

            var method = MethodSpec.methodBuilder(nameOfFunctionThatLeadsToState(i))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addCode(
                    CodeBlock.builder()
                        .addStatement(
                            "$T.$L = $L",
                            ClassName.get(GENERATED_FILE_PACKAGE, parentClassName),
                            CURR_STATE,
                            i % (1 << STATES_PER_FILE_LOG_2)
                        )
                        .addStatement(
                            "$T.$L = $L",
                            ClassName.get(GENERATED_FILE_PACKAGE, parentClassName),
                            CURR_STATE_HANDLER,
                            i >> STATES_PER_FILE_LOG_2
                        )
                        .build()
                );

            classImplBuilder.addMethod(method.build());

        }

        var restartSearchMethod = MethodSpec.methodBuilder(nameOfFunctionThatRestartsSearch())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(void.class)
            .addCode(
                CodeBlock.builder()
                    .addStatement(
                        "$T.$L = $L",
                        ClassName.get(GENERATED_FILE_PACKAGE, parentClassName), CURR_STATE,
                        defaultNode.getId()
                    )
                    .addStatement(
                        "$T.$L = $L",
                        ClassName.get(GENERATED_FILE_PACKAGE, parentClassName),
                        CURR_STATE_HANDLER,
                        defaultNode.getId() >> STATES_PER_FILE_LOG_2
                    )
                    .addStatement(
                        "$T.addResult()",
                        ClassName.get(GENERATED_FILE_PACKAGE, parentClassName)
                    )
                    .build()
            );

        classImplBuilder.addMethod(restartSearchMethod.build());

        var resultFoundMethod = MethodSpec.methodBuilder(nameOfFunctionThatAddsResultFound())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(void.class)
            .addCode(
                CodeBlock.builder()
                    .addStatement(
                        "$T.$L = $T.$L",
                        ClassName.get(GENERATED_FILE_PACKAGE, parentClassName),
                        LAST_SUCCESSFUL_MATCH_AT,
                        ClassName.get(GENERATED_FILE_PACKAGE, parentClassName),
                        CURR_INDEX
                    )
                    .build()
            );

        classImplBuilder.addMethod(resultFoundMethod.build());

        var javaFileBuilder = JavaFile.builder(
            GENERATED_FILE_PACKAGE,
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

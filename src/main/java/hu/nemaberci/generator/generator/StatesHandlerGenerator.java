package hu.nemaberci.generator.generator;

import static hu.nemaberci.generator.annotationprocessor.RegularExpressionAnnotationProcessor.GENERATED_FILE_PACKAGE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.individualStateHandlerName;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;

public class StatesHandlerGenerator {

    public static void createFileForStates(
        String className,
        String parentClassName,
        Writer targetLocation,
        int startingId,
        int endingId
    ) {

        var classImplBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(
                AnnotationSpec.builder(Generated.class)
                    .addMember("value", "$S", "hu.nemaberci.generator")
                    .addMember("date", "$S", Instant.now().toString())
                    .build()
            );

        Builder codeBlockBuilder = CodeBlock.builder();

        codeBlockBuilder.beginControlFlow(
            "switch ($T.$L)", ClassName.get(GENERATED_FILE_PACKAGE, parentClassName), CURR_STATE);

        for (int i = startingId; i <= endingId; i++) {

            codeBlockBuilder
                .beginControlFlow("case $L:", i - startingId)
                .addStatement(
                    "$T.run()", ClassName.get(GENERATED_FILE_PACKAGE,
                        individualStateHandlerName(parentClassName, i)
                    ))
                .addStatement("break")
                .endControlFlow();

        }

        codeBlockBuilder.endControlFlow();

        classImplBuilder.addMethod(
            MethodSpec.methodBuilder("run")
                .returns(void.class)
                .addCode(
                    codeBlockBuilder.build()
                )
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .build()
        );

        codeBlockBuilder = CodeBlock.builder();

        codeBlockBuilder.beginControlFlow(
            "switch ($T.$L)", ClassName.get(GENERATED_FILE_PACKAGE, parentClassName), CURR_STATE);

        for (int i = startingId; i <= endingId; i++) {

            codeBlockBuilder
                .beginControlFlow("case $L:", i - startingId)
                .addStatement(
                    "$T.runEmpty()", ClassName.get(GENERATED_FILE_PACKAGE,
                        individualStateHandlerName(parentClassName, i)
                    ))
                .addStatement("break")
                .endControlFlow();

        }

        codeBlockBuilder.endControlFlow();

        classImplBuilder.addMethod(
            MethodSpec.methodBuilder("runEmpty")
                .returns(void.class)
                .addCode(
                    codeBlockBuilder.build()
                )
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .build()
        );

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

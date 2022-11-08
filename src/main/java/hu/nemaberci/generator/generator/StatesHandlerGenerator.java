package hu.nemaberci.generator.generator;

import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.PARENT_PARSER;
import static hu.nemaberci.generator.generator.CodeGeneratorOrchestrator.individualStateHandlerName;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import hu.nemaberci.regex.api.AbstractParserPart;
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

        Builder stateInitializer = CodeBlock.builder();

        for (int i = startingId; i <= endingId; i++) {

            stateInitializer.addStatement(
                "$L = new $T($L)", fieldNameForState(i - startingId),
                ClassName.get(
                    "hu.nemaberci.regex.generated",
                    individualStateHandlerName(parentClassName, i)
                ), PARENT_PARSER
            );

        }

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
                    .addCode(stateInitializer.build())
                    .addModifiers(Modifier.PUBLIC)
                    .build()
            );

        for (int i = startingId; i <= endingId; i++) {
            classImplBuilder.addField(
                FieldSpec.builder(
                        ClassName.get(
                            "hu.nemaberci.regex.generated",
                            individualStateHandlerName(parentClassName, i)
                        ),
                        fieldNameForState(i - startingId),
                        Modifier.PRIVATE,
                        Modifier.FINAL
                    )
                    .build()
            );
        }

        Builder codeBlockBuilder = CodeBlock.builder();

        codeBlockBuilder.beginControlFlow("switch ($L.$L)", PARENT_PARSER, CURR_STATE);

        for (int i = startingId; i <= endingId; i++) {

            codeBlockBuilder
                .beginControlFlow("case $L:", i - startingId)
                .addStatement("$L.run()", fieldNameForState(i - startingId))
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

    public static String fieldNameForState(int stateId) {
        return "state_" + stateId;
    }

}

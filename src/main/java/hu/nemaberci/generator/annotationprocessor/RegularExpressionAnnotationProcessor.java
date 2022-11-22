package hu.nemaberci.generator.annotationprocessor;

import hu.nemaberci.generator.generator.CodeGeneratorOrchestrator;
import hu.nemaberci.regex.annotation.RegularExpression;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes(
    "hu.nemaberci.regex.annotation.RegularExpression"
)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class RegularExpressionAnnotationProcessor extends AbstractProcessor {

    private static final String PARSER_IMPLS_LOCATION = "META-INF/services/hu.nemaberci.regex.api.RegexParser";
    public static final String GENERATED_FILE_PACKAGE = "hu.nemaberci.regex.generated";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var annotated = roundEnv.getElementsAnnotatedWith(RegularExpression.class);
        if (annotated.isEmpty()) {
            return false;
        }
        var generator = new CodeGeneratorOrchestrator();
        try {
            var parserImpls = processingEnv.getFiler().createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                PARSER_IMPLS_LOCATION
            );
            try (
                var writer = parserImpls.openWriter();
            ) {
                for (var toGenerate : annotated) {
                    var generatedClassName = toGenerate.getSimpleName().toString() + "_impl";
                    generator.generateParser(
                        generatedClassName,
                        toGenerate.getAnnotation(RegularExpression.class).value(),
                        processingEnv.getFiler()
                    );
                    writer
                        .append(GENERATED_FILE_PACKAGE)
                        .append('.')
                        .append(generatedClassName)
                        .append('\n');
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}

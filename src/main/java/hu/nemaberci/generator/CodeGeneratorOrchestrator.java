package hu.nemaberci.generator;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.impl.DefaultJavaPackage;
import hu.nemaberci.generator.parser.ManualRegexParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class CodeGeneratorOrchestrator {

    public static final String IMPORT_HU_NEMABERCI_REGEX_DATA_PARSE_RESULT = "import hu.nemaberci.regex.data.ParseResult;";

    private String getPackageName(String packageName) {
        return "package " + packageName + ";";
    }

    private String getClassName(String originalClassName) {
        return originalClassName + "_impl";
    }

    private String functionImplementation(String functionName, String regexValue) {
        return "@Override\npublic ParseResult " + functionName
            + " (String str) { return new ParseResult(str.equals(" + regexValue + ")); }";
    }

    private String importedClasses() {
        return IMPORT_HU_NEMABERCI_REGEX_DATA_PARSE_RESULT;
    }

    private boolean isRegexParser(JavaAnnotation javaAnnotation) {
        return javaAnnotation.getType().isA("hu.nemaberci.regex.annotation.RegexParser");
    }

    public void generateParser(File sourceLocation, File targetLocation) {

        try {
            final var parser = new JavaProjectBuilder();
            parser.addSource(sourceLocation);
            final var parsedClass = parser.getClasses().stream().findFirst().orElseThrow();
            Files.createDirectories(targetLocation.getParentFile().toPath());
            final var targetLocationString = targetLocation.toString();
            final var newFileName =
                targetLocationString.substring(0, targetLocationString.indexOf(".java"))
                    + "_impl"
                    + targetLocationString.substring(targetLocationString.indexOf(".java"));
            boolean writeFile = false;
            StringBuilder stringBuilder = new StringBuilder();
            appendStartOfFile(parser, parsedClass, stringBuilder);
            for (var parsedFunction : parsedClass.getMethods()) {
                if (parsedFunction.getAnnotations().stream().anyMatch(this::isRegexParser)) {
                    writeFile = true;
                    final var regexValue = parsedFunction.getAnnotations().stream()
                        .filter(this::isRegexParser)
                        .findFirst().orElseThrow().getNamedParameter("value").toString();
                    stringBuilder.append("\n");
                    stringBuilder.append(
                        functionImplementation(parsedFunction.getName(), regexValue));
                    stringBuilder.append("\n");
                }
            }
            stringBuilder.append("}\n");
            if (writeFile) {
                Files.write(
                    Path.of(newFileName),
                    stringBuilder.toString().getBytes(StandardCharsets.UTF_8)
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void appendStartOfFile(
        JavaProjectBuilder parser,
        JavaClass parsedClass,
        StringBuilder stringBuilder
    ) {
        stringBuilder.append(getPackageName(parser.getPackages().stream()
            .findFirst().orElse(new DefaultJavaPackage("")).getName()));
        stringBuilder.append("\n\n");
        stringBuilder.append(importedClasses());
        stringBuilder.append("\n\n");
        stringBuilder.append("public class ");
        stringBuilder.append(getClassName(parsedClass.getName()));
        if (parsedClass.isInterface()) {
            stringBuilder.append(" implements ");
        } else {
            stringBuilder.append(" extends ");
        }
        stringBuilder.append(parsedClass.getName());
        stringBuilder.append(" {");
    }

    public static void main(String[] argv) {
        var parser = new ManualRegexParser();
        System.out.println(
            ToStringBuilder.reflectionToString(
                List.of(
                    (parser.parseRegex("(ab+)+")),
                    (parser.parseRegex("(ab){1,}")),
                    (parser.parseRegex("(ab){,2}")),
                    (parser.parseRegex("(ab){1,2}")),
                    (parser.parseRegex("((ab+)+[ab]?)")),
                    (parser.parseRegex("[ab-d]{3,5}")),
                    (parser.parseRegex("[^ab]*[-][]][]-]")),
                    (parser.parseRegex("[^\\]]")),
                    (parser.parseRegex("[^\\]-]")),
                    (parser.parseRegex("[a-zA-Z0-9]{4,8}"))
                ),
                ToStringStyle.JSON_STYLE
            )
        );
    }

}

package hu.nemaberci.generator;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.impl.DefaultJavaPackage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CodeGeneratorOrchestrator {

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
        return "import hu.nemaberci.regex.data.ParseResult;";
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

}

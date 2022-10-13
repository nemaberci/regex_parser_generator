package hu.nemaberci.generator;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.impl.DefaultJavaPackage;
import hu.nemaberci.generator.nfa.FiniteAutomata;
import hu.nemaberci.generator.regex.ManualRegexParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class CodeGeneratorOrchestrator {

    public static final String IMPORTS = "import hu.nemaberci.regex.data.ParseResult;\nimport javax.annotation.processing.Generated;";

    private String getPackageName(String packageName) {
        return "package " + packageName + ";";
    }

    private String getClassName(String originalClassName) {
        return originalClassName + "_impl";
    }

    private String functionBody(String regexValue) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("if (str.length() == 0) { return new ParseResult(false); }\n");
        stringBuilder.append("int currIndex = 0;\n");
        stringBuilder.append("int currState = 0;\n");
        stringBuilder.append("while (currIndex < str.length()) { \n");
        stringBuilder.append("char currChar = str.charAt(currIndex);\n");
        stringBuilder.append("switch (currState) {\n");
        var finiteAutomata = FiniteAutomata.fromRegexAlternation(
            ManualRegexParser.parseRegex(regexValue));
        for (var automataState : finiteAutomata.getStates()) {
            stringBuilder.append("case ");
            stringBuilder.append(automataState.getId());
            stringBuilder.append(": {\n");
            if (automataState.isTerminating()) {
                stringBuilder.append("return new ParseResult(true);\n");
            } else {
                // beginning of curr char case
                stringBuilder.append("switch (currChar) { \n");

                automataState.getNextStates().forEach(
                    (character, nextState) -> {
                        stringBuilder.append("case ");
                        stringBuilder.append("'").append(character).append("'");
                        stringBuilder.append(": {\n");
                        stringBuilder.append("currState = ");
                        stringBuilder.append(nextState.getId());
                        stringBuilder.append(";\n");
                        stringBuilder.append("break;\n");
                        stringBuilder.append("}\n");
                    }
                );

                stringBuilder.append("default: {\n");
                stringBuilder.append("currState = ");
                stringBuilder.append(finiteAutomata.getDefaultState().getId());
                stringBuilder.append(";\n");
                stringBuilder.append("break;\n");
                stringBuilder.append("}\n");
                stringBuilder.append("}\n");
                stringBuilder.append("break;");

            }
            stringBuilder.append("}\n");
        }
        stringBuilder.append("} \n");
        stringBuilder.append("currIndex++;\n");
        // todo: case when last character is part of the match
        stringBuilder.append("} ");
        stringBuilder.append("return new ParseResult(false);\n");
        return stringBuilder.toString();
    }

    private String functionImplementation(String functionName, String regexValue) {
        return "@Override\npublic ParseResult " + functionName
            + " (String str) { " + functionBody(regexValue) + " }";
    }

    private String importedClasses() {
        return IMPORTS;
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
        stringBuilder.append("@Generated(\"hu.nemaberci.generator.CodeGeneratorOrchestrator\")");
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
        System.out.println(
            ToStringBuilder.reflectionToString(
                //List.of(
                //    (ManualRegexParser.parseRegex("(ab+)+")),
                //    (ManualRegexParser.parseRegex("(ab){1,}")),
                //    (ManualRegexParser.parseRegex("(ab){,2}")),
                //    (ManualRegexParser.parseRegex("(ab){1,2}")),
                //    (ManualRegexParser.parseRegex("((ab+)+[ab]?)")),
                //    (ManualRegexParser.parseRegex("[ab-d]{3,5}")),
                //    (ManualRegexParser.parseRegex("[^ab]*[-][]][]-]")),
                //    (ManualRegexParser.parseRegex("[^\\]]")),
                //    (ManualRegexParser.parseRegex("[^\\]-]")),
                //    (ManualRegexParser.parseRegex("[a-zA-Z0-9]{4,8}"))
                //),
                ManualRegexParser.parseRegex("(ab[cd])+"),
                ToStringStyle.JSON_STYLE
            )
        );

    }

}

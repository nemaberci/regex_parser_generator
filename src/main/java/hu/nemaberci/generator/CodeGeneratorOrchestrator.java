package hu.nemaberci.generator;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.impl.DefaultJavaPackage;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.minimizer.DFAMinimizer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeGeneratorOrchestrator {

    public static final String IMPORTS = "import hu.nemaberci.regex.data.ParseResult;\nimport javax.annotation.processing.Generated;";

    private String getPackageName(String packageName) {
        return "package " + packageName + ";";
    }

    private String getClassName(String originalClassName) {
        return originalClassName + "_impl";
    }

    private void appendDFANode(StringBuilder stringBuilder, DFANode node, DFANode startingNode,
        List<Integer> done
    ) {

        if (done.contains(node.getId())) {
            return;
        }

        stringBuilder.append("\t".repeat(4));
        stringBuilder.append("case ");
        stringBuilder.append(node.getId());
        stringBuilder.append(": {\n");
        if (node.isAccepting()) {
            stringBuilder.append("\t".repeat(5));
            stringBuilder.append("return new ParseResult(true);\n");
        } else {
            // beginning of curr char case
            stringBuilder.append("\t".repeat(5));
            stringBuilder.append("switch (currChar) { \n");

            node.getTransitions().forEach(
                (character, dfaNode) -> {
                    stringBuilder.append("\t".repeat(6));
                    stringBuilder.append("case ");
                    stringBuilder.append("'").append(character).append("'");
                    stringBuilder.append(": {\n");
                    stringBuilder.append("\t".repeat(7));
                    stringBuilder.append("currState = ");
                    stringBuilder.append(dfaNode.getId());
                    stringBuilder.append(";\n");
                    stringBuilder.append("\t".repeat(7));
                    stringBuilder.append("break;\n");
                    stringBuilder.append("\t".repeat(6));
                    stringBuilder.append("}\n");
                }
            );

            stringBuilder.append("\t".repeat(6));
            stringBuilder.append("default: {\n");
            stringBuilder.append("\t".repeat(7));
            stringBuilder.append("currState = ");
            stringBuilder.append(startingNode.getId());
            stringBuilder.append(";\n");
            stringBuilder.append("\t".repeat(7));
            stringBuilder.append("break;\n");
            stringBuilder.append("\t".repeat(6));
            stringBuilder.append("}\n");
            stringBuilder.append("\t".repeat(5));
            stringBuilder.append("}\n");
            stringBuilder.append("\t".repeat(5));
            stringBuilder.append("break;");

        }
        stringBuilder.append("\t".repeat(4));
        stringBuilder.append("}\n");
        done.add(node.getId());
        node.getTransitions().forEach(
            (character, dfaNode) -> appendDFANode(stringBuilder, dfaNode, startingNode, done)
        );
    }

    private String functionBody(String regexValue) {
        var finiteAutomata = new DFAMinimizer().parseAndConvertAndMinimize(regexValue.substring(1, regexValue.length() - 1));
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\t".repeat(2));
        stringBuilder.append("if (str.length() == 0) {\n");
        stringBuilder.append("\t".repeat(3));
        stringBuilder.append("return new ParseResult(false);\n");
        stringBuilder.append("\t".repeat(2));
        stringBuilder.append("}\n");
        stringBuilder.append("\t".repeat(2));
        stringBuilder.append("int currIndex = 0;\n");
        stringBuilder.append("\t".repeat(2));
        stringBuilder.append("int currState = ");
        stringBuilder.append(finiteAutomata.getId());
        stringBuilder.append(";\n");
        stringBuilder.append("\t".repeat(2));
        stringBuilder.append("while (currIndex < str.length()) { \n");
        stringBuilder.append("\t".repeat(3));
        stringBuilder.append("char currChar = str.charAt(currIndex);\n");
        stringBuilder.append("\t".repeat(3));
        stringBuilder.append("switch (currState) {\n");
        appendDFANode(stringBuilder, finiteAutomata, finiteAutomata, new ArrayList<>());
        stringBuilder.append("\t".repeat(3));
        stringBuilder.append("}\n");
        stringBuilder.append("\t".repeat(3));
        stringBuilder.append("currIndex++;\n");
        // todo: case when last character is part of the match
        stringBuilder.append("\t".repeat(2));
        stringBuilder.append("}\n");
        stringBuilder.append("\t".repeat(2));
        stringBuilder.append("return new ParseResult(false);\n");
        return stringBuilder.toString();
    }

    private String functionImplementation(String functionName, String regexValue) {
        return "\t@Override\n\tpublic ParseResult " + functionName
            + " (String str) {\n" + functionBody(regexValue) + "\t}";
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
        stringBuilder.append("@Generated(\"hu.nemaberci.generator.CodeGeneratorOrchestrator\")\n");
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

    private static void print(List<DFANode> printed, DFANode dfaNode) {
        if (printed.contains(dfaNode)) {
            return;
        }
        printed.add(dfaNode);
        System.out.println("id:" + dfaNode.getId());
        System.out.println("edges:" + dfaNode.getTransitions().size());
        dfaNode.getTransitions().forEach((c, other) -> {
            System.out.println(dfaNode.getId() + " + " + c + " --> " + other.getId() + ", it is" + (other.isAccepting() ? "" : " not") + " terminating");
            print(printed, other);
        }
        );
    }

    public static void main(String[] args) {
        var dfa = new DFAMinimizer().parseAndConvertAndMinimize("[abcd]?(a|b|d)+ab");
        System.out.println(dfa);
        // print(new ArrayList<>(), dfa);
    }

}

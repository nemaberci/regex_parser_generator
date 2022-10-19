package hu.nemaberci.generator;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.minimizer.DFAMinimizer;
import hu.nemaberci.regex.api.RegexParser;
import hu.nemaberci.regex.container.RegexParserContainer;
import hu.nemaberci.regex.data.ParseResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.Modifier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeGeneratorOrchestrator {

    public static final String CURR_CHAR = "currentCharacter";
    public static final String CURR_INDEX = "currentIndex";
    public static final String FUNCTION_INPUT_VARIABLE_NAME = "inputString";
    public static final String CURR_STATE = "currentState";
    private static final String INSTANCE_NAME = "instance";

    private String getPackageName(String packageName) {
        return "package " + packageName + ";";
    }

    private String getClassName(String originalClassName) {
        return originalClassName + "_impl";
    }

    private CodeBlock functionImplementation(String regex) {
        var startingNode = new DFAMinimizer().parseAndConvertAndMinimize(regex);
        var codeBlockBuilder = CodeBlock.builder();
        handleEmptyInput(codeBlockBuilder);
        initStartingVariables(startingNode, codeBlockBuilder);
        addMainWhileLoop(startingNode, codeBlockBuilder);
        returnDefaultValue(codeBlockBuilder);
        return codeBlockBuilder.build();
    }

    private static void returnDefaultValue(Builder codeBlockBuilder) {
        codeBlockBuilder
            .addStatement("return new $T($T.emptyList())", ParseResult.class, Collections.class);
    }

    private static void initStartingVariables(DFANode finiteAutomata, Builder codeBlockBuilder) {
        codeBlockBuilder
            .addStatement("int $L = 0", CURR_INDEX)
            .addStatement("int $L = $L", CURR_STATE, finiteAutomata.getId());
    }

    private static void handleEmptyInput(Builder codeBlockBuilder) {
        codeBlockBuilder
            .beginControlFlow("if ($L.length() == 0)", FUNCTION_INPUT_VARIABLE_NAME)
            .addStatement("return new $T($T.emptyList())", ParseResult.class, Collections.class)
            .endControlFlow();
    }

    private static void addMainWhileLoop(DFANode startingNode, Builder codeBlockBuilder) {
        // Start while
        codeBlockBuilder
            .beginControlFlow("while ($L < $L.length())", CURR_INDEX, FUNCTION_INPUT_VARIABLE_NAME);

        codeBlockBuilder
            .addStatement(
                "char $L = $L.charAt($L)", CURR_CHAR, FUNCTION_INPUT_VARIABLE_NAME, CURR_INDEX);
        addStateSwitch(startingNode, codeBlockBuilder);

        codeBlockBuilder
            .addStatement("$L++", CURR_INDEX);
        // End while
        codeBlockBuilder
            .endControlFlow();
    }

    private static void addDFANodeCaseRecursively(List<DFANode> done, DFANode curr,
        DFANode defaultNode, Builder codeBlockBuilder
    ) {
        if (done.contains(curr)) {
            return;
        }
        done.add(curr);

        codeBlockBuilder
            .beginControlFlow("case $L:", curr.getId());

        addCurrentDFANodeTransitions(curr, defaultNode, codeBlockBuilder);

        codeBlockBuilder.endControlFlow();

        curr.getTransitions().forEach(
            (character, dfaNode) -> addDFANodeCaseRecursively(
                done, dfaNode, defaultNode, codeBlockBuilder)
        );
    }

    private static void addCurrentDFANodeTransitions(DFANode curr, DFANode defaultNode,
        Builder codeBlockBuilder
    ) {
        if (curr.isAccepting()) {
            codeBlockBuilder.addStatement("return new $T(true)", ParseResult.class);
        } else {
            codeBlockBuilder
                .beginControlFlow("switch ($L)", CURR_CHAR);

            curr.getTransitions().forEach(
                (character, dfaNode) ->
                    codeBlockBuilder
                        .beginControlFlow("case '$L':", character)
                        .addStatement("$L = $L", CURR_STATE, dfaNode.getId())
                        .addStatement("break")
                        .endControlFlow()
            );

            codeBlockBuilder
                .beginControlFlow("default: ")
                .addStatement("$L = $L", CURR_STATE, defaultNode.getId())
                .addStatement("break")
                .endControlFlow();

            codeBlockBuilder
                .endControlFlow();

            codeBlockBuilder.addStatement("break");
        }
    }

    private static void addStateSwitch(DFANode startingNode, Builder codeBlockBuilder) {
        // Start switch
        codeBlockBuilder
            .beginControlFlow("switch ($L)", CURR_STATE);

        addDFANodeCaseRecursively(new ArrayList<>(), startingNode, startingNode, codeBlockBuilder);

        // End switch
        codeBlockBuilder
            .endControlFlow();
    }

    private boolean isRegexParser(JavaAnnotation javaAnnotation) {
        return javaAnnotation.getType().isA("hu.nemaberci.regex.annotation.RegularExpression");
    }

    public void generateParser(File sourceLocation, File targetLocation) {

        try {
            final var parser = new JavaProjectBuilder();
            parser.addSource(sourceLocation);
            final var parsedClass = parser.getClasses().stream().findFirst().orElseThrow();
            if (parsedClass.getAnnotations().stream().noneMatch(this::isRegexParser)) {
                return;
            }

            Files.createDirectories(targetLocation.getParentFile().toPath());

            final var classImplementationName = getClassName(parsedClass.getName());
            var classImplBuilder = TypeSpec.classBuilder(classImplementationName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(RegexParser.class);
            final var regexValueStr = parsedClass.getAnnotations().stream()
                .filter(this::isRegexParser)
                .findFirst().orElseThrow().getNamedParameter("value").toString();
            final var regexValue = regexValueStr.substring(1, regexValueStr.length() - 1);

            classImplBuilder
                .addField(
                    FieldSpec
                        .builder(
                            RegexParser.class, INSTANCE_NAME, Modifier.STATIC, Modifier.PRIVATE,
                            Modifier.FINAL
                        )
                        .initializer("new $L()", classImplementationName)
                        .build()
                );

            classImplBuilder.addStaticBlock(
                CodeBlock.builder()
                    .addStatement(
                        "$T.registerParser($S, $L)", RegexParserContainer.class,
                        regexValue, INSTANCE_NAME
                    )
                    .build()
            );

            var methodSpecBuilder = MethodSpec.methodBuilder("match")
                .addParameter(String.class, FUNCTION_INPUT_VARIABLE_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParseResult.class);

            methodSpecBuilder.addCode(functionImplementation(regexValue));

            classImplBuilder.addMethod(methodSpecBuilder.build());
            var javaFileBuilder = JavaFile.builder(
                "hu.nemaberci.regex.generated",
                classImplBuilder.build()
            );
            javaFileBuilder.build().writeTo(targetLocation);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void print(List<DFANode> printed, DFANode dfaNode) {
        if (printed.contains(dfaNode)) {
            return;
        }
        printed.add(dfaNode);
        System.out.println("id:" + dfaNode.getId());
        System.out.println("edges:" + dfaNode.getTransitions().size());
        dfaNode.getTransitions().forEach((c, other) -> {
                System.out.println(dfaNode.getId() + " + " + c + " --> " + other.getId() + ", it is" + (
                    other.isAccepting() ? "" : " not") + " terminating");
                print(printed, other);
            }
        );
    }

    public static void main(String[] args) {
        var dfa = new DFAMinimizer().parseAndConvertAndMinimize("[abcd]?(a|b|d)+ab");
        new CodeGeneratorOrchestrator().generateParser(
            new File(
                "C:\\Work\\bme\\7_felev\\szakdoga\\test\\src\\main\\java\\hu\\nemaberci\\api\\TestRegexParser.java"),
            new File("C:\\Work\\bme\\7_felev\\szakdoga\\qwe123\\")
        );
        System.out.println(dfa);
        // print(new ArrayList<>(), dfa);
    }

}

package hu.nemaberci.generator.generator;

import static hu.nemaberci.generator.annotationprocessor.RegularExpressionAnnotationProcessor.GENERATED_FILE_PACKAGE;
import static hu.nemaberci.generator.generator.ParserFileGenerator.createMainParserFile;
import static hu.nemaberci.generator.generator.StatesHandlerGenerator.createFileForStates;
import static hu.nemaberci.generator.regex.dfa.util.DFAUtils.extractAllNodes;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.data.DFAParseResult;
import hu.nemaberci.generator.regex.dfa.minimizer.DFAMinimizer;
import hu.nemaberci.regex.annotation.RegularExpressionParserImplementation;
import hu.nemaberci.regex.api.RegexParser;
import hu.nemaberci.regex.container.RegexParserContainer;
import hu.nemaberci.regex.data.ParseResult;
import hu.nemaberci.regex.data.ParseResultMatch;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.CharUtils;

@Slf4j
public class CodeGeneratorOrchestrator {

    public static final String CURR_CHAR = "currentCharacter";
    public static final String CHARS = "characters";
    public static final String CURR_INDEX = "currentIndex";
    public static final String FUNCTION_INPUT_VARIABLE_NAME = "inputString";
    public static final String CURR_STATE = "currentState";
    public static final String FOUND = "found";
    public static final String MATCH_STARTED_AT = "currentMatchStartedAt";
    public static final String LAST_SUCCESSFUL_MATCH_AT = "lastSuccessfulMatchAt";
    public static final String INSTANCE_NAME = "instance";
    public static final String INPUT_STRING_LENGTH = "stringLength";
    public static final String CURRENT_STATE_ARRAY = "currentStateArray";
    public static final String STATE_HANDLERS = "stateHandlers";
    public static final int CURR_INDEX_POSITION = 0;
    public static final int CURR_STATE_POSITION = 1;
    public static final int MATCH_STARTED_AT_POSITION = 2;
    public static final int LAST_SUCCESSFUL_MATCH_AT_POSITION = 3;
    public static final int IMPOSSIBLE_STATE_ID = -1;
    public static final int STATES_PER_FILE = 32;

    private static void calculateDistanceFromStartAndAddParents(DFANode startNode) {
        startNode.setDistanceFromStart(0);
        List<DFANode> queue = new ArrayList<>();
        queue.add(startNode);

        while (!queue.isEmpty()) {
            var curr = queue.get(0);
            queue.remove(0);
            curr.getTransitions().values().stream().filter(Objects::nonNull).forEach(
                node -> {
                    if (!node.getParents().contains(curr)) {
                        node.getParents().add(curr);
                        if (node.getDistanceFromStart() < curr.getDistanceFromStart()) {
                            node.setDistanceFromStart(curr.getDistanceFromStart() + 1);
                            queue.add(node);
                        }
                    }
                }
            );
            if (curr.getDefaultTransition() != null) {
                var node = curr.getDefaultTransition();
                if (!node.getParents().contains(curr)) {
                    node.getParents().add(curr);
                    if (node.getDistanceFromStart() < curr.getDistanceFromStart()) {
                        node.setDistanceFromStart(curr.getDistanceFromStart() + 1);
                        queue.add(node);
                    }
                }
            }
        }

    }

    private static void addAlternateEdges(DFANode startingNode) {

        for (var node : extractAllNodes(startingNode)) {
            node.getTransitions().keySet().stream()
                .filter(CharUtils::isAsciiPrintable)
                .forEach(c -> {
                    char otherCaseChar = Character.isUpperCase(c) ?
                        Character.toLowerCase(c) :
                        Character.toUpperCase(c);
                    if (!node.getTransitions().containsKey(otherCaseChar)) {
                        node.getTransitions().put(
                            otherCaseChar,
                            node.getTransitions().get(c)
                        );
                    }
                });
        }

    }

    public String generateParser(String className, String regex, Filer filer) {

        try {

            var parseResult = new DFAMinimizer().parseAndConvertAndMinimize(regex);
            // non-lazy doesn't really work atm
            parseResult.getFlags().add(RegexFlag.LAZY);
            calculateDistanceFromStartAndAddParents(parseResult.getStartingNode());
            if (parseResult.getFlags().contains(RegexFlag.CASE_INDEPENDENT_ASCII)) {
                addAlternateEdges(parseResult.getStartingNode());
            }

            var allNodes = extractAllNodes(parseResult.getStartingNode()).stream()
                .sorted(Comparator.comparingInt(DFANode::getId)).collect(Collectors.toList());
            final List<List<DFANode>> splitNodes = getSplitNodes(allNodes);
            for (int i = 0; i < splitNodes.size(); i++) {
                List<DFANode> split = splitNodes.get(i);
                final var partClassName = className + "_part_" + i;
                try (var writer = filer.createSourceFile(GENERATED_FILE_PACKAGE + "." + partClassName).openWriter()) {
                    createFileForStates(
                        split,
                        parseResult.getFlags(),
                        parseResult.getStartingNode(),
                        partClassName,
                        writer
                    );
                }
            }

            createMainParserFile(
                allNodes,
                parseResult.getStartingNode(),
                className,
                regex,
                filer.createSourceFile(GENERATED_FILE_PACKAGE + "." + className).openWriter()
            );

            return className;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;

    }

    private static List<List<DFANode>> getSplitNodes(List<DFANode> allNodes) {
        final List<List<DFANode>> splitNodes = new ArrayList<>();
        List<DFANode> temp = new ArrayList<>();
        int count = 0;
        for (var node : allNodes) {
            temp.add(node);
            count++;
            if (count == STATES_PER_FILE) {
                splitNodes.add(temp);
                temp = new ArrayList<>();
                count = 0;
            }
        }
        if (!splitNodes.contains(temp)) {
            splitNodes.add(temp);
        }
        return splitNodes;
    }

}

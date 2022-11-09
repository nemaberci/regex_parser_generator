package hu.nemaberci.generator.generator;

import static hu.nemaberci.generator.annotationprocessor.RegularExpressionAnnotationProcessor.GENERATED_FILE_PACKAGE;
import static hu.nemaberci.generator.generator.IndividualStateHandlerGenerator.createIndividualStateHandler;
import static hu.nemaberci.generator.generator.ParserFileGenerator.createMainParserFile;
import static hu.nemaberci.generator.generator.StateTransitionHandlerGenerator.createStateTransitionHandlerUtil;
import static hu.nemaberci.generator.generator.StatesHandlerGenerator.createFileForStates;
import static hu.nemaberci.generator.regex.dfa.util.DFAUtils.extractAllNodes;

import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.minimizer.DFAMinimizer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
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
    public static final String CURR_STATE_HANDLER = "currentStateHandler";
    public static final String INPUT_STRING_LENGTH = "stringLength";
    public static final String UTIL = "util";
    public static final int IMPOSSIBLE_STATE_ID = -1;
    public static final int STATES_PER_FILE_LOG_2 = 4;

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

    public static String stateHandlerPartName(String originalClassName, int i) {
        return originalClassName + "_part_" + i;
    }

    public static String individualStateHandlerName(String originalClassName, int i) {
        return originalClassName + "_state_" + i;
    }

    public static String utilName(String originalClassName) {
        return originalClassName + "_util";
    }

    public static String nameOfFunctionThatLeadsToState(int stateId) {
        return "jumpToState" + stateId;
    }

    public static String nameOfFunctionThatRestartsSearch() {
        return "restartSearch";
    }

    public static String nameOfFunctionThatAddsResultFound() {
        return "resultFound";
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
                final var partClassName = stateHandlerPartName(className, i);
                try (var writer = filer.createSourceFile(
                    GENERATED_FILE_PACKAGE + "." + partClassName).openWriter()) {
                    createFileForStates(
                        partClassName,
                        className,
                        writer,
                        i * (1 << STATES_PER_FILE_LOG_2),
                        Math.min(
                            i * (1 << STATES_PER_FILE_LOG_2) + (1 << STATES_PER_FILE_LOG_2) - 1,
                            allNodes.size() - 1
                        )
                    );
                }
            }

            for (int i = 0; i < allNodes.size(); i++) {
                var name = individualStateHandlerName(className, i);
                try (var writer = filer.createSourceFile(GENERATED_FILE_PACKAGE + "." + name)
                    .openWriter()) {
                    createIndividualStateHandler(
                        allNodes.get(i),
                        parseResult.getFlags(),
                        parseResult.getStartingNode(),
                        name,
                        className,
                        writer
                    );
                }
            }

            try (var writer = filer.createSourceFile(
                GENERATED_FILE_PACKAGE + "." + utilName(className)).openWriter()) {
                createStateTransitionHandlerUtil(
                    allNodes.size(),
                    writer,
                    parseResult.getStartingNode(),
                    utilName(className),
                    className
                );
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
            if (count == (1 << STATES_PER_FILE_LOG_2)) {
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

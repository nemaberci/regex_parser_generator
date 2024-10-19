package hu.nemaberci.generator.generator.python;

import static hu.nemaberci.generator.regex.dfa.util.DFAUtils.extractAllNodes;

import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.minimizer.DFAMinimizer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.CharUtils;

@Slf4j
public class PythonCodeGeneratorOrchestrator {

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

    private static void calculateDistanceFromStartAndAddParents(DFANode startNode) {
        startNode.setDistanceFromStart(0);
        List<DFANode> queue = new ArrayList<>();
        queue.add(startNode);

        while (! queue.isEmpty()) {
            var curr = queue.get(0);
            queue.remove(0);
            curr.getTransitions().values().stream().filter(Objects::nonNull).forEach(
                node -> {
                    if (! node.getParents().contains(curr)) {
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
                if (! node.getParents().contains(curr)) {
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
            var transitionKeyset = node.getTransitions().keySet().stream()
                .filter(CharUtils::isAsciiPrintable).collect(Collectors.toList());
            for (var c : transitionKeyset) {
                char otherCaseChar = Character.isUpperCase(c) ?
                    Character.toLowerCase(c) :
                    Character.toUpperCase(c);
                if (! node.getTransitions().containsKey(otherCaseChar)) {
                    node.getTransitions().put(
                        otherCaseChar,
                        node.getTransitions().get(c)
                    );
                }
            }
        }

    }

    public String generateParser(String className, String regex, String folderName) {

        try {

            var parseResult = DFAMinimizer.parseAndConvertAndMinimize(regex);
            calculateDistanceFromStartAndAddParents(parseResult.getStartingNode());
            if (parseResult.getFlags().contains(RegexFlag.CASE_INDEPENDENT_ASCII)) {
                addAlternateEdges(parseResult.getStartingNode());
            }

            var allNodes = extractAllNodes(parseResult.getStartingNode()).stream()
                .sorted(Comparator.comparingInt(DFANode::getId))
                .toList();

            if (! new File(folderName).exists()) {
                if (!new File(folderName).mkdirs()) {
                    log.error("Could not create directory");
                    return null;
                }
            }

            final var folderPrefix = folderName + "/";
            final var fileSuffix = ".py";

            try (
                var writer = Files.newBufferedWriter(
                    new File(folderPrefix + className + fileSuffix).toPath()
                )
            ) {
                PythonParserFileGenerator.createMainParserFile(
                    allNodes,
                    parseResult.getStartingNode(),
                    parseResult.getFlags(),
                    className,
                    regex,
                    writer
                );
            }

            return className;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;

    }

}

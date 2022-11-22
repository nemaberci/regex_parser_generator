package hu.nemaberci.generator.regex.dfa.minimizer;

import static hu.nemaberci.generator.regex.dfa.util.DFAUtils.extractAllNodes;

import hu.nemaberci.generator.regex.dfa.NFAToDFAConverter;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.data.DFAParseResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

public class DFAMinimizer {

    private DFAMinimizer() {}

    private static DFANode minimize(
        List<DFANode> allNodes,
        DFANode startingNode
    ) {

        final List<DFANode> acceptingNodes = allNodes.stream()
            .filter(DFANode::isAccepting)
            .collect(Collectors.toList());
        final List<DFANode> nonAcceptingNodes = allNodes.stream()
            .filter(Predicate.not(DFANode::isAccepting))
            .collect(Collectors.toList());
        final Map<DFANode, Integer> dfaNodeToPartitionIndexMap = new HashMap<>();

        final List<List<DFANode>> partitions = new ArrayList<>();
        partitions.add(acceptingNodes);
        if (!nonAcceptingNodes.isEmpty()) {
            partitions.add(nonAcceptingNodes);
        }
        for (var node : allNodes) {
            if (node.isAccepting()) {
                dfaNodeToPartitionIndexMap.put(node, 0);
            } else {
                dfaNodeToPartitionIndexMap.put(node, 1);
            }
        }

        boolean separationHappened = true;

        while (separationHappened) {

            separationHappened = false;

            int i = 0;
            while (i < partitions.size()) {

                var currPartition = partitions.get(i++);

                var sampleNode = currPartition.get(0);

                final List<DFANode> hasSameTransitionsAsSampleNode = new ArrayList<>();
                final List<DFANode> hasDifferentTransitionsAsSampleNode = new ArrayList<>();

                for (var node : currPartition) {

                    if (!Objects.equals(
                        node.getDefaultTransition(), sampleNode.getDefaultTransition())) {
                        hasDifferentTransitionsAsSampleNode.add(node);
                    } else {
                        if (node.getTransitions().keySet().size() != sampleNode.getTransitions()
                            .keySet().size()) {
                            hasDifferentTransitionsAsSampleNode.add(node);
                        } else {
                            if (node.getTransitions().keySet().stream().allMatch(
                                c -> Objects.equals(
                                    dfaNodeToPartitionIndexMap.get(
                                        sampleNode.getTransitions().get(c)),
                                    dfaNodeToPartitionIndexMap.get(node.getTransitions().get(c))
                                )
                            )) {
                                hasSameTransitionsAsSampleNode.add(node);
                            } else {
                                hasDifferentTransitionsAsSampleNode.add(node);
                            }
                        }
                    }

                }

                if (!hasDifferentTransitionsAsSampleNode.isEmpty()) {

                    separationHappened = true;

                    partitions.set(i - 1, hasSameTransitionsAsSampleNode);
                    partitions.add(hasDifferentTransitionsAsSampleNode);

                    hasDifferentTransitionsAsSampleNode.forEach(
                        node -> dfaNodeToPartitionIndexMap.put(node, partitions.size() - 1)
                    );

                }

            }

        }

        int newId = 0;
        List<DFANode> createdNodes = new ArrayList<>();
        for (var ignored : partitions) {
            createdNodes.add(new DFANode().setId(newId++));
        }

        DFANode newStartingNode = startingNode;

        for (int i = 0; i < createdNodes.size(); i++) {

            var sampleNode = partitions.get(i).get(0);
            var createdNode = createdNodes.get(i);
            if (sampleNode.getDefaultTransition() != null) {
                var defaultTransitionNewIndex =
                    dfaNodeToPartitionIndexMap.get(
                        sampleNode.getDefaultTransition()
                    );
                createdNode.setDefaultTransition(createdNodes.get(defaultTransitionNewIndex));
            }
            for (var transition : sampleNode.getTransitions().entrySet()) {

                var newTransitionTargetIndex =
                    dfaNodeToPartitionIndexMap.get(
                        transition.getValue()
                    );
                if (newTransitionTargetIndex != null) {
                    createdNode.getTransitions().put(
                        transition.getKey(),
                        createdNodes.get(newTransitionTargetIndex)
                    );
                } else {
                    createdNode.getTransitions().put(
                        transition.getKey(),
                        null
                    );
                }

            }

            if (partitions.get(i).contains(startingNode)) {
                newStartingNode = createdNode;
            }
            if (partitions.get(i).stream().anyMatch(DFANode::isAccepting)) {
                createdNode.setAccepting(true);
            }

        }

        return newStartingNode;
    }

    public static DFAParseResult parseAndConvertAndMinimize(String regex) {
        var dfaParseResult = NFAToDFAConverter.parseAndConvert(regex);
        return new DFAParseResult(
            minimize(
                extractAllNodes(dfaParseResult.getStartingNode()),
                dfaParseResult.getStartingNode()
            ),
            dfaParseResult.getFlags()
        );
    }

}

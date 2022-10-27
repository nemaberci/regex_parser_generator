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

    // public DFANode hopcroft(
    //     Collection<Character> alphabet,
    //     List<DFANode> allNodes,
    //     DFANode startNode
    // ) {
    //     var acceptingNodes = new ArrayList<DFANode>();
    //     var nonAcceptingNodes = new ArrayList<DFANode>();
    //     for (var node : allNodes) {
    //         if (node.isAccepting()) {
    //             acceptingNodes.add(node);
    //         } else {
    //             nonAcceptingNodes.add(node);
    //         }
    //     }
    //     List<List<DFANode>> queue = new ArrayList<>();
    //     List<List<DFANode>> partitions = new ArrayList<>();
    //     queue.add(acceptingNodes);
    //     partitions.add(acceptingNodes);
    //     if (!nonAcceptingNodes.isEmpty()) {
    //         queue.add(nonAcceptingNodes);
    //         partitions.add(nonAcceptingNodes);
    //     }
    //     while (!queue.isEmpty()) {
    //         var curr = queue.get(0);
    //         queue.remove(0);
    //         if (curr.isEmpty()) {
    //             continue;
    //         }
    //         for (var c : alphabet) {
//
    //             List<DFANode> canSeeCurr = new ArrayList<>();
    //             for (var dfaNode : allNodes) {
    //                 var nodesFoundThroughC = dfaNode.getTransitions()
    //                     .stream()
    //                     .filter(edge -> edge.getCharacter() == c || edge.isDefault())
    //                     .collect(Collectors.toList());
    //                 for (var nodeFoundThroughC : nodesFoundThroughC) {
    //                     if (
    //                         curr.contains(nodeFoundThroughC.getEnd())
    //                     ) {
    //                         canSeeCurr.add(dfaNode);
    //                     }
    //                 }
    //             }
//
    //             int i = 0;
    //             while (i < partitions.size()) {
    //                 List<DFANode> currentPartition = partitions.get(i);
    //                 List<DFANode> group1 = new ArrayList<>();
    //                 List<DFANode> group2 = new ArrayList<>();
    //                 for (var node : currentPartition) {
    //                     if (canSeeCurr.contains(node)) {
    //                         group1.add(node);
    //                     } else {
    //                         group2.add(node);
    //                     }
    //                 }
    //                 if (!group1.isEmpty() && !group2.isEmpty()) {
    //                     partitions.remove(i);
    //                     partitions.add(group1);
    //                     partitions.add(group2);
    //                     if (queue.contains(currentPartition)) {
    //                         queue.remove(currentPartition);
    //                         queue.add(group1);
    //                         queue.add(group2);
    //                     } else {
    //                         if (group1.size() <= group2.size()) {
    //                             queue.add(group1);
    //                         } else {
    //                             queue.add(group2);
    //                         }
    //                     }
    //                 } else {
    //                     i++;
    //                 }
    //             }
    //         }
    //     }
//
    //     DFANode newStartingNode = null;
    //     final List<DFANode> newDFANodes = new ArrayList<>();
    //     final Map<Integer, DFANode> oldToNewDFAMap = new HashMap<>();
    //     for (List<DFANode> partition : partitions) {
    //         var newNode = new DFANode()
    //             .setId(partition.stream().map(DFANode::getId).max(Comparator.naturalOrder()).orElseThrow())
    //             // Accepting and non-accepting cannot be in the same group
    //             .setAccepting(partition.get(0).isAccepting());
    //         newDFANodes.add(newNode);
    //         for (var node : partition) {
    //             oldToNewDFAMap.put(node.getId(), newNode);
    //         }
    //     }
    //     for (var entry : oldToNewDFAMap.entrySet()) {
    //         var oldNode = allNodes.get(entry.getKey());
    //         var newNode = entry.getValue();
    //         for (var transition : oldNode.getTransitions()) {
    //             newNode.getTransitions().add(
    //                 new DFANodeEdge()
    //                     .setDefault(transition.isDefault())
    //                     .setCharacter(transition.getCharacter())
    //                     .setEnd(
    //                         oldToNewDFAMap.get(transition.getEnd().getId())
    //                     )
    //             );
    //         }
    //     }
    //     for (int i = 0; i < partitions.size(); i++) {
    //         if (partitions.get(i).contains(startNode)) {
    //             newStartingNode = newDFANodes.get(i);
    //         }
    //     }
//
    //     return startNode;
    // }

    private DFANode minimize(
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

        int newId = 1;
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

    public DFAParseResult parseAndConvertAndMinimize(String regex) {
        var dfaParseResult = new NFAToDFAConverter().parseAndConvert(regex);
        return new DFAParseResult(
            minimize(
                extractAllNodes(dfaParseResult.getStartingNode()),
                dfaParseResult.getStartingNode()
            ),
            dfaParseResult.getFlags()
        );
    }

}

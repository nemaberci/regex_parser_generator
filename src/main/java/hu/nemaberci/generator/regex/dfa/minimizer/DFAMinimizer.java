package hu.nemaberci.generator.regex.dfa.minimizer;

import hu.nemaberci.generator.regex.dfa.NFAToDFAConverter;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.data.DFANodeEdge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

public class DFAMinimizer {

    public DFANode hopcroft(
        List<Transition> alphabet,
        List<DFANode> dfaNodes,
        DFANode startNode
    ) {
        var group1 = new ArrayList<DFANode>();
        var group2 = new ArrayList<DFANode>();
        for (var node : dfaNodes) {
            if (node.isAccepting()) {
                group1.add(node);
            } else {
                group2.add(node);
            }
        }
        List<List<DFANode>> queue = new ArrayList<>();
        List<List<DFANode>> partitions = new ArrayList<>();
        queue.add(group1);
        partitions.add(group1);
        if (!group2.isEmpty()) {
            queue.add(group2);
            partitions.add(group2);
        }
        while (!queue.isEmpty()) {
            var curr = queue.get(0);
            queue.remove(0);
            if (curr.isEmpty()) {
                continue;
            }
            for (var c : alphabet) {

                List<DFANode> canSeeS = new ArrayList<>();
                for (var dfaNode : dfaNodes) {
                    if (
                        dfaNode.getTransitions().stream()
                            .anyMatch(hasEdgeWithCharacter(c)) &&
                            curr.contains(
                                dfaNode.getTransitions().stream()
                                    .filter(hasEdgeWithCharacter(c))
                                    .findFirst().get().getEnd()
                            )
                    ) {
                        canSeeS.add(dfaNode);
                    }
                }

                int i = 0;
                while (i < partitions.size()) {
                    List<DFANode> nodes = partitions.get(i);
                    group1 = new ArrayList<>();
                    group2 = new ArrayList<>();
                    for (var node : nodes) {
                        if (canSeeS.contains(node)) {
                            group1.add(node);
                        } else {
                            group2.add(node);
                        }
                    }
                    if (!group1.isEmpty() && !group2.isEmpty()) {
                        partitions.remove(i);
                        partitions.add(group1);
                        partitions.add(group2);
                        if (queue.contains(nodes)) {
                            queue.remove(nodes);
                            queue.add(group1);
                            queue.add(group2);
                        } else {
                            if (group1.size() <= group2.size()) {
                                queue.add(group1);
                            } else {
                                queue.add(group2);
                            }
                        }
                    } else {
                        i++;
                    }
                }
            }

        }

        int id = 0;
        DFANode newStartingNode = null;
        final List<DFANode> newDFANodes = new ArrayList<>();
        final Map<Integer, DFANode> oldToNewDFAMap = new HashMap<>();
        for (List<DFANode> partition : partitions) {
            var newNode = new DFANode()
                .setId(id++)
                // Accepting and non-accepting cannot be in the same group
                .setAccepting(partition.get(0).isAccepting());
            newDFANodes.add(newNode);
            for (var node : partition) {
                oldToNewDFAMap.put(node.getId(), newNode);
            }
        }
        for (int i = 0; i < partitions.size(); i++) {
            if (partitions.get(i).contains(startNode)) {
                newStartingNode = newDFANodes.get(i);
            }
            for (var node : partitions.get(i)) {
                newDFANodes.get(i).getTransitions().addAll(node.getTransitions());
            }
        }

        return newStartingNode;
    }

    private static Predicate<DFANodeEdge> hasEdgeWithCharacter(Transition c) {
        return edge -> edge.isNegated() == c.isNegated() && edge.getCharacter() == c.getC();
    }

    public DFANode parseAndConvertAndMinimize(String regex) {
        var dfa = new NFAToDFAConverter().parseAndConvert(regex);
        hopcroft(
            extractAlphabet(extractAllNodes(dfa)),
            extractAllNodes(dfa),
            dfa
        );
        return dfa;
    }

    private static List<DFANode> extractAllNodes(DFANode startNode) {
        List<DFANode> allNodes = new ArrayList<>();
        List<DFANode> queue = new ArrayList<>();
        queue.add(startNode);

        while (!queue.isEmpty()) {
            var otherNode = queue.get(0);
            queue.remove(0);
            if (allNodes.contains(otherNode)) {
                continue;
            }
            allNodes.add(otherNode);
            queue.addAll(otherNode.getTransitions().stream()
                .map(DFANodeEdge::getEnd)
                .filter(Predicate.not(queue::contains))
                .collect(Collectors.toList())
            );
        }

        return allNodes;
    }

    private static List<Transition> extractAlphabet(List<DFANode> allNodes) {
        Set<Transition> alphabet = new HashSet<>();
        for (var node : allNodes) {
            alphabet.addAll(
                node.getTransitions().stream()
                    .map(edge -> new Transition().setC(edge.getCharacter()).setNegated(edge.isNegated()))
                    .collect(Collectors.toList()));
        }
        return new ArrayList<>(alphabet);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(chain = true)
    @EqualsAndHashCode
    private static class Transition {

        private Character c;
        private boolean negated = false;

    }

}

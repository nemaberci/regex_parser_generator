package hu.nemaberci.generator.regex.dfa;

import static hu.nemaberci.generator.regex.nfa.RegexToNFAParser.EPSILON;

import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.data.DFAParseResult;
import hu.nemaberci.generator.regex.nfa.RegexToNFAParser;
import hu.nemaberci.generator.regex.nfa.data.NFANode;
import hu.nemaberci.generator.regex.nfa.data.NFANode.NFANodeType;
import hu.nemaberci.generator.regex.nfa.data.NFAParseResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

public class NFAToDFAConverter {

    private NFAToDFAConverter() {}

    public static DFAParseResult parseAndConvert(String regex) {
        return convert(RegexToNFAParser.parseAndConvert(regex));
    }

    private static int nodeId = 0;

    private static DFANode reachableNFANodes(Collection<NFANode> nfaNodes) {

        DFANode createdNode = new DFANode();
        List<NFANode> writableNFANodes = new ArrayList<>(nfaNodes);

        int i = 0;
        while (i < writableNFANodes.size()) {
            var nfaNode = writableNFANodes.get(i);
            for (var edge : nfaNode.getEdges()) {
                if (edge.getCharacters().equals(Set.of(EPSILON)) && !writableNFANodes.contains(edge.getEnd())) {
                    writableNFANodes.add(
                        edge.getEnd()
                    );
                }
            }
            i++;
        }

        createdNode.getContainedNfaNodes().addAll(writableNFANodes);
        createdNode.setAccepting(
            writableNFANodes
                .stream()
                .anyMatch(node -> node.getType().equals(NFANodeType.ACCEPT))
        );

        return createdNode;

    }

    public static DFAParseResult convert(NFAParseResult nfaParseResult) {

        var startingDFANode = reachableNFANodes(List.of(nfaParseResult.getStartingNode()));
        startingDFANode.setId(nodeId++);
        List<DFANode> createdNodes = new ArrayList<>(List.of(startingDFANode));

        int i = 0;
        while (i < createdNodes.size()) {
            var currDFANode = createdNodes.get(i++);
            Map<Transition, List<NFANode>> reachableNFANodes = new HashMap<>();
            Set<Character> currNodeTransitionCharacterSet = new HashSet<>();
            for (var containedNFANode : currDFANode.getContainedNfaNodes()) {
                for (var nfaEdge : containedNFANode.getEdges()) {
                    if (nfaEdge.getCharacters() != null && !nfaEdge.getCharacters().equals(Set.of(EPSILON))) {
                        var transition = new Transition()
                            .setCharacters(nfaEdge.isWildcard() ? null : nfaEdge.getCharacters())
                            .setNegated(nfaEdge.isNegated())
                            .setWildcard(nfaEdge.isWildcard());
                        currNodeTransitionCharacterSet.addAll(nfaEdge.getCharacters());
                        reachableNFANodes
                            .computeIfAbsent(transition, unused -> new ArrayList<>())
                            .add(nfaEdge.getEnd());
                    }
                }
            }

            for (var c : currNodeTransitionCharacterSet) {

                if (currDFANode.getTransitions().containsKey(c)) {
                    continue;
                }

                Set<NFANode> nodesTraversable = new HashSet<>(
                    reachableNFANodes.getOrDefault(
                        new Transition()
                            .setWildcard(true)
                            .setNegated(false),
                        Collections.emptyList()
                    )
                );

                nodesTraversable.addAll(
                    reachableNFANodes.entrySet().stream()
                        .filter(
                            entry -> {
                                if (entry.getKey().isWildcard()) {
                                    return true;
                                }
                                if (entry.getKey().isNegated()) {
                                    return !entry.getKey().getCharacters().contains(c);
                                } else {
                                    return entry.getKey().getCharacters().contains(c);
                                }
                            }
                        )
                        .map(Entry::getValue)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())
                );

                if (!nodesTraversable.isEmpty()) {
                    final var node = getDFANodeOfNFANodeList(createdNodes, nodesTraversable);
                    currDFANode.getTransitions().put(
                        c,
                        node
                    );
                } else {
                    currDFANode.getTransitions().put(
                        c,
                        null
                    );
                }

            }

            Set<NFANode> defaultReachableNodes = new HashSet<>();

            for (var reachableNFANodeEntry : reachableNFANodes.entrySet()) {

                if (reachableNFANodeEntry.getKey().isNegated() || reachableNFANodeEntry.getKey().isWildcard()) {
                    defaultReachableNodes.addAll(reachableNFANodeEntry.getValue());
                }

            }

            if (!defaultReachableNodes.isEmpty()) {

                final var defaultNode = getDFANodeOfNFANodeList(
                    createdNodes,
                    defaultReachableNodes
                );
                currDFANode.setDefaultTransition(defaultNode);

            }

        }

        return new DFAParseResult(
            startingDFANode,
            nfaParseResult.getFlags()
        );

    }

    private static Set<NFANode> reachableThroughEpsilon(Collection<NFANode> startingNodes) {
        Set<NFANode> found = new HashSet<>(startingNodes);
        List<NFANode> queue = new ArrayList<>(startingNodes);
        while (!queue.isEmpty()) {
            var curr = queue.get(0);
            queue.remove(0);
            for (var edge : curr.getEdges()) {
                if (edge.getCharacters().equals(Set.of(EPSILON)) && !found.contains(edge.getEnd())) {
                    found.add(edge.getEnd());
                    queue.add(edge.getEnd());
                }
            }
        }
        return found;
    }

    private static DFANode getDFANodeOfNFANodeList(List<DFANode> createdNodes, Set<NFANode> nfaNodeList) {
        var reachable = reachableThroughEpsilon(nfaNodeList);
        return createdNodes.stream()
            .filter(existingNode ->
                existingNode.getContainedNfaNodes().equals(reachable))
            .findAny()
            .orElseGet(() -> {
                var newNode = new DFANode()
                    .setId(nodeId++)
                    .setAccepting(
                        reachable
                            .stream()
                            .anyMatch(node -> node.getType().equals(NFANodeType.ACCEPT))
                    );
                newNode.getContainedNfaNodes()
                    .addAll(reachable);
                createdNodes.add(newNode);
                return newNode;
            });
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(chain = true)
    @EqualsAndHashCode
    private static class Transition {

        private Set<Character> characters = new HashSet<>();
        private boolean negated = false;
        private boolean wildcard = false;

        @Override
        public String toString() {
            return (wildcard ? "*" : ((negated ? "!" : "") + characters));
        }
    }

}

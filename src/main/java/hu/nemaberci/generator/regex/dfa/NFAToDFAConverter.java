package hu.nemaberci.generator.regex.dfa;

import static hu.nemaberci.generator.regex.nfa.RegexToNFAParser.EPSILON;

import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.nfa.RegexToNFAParser;
import hu.nemaberci.generator.regex.nfa.data.NFANode;
import hu.nemaberci.generator.regex.nfa.data.NFANode.NFANodeType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class NFAToDFAConverter {

    public DFANode parseAndConvert(String regex) {
        return convert(new RegexToNFAParser().parseAndConvert(regex));
    }

    int nodeId = 0;

    private DFANode reachableNFANodes(Collection<NFANode> nfaNodes) {

        DFANode createdNode = new DFANode();
        List<NFANode> writableNFANodes = new ArrayList<>(nfaNodes);

        int i = 0;
        while (i < writableNFANodes.size()) {
            var nfaNode = writableNFANodes.get(i);
            for (var edge : nfaNode.getEdges()) {
                if (edge.getCharacter() == EPSILON && !writableNFANodes.contains(edge.getEnd())) {
                    writableNFANodes.add(
                        edge.getEnd()
                    );
                }
            }
            i++;
        }

        createdNode.getItems().addAll(writableNFANodes);
        createdNode.setAccepting(
            writableNFANodes
                .stream()
                .anyMatch(node -> node.getType().equals(NFANodeType.ACCEPT))
        );

        return createdNode;

    }

    public DFANode convert(NFANode startingNFANode) {

        var startingDFANode = reachableNFANodes(List.of(startingNFANode));
        startingDFANode.setId(nodeId++);
        List<DFANode> createdNodes = new ArrayList<>(List.of(startingDFANode));

        int i = 0;
        while (i < createdNodes.size()) {
            var currDFANode = createdNodes.get(i++);
            Map<Character, List<NFANode>> reachableNFANodes = new HashMap<>();
            for (var containedNFANode : currDFANode.getItems()) {
                for (var nfaEdge : containedNFANode.getEdges()) {
                    if (nfaEdge.getCharacter() != EPSILON) {
                        reachableNFANodes.computeIfAbsent(
                                nfaEdge.getCharacter(), unused -> new ArrayList<>())
                            .add(nfaEdge.getEnd());
                    }
                }
            }
            Map<List<NFANode>, List<Character>> nfaNodeCollectionsToCharactersMap = new HashMap<>();
            reachableNFANodes.forEach(
                (character, nfaNodes) ->
                    nfaNodeCollectionsToCharactersMap.computeIfAbsent(
                            nfaNodes, unused -> new ArrayList<>())
                        .add(character)
            );
            nfaNodeCollectionsToCharactersMap.forEach(
                (nfaNodes, characters) -> {
                    var newDFANode = reachableNFANodes(nfaNodes);
                    DFANode dfaNode;
                    newDFANode.getSymbols().addAll(characters);
                    final var existingNode = createdNodes.stream().filter(node -> node.getItems().equals(newDFANode.getItems()) && node.getSymbols().equals(newDFANode.getSymbols())).findAny().orElse(null);
                    if (existingNode != null) {
                        dfaNode = existingNode;
                    } else {
                        createdNodes.add(newDFANode);
                        newDFANode.setId(nodeId++);
                        dfaNode = newDFANode;
                    }
                    characters.forEach(
                        c -> currDFANode.getTransitions().put(c, dfaNode)
                    );
                }
            );
        }

        return startingDFANode;

    }

}

package hu.nemaberci.generator.regex.nfa;

import hu.nemaberci.generator.regex.data.RegexNode;
import hu.nemaberci.generator.regex.nfa.data.NFANode;
import hu.nemaberci.generator.regex.nfa.data.NFANode.NFANodeType;
import hu.nemaberci.generator.regex.nfa.data.NFANodeEdge;
import hu.nemaberci.generator.regex.parser.RegexParser;
import java.util.Arrays;
import java.util.List;

public class RegexToNFAParser {

    public static final char EPSILON = 0x03B5;

    public NFANode parseAndConvert(String regex) {
        return convert(new RegexParser().parseRegex(regex).getFirstNode());
    }

    // todo list:
    // 1. Negated character ranges
    // 2. Lazy or eager switches
    private int generateNFAGraph(RegexNode regexNode, NFANode startNode, NFANode endNode, int count
    ) {
        if (startNode.getId() == -1) {
            startNode.setId(count++);
        }
        switch (regexNode.getType()) {
            case EMPTY: {
                startNode.getEdges().add(
                    new NFANodeEdge().setEnd(endNode).setCharacter(EPSILON)
                );
                break;
            }
            case CHARACTER: {
                startNode.getEdges().add(
                    new NFANodeEdge().setEnd(endNode).setCharacter(regexNode.getCharacters()[0])
                );
                break;
            }
            case STAR: {
                var tempStart = new NFANode();
                var tempEnd = new NFANode();
                tempEnd.getEdges().addAll(
                    List.of(
                        new NFANodeEdge()
                            .setCharacter(EPSILON)
                            .setEnd(tempStart),
                        new NFANodeEdge()
                            .setCharacter(EPSILON)
                            .setEnd(endNode)
                    )
                );
                startNode.getEdges().addAll(
                    List.of(
                        new NFANodeEdge()
                            .setCharacter(EPSILON)
                            .setEnd(tempStart),
                        new NFANodeEdge()
                            .setCharacter(EPSILON)
                            .setEnd(endNode)
                    )
                );
                count = generateNFAGraph(regexNode.getExpression(), tempStart, tempEnd, count);
                break;
            }
            case CONCATENATION: {
                var last = startNode;
                for (int i = 0; i < regexNode.getParts().size() - 1; i++) {
                    var tempNode = new NFANode();
                    count = generateNFAGraph(
                        regexNode.getParts().get(i),
                        last,
                        tempNode,
                        count
                    );
                    last = tempNode;
                }
                count = generateNFAGraph(
                    regexNode.getParts().get(regexNode.getParts().size() - 1),
                    last,
                    endNode,
                    count
                );
                break;
            }
            case ALTERNATION: {
                for (var node : regexNode.getParts()) {
                    var tempStart = new NFANode();
                    var tempEnd = new NFANode();
                    tempEnd.getEdges().add(
                        new NFANodeEdge()
                            .setCharacter(EPSILON)
                            .setEnd(endNode)
                    );
                    startNode.getEdges().add(
                        new NFANodeEdge()
                            .setCharacter(EPSILON)
                            .setEnd(tempStart)
                    );
                    count = generateNFAGraph(node, tempStart, tempEnd, count);
                }
                break;
            }
            case CHARACTER_RANGE: {
                for (char c : regexNode.getCharacters()) {
                    startNode.getEdges().add(
                        new NFANodeEdge().setEnd(endNode).setCharacter(c)
                    );
                }
                break;
            }
            case NEGATED_CHARACTER_RANGE: {
                startNode.setType(NFANodeType.NEGATED);
                for (char c : regexNode.getCharacters()) {
                    startNode.getEdges().add(
                        new NFANodeEdge().setEnd(endNode).setCharacter(c)
                    );
                }
                break;
            }
            case ANY: {
                startNode.getEdges().add(
                    new NFANodeEdge()
                        .setWildcard(true)
                        .setEnd(endNode)
                );
                break;
            }
        }
        if (endNode.getId() == -1) {
            endNode.setId(count++);
        }
        return count;
    }

    public NFANode convert(RegexNode regexNode) {
        var startNode = new NFANode()
            .setType(NFANodeType.START);
        var acceptNode = new NFANode()
            .setType(NFANodeType.ACCEPT);
        generateNFAGraph(regexNode, startNode, acceptNode, 0);
        return startNode;
    }

}

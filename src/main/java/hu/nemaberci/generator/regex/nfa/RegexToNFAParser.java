package hu.nemaberci.generator.regex.nfa;

import hu.nemaberci.generator.regex.data.RegexNode;
import hu.nemaberci.generator.regex.data.RegexParseResult;
import hu.nemaberci.generator.regex.nfa.data.NFANode;
import hu.nemaberci.generator.regex.nfa.data.NFANode.NFANodeType;
import hu.nemaberci.generator.regex.nfa.data.NFANodeEdge;
import hu.nemaberci.generator.regex.nfa.data.NFAParseResult;
import hu.nemaberci.generator.regex.parser.RegexParser;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.ArrayUtils;

public class RegexToNFAParser {

    public static final char EPSILON = 0x03B5;

    private int id = 0;

    public NFAParseResult parseAndConvert(String regex) {
        return convert(new RegexParser().parseRegex(regex));
    }

    private Set<Character> singleChar(char c) {
        return Set.of(c);
    }

    // todo list:
    // 2. Lazy or eager switches
    private void generateNFAGraph(RegexNode regexNode, NFANode startNode, NFANode endNode) {
        switch (regexNode.getType()) {
            case EMPTY: {
                startNode.getEdges().add(
                    new NFANodeEdge().setEnd(endNode).setCharacters(singleChar(EPSILON))
                );
                break;
            }
            case CHARACTER: {
                startNode.getEdges().add(
                    new NFANodeEdge().setEnd(endNode).setCharacters(singleChar(regexNode.getCharacters()[0]))
                );
                break;
            }
            case STAR: {
                var tempStart = new NFANode()
                    .setId(id++);
                var tempEnd = new NFANode()
                    .setId(id++);
                tempEnd.getEdges().addAll(
                    List.of(
                        new NFANodeEdge()
                            .setCharacters(singleChar(EPSILON))
                            .setEnd(tempStart),
                        new NFANodeEdge()
                            .setCharacters(singleChar(EPSILON))
                            .setEnd(endNode)
                    )
                );
                startNode.getEdges().addAll(
                    List.of(
                        new NFANodeEdge()
                            .setCharacters(singleChar(EPSILON))
                            .setEnd(tempStart),
                        new NFANodeEdge()
                            .setCharacters(singleChar(EPSILON))
                            .setEnd(endNode)
                    )
                );
                generateNFAGraph(regexNode.getExpression(), tempStart, tempEnd);
                break;
            }
            case CONCATENATION: {
                var last = startNode;
                for (int i = 0; i < regexNode.getParts().size() - 1; i++) {
                    var tempNode = new NFANode()
                        .setId(id++);
                    generateNFAGraph(
                        regexNode.getParts().get(i),
                        last,
                        tempNode
                    );
                    last = tempNode;
                }
                generateNFAGraph(
                    regexNode.getParts().get(regexNode.getParts().size() - 1),
                    last,
                    endNode
                );
                break;
            }
            case ALTERNATION: {
                var altStart = new NFANode()
                    .setId(id++);
                var altEnd = new NFANode()
                    .setId(id++);
                for (var node : regexNode.getParts()) {
                    var tempStart = new NFANode()
                        .setId(id++);
                    var tempEnd = new NFANode()
                        .setId(id++);
                    tempEnd.getEdges().add(
                        new NFANodeEdge()
                            .setCharacters(singleChar(EPSILON))
                            .setEnd(altEnd)
                    );
                    altStart.getEdges().add(
                        new NFANodeEdge()
                            .setCharacters(singleChar(EPSILON))
                            .setEnd(tempStart)
                    );
                    generateNFAGraph(node, tempStart, tempEnd);
                }
                startNode.getEdges()
                    .add(
                        new NFANodeEdge()
                            .setCharacters(singleChar(EPSILON))
                            .setEnd(altStart)
                    );
                altEnd.getEdges()
                    .add(
                        new NFANodeEdge()
                            .setCharacters(singleChar(EPSILON))
                            .setEnd(endNode)
                    );
                break;
            }
            case CHARACTER_RANGE: {
                var temp = new NFANode()
                    .setId(id++);
                startNode.getEdges().add(
                    new NFANodeEdge()
                        .setCharacters(new HashSet<>(
                            List.of(
                                ArrayUtils.toObject(
                                    regexNode.getCharacters()
                                )
                            ))
                        )
                        .setEnd(temp)
                );
                temp.getEdges().add(
                    new NFANodeEdge()
                        .setCharacters(singleChar(EPSILON))
                        .setEnd(endNode)
                );
                break;
            }
            case NEGATED_CHARACTER_RANGE: {
                var temp = new NFANode()
                    .setId(id++);
                startNode.getEdges().add(
                    new NFANodeEdge()
                        .setNegated(true)
                        .setCharacters(new HashSet<>(
                            List.of(
                                ArrayUtils.toObject(
                                    regexNode.getCharacters()
                                )
                            ))
                        )
                        .setEnd(temp)
                );
                temp.getEdges().add(
                    new NFANodeEdge()
                        .setCharacters(singleChar(EPSILON))
                        .setEnd(endNode)
                );
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
    }

    public NFAParseResult convert(RegexParseResult regexParseResult) {
        this.id = 0;
        var startNode = new NFANode()
            .setType(NFANodeType.START)
            .setId(id++);
        var acceptNode = new NFANode()
            .setType(NFANodeType.ACCEPT)
            .setId(id++);
        generateNFAGraph(regexParseResult.getFirstNode(), startNode, acceptNode);
        return new NFAParseResult(startNode, regexParseResult.getFlags());
    }

}

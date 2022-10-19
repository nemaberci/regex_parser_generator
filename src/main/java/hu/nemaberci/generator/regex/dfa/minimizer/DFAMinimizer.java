package hu.nemaberci.generator.regex.dfa.minimizer;

import hu.nemaberci.generator.regex.dfa.NFAToDFAConverter;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DFAMinimizer {

    /*
     * ORIGINAL JS CODE
     *
     * function minDfa(dfa) {
     *     'use strict';
     *     function getReverseEdges(start) {
     *         var i, top, symbol, next,
     *             front = 0,
     *             queue = [start],
     *             visited = {},
     *             symbols = {},   // The input alphabet
     *             idMap = {},     // Map id to states
     *             revEdges = {};  // Map id to the ids which connects to the id with an alphabet
     *         visited[start.id] = true;
     *         while (front < queue.length) {
     *             top = queue[front];
     *             front += 1;
     *             idMap[top.id] = top;
     *             for (i = 0; i < top.symbols.length; i += 1) {
     *                 symbol = top.symbols[i];
     *                 if (!symbols.hasOwnProperty(symbol)) {
     *                     symbols[symbol] = true;
     *                 }
     *                 next = top.trans[symbol];
     *                 if (!revEdges.hasOwnProperty(next.id)) {
     *                     revEdges[next.id] = {};
     *                 }
     *                 if (!revEdges[next.id].hasOwnProperty(symbol)) {
     *                     revEdges[next.id][symbol] = [];
     *                 }
     *                 revEdges[next.id][symbol].push(top.id);
     *                 if (!visited.hasOwnProperty(next.id)) {
     *                     visited[next.id] = true;
     *                     queue.push(next);
     *                 }
     *             }
     *         }
     *         return [Object.keys(symbols), idMap, revEdges];
     *     }
     *     function hopcroft(symbols, idMap, revEdges) {
     *         var i, j, k, keys, key, key1, key2, top, group1, group2, symbol, revGroup,
     *             ids = Object.keys(idMap).sort(),
     *             partitions = {},
     *             front = 0,
     *             queue = [],
     *             visited = {};
     *         group1 = [];
     *         group2 = [];
     *         for (i = 0; i < ids.length; i += 1) {
     *             if (idMap[ids[i]].type === 'accept') {
     *                 group1.push(ids[i]);
     *             } else {
     *                 group2.push(ids[i]);
     *             }
     *         }
     *         key = group1.join(',');
     *         partitions[key] = group1;
     *         queue.push(key);
     *         visited[key] = 0;
     *         if (group2.length !== 0) {
     *             key = group2.join(',');
     *             partitions[key] = group2;
     *             queue.push(key);
     *         }
     *         while (front < queue.length) {
     *             top = queue[front];
     *             front += 1;
     *             if (top) {
     *                 top = top.split(',');
     *                 for (i = 0; i < symbols.length; i += 1) {
     *                     symbol = symbols[i];
     *                     revGroup = {};
     *                     for (j = 0; j < top.length; j += 1) {
     *                         if (revEdges.hasOwnProperty(top[j]) && revEdges[top[j]].hasOwnProperty(symbol)) {
     *                             for (k = 0; k < revEdges[top[j]][symbol].length; k += 1) {
     *                                 revGroup[revEdges[top[j]][symbol][k]] = true;
     *                             }
     *                         }
     *                     }
     *                     keys = Object.keys(partitions);
     *                     for (j = 0; j < keys.length; j += 1) {
     *                         key = keys[j];
     *                         group1 = [];
     *                         group2 = [];
     *                         for (k = 0; k < partitions[key].length; k += 1) {
     *                             if (revGroup.hasOwnProperty(partitions[key][k])) {
     *                                 group1.push(partitions[key][k]);
     *                             } else {
     *                                 group2.push(partitions[key][k]);
     *                             }
     *                         }
     *                         if (group1.length !== 0 && group2.length !== 0) {
     *                             delete partitions[key];
     *                             key1 = group1.join(',');
     *                             key2 = group2.join(',');
     *                             partitions[key1] = group1;
     *                             partitions[key2] = group2;
     *                             if (visited.hasOwnProperty(key1)) {
     *                                 queue[visited[key1]] = null;
     *                                 visited[key1] = queue.length;
     *                                 queue.push(key1);
     *                                 visited[key2] = queue.length;
     *                                 queue.push(key2);
     *                             } else if (group1.length <= group2.length) {
     *                                 visited[key1] = queue.length;
     *                                 queue.push(key1);
     *                             } else {
     *                                 visited[key2] = queue.length;
     *                                 queue.push(key2);
     *                             }
     *                         }
     *                     }
     *                 }
     *             }
     *         }
     *         return Object.values(partitions);
     *     }
     *     function buildMinNfa(start, partitions, idMap, revEdges) {
     *         var i, j, temp, node, symbol,
     *             nodes = [],
     *             group = {},
     *             edges = {};
     *         partitions.sort(function (a, b) {
     *             var ka = a.join(','), kb = b.join(',');
     *             if (ka < kb) {
     *                 return -1;
     *             }
     *             if (ka > kb) {
     *                 return 1;
     *             }
     *             return 0;
     *         });
     *         for (i = 0; i < partitions.length; i += 1) {
     *             if (partitions[i].indexOf(start.id) >= 0) {
     *                 if (i > 0) {
     *                     temp = partitions[i];
     *                     partitions[i] = partitions[0];
     *                     partitions[0] = temp;
     *                 }
     *                 break;
     *             }
     *         }
     *         for (i = 0; i < partitions.length; i += 1) {
     *             node = {
     *                 id: (i + 1).toString(),
     *                 key: partitions[i].join(','),
     *                 items: [],
     *                 symbols: [],
     *                 type: idMap[partitions[i][0]].type,
     *                 edges: [],
     *                 trans: {},
     *             };
     *             for (j = 0; j < partitions[i].length; j += 1) {
     *                 node.items.push(idMap[partitions[i][j]]);
     *                 group[partitions[i][j]] = i;
     *             }
     *             edges[i] = {};
     *             nodes.push(node);
     *         }
     *         Object.keys(revEdges).forEach(function (to) {
     *             Object.keys(revEdges[to]).forEach(function (symbol) {
     *                 revEdges[to][symbol].forEach(function (from) {
     *                     if (!edges[group[from]].hasOwnProperty(group[to])) {
     *                         edges[group[from]][group[to]] = {};
     *                     }
     *                     edges[group[from]][group[to]][symbol] = true;
     *                 });
     *             });
     *         });
     *         Object.keys(edges).forEach(function (from) {
     *             Object.keys(edges[from]).forEach(function (to) {
     *                 symbol = Object.keys(edges[from][to]).sort().join(',');
     *                 nodes[from].symbols.push(symbol);
     *                 nodes[from].edges.push([symbol, nodes[to]]);
     *                 nodes[from].trans[symbol] = nodes[to];
     *             });
     *         });
     *         return nodes[0];
     *     }
     *     var edgesTuple = getReverseEdges(dfa),
     *         symbols = edgesTuple[0],
     *         idMap = edgesTuple[1],
     *         revEdges = edgesTuple[2],
     *         partitions = hopcroft(symbols, idMap, revEdges);
     *     return buildMinNfa(dfa, partitions, idMap, revEdges);
     * }
     * */

    public DFANode parseAndConvertAndMinimize(String regex) {
        return new NFAToDFAConverter().parseAndConvert(regex);
    }

    private void walkTreeAndAllNeighbours(List<DFANode> nodes, DFANode curr) {
        curr.getTransitions().forEach(
            (character, otherNode) -> {
                if (!nodes.contains(otherNode)) {
                    nodes.add(otherNode);
                    walkTreeAndAllNeighbours(nodes, otherNode);
                }
            }
        );
    }

    private DFANode find(List<DFANode> nodes, int id) {
        return nodes.stream()
            .filter(node -> node.getId() == id)
            .findFirst()
            .get();
    }

    private List<DFANode> createInverseDFA(List<DFANode> nodes) {
        List<DFANode> inverse = new ArrayList<>();
        nodes.forEach(
            node ->
                inverse.add(
                    new DFANode().setId(node.getId()).setAccepting(node.isAccepting())
                )
        );
        nodes.forEach(
            node ->
                node.getTransitions().forEach(
                    (c, other) ->
                        find(inverse, other.getId()).getTransitions().put(c, node)
                )
        );
        return inverse;
    }

    private void constructA(Map<Character, List<List<DFANode>>> a, List<Character> alphabet, List<List<DFANode>> B, int j) {
        for (var c : alphabet) {
            if (a.get(c).size() > j) {
                a.get(c).get(j).clear();
            } else {
                a.get(c).add(new ArrayList<>());
            }
            for (var node : B.get(j)) {
                if (node.getTransitions().containsKey(c)) {
                    a.get(c).get(j).add(node);
                }
            }
            a.get(c).add(new ArrayList<>());
            for (var node : B.get(a.get(c).size() - 1)) {
                if (node.getTransitions().containsKey(c)) {
                    a.get(c).get(a.get(c).size() - 1).add(node);
                }
            }
        }
    }

    private List<List<DFANode>> hopcroft(List<DFANode> states) {
        // Step 1.
        final List<DFANode> inverse = createInverseDFA(states);
        final List<Character> alphabet = extractAlphabet(states);

        final List<DFANode> acceptingNodes = states.stream()
            .filter(DFANode::isAccepting)
            .collect(Collectors.toList());
        final List<DFANode> nonAcceptingNodes = states.stream()
            .filter(Predicate.not(DFANode::isAccepting))
            .collect(Collectors.toList());

        final List<DFANode> inverseAcceptingNodes = inverse.stream().filter(DFANode::isAccepting)
            .collect(Collectors.toList());
        final List<DFANode> inverseNonAcceptingNodes = inverse.stream()
            .filter(Predicate.not(DFANode::isAccepting))
            .collect(Collectors.toList());

        /*
         * Hopcroft used indexing starting at 1, however, in Java, list indexes begin at 0, so
         * some portions of the implementation have been changed to reflect this implementation
         * detail.
         * */

        // Step 2.
        // Named originally "a" in Hopcroft's paper.
        Map<Character, List<List<DFANode>>> a = new HashMap<>();
        // Named originally "B" in Hopcroft's paper.
        List<List<DFANode>> B = new ArrayList<>();
        B.add(acceptingNodes);
        B.add(nonAcceptingNodes);
        List<List<DFANode>> inverseB = new ArrayList<>();
        inverseB.add(inverseAcceptingNodes);
        inverseB.add(inverseNonAcceptingNodes);
        for (var c : alphabet) {
            a.put(c, new ArrayList<>());
        }
        for (var c : alphabet) {
            a.get(c).add(new ArrayList<>());
            for (var node : inverseB.get(0)) {
                if (node.getTransitions().containsKey(c)) {
                    a.get(c).get(0).add(find(B.get(0), node.getId()));
                }
            }
            a.get(c).add(new ArrayList<>());
            for (var node : inverseB.get(1)) {
                if (node.getTransitions().containsKey(c)) {
                    a.get(c).get(1).add(find(B.get(1), node.getId()));
                }
            }
        }
        // Step 3.
        int k = 2;
        // Step 4.
        Map<Character, List<Integer>> L = new HashMap<>();
        for (var c : alphabet) {
            L.put(
                c,
                a.get(c).get(0).size() > a.get(c).get(1).size() ? new ArrayList<>(List.of(1))
                    : new ArrayList<>(List.of(0))
            );
        }
        // Step 5.
        // Originally "a" in Hopcroft's paper, however, he uses "a" multiple times to
        // refer to multiple variables.
        int characterIndex = 0;
        char c;
        boolean complete = false;
        while (!complete) {
            c = alphabet.get(characterIndex++);
            while (!L.get(c).isEmpty()) {
                // Step 6.
                var i = L.get(c).get(0);
                L.get(c).remove(0);

                // Step 7.
                for (int j = 0; j < k; j++) {

                    char finalC = c;
                    final List<DFANode> ai = a.get(finalC).get(i);
                    final var bj = B.get(j);
                    final var transitionsWithCharacter = bj.stream().map(
                        t -> t.getTransitions().keySet().stream()
                            .filter(character -> finalC == character)
                            .map(character -> t.getTransitions().get(character))
                            .collect(Collectors.toList())
                    ).flatMap(Collection::stream).collect(Collectors.toList());
                    // Step 7.a.
                    // Originally B'(j) in Hopcroft's paper
                    List<DFANode> split = bj.stream().filter(
                        t -> t.getTransitions().keySet().stream()
                            .anyMatch(
                                character ->
                                    character == finalC &&
                                        ai.contains(
                                            t.getTransitions().get(character)
                                        )
                            )
                    ).collect(Collectors.toList());
                    if (!split.isEmpty()) {
                        // Originally B"(j) in Hopcroft's paper
                        List<DFANode> remainder = B.get(j).stream()
                            .filter(Predicate.not(split::contains))
                            .collect(Collectors.toList());

                        // Step 7.b.
                        B.set(j, remainder);
                        B.add(remainder);
                        constructA(a, alphabet, B, k);

                        // Step 7.c.
                        int finalJ = j;
                        int finalK = k;
                        L.forEach(
                            (character, integers) -> {
                                final List<DFANode> aj = a.get(character).get(finalJ);
                                final List<DFANode> ak = a.get(character).get(finalK);
                                if (
                                    integers.contains(finalJ) ||
                                        (
                                            !aj.isEmpty() &&
                                                aj.size() <= ak.size()
                                        )
                                ) {
                                    integers.add(finalK);
                                } else {
                                    integers.add(finalJ);
                                }
                            }
                        );

                        // Step 7.d.
                        k++;

                        // Probably needed
                        characterIndex = 0;
                    }

                }

            }

            // Step 5. cont. / Step 8.
            complete = alphabet.stream().allMatch(
                character -> L.get(character).isEmpty()
            );
        }

        return B;

    }

    // todo
    public DFANode minimize(DFANode startNode) {
        final List<DFANode> allNodes = extractAllNodes(startNode);
        var done = hopcroft(allNodes);
        return startNode;
    }

    private List<DFANode> extractAllNodes(DFANode startNode) {
        List<DFANode> allNodes = new ArrayList<>();
        allNodes.add(startNode);
        walkTreeAndAllNeighbours(allNodes, startNode);
        return allNodes;
    }

    private static List<Character> extractAlphabet(List<DFANode> allNodes) {
        List<Character> alphabet = new ArrayList<>();
        for (var node : allNodes) {
            for (var c : node.getTransitions().keySet()) {
                if (!alphabet.contains(c)) {
                    alphabet.add(c);
                }
            }
        }
        return alphabet;
    }

}

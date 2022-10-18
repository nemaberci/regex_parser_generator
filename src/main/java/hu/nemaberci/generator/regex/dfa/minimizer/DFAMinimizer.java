package hu.nemaberci.generator.regex.dfa.minimizer;

import hu.nemaberci.generator.regex.dfa.NFAToDFAConverter;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.generator.regex.dfa.data.DFANodeEdge;

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

    // todo
    public DFANode minimize(DFANode startNode) {
        return startNode;
    }

}

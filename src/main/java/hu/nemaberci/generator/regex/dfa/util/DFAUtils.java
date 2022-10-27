package hu.nemaberci.generator.regex.dfa.util;

import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DFAUtils {

    public static List<DFANode> extractAllNodes(DFANode startingNode) {
        List<DFANode> allNodes = new ArrayList<>();
        List<DFANode> queue = new ArrayList<>();
        queue.add(startingNode);

        while (!queue.isEmpty()) {
            var otherNode = queue.get(0);
            queue.remove(0);
            if (allNodes.contains(otherNode)) {
                continue;
            }
            allNodes.add(otherNode);
            queue.addAll(otherNode.getTransitions().values().stream()
                .filter(Predicate.not(queue::contains))
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
            );
            if (otherNode.getDefaultTransition() != null) {
                queue.add(otherNode.getDefaultTransition());
            }
        }

        return allNodes;
    }

}

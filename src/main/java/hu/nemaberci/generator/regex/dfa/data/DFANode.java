package hu.nemaberci.generator.regex.dfa.data;

import hu.nemaberci.generator.regex.nfa.data.NFANode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Exclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Getter
@NoArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode
public class DFANode {

    private int id = -1;
    private boolean accepting = false;
    private final List<NFANode> items = new ArrayList<>();
    @Exclude
    private final Set<DFANodeEdge> transitions = new HashSet<>();
    @Exclude
    private int distanceFromStart = -1;

    @Override
    public String toString() {
        return Integer.toString(id);
    }

}

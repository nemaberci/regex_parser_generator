package hu.nemaberci.generator.regex.nfa.data;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Getter
@NoArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode
public class NFANode {

    private final List<NFANodeEdge> edges = new ArrayList<>();
    private NFANodeType type = NFANodeType.EMPTY;
    private int id = -1; //

    public enum NFANodeType {
        START,
        ACCEPT,
        EMPTY
    }

    @Override
    public String toString() {
        return Integer.toString(id);
    }

}

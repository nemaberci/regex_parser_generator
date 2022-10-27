package hu.nemaberci.generator.regex.nfa.data;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Getter
@NoArgsConstructor
@Accessors(chain = true)
public class NFANodeEdge {

    private Set<Character> characters = new HashSet<>();
    private NFANode end;
    private boolean wildcard = false;
    private boolean negated = false;

    @Override
    public String toString() {
        return (wildcard ? "*" : characters) + " --> " + end;
    }
}

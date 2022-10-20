package hu.nemaberci.generator.regex.dfa.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Getter
@NoArgsConstructor
@Accessors(chain = true)
public class DFANodeEdge {

    private boolean negated = false;
    private boolean wildcard = false;
    private char character;
    private DFANode end;

    @Override
    public String toString() {
        return (wildcard ? "*" : "") + (negated ? "!" : "") + character + " --> " + end;
    }
}

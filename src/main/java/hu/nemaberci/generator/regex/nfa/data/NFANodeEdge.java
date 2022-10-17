package hu.nemaberci.generator.regex.nfa.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Getter
@NoArgsConstructor
@Accessors(chain = true)
public class NFANodeEdge {

    private char character;
    private NFANode end;

}

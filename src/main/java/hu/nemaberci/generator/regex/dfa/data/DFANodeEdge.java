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

    private char character;
    private DFANode end;

}

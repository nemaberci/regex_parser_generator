package hu.nemaberci.generator.regex.nfa.data;

import hu.nemaberci.generator.regex.data.RegexFlag;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@AllArgsConstructor
@Accessors(chain = true)
public class NFAParseResult {

    private final NFANode startingNode;
    private final Collection<RegexFlag> flags;

}

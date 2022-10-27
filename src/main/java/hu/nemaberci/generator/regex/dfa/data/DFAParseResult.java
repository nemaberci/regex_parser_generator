package hu.nemaberci.generator.regex.dfa.data;

import hu.nemaberci.generator.regex.data.RegexFlag;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@AllArgsConstructor
@Setter
@Getter
@Accessors(chain = true)
public class DFAParseResult {

    private DFANode startingNode;
    private Collection<RegexFlag> flags;

}

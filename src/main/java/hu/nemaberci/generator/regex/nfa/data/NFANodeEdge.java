package hu.nemaberci.generator.regex.nfa.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Setter
@Getter
@NoArgsConstructor
@Accessors(chain = true)
public class NFANodeEdge {

    private char character;
    private NFANode end;
    private boolean wildcard = false;

    //@Override
    //public String toString() {
    //    return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
    //}

}

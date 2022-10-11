package hu.nemaberci.generator.data;

import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Data
public class RegexAtom {

    private RegexAlternation alternation;
    private RegexLiteral literal;
    private RegexCharacterclass characterClass;

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
    }

}

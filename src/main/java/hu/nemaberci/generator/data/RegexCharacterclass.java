package hu.nemaberci.generator.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Data
public class RegexCharacterclass {

    private boolean negated;
    private List<RegexLiteral> acceptedLiterals = new ArrayList<>();

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
    }
}

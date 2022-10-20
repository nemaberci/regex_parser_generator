package hu.nemaberci.generator.regex.data;

import java.util.ArrayList;
import java.util.List;
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
public class RegexNode {

    private final List<RegexNode> parts = new ArrayList<>();
    private RegexNode expression;
    private RegexNodeType type;
    int start;
    int end;
    char[] characters;

    public RegexNode copy() {
        var toReturn = new RegexNode()
            .setStart(start)
            .setEnd(end)
            .setCharacters(characters)
            .setType(type)
            .setExpression(expression);
        toReturn.getParts().addAll(parts);
        return toReturn;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
    }

    public enum RegexNodeType {
        EMPTY, // epsilon
        CHARACTER, // a
        STAR, // *
        CONCATENATION, // +
        ALTERNATION, // ?
        CHARACTER_RANGE, // []
        NEGATED_CHARACTER_RANGE, // [^]
        ANY // .
    }

}

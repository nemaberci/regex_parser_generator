package hu.nemaberci.generator.regex.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class RegexParseResult {

    private RegexNode firstNode;
    private final List<RegexFlag> flags = new ArrayList<>();

}

package hu.nemaberci.generator.dfa;

import hu.nemaberci.generator.regex.dfa.minimizer.DFAMinimizer;
import hu.nemaberci.generator.regex.dfa.util.DFAUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DFATests {

    @Test
    void testDfaGetsGenerated() {
        final var parseResult = DFAMinimizer.parseAndConvertAndMinimize("qwe123");
        Assertions.assertEquals(
            7,
            DFAUtils.extractAllNodes(parseResult.getStartingNode()).size()
        );;
    }

}

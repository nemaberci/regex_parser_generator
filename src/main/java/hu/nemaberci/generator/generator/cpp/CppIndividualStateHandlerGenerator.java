package hu.nemaberci.generator.generator.cpp;

import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.*;
import static hu.nemaberci.generator.generator.cpp.CppParserFileGenerator.addResultFunctionImplementation;

import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.SwitchCase;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

public class CppIndividualStateHandlerGenerator {

    static String restartSearchBody(
        DFANode defaultNode,
        Collection<RegexFlag> flags
    ) {
        StringBuilder restartSearchBody = new StringBuilder();
        if (flags.contains(RegexFlag.START_OF_STRING)) {
            restartSearchBody.append(
                    // String.format("std::cout << \"Transitioning to state -1\\n\"") +
                    String.format(
                        "%s = -1;\n",
                        CURR_STATE
                    )
                )
                .append(
                    String.format(
                        "if (%s > 0) {\n",
                        LAST_SUCCESSFUL_MATCH_AT
                    )
                )
                .append(
                    addResultFunctionImplementation()
                    // String.format(
                    //     "addResult("
                    //         + "%s,"
                    //         + "%s,"
                    //         + "%s,"
                    //         + "%s"
                    //         + ");\n",
                    //     LAST_SUCCESSFUL_MATCH_AT,
                    //     MATCH_STARTED_AT,
                    //     CURR_INDEX,
                    //     FOUND
                    // )
                )
                .append("}\n");
        } else {
            restartSearchBody.append(
                // String.format("std::cout << \"Restarting search that started at \" << %s << \", currently in state \" << %s << \", current character is \" << %s << std::endl;\n", MATCH_STARTED_AT, CURR_STATE, CURR_CHAR) +
                String.format(
                    "%s = %d;\n",
                    CURR_STATE,
                    defaultNode.getId()
                )
            );
            if (! flags.contains(RegexFlag.END_OF_STRING)) {
                restartSearchBody.append(
                    addResultFunctionImplementation()
                    // String.format(
                    //     "addResult("
                    //         + "%s,"
                    //         + "%s,"
                    //         + "%s,"
                    //         + "%s"
                    //         + ");\n",
                    //     LAST_SUCCESSFUL_MATCH_AT,
                    //     MATCH_STARTED_AT,
                    //     CURR_INDEX,
                    //     FOUND
                    // )
                );
            }
        }
        return restartSearchBody.toString();
    }

    protected static List<SwitchCase> switchCasesForOneVariableSwitchForMatches(
        DFANode curr,
        DFANode defaultNode,
        Collection<RegexFlag> flags
    ) {
        final List<SwitchCase> switchCases = new ArrayList<>();
        if (curr.isAccepting()) {
            for (int i = 0; i < 256; i++) {
                switchCases.add(
                    new SwitchCase(
                        String.format(
                            "(%d << 8) ^ %d",
                            curr.getId(),
                            i
                        ),
                        "return true;"
                    )
                );
            }
        } else {
            addNextStateNavigation(curr, defaultNode, switchCases, flags);
        }
        return switchCases;
    }

    protected static List<SwitchCase> addCurrentDFANodeTransitionsForFindMatches(
        DFANode curr,
        DFANode defaultNode,
        Collection<RegexFlag> flags
    ) {
        final List<SwitchCase> switchCases = new ArrayList<>();
        if (curr.isAccepting()) {
            if (curr.getTransitions().isEmpty()) {
                for (int i = 0; i < 256; i++) {
                    switchCases.add(
                        new SwitchCase(
                            String.format(
                                "(%d << 8) ^ %d",
                                curr.getId(),
                                i
                            ),
                            // String.format("std::cout << \"Found match at \" << %s << std::endl;\n", CURR_INDEX) +
                            String.format(
                                "%s = %s;\n",
                                LAST_SUCCESSFUL_MATCH_AT,
                                CURR_INDEX
                            ) + restartSearchBody(
                                defaultNode,
                                flags
                            )
                        )
                    );
                }
            } else {
                lazyCharSwitch(curr, defaultNode, switchCases, flags);
            }
        } else {
            addNextStateNavigation(curr, defaultNode, switchCases, flags);
        }
        return switchCases;
    }

    private static void lazyCharSwitch(
        DFANode curr,
        DFANode defaultNode,
        List<SwitchCase> switchCases,
        Collection<RegexFlag> flags
    ) {

        List<SwitchCase> edgeSwitchCases = new ArrayList<>();
        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(curr, defaultNode, edgeSwitchCases, edge, flags, true)
        );

        for (int i = 0; i < 256; i++) {
            if (
                curr.getTransitions().get((char) i) == null
                    && ! flags.contains(RegexFlag.START_OF_STRING)
            ) {
                if (curr.getDefaultTransition() != null) {
                    edgeSwitchCases.add(
                        new SwitchCase(
                            String.format(
                                "(%d << 8) ^ %d",
                                curr.getId(),
                                i
                            ),
                            // String.format("std::cout << \"Transitioning to state %d\n\";", curr.getDefaultTransition().getId()) +
                            String.format(
                                "%s = %s;\n%s = %d;\n",
                                LAST_SUCCESSFUL_MATCH_AT,
                                CURR_INDEX,
                                CURR_STATE,
                                curr.getDefaultTransition().getId()
                            )
                        )
                    );
                } else {
                    edgeSwitchCases.add(
                        new SwitchCase(
                            String.format(
                                "(%d << 8) ^ %d",
                                curr.getId(),
                                i
                            ),
                            // String.format("std::cout << \"Transitioning to state %d\n\";", curr.getDefaultTransition().getId()) +
                            String.format(
                                "%s = %s;\n",
                                LAST_SUCCESSFUL_MATCH_AT,
                                CURR_INDEX
                            ) + restartSearchBody(
                                defaultNode,
                                flags
                            )
                        )
                    );
                }
            }
        }

        switchCases.addAll(edgeSwitchCases);
    }

    private static void addNextStateNavigation(
        DFANode curr,
        DFANode defaultNode,
        List<SwitchCase> switchCases,
        Collection<RegexFlag> flags
    ) {

        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(curr, defaultNode, switchCases, edge, flags, false)
        );

        for (int i = 0; i < 256; i++) {
            if (
                curr.getTransitions().get((char) i) == null
                    && ! flags.contains(RegexFlag.START_OF_STRING)
                    && curr.getDefaultTransition() != null
            ) {
                switchCases.add(
                    new SwitchCase(
                        String.format(
                            "(%d << 8) ^ %d",
                            curr.getId(),
                            i
                        ),
                        // String.format("std::cout << \"Transitioning to state %d\n\";", curr.getDefaultTransition().getId()) +
                        String.format(
                            "%s = %d;\n",
                            CURR_STATE,
                            curr.getDefaultTransition().getId()
                        )
                    )
                );
            }
        }

    }

    private static void caseOfEdge(
        DFANode curr,
        DFANode defaultNode,
        List<SwitchCase> switchCases,
        Entry<Character, DFANode> edge,
        Collection<RegexFlag> flags,
        boolean addMatch
    ) {
        if (edge.getValue() != null) {

            switchCases.add(
                new SwitchCase(
                    String.format(
                        "(%d << 8) ^ %d",
                        curr.getId(),
                        (int) edge.getKey()
                    ),
                    (
                        addMatch
                            ? (
                            // String.format("std::cout << \"Found match at \" << %s << std::endl;\n", CURR_INDEX) +
                            String.format("%s = %s;\n", LAST_SUCCESSFUL_MATCH_AT, CURR_INDEX)
                        )
                            : ""
                    )
                        +
                        // String.format("std::cout << \"Transitioning to state %d\n\";", edge.getValue().getId()) +
                        String.format(
                            "%s = %d;\n",
                            CURR_STATE,
                            edge.getValue().getId()
                        )
                )
            );

        } else {

            switchCases.add(
                new SwitchCase(
                    String.format(
                        "(%d << 8) ^ %d",
                        curr.getId(),
                        (int) edge.getKey()
                    ),
                    (addMatch
                        ? (
                        // String.format("std::cout << \"Found match at \" << %s << std::endl;\n", CURR_INDEX) +
                        String.format("%s = %s;\n", LAST_SUCCESSFUL_MATCH_AT, CURR_INDEX)
                    )
                        : "")
                        + restartSearchBody(
                        defaultNode,
                        flags
                    )
                )
            );

        }
    }

    private static void caseOfEdge(
        DFANode defaultNode,
        List<SwitchCase> switchCases,
        Entry<Character, DFANode> edge,
        Collection<RegexFlag> flags
    ) {
        if (edge.getValue() != null) {

            switchCases.add(
                new SwitchCase(
                    '\'' + edge.getKey().toString() + '\'',
                    // String.format("std::cout << \"Transitioning to state %d\n\";", edge.getValue().getId()) +
                    String.format(
                        "%s = %d;\n",
                        CURR_STATE,
                        edge.getValue().getId()
                    )
                )
            );

        } else {

            switchCases.add(
                new SwitchCase(
                    '\'' + edge.getKey().toString() + '\'',
                    restartSearchBody(
                        defaultNode,
                        flags
                    )
                )
            );

        }
    }

    private static String returnToDefaultNode(
        DFANode curr,
        DFANode defaultNode,
        Collection<RegexFlag> flags
    ) {

        if (flags.contains(RegexFlag.START_OF_STRING)) {

            // If the string has to match from the start and there is match in the DFA,
            // we move to an impossible state.
            return String.format(
                "std::cout << \"Transitioning to state %d\\n\";%s = %s;",
                IMPOSSIBLE_STATE_ID,
                CURR_STATE,
                IMPOSSIBLE_STATE_ID
            );

        } else {

            if (curr.getDefaultTransition() != null) {

                return String.format(
                    "std::cout << \"Transitioning to state %d\\n\";%s = %d;\n",
                    curr.getDefaultTransition().getId(),
                    CURR_STATE,
                    curr.getDefaultTransition().getId()
                );

            } else {

                return restartSearchBody(
                    defaultNode,
                    flags
                );

            }

        }

    }

}

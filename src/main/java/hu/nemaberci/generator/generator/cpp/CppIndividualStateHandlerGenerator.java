package hu.nemaberci.generator.generator.cpp;

import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.CURR_INDEX;
import static hu.nemaberci.generator.generator.cpp.CppParserFileGenerator.addResultFunctionImplementation;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CURR_CHAR;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.IMPOSSIBLE_STATE_ID;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.LAST_SUCCESSFUL_MATCH_AT;

import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.SwitchCase;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

public class CppIndividualStateHandlerGenerator {

    private static String restartSearchBody(
        DFANode defaultNode,
        Collection<RegexFlag> flags
    ) {
        StringBuilder restartSearchBody = new StringBuilder();
        if (flags.contains(RegexFlag.START_OF_STRING)) {
            restartSearchBody.append(
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
                    String.format(
                        "%s = %s;\n",
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

    protected static String addCurrentDFANodeTransitionsForMatches(
        DFANode curr,
        DFANode defaultNode,
        Collection<RegexFlag> flags
    ) {
        StringBuilder code = new StringBuilder();
        if (curr.isAccepting()) {
            code.append(
                "return true;"
            );
            if (curr.getTransitions().isEmpty()) {
                code.append(
                    restartSearchBody(
                        defaultNode,
                        flags
                    )
                );
            } else {
                lazyCharSwitch(curr, defaultNode, code, flags);
            }
        } else {
            addNextStateNavigation(curr, defaultNode, code, flags);
        }
        return code.toString();
    }

    protected static String addCurrentDFANodeTransitionsForFindMatches(
        DFANode curr,
        DFANode defaultNode,
        Collection<RegexFlag> flags
    ) {
        StringBuilder code = new StringBuilder();
        if (curr.isAccepting()) {
            code.append(
                String.format(
                    "%s = %s;",
                    LAST_SUCCESSFUL_MATCH_AT,
                    CURR_INDEX
                )
            );
            if (curr.getTransitions().isEmpty()) {
                code.append(
                    restartSearchBody(
                        defaultNode,
                        flags
                    )
                );
            } else {
                lazyCharSwitch(curr, defaultNode, code, flags);
            }
        } else {
            addNextStateNavigation(curr, defaultNode, code, flags);
        }
        return code.toString();
    }

    private static void lazyCharSwitch(
        DFANode curr,
        DFANode defaultNode,
        StringBuilder stringBuilder,
        Collection<RegexFlag> flags
    ) {
        List<SwitchCase> switchCases = new ArrayList<>();

        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(defaultNode, switchCases, edge, flags)
        );

        addDefaultLazyCase(curr, defaultNode, switchCases, flags);

        stringBuilder.append(
            CppFileGeneratorUtils.switchStatement(
                CURR_CHAR,
                switchCases
            )
        );
    }

    private static void addDefaultLazyCase(
        DFANode curr,
        DFANode defaultNode,
        List<SwitchCase> switchCases,
        Collection<RegexFlag> flags
    ) {
        switchCases.add(
            new SwitchCase(
                "default",
                returnToDefaultNode(curr, defaultNode, flags)
            )
        );
    }

    private static void addNextStateNavigation(
        DFANode curr,
        DFANode defaultNode,
        StringBuilder stringBuilder,
        Collection<RegexFlag> flags
    ) {

        List<SwitchCase> switchCases = new ArrayList<>();

        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(defaultNode, switchCases, edge, flags)
        );

        switchCases.add(
            new SwitchCase(
                "default",
                returnToDefaultNode(curr, defaultNode, flags)
            )
        );
        returnToDefaultNode(curr, defaultNode, flags);
        stringBuilder.append(
            CppFileGeneratorUtils.switchStatement(
                CURR_CHAR,
                switchCases
            )
        );

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
                "%s = %s;",
                CURR_STATE,
                IMPOSSIBLE_STATE_ID
            );

        } else {

            if (curr.getDefaultTransition() != null) {

                return String.format(
                        "%s = %d;\n",
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

package hu.nemaberci.generator.generator.python;

import static hu.nemaberci.generator.generator.python.PythonCodeGeneratorOrchestrator.*;
import static hu.nemaberci.generator.generator.python.PythonFileGeneratorUtils.*;

import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

public class PythonIndividualStateHandlerGenerator {

    private static String restartSearchBody(
        int depth,
        DFANode defaultNode,
        Collection<RegexFlag> flags
    ) {
        StringBuilder restartSearchBody = new StringBuilder();
        if (flags.contains(RegexFlag.START_OF_STRING)) {
            restartSearchBody.append(
                    statement(
                        depth,
                        String.format(
                            "self.parent.%s = -1",
                            CURR_STATE
                        )
                    ) + statement(
                        depth,
                        String.format(
                            "self.parent.%s = None",
                            CURR_STATE_HANDLER
                        )
                    )
                )
                .append(
                    addResultFunctionImplementation(depth)
                );
        } else {
            restartSearchBody.append(
                statement(
                    depth,
                    String.format(
                        "self.parent.%s = %s",
                        CURR_STATE,
                        defaultNode.getId()
                    )
                ) + statement(
                    depth,
                    String.format(
                        "self.parent.%s = self.parent.state_%d",
                        CURR_STATE_HANDLER,
                        defaultNode.getId()
                    )
                )
            );
            if (! flags.contains(RegexFlag.END_OF_STRING)) {
                restartSearchBody.append(
                    addResultFunctionImplementation(depth)
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
                "return True"
            );
            if (curr.getTransitions().isEmpty()) {
                code.append(
                    restartSearchBody(
                        3,
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
                statement(
                    3,
                    String.format(
                        "self.parent.%s = self.parent.%s;",
                        LAST_SUCCESSFUL_MATCH_AT,
                        CURR_INDEX
                    )
                )
            );
            if (curr.getTransitions().isEmpty()) {
                code.append(
                    restartSearchBody(
                        3,
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
            switchStatement(
                3,
                "c",
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
        stringBuilder.append(
            switchStatement(
                3,
                "c",
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
                    statement(
                        4,
                        String.format(
                            "self.parent.%s = %d",
                            CURR_STATE,
                            edge.getValue().getId()
                        )
                    ) + statement(
                        4,
                        String.format(
                            "self.parent.%s = self.parent.state_%d",
                            CURR_STATE_HANDLER,
                            edge.getValue().getId()
                        )
                    )
                )
            );

        } else {

            switchCases.add(
                new SwitchCase(
                    '\'' + edge.getKey().toString() + '\'',
                    restartSearchBody(
                        3,
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
            return statement(
                4,
                String.format(
                    "self.parent.%s = %s;",
                    CURR_STATE,
                    - 1
                )
            );

        } else {

            if (curr.getDefaultTransition() != null) {

                return statement(
                    4,
                    String.format(
                        "self.parent.%s = %d",
                        CURR_STATE,
                        curr.getDefaultTransition().getId()
                    )
                ) + statement(
                    4,
                    String.format(
                        "self.parent.%s = self.parent.state_%d",
                        CURR_STATE_HANDLER,
                        curr.getDefaultTransition().getId()
                    )
                );

            } else {

                return restartSearchBody(
                    4,
                    defaultNode,
                    flags
                );

            }

        }

    }

}

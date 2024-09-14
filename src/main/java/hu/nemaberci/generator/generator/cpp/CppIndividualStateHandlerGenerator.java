package hu.nemaberci.generator.generator.cpp;

import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.CURR_INDEX;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.FOUND;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.MATCH_STARTED_AT;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CURR_CHAR;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CURR_STATE_HANDLER;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.IMPOSSIBLE_STATE_ID;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.LAST_SUCCESSFUL_MATCH_AT;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.STATES_PER_FILE_LOG_2;

import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.FunctionParameter;
import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.SwitchCase;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.io.IOException;
import java.io.Writer;
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
                        "*%s = -1;\n",
                        CURR_STATE
                    )
                )
                .append(
                    String.format(
                        "if (*%s > 0) {\n",
                        LAST_SUCCESSFUL_MATCH_AT
                    )
                )
                .append(
                    String.format(
                        "addResult("
                            + "%s,"
                            + "%s,"
                            + "%s,"
                            + "%s"
                            + ");\n",
                        LAST_SUCCESSFUL_MATCH_AT,
                        MATCH_STARTED_AT,
                        CURR_INDEX,
                        FOUND
                    )
                )
                .append("}\n");
        } else {
            restartSearchBody.append(
                    String.format(
                        "*%s = %s;\n",
                        CURR_STATE,
                        defaultNode.getId()
                    )
                )
                .append(
                    String.format(
                        "*%s = %s;\n",
                        CURR_STATE_HANDLER,
                        defaultNode.getId() >> STATES_PER_FILE_LOG_2
                    )
                );
            if (! flags.contains(RegexFlag.END_OF_STRING)) {
                restartSearchBody.append(
                    String.format(
                        "addResult("
                            + "%s,"
                            + "%s,"
                            + "%s,"
                            + "%s"
                            + ");\n",
                        LAST_SUCCESSFUL_MATCH_AT,
                        MATCH_STARTED_AT,
                        CURR_INDEX,
                        FOUND
                    )
                );
            }
        }
        return restartSearchBody.toString();
    }

    private static String addCurrentDFANodeTransitionsForFindMatches(
        DFANode curr,
        DFANode defaultNode,
        Collection<RegexFlag> flags,
        String parentClassName
    ) {
        StringBuilder code = new StringBuilder();
        if (curr.isAccepting()) {
            code.append(
                String.format(
                    "*%s = *%s;",
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
                lazyCharSwitch(curr, defaultNode, code, flags, parentClassName);
            }
        } else {
            addNextStateNavigation(curr, defaultNode, code, flags, parentClassName);
        }
        return code.toString();
    }

    private static void lazyCharSwitch(
        DFANode curr,
        DFANode defaultNode,
        StringBuilder stringBuilder,
        Collection<RegexFlag> flags,
        String parentClassName
    ) {
        List<SwitchCase> switchCases = new ArrayList<>();

        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(defaultNode, switchCases, edge, flags)
        );

        addDefaultLazyCase(curr, defaultNode, switchCases, flags, parentClassName);

        stringBuilder.append(
            CppFileGeneratorUtils.switchStatement(
                '*' + CURR_CHAR,
                switchCases
            )
        );
    }

    private static void addDefaultLazyCase(
        DFANode curr,
        DFANode defaultNode,
        List<SwitchCase> switchCases,
        Collection<RegexFlag> flags,
        String parentClassName
    ) {
        switchCases.add(
            new SwitchCase(
                "default",
                returnToDefaultNode(curr, defaultNode, flags, parentClassName)
            )
        );
    }

    private static void addNextStateNavigation(
        DFANode curr,
        DFANode defaultNode,
        StringBuilder stringBuilder,
        Collection<RegexFlag> flags,
        String parentClassName
    ) {

        List<SwitchCase> switchCases = new ArrayList<>();

        curr.getTransitions().entrySet().stream().sorted(Entry.comparingByKey()).forEach(
            edge -> caseOfEdge(defaultNode, switchCases, edge, flags)
        );

        switchCases.add(
            new SwitchCase(
                "default",
                returnToDefaultNode(curr, defaultNode, flags, parentClassName)
            )
        );
        returnToDefaultNode(curr, defaultNode, flags, parentClassName);
        stringBuilder.append(
            CppFileGeneratorUtils.switchStatement(
                '*' + CURR_CHAR,
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
                        "*%s = %d;\n*%s = %d;\n",
                        CURR_STATE,
                        edge.getValue().getId() % (1 << STATES_PER_FILE_LOG_2),
                        CURR_STATE_HANDLER,
                        edge.getValue().getId() >> STATES_PER_FILE_LOG_2
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
        Collection<RegexFlag> flags,
        String parentClassName
    ) {

        if (flags.contains(RegexFlag.START_OF_STRING)) {

            // If the string has to match from the start and there is match in the DFA,
            // we move to an impossible state.
            return String.format(
                "*%s = %s;",
                CURR_STATE,
                IMPOSSIBLE_STATE_ID
            );

        } else {

            if (curr.getDefaultTransition() != null) {

                return String.format(
                        "*%s = %d;\n*%s = %d;\n",
                        CURR_STATE,
                    curr.getDefaultTransition().getId() % (1 << STATES_PER_FILE_LOG_2),
                        CURR_STATE_HANDLER,
                    curr.getDefaultTransition().getId() >> STATES_PER_FILE_LOG_2
                    );

            } else {

                return restartSearchBody(
                    defaultNode,
                    flags
                );

            }

        }

    }

    private static String handleStates(
        DFANode node,
        DFANode defaultNode,
        Collection<RegexFlag> flags,
        String parentClassName
    ) {

        return addCurrentDFANodeTransitionsForFindMatches(
            node,
            defaultNode,
            flags,
            parentClassName
        );

    }

    public static void createIndividualStateHandler(
        DFANode node,
        DFANode defaultNode,
        Collection<RegexFlag> flags,
        String className,
        String parentClassName,
        Writer targetLocation
    ) {

        String classImpl = CppFileGeneratorUtils.withClass(
            className,
            List.of("deque", "utility"),
            CppFileGeneratorUtils.functionBody(
                "run",
                "void",
                List.of(
                    new FunctionParameter(
                        "char*",
                        "currentCharacter"
                    ),
                    new FunctionParameter(
                        "int*",
                        "currentState"
                    ),
                    new FunctionParameter(
                        "int*",
                        "currentStateHandler"
                    ),
                    new FunctionParameter(
                        "int*",
                        "lastSuccessfulMatchAt"
                    ),
                    new FunctionParameter(
                        "int*",
                        "currentIndex"
                    ),
                    new FunctionParameter(
                        "int*",
                        "currentMatchStartedAt"
                    ),
                    new FunctionParameter(
                        "std::deque<std::pair<int, int>>*",
                        "found"
                    ),
                    new FunctionParameter(
                        "void (*addResult)(int*, int*, int*, std::deque<std::pair<int, int>>*)",
                        ""
                    )
                ),
                handleStates(node, defaultNode, flags, parentClassName)
            )
        );

        try {
            targetLocation.write(classImpl);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}

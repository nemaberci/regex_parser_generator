package hu.nemaberci.generator.generator.python;

import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.*;
import static hu.nemaberci.generator.generator.python.PythonFileGeneratorUtils.*;
import static hu.nemaberci.generator.generator.python.PythonIndividualStateHandlerGenerator.addCurrentDFANodeTransitionsForFindMatches;

import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.*;
import hu.nemaberci.generator.generator.python.PythonFileGeneratorUtils.SwitchCase;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class PythonParserFileGenerator {

    private static void initStartingVariables(
        DFANode startingNode,
        String className,
        StringBuilder stringBuilder
    ) {
        stringBuilder
            .append(variable(2, "self." + CHARS, String.format("%s", FUNCTION_INPUT_VARIABLE_NAME)))
            .append(variable(2, "self." + CURR_CHAR, "0"))
            .append(variable(2, "self." + CURR_INDEX, "0"))
            .append(variable(2, "self." + MATCH_STARTED_AT, "0"))
            .append(variable(2, "self." + LAST_SUCCESSFUL_MATCH_AT, "0"))
            .append(variable(2, "self." + CURR_STATE, String.valueOf(startingNode.getId())))
            .append(variable(
                2,
                "self." + INPUT_STRING_LENGTH,
                String.format("len(%s)", FUNCTION_INPUT_VARIABLE_NAME)
            ))
            .append(variable(2, "self." + FOUND, "[]"))
            .append(variable(
                2,
                "self." + CURR_STATE_HANDLER,
                String.format("self.state_%s", startingNode.getId())
            ));
    }

    private static String matchesFunctionImplementation(
        DFANode startingNode,
        List<DFANode> dfaNodes,
        DFANode defaultNode,
        Collection<RegexFlag> flags,
        String className
    ) {
        var stringBuilder = new StringBuilder();
        initStartingVariables(startingNode, className, stringBuilder);
        handleEmptyInputWithBooleanOutput(stringBuilder);
        addMainWhileLoopForMatches(
            startingNode,
            dfaNodes,
            defaultNode,
            stringBuilder,
            className,
            flags
        );
        checkIfStateIsAcceptingAndReturnBoolean(stringBuilder, dfaNodes, flags, className);
        return stringBuilder.toString();
    }

    private static void handleEmptyInputWithBooleanOutput(StringBuilder stringBuilder) {
        stringBuilder
            .append("\t".repeat(2))
            .append("if ")
            .append(INPUT_STRING_LENGTH)
            .append(" == 0: \n")
            .append(statement(3, "return False"));
    }

    private static void checkIfStateIsAcceptingAndReturnBoolean(
        StringBuilder stringBuilder,
        List<DFANode> allNodes,
        Collection<RegexFlag> flags,
        String className
    ) {

        List<SwitchCase> switchCases = new ArrayList<>();

        for (int i = 0; i < allNodes.size(); i++) {
            var node = allNodes.get(i);
            if (node.isAccepting()) {
                switchCases.add(
                    new SwitchCase(
                        String.valueOf(i),
                        String.format(
                            "%s = %s - 1;\n",
                            LAST_SUCCESSFUL_MATCH_AT,
                            INPUT_STRING_LENGTH
                        )
                    )
                );
            }
        }

        // stringBuilder.append(
        //     switchStatement(
        //         1,
        //         CURR_STATE,
        //         switchCases
        //     )
        // );

        if (flags.contains(RegexFlag.END_OF_STRING)) {
            stringBuilder.append(
                String.format(
                    "if (%s == %s - 1 && %s != %s) {\n",
                    LAST_SUCCESSFUL_MATCH_AT,
                    INPUT_STRING_LENGTH,
                    CURR_STATE,
                    IMPOSSIBLE_STATE_ID
                )
            );
        } else {
            stringBuilder.append(
                String.format(
                    "if (%s > %s) {\n",
                    LAST_SUCCESSFUL_MATCH_AT,
                    0
                )
            );
        }

        stringBuilder
            .append("return true;\n")
            .append("}\n")
            .append("return false;\n");
    }

    private static void addMainWhileLoopForMatches(
        DFANode startingNode,
        Collection<DFANode> dfaNodes,
        DFANode defaultNode,
        StringBuilder stringBuilder,
        String className,
        Collection<RegexFlag> flags
    ) {

        stringBuilder
            .append(
                statement(
                    2,
                    String.format(
                        "while %s < %s:\n",
                        CURR_INDEX,
                        INPUT_STRING_LENGTH
                    )
                )
            )
            .append(
                statement(
                    3,
                    String.format(
                        "if %s == %s:\n",
                        CURR_STATE,
                        IMPOSSIBLE_STATE_ID
                    )
                )
            )
            .append(
                statement(
                    4,
                    "return False"
                )
            )
            .append(
                statement(
                    3,
                    String.format(
                        "%s = %s[%s];\n",
                        CURR_CHAR,
                        CHARS,
                        CURR_INDEX
                    )
                )
            );

        if (flags.contains(RegexFlag.END_OF_STRING)) {
            stringBuilder
                .append(
                    String.format(
                        "if (%s == %s - 1 && %s != %s) {\n",
                        LAST_SUCCESSFUL_MATCH_AT,
                        INPUT_STRING_LENGTH,
                        CURR_STATE,
                        IMPOSSIBLE_STATE_ID
                    )
                );
        } else {
            stringBuilder
                .append(
                    String.format(
                        "if (%s > %s) {\n",
                        LAST_SUCCESSFUL_MATCH_AT,
                        0
                    )
                );
        }

        stringBuilder
            .append("return true;\n")
            .append("}\n")
            .append(
                String.format(
                    "if (%s > %s) {\n",
                    CURR_STATE,
                    startingNode.getId()
                )
            )
            .append(
                String.format(
                    "%s = %s + 1;\n",
                    MATCH_STARTED_AT,
                    CURR_INDEX
                )
            )
            .append("}\n")
            .append(
                String.format(
                    "%s++;\n",
                    CURR_INDEX
                )
            )
            .append("}\n");
    }

    private static String findMatchesFunctionImplementation(
        DFANode startingNode,
        List<DFANode> dfaNodes,
        Collection<RegexFlag> flags,
        String className
    ) {
        var stringBuilder = new StringBuilder();
        initStartingVariables(startingNode, className, stringBuilder);
        handleEmptyInputWithParseResultOutput(stringBuilder);
        addMainWhileLoopForFindMatches(stringBuilder, dfaNodes, startingNode, className, flags);
        checkIfStateIsAcceptingAndReturnParseResult(stringBuilder, dfaNodes, flags, className);
        return stringBuilder.toString();
    }

    private static void checkIfStateIsAcceptingAndReturnParseResult(
        StringBuilder stringBuilder,
        List<DFANode> allNodes,
        Collection<RegexFlag> flags,
        String className
    ) {

        List<SwitchCase> switchCases = new ArrayList<>();

        for (int i = 0; i < allNodes.size(); i++) {
            if (allNodes.get(i).isAccepting()) {
                switchCases.add(
                    new SwitchCase(
                        String.valueOf(i),
                        statement(
                            3,
                            String.format(
                                "%s = %s - 1",
                                "self." + LAST_SUCCESSFUL_MATCH_AT,
                                "self." + INPUT_STRING_LENGTH
                            )
                        )
                    )
                );
            }
        }

        stringBuilder.append(
            switchStatement(
                2,
                "self." + CURR_STATE,
                switchCases
            )
        );

        if (flags.contains(RegexFlag.END_OF_STRING)) {
            stringBuilder.append(
                statement(
                    2,
                    String.format(
                        "if (%s == %s - 1):",
                        "self." + LAST_SUCCESSFUL_MATCH_AT,
                        "self." + INPUT_STRING_LENGTH
                    )
                )
            );
        } else {
            stringBuilder.append(
                statement(
                    2,
                    String.format(
                        "if (%s > %s):",
                        "self." + LAST_SUCCESSFUL_MATCH_AT,
                        0
                    )
                )
            );
        }
        stringBuilder
            .append(
                statement(
                    3,
                    String.format(
                        "%s.append((%s, %s));",
                        "self." + FOUND,
                        "self." + MATCH_STARTED_AT,
                        "self." + LAST_SUCCESSFUL_MATCH_AT
                    )
                )
            )
            .append(
                statement(
                    2,
                    String.format(
                        "return %s",
                        "self." + FOUND
                    )
                )
            );
    }

    private static void handleEmptyInputWithParseResultOutput(StringBuilder stringBuilder) {
        stringBuilder
            .append("\t".repeat(2))
            .append("if (")
            .append("self." + INPUT_STRING_LENGTH)
            .append(" == 0):\n")
            .append(
                statement(
                    3,
                    "return []"
                )
            );
    }

    private static void addMainWhileLoopForFindMatches(
        StringBuilder stringBuilder,
        Collection<DFANode> dfaNodes,
        DFANode defaultNode,
        String className,
        Collection<RegexFlag> flags
    ) {
        stringBuilder
            .append(
                statement(
                    2,
                    String.format(
                        "while (%s < %s) :",
                        "self." + CURR_INDEX,
                        "self." + INPUT_STRING_LENGTH
                    )
                )
            )
            .append(
                statement(
                    3,
                    String.format(
                        "%s = %s[%s]",
                        "self." + CURR_CHAR,
                        "self." + CHARS,
                        "self." + CURR_INDEX
                    )
                )
            )
            .append(
                statement(
                    3,
                    String.format(
                        "%s.handle(%s)",
                        "self." + CURR_STATE_HANDLER,
                        "self." + CURR_CHAR
                    )
                )
            )
            .append(
            statement(
                3,
                String.format(
                    "%s = %s + 1",
                    "self." + CURR_INDEX,
                    "self." + CURR_INDEX
                )
            )
        );
    }

    public static String addResultFunctionImplementation() {
        var stringBuilder = new StringBuilder();
        stringBuilder
            .append(
                String.format(
                    "if (%s > %s) {\n",
                    LAST_SUCCESSFUL_MATCH_AT,
                    MATCH_STARTED_AT
                )
            )
            .append(
                String.format(
                    "%s.push_back(%s(%s, %s));\n",
                    FOUND,
                    "std::pair<int, int>",
                    MATCH_STARTED_AT,
                    LAST_SUCCESSFUL_MATCH_AT
                )
            )
            .append("}\n")
            .append(
                String.format(
                    "%s = %s > %s ? %s - 1 : %s;\n",
                    CURR_INDEX,
                    LAST_SUCCESSFUL_MATCH_AT,
                    MATCH_STARTED_AT,
                    LAST_SUCCESSFUL_MATCH_AT,
                    MATCH_STARTED_AT
                )
            )
            .append(
                String.format(
                    "%s = %s + 1;\n",
                    MATCH_STARTED_AT,
                    CURR_INDEX
                )
            );
        return stringBuilder.toString();
    }

    public static void createMainParserFile(
        List<DFANode> dfaNodes,
        DFANode startingNode,
        Collection<RegexFlag> flags,
        String className,
        String regex,
        Writer targetLocation
    ) {

        final StringBuilder classBody = new StringBuilder();

        classBody
            // .append(
            //     functionBody(
            //         1,
            //         "matches",
            //         List.of(
            //             FUNCTION_INPUT_VARIABLE_NAME
            //         ),
            //         matchesFunctionImplementation(
            //             startingNode,
            //             dfaNodes,
            //             startingNode,
            //             flags,
            //             className
            //         )
            //     )
            // )
            .append(
                functionBody(
                    1,
                    "findMatches",
                    List.of(
                        FUNCTION_INPUT_VARIABLE_NAME
                    ),
                    findMatchesFunctionImplementation(
                        startingNode,
                        dfaNodes,
                        flags,
                        className
                    )
                )
            );

        final StringBuilder innerClasses = new StringBuilder();

        for (var node : dfaNodes) {
            innerClasses
                .append("\t".repeat(1))
                .append(
                    withChildClass(
                        String.format("State_%s", node.getId()),
                        addCurrentDFANodeTransitionsForFindMatches(
                            node,
                            startingNode,
                            flags
                        )
                    )
                );
        }

        innerClasses.append(statement(1, "def __init__(self):"));
        for (var node : dfaNodes) {
            innerClasses.append(statement(
                2,
                String.format("self.state_%s = self.State_%s(self)", node.getId(), node.getId())
            ));
        }

        final var classImpl = withClass(
            className,
            innerClasses.append("\n")
                .append(classBody)
                .toString()
        );

        try {
            targetLocation.write(classImpl);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}

package hu.nemaberci.generator.generator.cpp;

import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.CHARS;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.CURR_CHAR;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.CURR_INDEX;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.FOUND;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.FUNCTION_INPUT_VARIABLE_NAME;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.IMPOSSIBLE_STATE_ID;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.INPUT_STRING_LENGTH;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.LAST_SUCCESSFUL_MATCH_AT;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.MATCH_STARTED_AT;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.SWITCH_VAR;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.functionBody;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.switchStatement;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.variable;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.withClass;
import static hu.nemaberci.generator.generator.cpp.CppIndividualStateHandlerGenerator.addCurrentDFANodeTransitionsForFindMatches;
import static hu.nemaberci.generator.generator.cpp.CppIndividualStateHandlerGenerator.restartSearchBody;
import static hu.nemaberci.generator.generator.cpp.CppIndividualStateHandlerGenerator.switchCasesForOneVariableSwitchForMatches;

import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.FunctionParameter;
import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.SwitchCase;
import hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class CppParserFileGenerator {

    private static void initStartingVariables(DFANode startingNode, String className, StringBuilder stringBuilder) {
        stringBuilder
            .append(variable("const char *", CHARS, String.format("%s.c_str()", FUNCTION_INPUT_VARIABLE_NAME)))
            .append(variable("char", CURR_CHAR, "0"))
            .append(variable("int", SWITCH_VAR, "0"))
            .append(variable("int", CURR_INDEX, "0"))
            .append(variable("int", MATCH_STARTED_AT, "0"))
            .append(variable("int", LAST_SUCCESSFUL_MATCH_AT, "0"))
            .append(variable("int", CURR_STATE, String.valueOf(startingNode.getId())))
            .append(variable("const int", INPUT_STRING_LENGTH, String.format("%s.length()", FUNCTION_INPUT_VARIABLE_NAME)))
            .append(variable("std::deque<std::pair<int, int>>", FOUND, "std::deque<std::pair<int, int>>()"));
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
        addMainWhileLoopForMatches(startingNode, dfaNodes, defaultNode, stringBuilder, className, flags);
        checkIfStateIsAcceptingAndReturnBoolean(stringBuilder, dfaNodes, flags, className);
        return stringBuilder.toString();
    }

    private static void handleEmptyInputWithBooleanOutput(StringBuilder stringBuilder) {
        stringBuilder
            .append("if (")
            .append(INPUT_STRING_LENGTH)
            .append(" == 0) {\n")
            .append("return false;\n")
            .append("}\n");
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

        stringBuilder.append(
            switchStatement(
                CURR_STATE,
                switchCases
            )
        );

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
                String.format(
                    "while (%s < %s) {\n",
                    CURR_INDEX,
                    INPUT_STRING_LENGTH
                )
            )
            .append(
                String.format(
                    "if (%s == %s) {\n",
                    CURR_STATE,
                    IMPOSSIBLE_STATE_ID
                )
            )
            .append("return false;\n")
            .append("}\n")
            .append(
                String.format(
                    "%s = %s[%s];\n",
                    CURR_CHAR,
                    CHARS,
                    CURR_INDEX
                )
            )
            .append(
                String.format(
                    "%s = (%s << 8) ^ ((int) %s);\n",
                    SWITCH_VAR,
                    CURR_STATE,
                    CURR_CHAR
                )
            );

        List<SwitchCase> switchCases = new ArrayList<>();

        Iterator<DFANode> iterator = dfaNodes.iterator();
        int i = 0;

        while (iterator.hasNext()) {
            final var node = iterator.next();

            // switchCases.add(
            //     new SwitchCase(
            //         String.valueOf(i),
            //         addCurrentDFANodeTransitionsForMatches(
            //             node,
            //             defaultNode,
            //             flags
            //         )
            //     )
            // );
            switchCases.addAll(switchCasesForOneVariableSwitchForMatches(
                i,
                node,
                defaultNode,
                flags
            ));

            i++;
        }

        if (flags.contains(RegexFlag.START_OF_STRING)) {
            switchCases.add(
                new SwitchCase(
                    "default",
                    String.format(
                        "%s = %s;",
                        CURR_STATE,
                        IMPOSSIBLE_STATE_ID
                    )
                )
            );
        } else {
            switchCases.add(
                new SwitchCase(
                    "default",
                    restartSearchBody(
                        defaultNode,
                        flags
                    )
                )
            );
        }

        stringBuilder.append(
            switchStatement(
                SWITCH_VAR,
                switchCases
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
                        String.format(
                            "%s = %s - 1;\n",
                            LAST_SUCCESSFUL_MATCH_AT,
                            INPUT_STRING_LENGTH
                        )
                    )
                );
            }
        }

        stringBuilder.append(
            switchStatement(
                CURR_STATE,
                switchCases
            )
        );

        if (flags.contains(RegexFlag.END_OF_STRING)) {
            stringBuilder.append(
                String.format(
                    "if (%s == %s - 1) {\n",
                    LAST_SUCCESSFUL_MATCH_AT,
                    INPUT_STRING_LENGTH
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
            .append(
                String.format(
                    "%s.push_back(std::pair<int, int>(%s, %s));\n",
                    FOUND,
                    MATCH_STARTED_AT,
                    LAST_SUCCESSFUL_MATCH_AT
                )
            )
            .append("}\n")
            .append(
                String.format(
                    "return %s;",
                    FOUND
                )
            );
    }

    private static void handleEmptyInputWithParseResultOutput(StringBuilder stringBuilder) {
        stringBuilder
            .append("if (")
            .append(INPUT_STRING_LENGTH)
            .append(" == 0) {\n")
            .append("return std::deque<std::pair<int, int>>();\n")
            .append("}\n");
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
                String.format(
                    "while (%s < %s) {\n",
                    CURR_INDEX,
                    INPUT_STRING_LENGTH
                )
            )
            .append(
                String.format(
                    "if (%s < %s) {\n",
                    CURR_STATE,
                    0
                )
            )
            .append(
                String.format(
                    "return std::deque<std::pair<int, int>>();\n"
                )
            )
            .append("}\n")
            .append(
                String.format(
                    "%s = %s[%s];\n",
                    CURR_CHAR,
                    CHARS,
                    CURR_INDEX
                )
            )
            .append(
                String.format(
                    "%s = (%s << 8) ^ ((int) %s);\n",
                    SWITCH_VAR,
                    CURR_STATE,
                    CURR_CHAR
                )
            );

        List<SwitchCase> switchCases = new ArrayList<>();

        Iterator<DFANode> iterator = dfaNodes.iterator();
        int i = 0;

        while (iterator.hasNext()) {
            final var node = iterator.next();

            // switchCases.add(
            //     new SwitchCase(
            //         String.valueOf(i),
            //         addCurrentDFANodeTransitionsForFindMatches(
            //             node,
            //             defaultNode,
            //             flags
            //         )
            //     )
            // );
            switchCases.addAll(addCurrentDFANodeTransitionsForFindMatches(
                i,
                node,
                defaultNode,
                flags
            ));

            i++;
        }

        if (flags.contains(RegexFlag.START_OF_STRING)) {
            switchCases.add(
                new SwitchCase(
                    "default",
                    String.format(
                        "%s = %s;",
                        CURR_STATE,
                        IMPOSSIBLE_STATE_ID
                    )
                )
            );
        } else {
            switchCases.add(
                new SwitchCase(
                    "default",
                    restartSearchBody(
                        defaultNode,
                        flags
                    )
                )
            );
        }

        stringBuilder.append(
            switchStatement(
                SWITCH_VAR,
                switchCases
            )
        );

        stringBuilder.append(
            String.format(
                "%s++;\n",
                CURR_INDEX
            )
        );
        stringBuilder.append("}\n");
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
        final List<String> includes = new ArrayList<>(List.of("string", "deque", "utility"));

        classBody
            .append(
                functionBody(
                    "matches",
                    "bool",
                    List.of(
                        new FunctionParameter(
                            "std::string",
                            FUNCTION_INPUT_VARIABLE_NAME
                        )
                    ),
                    matchesFunctionImplementation(startingNode, dfaNodes, startingNode, flags, className)
                )
            )
            .append(
                functionBody(
                    "findMatches",
                    "std::deque<std::pair<int, int>>",
                    List.of(
                        new FunctionParameter(
                            "std::string",
                            FUNCTION_INPUT_VARIABLE_NAME
                        )
                    ),
                    findMatchesFunctionImplementation(
                        startingNode,
                        dfaNodes,
                        flags,
                        className
                    )
                )
            );
        final var classImpl = withClass(
            className,
            includes,
            classBody.toString()
        );

        try {
            targetLocation.write(classImpl);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}

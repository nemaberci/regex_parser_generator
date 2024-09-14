package hu.nemaberci.generator.generator.cpp;

import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.staticVariable;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.switchStatement;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.variable;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.withClass;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.functionBody;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CHARS;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CURR_CHAR;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CURR_INDEX;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CURR_STATE;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.CURR_STATE_HANDLER;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.FOUND;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.FUNCTION_INPUT_VARIABLE_NAME;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.IMPOSSIBLE_STATE_ID;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.INPUT_STRING_LENGTH;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.LAST_SUCCESSFUL_MATCH_AT;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.MATCH_STARTED_AT;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.STATES_PER_FILE_LOG_2;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.stateHandlerPartName;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.FunctionParameter;
import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.SwitchCase;
import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.dfa.data.DFANode;
import hu.nemaberci.regex.data.ParseResult;
import hu.nemaberci.regex.data.ParseResultMatch;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CppParserFileGenerator {

    private static void initStartingVariables(DFANode startingNode, String className, StringBuilder stringBuilder) {
        stringBuilder
            .append(variable("const char*", CHARS, String.format("%s.c_str()", FUNCTION_INPUT_VARIABLE_NAME)))
            .append(variable("char", CURR_CHAR, "0"))
            .append(variable("int", CURR_INDEX, "0"))
            .append(variable("int", MATCH_STARTED_AT, "0"))
            .append(variable("int", LAST_SUCCESSFUL_MATCH_AT, "0"))
            .append(variable("int", CURR_STATE, String.valueOf(startingNode.getId() % (1 << STATES_PER_FILE_LOG_2))))
            .append(variable("int", CURR_STATE_HANDLER, String.valueOf(startingNode.getId() / (1 << STATES_PER_FILE_LOG_2))))
            .append(variable("int", INPUT_STRING_LENGTH, String.format("%s.length()", FUNCTION_INPUT_VARIABLE_NAME)))
            .append(variable("std::deque<std::pair<int, int>>", FOUND, "std::deque<std::pair<int, int>>()"));
    }

    private static String matchesFunctionImplementation(
        DFANode startingNode,
        List<DFANode> dfaNodes,
        Collection<RegexFlag> flags,
        String className
    ) {
        var stringBuilder = new StringBuilder();
        initStartingVariables(startingNode, className, stringBuilder);
        handleEmptyInputWithBooleanOutput(stringBuilder);
        addMainWhileLoopForMatches(startingNode, dfaNodes, stringBuilder, className, flags);
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

        stringBuilder.append(
            String.format(
                "switch (%s) {\n",
                CURR_STATE_HANDLER
            )
        );
        boolean[] included = new boolean[(1 << STATES_PER_FILE_LOG_2)];

        for (int i = 0; i < allNodes.size(); i++) {
            var handler = i / (1 << STATES_PER_FILE_LOG_2);
            var indexInHandler = i % (1 << STATES_PER_FILE_LOG_2);
            var node = allNodes.get(i);
            if (node.isAccepting() && !flags.contains(RegexFlag.END_OF_STRING)) {
                if (! included[handler]) {
                    stringBuilder
                        .append(
                            String.format(
                                "case %d: {\n",
                                handler
                            )
                        )
                        .append(
                            String.format(
                                "switch (%s) {\n",
                                CURR_STATE
                            )
                        );
                }
                stringBuilder
                    .append(
                        String.format(
                            "case %d: {\n",
                            indexInHandler
                        )
                    )
                    .append(
                        String.format(
                            "%s = %s - 1;\n",
                            LAST_SUCCESSFUL_MATCH_AT,
                            INPUT_STRING_LENGTH
                        )
                    )
                    .append("}\n");
                included[handler] = true;
            }
            if ((i == allNodes.size() - 1 || indexInHandler == (1 << STATES_PER_FILE_LOG_2) - 1)
                && included[handler]) {
                stringBuilder
                    .append("}\n")
                    .append("}\n");
            }
        }

        stringBuilder.append("}\n");

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
            .append("return true;\n")
            .append("}\n")
            .append("}\n")
            .append("return false;\n");
    }

    private static void addMainWhileLoopForMatches(
        DFANode startingNode,
        Collection<DFANode> dfaNodes,
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
            );

        List<SwitchCase> switchCases = new ArrayList<>();

        for (int i = 0; i <= dfaNodes.size() >> STATES_PER_FILE_LOG_2; i++) {

            switchCases.add(
                new SwitchCase(
                    String.valueOf(i),
                    String.format(
                        "%s::run("
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s)"
                            + ");",
                        stateHandlerPartName(className, i),
                        CURR_CHAR,
                        CURR_STATE,
                        CURR_STATE_HANDLER,
                        LAST_SUCCESSFUL_MATCH_AT,
                        CURR_INDEX,
                        MATCH_STARTED_AT,
                        FOUND,
                        "addResult"
                    )
                )
            );

        }

        stringBuilder.append(
            switchStatement(
                CURR_STATE_HANDLER,
                switchCases
            )
        );

        stringBuilder.append("{\n");

        if (flags.contains(RegexFlag.END_OF_STRING)) {
            stringBuilder
                .append(
                    String.format(
                        "if (%s == %s - 1) {\n",
                        LAST_SUCCESSFUL_MATCH_AT,
                        INPUT_STRING_LENGTH
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
        stringBuilder.append(
            String.format(
                "%s = std::deque<std::pair<int, int>>();\n",
                FOUND
            )
        );
        handleEmptyInputWithParseResultOutput(stringBuilder);
        addMainWhileLoopForFindMatches(stringBuilder, dfaNodes, className);
        checkIfStateIsAcceptingAndReturnParseResult(stringBuilder, dfaNodes, flags, className);
        return stringBuilder.toString();
    }

    private static void checkIfStateIsAcceptingAndReturnParseResult(
        StringBuilder stringBuilder,
        List<DFANode> allNodes,
        Collection<RegexFlag> flags,
        String className
    ) {

        stringBuilder.append(
            String.format(
                "switch (%s) {\n",
                CURR_STATE
            )
        );
        boolean[] included = new boolean[(1 << STATES_PER_FILE_LOG_2)];

        for (int i = 0; i < allNodes.size(); i++) {
            var handler = i / (1 << STATES_PER_FILE_LOG_2);
            var indexInHandler = i % (1 << STATES_PER_FILE_LOG_2);
            var node = allNodes.get(i);
            if (node.isAccepting() && !flags.contains(RegexFlag.END_OF_STRING)) {
                if (! included[handler]) {
                    stringBuilder
                        .append(
                            String.format(
                                "case %d: {\n",
                                handler
                            )
                        )
                        .append(
                            String.format(
                                "switch (%s) {\n",
                                CURR_STATE
                            )
                        );
                }
                stringBuilder
                    .append(
                        String.format(
                            "case %d: {\n",
                            indexInHandler
                        )
                    )
                    .append(
                        String.format(
                            "%s = %s - 1;\n",
                            LAST_SUCCESSFUL_MATCH_AT,
                            INPUT_STRING_LENGTH
                        )
                    )
                    .append("}\n");
                included[handler] = true;
            }
            if ((i == allNodes.size() - 1 || indexInHandler == (1 << STATES_PER_FILE_LOG_2) - 1)
                && included[handler]) {
                stringBuilder
                    .append("}\n")
                    .append("}\n");
            }
        }

        stringBuilder.append("}\n");

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
        String className
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
            );

        List<SwitchCase> switchCases = new ArrayList<>();

        for (int i = 0; i <= dfaNodes.size() >> STATES_PER_FILE_LOG_2; i++) {

            switchCases.add(
                new SwitchCase(
                    String.valueOf(i),
                    String.format(
                        "%s::run("
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s),"
                            + "&(%s)"
                            + ");",
                        stateHandlerPartName(className, i),
                        CURR_CHAR,
                        CURR_STATE,
                        CURR_STATE_HANDLER,
                        LAST_SUCCESSFUL_MATCH_AT,
                        CURR_INDEX,
                        MATCH_STARTED_AT,
                        FOUND,
                        "addResult"
                    )
                )
            );

        }

        stringBuilder.append(
            switchStatement(
                CURR_STATE_HANDLER,
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

    private static String addResultFunctionImplementation(String className) {
        var stringBuilder = new StringBuilder();
        stringBuilder
            .append(
                String.format(
                    "if (*%s > *%s) {\n",
                    LAST_SUCCESSFUL_MATCH_AT,
                    MATCH_STARTED_AT
                )
            )
            .append(
                String.format(
                    "%s->push_back(%s(*%s, *%s));\n",
                    FOUND,
                    "std::pair<int, int>",
                    MATCH_STARTED_AT,
                    LAST_SUCCESSFUL_MATCH_AT
                )
            )
            .append("}\n")
            .append(
                String.format(
                    "*%s = *%s > *%s ? *%s - 1 : *%s;\n",
                    CURR_INDEX,
                    LAST_SUCCESSFUL_MATCH_AT,
                    MATCH_STARTED_AT,
                    LAST_SUCCESSFUL_MATCH_AT,
                    MATCH_STARTED_AT
                )
            )
            .append(
                String.format(
                    "*%s = *%s + 1;\n",
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

        for (int i = 0; i <= dfaNodes.size() >> STATES_PER_FILE_LOG_2; i++) {

            includes.add(
                stateHandlerPartName(className, i) + ".cpp"
            );

        }
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
                    matchesFunctionImplementation(startingNode, dfaNodes, flags, className)
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
            )
            .append(
                functionBody(
                    "addResult",
                    "void",
                    List.of(
                        new FunctionParameter(
                            "int*",
                            "lastSuccessfulMatchAt"
                        ),
                        new FunctionParameter(
                            "int*",
                            "currentMatchStartedAt"
                        ),
                        new FunctionParameter(
                            "int*",
                            "currentIndex"
                        ),
                        new FunctionParameter(
                            "std::deque<std::pair<int, int>>*",
                            "found"
                        )
                    ),
                    addResultFunctionImplementation(className)
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

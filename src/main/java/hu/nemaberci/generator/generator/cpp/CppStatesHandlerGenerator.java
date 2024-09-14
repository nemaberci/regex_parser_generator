package hu.nemaberci.generator.generator.cpp;

import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.switchStatement;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.withClass;
import static hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.functionBody;
import static hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator.individualStateHandlerName;

import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.FunctionParameter;
import hu.nemaberci.generator.generator.cpp.CppFileGeneratorUtils.SwitchCase;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class CppStatesHandlerGenerator {

    public static void createFileForStates(
        String className,
        String parentClassName,
        Writer targetLocation,
        int startingId,
        int endingId
    ) {

        final List<SwitchCase> switchCases = new ArrayList<>();
        final List<String> imports = new ArrayList<>();

        for (int i = startingId; i <= endingId; i++) {

            switchCases.add(
                new SwitchCase(
                    Integer.valueOf(i - startingId).toString(),
                    String.format(
                        "%s::run("
                            + "currentCharacter,"
                            + "currentState,"
                            + "currentStateHandler,"
                            + "lastSuccessfulMatchAt,"
                            + "currentIndex,"
                            + "currentMatchStartedAt,"
                            + "found,"
                            + "addResult"
                            + ");",
                        individualStateHandlerName(parentClassName, i)
                    )
                )
            );
            imports.add(
                individualStateHandlerName(parentClassName, i) + ".cpp"
            );

        }

        final var classImpl = withClass(
            className,
            imports,
            functionBody(
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
                switchStatement(
                    "*currentState",
                    switchCases
                )
            )
        );

        try {
            targetLocation.write(classImpl);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}

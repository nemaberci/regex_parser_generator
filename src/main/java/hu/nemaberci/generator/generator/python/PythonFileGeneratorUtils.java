package hu.nemaberci.generator.generator.python;

import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.CURR_INDEX;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.FOUND;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.LAST_SUCCESSFUL_MATCH_AT;
import static hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator.MATCH_STARTED_AT;

import java.util.List;
import java.util.Objects;

public interface PythonFileGeneratorUtils {

    static String statement(
        int depth,
        String content
    ) {
        return "\t".repeat(Math.max(0, depth)) + content + "\n";
    }

    static String withClass(
        String className,
        String content
    ) {
        return "class "
            + className
            + ":\n"
            + content;
    }

    static String withChildClass(
        String className,
        String content
    ) {
        return "class "
            + className
            + ":\n"
            + "\t\tdef __init__(self, p):\n"
            + "\t\t\tself.parent = p\n"
            + "\t\tdef handle(self, c):\n"
            + content
            + "\n";
    }

    static String functionBody(
        int depth,
        String functionName,
        List<String> parameters,
        String content
    ) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\t".repeat(Math.max(0, depth)))
            .append("def ")
            .append(functionName)
            .append("(self,");
        for (int i = 0; i < parameters.size(); i++) {
            stringBuilder.append(" ")
                .append(parameters.get(i));
            if (i < parameters.size() - 1) {
                stringBuilder.append(", ");
            }
        }
        stringBuilder.append(
            "):\n"
        );
        stringBuilder.append(content);
        return stringBuilder.toString();
    }

    static String variable(
        int depth,
        String name,
        String value
    ) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            stringBuilder.append("\t");
        }
        return
            stringBuilder
                .append(name)
                .append(" = ")
                .append(value)
                .append("\n")
                .toString();
    }

    record SwitchCase(
        String id,
        String content
    ) {}

    static String switchStatement(
        int depth,
        String varName,
        List<SwitchCase> cases
    ) {

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < cases.size(); i++) {
            if (Objects.equals(cases.get(i).id, "default")) {
                continue;
            }
            if (i == 0) {
                stringBuilder.append(statement(depth, String.format("if (%s == %s):", varName, cases.get(i).id)));
            } else {
                stringBuilder.append(statement(depth, String.format("elif (%s == %s):", varName, cases.get(i).id)));
            }
            stringBuilder.append(cases.get(i).content);
        }
        if (cases.size() == 1) {
            stringBuilder.append(statement(depth, "if True:"));
            stringBuilder.append(cases.get(0).content);
        } else if (cases.size() > 1) {
            if (cases.stream().anyMatch(aCase -> Objects.equals(aCase.id, "default"))) {
                stringBuilder.append(statement(depth, "else:"));
                for (SwitchCase aCase : cases) {
                    if (Objects.equals(aCase.id, "default")) {
                        stringBuilder.append(aCase.content);
                    }
                }
            }
        } else {
            stringBuilder.append(statement(depth, "pass"));
        }
        return stringBuilder.toString();
    }

    static String addResultFunctionImplementation(
        int depth
    ) {
        var stringBuilder = new StringBuilder();
        stringBuilder
            .append(
                statement(
                    depth,
                    String.format(
                        "if (self.parent.%s > self.parent.%s):",
                        LAST_SUCCESSFUL_MATCH_AT,
                        MATCH_STARTED_AT
                    )
                )
            )
            .append(
                statement(
                    depth + 1,
                    String.format(
                        "self.parent.%s.append((self.parent.%s, self.parent.%s))",
                        FOUND,
                        MATCH_STARTED_AT,
                        LAST_SUCCESSFUL_MATCH_AT
                    )
                )
            )
            .append(
                statement(
                    depth,
                    String.format(
                        "self.parent.%s = self.parent.%s - 1 if self.parent.%s > self.parent.%s else self.parent.%s",
                        CURR_INDEX,
                        LAST_SUCCESSFUL_MATCH_AT,
                        LAST_SUCCESSFUL_MATCH_AT,
                        MATCH_STARTED_AT,
                        MATCH_STARTED_AT
                    )
                )
            )
            .append(
                statement(
                    depth,
                    String.format(
                        "self.parent.%s = self.parent.%s + 1",
                        MATCH_STARTED_AT,
                        CURR_INDEX
                    )
                )
            );
        return stringBuilder.toString();
    }

}

package hu.nemaberci.generator.generator.cpp;

import java.util.List;

public interface CppFileGeneratorUtils {

    static String withClass(
        String className,
        List<String> imports,
        String content
    ) {
        return imports.stream().map(importStatement -> "#include \"" + importStatement + "\"\n").reduce("", String::concat)
            + "class "
            + className
            + " {\n"
            + "public:\n"
            + content
            + "};";
    }

    record FunctionParameter(
        String type,
        String name
    ) {}

    static String functionBody(
        String functionName,
        String returnType,
        List<FunctionParameter> parameters,
        String content
    ) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("static ")
            .append(returnType)
            .append(" ")
            .append(functionName)
            .append("(");
        for (int i = 0; i < parameters.size(); i++) {
            stringBuilder.append(parameters.get(i).type)
                .append(" ")
                .append(parameters.get(i).name);
            if (i < parameters.size() - 1) {
                stringBuilder.append(", ");
            }
        }
        stringBuilder.append(
            ") {\n"
        );
        stringBuilder.append(content);
        stringBuilder.append(
            "}\n"
        );
        return stringBuilder.toString();
    }

    static String variable(
        String type,
        String name,
        String value
    ) {
        return type + " " + name + " = " + value + ";";
    }

    static String staticVariable(
        String type,
        String name
    ) {
        return "static " + type + " " + name + ";";
    }

    record SwitchCase(
        String id,
        String content
    ) {}

    static String switchStatement(
        String parameterName,
        List<SwitchCase> cases
    ) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("switch (")
            .append(parameterName)
            .append(") {\n");
        for (SwitchCase switchCase : cases) {
            if (switchCase.id.equals("default")) {
                stringBuilder.append("default:\n");
            }
            else {
                stringBuilder.append("case ")
                    .append(switchCase.id)
                    .append(":\n");
            }
            stringBuilder
                .append(switchCase.content)
                .append("break;\n");
        }
        stringBuilder.append("}\n");
        return stringBuilder.toString();
    }

}

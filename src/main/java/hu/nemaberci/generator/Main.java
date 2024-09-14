package hu.nemaberci.generator;

import hu.nemaberci.generator.generator.cpp.CppCodeGeneratorOrchestrator;
import hu.nemaberci.generator.generator.java.JavaCodeGeneratorOrchestrator;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {

    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addRequiredOption("r", "regex", true, "Regular expression to parse");
        options.addOption("l", "lang", true, "Language to use (one of: java, py, c)");
        options.addOption("o", "output", true, "Output folder");

        final var parser = new DefaultParser();
        final var parsedOptions = parser.parse(options, args);


        if (parsedOptions.getParsedOptionValue("l", "java").equals("java")) {
            final var generator = new JavaCodeGeneratorOrchestrator();
            generator.generateParser(
                "GeneratedParser",
                parsedOptions.getParsedOptionValue("r"),
                parsedOptions.getParsedOptionValue("o", ".")
            );
        } else if (parsedOptions.getParsedOptionValue("l").equals("c")) {
            final var generator = new CppCodeGeneratorOrchestrator();
            generator.generateParser(
                "GeneratedParser",
                parsedOptions.getParsedOptionValue("r"),
                parsedOptions.getParsedOptionValue("o", ".")
            );
        }

    }

}

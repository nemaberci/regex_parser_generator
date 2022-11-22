created_files = 0
with open("regexes.csv", "r") as file:
    lines = [line.strip() for line in file]
    for line in lines:
        created_files = created_files + 1
        regex = line.split(";")[0]
        teststring = line.split(";")[1]
        expected_results = line.split(";")[2]
        new_file1 = open("../java/hu/nemaberci/generator/parser/TestParser_" + str(created_files) + ".java", "w")
        new_file1.write("package hu.nemaberci.generator.parser;\n")
        new_file1.write("import hu.nemaberci.regex.annotation.RegularExpression;\n")
        new_file1.write("import hu.nemaberci.regex.api.RegexParser;\n")
        new_file1.write("@RegularExpression(\"" + regex + "\")\n")
        new_file1.write("public interface TestParser_" + str(created_files) + " extends RegexParser {\n")
        new_file1.write("}\n")
        new_file1.close()
        new_file2 = open("../java/hu/nemaberci/generator/parsertest/TestParser_" + str(created_files) + "_tester.java", "w")
        new_file2.write("package hu.nemaberci.generator.parsertest;\n")
        new_file2.write("import hu.nemaberci.generator.parser.TestParser_" + str(created_files) + ";\n")
        new_file2.write("import hu.nemaberci.regex.container.RegexParserContainer;\n")
        new_file2.write("import org.junit.Assert;\n")
        new_file2.write("import org.junit.jupiter.api.Test;\n")
        new_file2.write("public class TestParser_" + str(created_files) + "_tester {\n")
        new_file2.write("    @Test\n")
        new_file2.write("    public void testParser1() {\n")
        new_file2.write("        Assert.assertEquals(" + str(expected_results) + ", RegexParserContainer.getImplementation(TestParser_" + str(created_files) + ".class).findMatches(\"" + teststring + "\").getMatches().size());\n")
        new_file2.write("    }\n")
        new_file2.write("}\n")
        new_file2.close()

print("Created", created_files, "test cases")

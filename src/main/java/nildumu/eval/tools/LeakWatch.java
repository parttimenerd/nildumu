package nildumu.eval.tools;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import nildumu.eval.*;

/**
 * Tool that uses the LeakWatch tool, using SecureRandom to generate secrets
 *
 * http://www.cs.bham.ac.uk/research/projects/infotools/leakwatch/index.php
 */
public class LeakWatch extends JavaBytecodeBasedTool {

    private static final Path JAR_PATH = Paths.get("../eval-programs/leakwatch-0.5.jar");

    protected LeakWatch() {
        super("LeakWatch", JAR_PATH);
    }

    @Override
    public String generateJavaSourceCode(TestProgram program, String name) {
        assert program.integerType.width == 32;
        String global = program.globalToJavaCode(input -> {
            return String.format("%s %s = (%s) $$rand$$.nextInt();\n",
                    program.integerType.toJavaTypeName(),

                    input.variable,
                    program.integerType.toJavaTypeName()) + (input.secLevel.equals("h") ? String.format("LeakWatchAPI.secret(\"%s\", %s);", input.variable,
                    input.variable) : "");
        }, output -> {
            return String.format("LeakWatchAPI.observe(%s);", program.formatExpression(output.expression));
        });
        return "import bham.leakwatch.LeakWatchAPI;\n" +
                "\n" +
                String.format("public class %s {\n", name) +
                "    public static void main(String[] args) {\n" +
                "        java.security.SecureRandom $$rand$$ = new java.security.SecureRandom();" +
                global +
                "    }\n\n" +
                program.methodsToJavaCode("public static") +
                "}";
    }

    @Override
    public String getShellCommand(TestProgram program, String name, Path folder, PathFormatter formatter) {
        try {
            Files.copy(JAR_PATH, folder.resolve("leakwatch.jar"));
        } catch (IOException e) {
            //e.printStackTrace();
        }
        return String.format("java -Xss100m -jar leakwatch.jar --measure mel -i 1000 %s", name);
    }

    @Override
    public LeakageParser getLeakageParser(TestProgram program) {
        return (out, err) -> {
            String combined = out + "\n" + err;
            List<String> leakLines = Arrays.stream(combined.toLowerCase().split("estimated leakage:? "))
                    .map(l -> l.split(" ([(b])")[0]).collect(Collectors.toList());
            if (leakLines.size() > 0){
                String leakLine = leakLines.get(leakLines.size() - 1);
                try {
                    return Float.parseFloat(leakLine);
                } catch (NumberFormatException ex) {}
            }
            throw new LeakageParserException(this, out, err);
        };
    }
}

package nildumu.eval.tools;

import java.io.IOException;
import java.nio.file.*;

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
    public String generateJavaSourceCode(TestProgram program) {
        String global = program.globalToJavaCode(input -> {
            return String.format("%s %s = (%s) $$rand$$.nextInt(%s);\n" +
                            "LeakWatchAPI.secret(\"%s\", %s);",
                    program.integerType.toJavaTypeName(),
                    input.variable,
                    program.integerType.toJavaTypeName(),
                    program.integerType.width,
                    input.variable,
                    input.variable);
        }, output -> {
            return String.format("LeakWatchAPI.observe(%s);", output.expression);
        });
        return "import bham.leakwatch.LeakWatchAPI;\n" +
                "\n" +
                String.format("public class %s {\n", MAIN_CLASS_NAME) +
                "    public static void main(String[] args) {\n" +
                "        java.security.SecureRandom $$rand$$ = new java.security.SecureRandom();" +
                global +
                "    }\n\n" +
                program.methodsToJavaCode("public static") +
                "}";
    }

    @Override
    public String getShellCommand(TestProgram program, Path folder, PathFormatter formatter) {
        try {
            Files.copy(JAR_PATH, folder.resolve("leakwatch.jar"));
        } catch (IOException e) {
            //e.printStackTrace();
        }
        return String.format("java -jar leakwatch.jar %s", MAIN_CLASS_NAME);
    }

    @Override
    public LeakageParser getLeakageParser(TestProgram program) {
        return (outs, errs) -> {
            return Float.parseFloat(outs
                    .split("corrected leakage: ")[1]
                    .split("bits")[0]);
        };
    }
}

package nildumu.eval.tools;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Collections;

import nildumu.eval.*;
import nildumu.util.Util;

/**
 * Uses the Flowcheck tool (https://www-users.cs.umn.edu/~smccaman/flowcheck)
 */
public class Flowcheck extends AbstractTool {

    static final Path approxFlowFolder = Paths.get("../eval-programs/flowcheck-1.20");

    protected Flowcheck() {
        super("flowcheck", true);
    }

    static String toCCode(TestProgram program){
        String type = program.integerType == IntegerType.BYTE ?
                "char" :
                program.integerType.toJavaTypeName();
        assert program.getOutputVariables().size() == 1;
        String methods = program.methodsToJavaCode("", type);
        Util.Box<Integer> argCount = new Util.Box<>(1);
        return "#include <stdio.h>\n#include <stdlib.h>\n" +
                String.format("#include <%s/include/valgrind/flowcheck.h>\n\n",
                        approxFlowFolder.toAbsolutePath()) +
                program.methodsToCDeclarations(type) +
                methods + "\n\n" +
                "int main(int argc, char *argv[]){\n" +
                program.globalToJavaCode(
                        s -> {
                            return String.format("%s %s = (%s)atoi(argv[%d]);\n",
                                    type, s.variable, type, argCount.val++)
                                    + (s.secLevel.equals("h") ? String.format("FC_TAINT_WORD(&%s);\n", s.variable) : "");
                        },
                        s -> String.format("printf(\"%%d\", (%s)(%s));", type,
                                program.formatExpression(s.expression)), type) +
                "}";
    }

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        Path sourceFile = folder.resolve("code.c");
        try {
            Files.write(sourceFile, Collections.singletonList(toCCode(program)));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter, Duration timeLimit) {

                return String.format("cd %s; python3 analysis.py %s %d %d",
                        formatter.format(approxFlowFolder),
                        sourceFile.toAbsolutePath(),
                        program.getInputVariables().size(),
                        program.integerType.width);
            }

            @Override
            public LeakageParser getLeakageParser() {
                return LeakageParser.forLine(Flowcheck.this, "--- - leakage ", "");
            }
        };
    }
}
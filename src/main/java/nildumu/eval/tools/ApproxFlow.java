package nildumu.eval.tools;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Collections;
import java.util.stream.*;

import nildumu.Variable;
import nildumu.eval.*;

/**
 * Uses the ApproxFlow tool (of "Scalable Approximation of Quantitative Information Flow in Programs")
 *
 * https://github.com/approxflow/approxflow
 */
public class ApproxFlow extends AbstractTool {

    static final String GLOBAL_FUNCTION = "__global__";

    static final Path approxFlowFolder = Paths.get("../eval-programs/approxflow");

    protected ApproxFlow() {
        super("ApproxFlow", true);
    }

    static String toCCode(TestProgram program){
        String type = program.integerType == IntegerType.BYTE ?
                "char" :
                program.integerType.toJavaTypeName();
        assert program.getOutputVariables().size() == 1;
        String methods = program.methodsToJavaCode("", type);
        return String.format("%s nondet(void);\n", type) +
                String.format("%s %s(){\n", type, GLOBAL_FUNCTION) +
                program.globalToJavaCode(
                        s -> String.format("%s %s = nondet();", type, s.variable),
                        s -> String.format("return (%s)(%s);", type,
                                program.formatExpression(s.expression)), type) +
                "}\n\n" +
                methods;
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

                return String.format("cd %s; cp %s code.c; python ApproxFlow.py code.c %s",
                        formatter.format(approxFlowFolder),
                        sourceFile.toAbsolutePath(),
                        GLOBAL_FUNCTION);
            }

            @Override
            public LeakageParser getLeakageParser() {
                return LeakageParser.forLine(ApproxFlow.this, GLOBAL_FUNCTION + " : ", "");
            }
        };
    }
}
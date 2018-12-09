package nildumu.eval.tools;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Collections;

import nildumu.eval.*;

/**
 * Uses the ApproxFlow tool (of "Scalable Approximation of Quantitative Information Flow in Programs")
 *
 * https://github.com/approxflow/approxflow
 */
public class ApproxFlow extends AbstractTool {

    static final String GLOBAL_FUNCTION = "__global__";

    static final Path approxFlowFolder = Paths.get("../eval-programs/approxflow");

    final int unwindLimit;

    ApproxFlow() {
        this(32);
    }

    ApproxFlow(int unwindLimit) {
        super("ApproxFlow" + unwindLimit, true);
        this.unwindLimit = unwindLimit;
    }

    static String toCCode(TestProgram program){
        String type = program.integerType == IntegerType.BYTE ?
                "char" :
                program.integerType.toJavaTypeName();
        assert program.getOutputVariables().size() == 1;
        String methods = program.methodsToJavaCode("", type);
        return String.format("%s nondet(void);\n\n", type) +
                program.methodsToCDeclarations(type) +
                methods + "\n\n" +
                String.format("%s %s(){\n", type, GLOBAL_FUNCTION) +
                program.globalToJavaCode(
                        s -> {
                            if (s.secLevel.equals("l")){
                                throw new UnsupportedLanguageFeatureException(new ApproxFlow(), "low inputs");
                            }
                            return String.format("%s %s = nondet();", type, s.variable);
                        },
                        s -> String.format("return (%s)(%s);", type,
                                program.formatExpression(s.expression)), type) +
                "}";
    }

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        String codeFileName = program.getUniqueCodeName(".c");
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
                return String.format("cd %s; cp %s %s; UNWIND=%d python ApproxFlow.py %s %s",
                        formatter.format(approxFlowFolder),
                        sourceFile.toAbsolutePath(),
                        codeFileName,
                        unwindLimit,
                        codeFileName,
                        GLOBAL_FUNCTION);
            }

            @Override
            public LeakageParser getLeakageParser() {
                return LeakageParser.forLine(ApproxFlow.this, GLOBAL_FUNCTION + " : ", "");
            }
        };
    }
}
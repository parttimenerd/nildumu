package nildumu.eval.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Collections;
import java.util.stream.Collectors;

import nildumu.eval.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Uses the ApproxFlow tool (of "Scalable Approximation of Quantitative Information Flow in Programs")
 *
 * https://github.com/approxflow/approxflow
 */
public class ApproxFlow extends AbstractTool {

    static final String GLOBAL_FUNCTION = "__global__";

    static final Path approxFlowFolder = Paths.get("eval-programs/approxflow");
    private final double epsilon;
    private final double delta;

    ApproxFlow() {
        this(32);
    }

    ApproxFlow(int unwindLimit) {
        this(unwindLimit, 0.8, 0.2);
    }


    public ApproxFlow(int unwindLimit, double epsilon, double delta) {
        super(String.format("ApproxFlow%02d", unwindLimit), unwindLimit, "c");
        this.epsilon = epsilon;
        this.delta = delta;
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
        return createDirectPacket(program, sourceFile);
    }

    @Override
    public AnalysisPacket createDirectPacket(TestProgram program, Path path) {
        String function = path.toString().contains("eval-specimen") ? "main" : GLOBAL_FUNCTION;
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter, Duration timeLimit) {
                File file = null;
                try {
                     file = File.createTempFile(program.name, ".c");
                     Files.copy(path.toAbsolutePath(), file.toPath(), REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                File envFile = new File(path.toString() + ".env");
                String envString = "";
                if (envFile.exists()) {
                    try {
                        envString = String.join(" ", Files.readAllLines(envFile.toPath()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return String.format("cd %s; %s UNWIND=%d EPSILON=%f DELTA=%f PARTIAL_LOOPS=true taskset -c 0,1 python ApproxFlow.py %s %s; rm %s* || true",
                        formatter.format(approxFlowFolder),
                        envString,
                        unwind, epsilon, delta,
                        file.getAbsolutePath(),
                        function, file.getAbsolutePath());
            }

            @Override
            public LeakageParser getLeakageParser() {
                return LeakageParser.forLine(ApproxFlow.this, function + " : ", "");
            }
        };
    }

    @Override
    public void check() {
        checkExistence(approxFlowFolder);
    }
}
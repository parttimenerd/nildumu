package nildumu.eval.tools;

import nildumu.eval.*;
import swp.util.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static nildumu.util.Util.p;

/**
 * Uses the ApproxFlow tool (of "Scalable Approximation of Quantitative Information Flow in Programs")
 *
 * https://github.com/approxflow/approxflow
 */
public class DSharpy extends AbstractTool {

    static final Path dsharpyFolder = Paths.get("eval-programs/dsharpy");

    private final String modelChecker;
    private final String leakageComputer;
    private final double epsilon;
    private final double delta;

    private DSharpy(String name, String modelChecker, String leakageComputer, int unwindLimit, double epsilon, double delta) {
        super(String.format("%s%02d", name, unwindLimit), unwindLimit, "c");
        this.modelChecker = modelChecker;
        this.leakageComputer = leakageComputer;
        this.epsilon = epsilon;
        this.delta = delta;
    }

    public static DSharpy createApproxFlow(int unwindLimit) {
        return createApproxFlow(unwindLimit, 0.8, 0.2);
    }

    public static DSharpy createApproxFlow(int unwindLimit, double epsilon, double delta) {
        return new DSharpy("ApproxFlow", "cbmc", "approxflow", unwindLimit, epsilon, delta);
    }

    public static DSharpy createModifiedApproxFlow(int unwindLimit) {
        return createModifiedApproxFlow(unwindLimit, 0.8, 0.2);
    }

    /** with modified CBMC */
    public static DSharpy createModifiedApproxFlow(int unwindLimit, double epsilon, double delta) {
        return new DSharpy("MApproxFlow", "modified_cbmc", "approxflow", unwindLimit, epsilon, delta);
    }

    public static DSharpy createRelationsCutter(int unwindLimit) {
        return new DSharpy("RelationsCutter", "modified_cbmc", "relationscutter", unwindLimit, 0, 0);
    }

    public static String toCCode(TestProgram program){
        throw new UnsupportedOperationException();
    }

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        String codeFileName = program.getUniqueCodeName(".c");
        Path sourceFile = folder.resolve("code.c");
        return createDirectPacket(program, sourceFile);
    }

    @Override
    public AnalysisPacket createDirectPacket(TestProgram program, Path path) {
        String function = "main";
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter, Duration timeLimit) {
                File file = null;
                try {
                     file = File.createTempFile(program.name, ".cpp");
                     Files.copy(path.toAbsolutePath(), file.toPath(), REPLACE_EXISTING);
                    return String.format("cd \"%s\"; taskset -c 0,1 poetry run dsharpy \"%s\" --unwind %d --epsilon %f --delta %f -l %s -m %s; rm \"%s\"* || true",
                            formatter.format(dsharpyFolder),
                            file.getAbsolutePath(),
                            unwind,
                            epsilon, delta,
                            leakageComputer,
                            modelChecker,
                            file.getAbsolutePath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            public LeakageParser getLeakageParser() {
                return LeakageParser.forLine(DSharpy.this, "Leakage: ", "");
            }
        };
    }

    private String formatEnvVariables(List<Pair<String, Object>> variables) {
        return variables.stream().map(v -> String.format("%s=\"%s\"", v.first, v.second)).collect(Collectors.joining(" "));
    }

    /** List of key value pair for environment variables to set, at least UNWIND should be set,
     * EPSILON, DELTA and PARTIAL_LOOPS are set by other code */
    public List<Pair<String, Object>> getEnvVariables(){
        return Collections.singletonList(p("UNWIND", unwind));
    }

    @Override
    public void check() {
        checkExistence(dsharpyFolder);
        checkExistence(dsharpyFolder.resolve("tools/cbmc/build/bin/cbmc"));
        checkExistence(dsharpyFolder.resolve("tools/modified_cbmc/build/bin/cbmc"));
    }
}
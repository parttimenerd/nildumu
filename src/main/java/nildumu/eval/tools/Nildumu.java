package nildumu.eval.tools;

import java.nio.file.*;
import java.time.Duration;

import nildumu.LeakageAlgorithm;
import nildumu.eval.*;

/**
 * Tool that uses the analysis of this project
 */
public class Nildumu extends AbstractTool {

    private static final Path JAR_PATH = Paths.get("eval-programs/nildumu.jar");

    private final String mih;

    private final LeakageAlgorithm.Algo algo;

    private final boolean useSimplifiedEdgeHeuristic;
    private final boolean useReplacements;

    public Nildumu(int unwind, LeakageAlgorithm.Algo algo) {
        this(unwind, algo, true, true);
    }

    public Nildumu(int unwind, LeakageAlgorithm.Algo algo, boolean useSimplifiedEdgeHeuristic, boolean useReplacements) {
        this(unwind, 0, algo, useSimplifiedEdgeHeuristic, useReplacements, true);
    }

    public Nildumu(int unwind, boolean summaryUnwind, LeakageAlgorithm.Algo algo) {
        this(unwind, summaryUnwind ? unwind : 0, algo, true, true, true);
    }

    public static Nildumu withoutSummary(int unwind, LeakageAlgorithm.Algo algo) {
        return withoutSummary(unwind, algo, true, true);
    }

    public Nildumu(int unwind, boolean summaryUnwind, LeakageAlgorithm.Algo algo, boolean useSimplifiedEdgeHeuristic,
                   boolean useReplacements) {
        this(unwind, summaryUnwind ? unwind : 0, algo, useSimplifiedEdgeHeuristic, useReplacements, true);
    }

    public static Nildumu withoutSummary(int unwind, LeakageAlgorithm.Algo algo, boolean useSimplifiedEdgeHeuristic,
                   boolean useReplacements) {
        return new Nildumu(unwind, 0, algo, useSimplifiedEdgeHeuristic, useReplacements, false);
    }

    /**
     *  @param csrec maximum recursion depth for the call string handler
     * @param scsrec maximum recusion depth for the call string handler used by the summary handler
     */
    public Nildumu(int csrec, int scsrec, LeakageAlgorithm.Algo algo, boolean useSimplifiedEdgeHeuristic,
                   boolean useReplacements, boolean useSummaryHandler){
        super(String.format("nildumu%s%02d_%02d_%s_%s_%s", useSummaryHandler ? "" : "WOS",
                csrec, scsrec, algo.shortName, useSimplifiedEdgeHeuristic ? "s" : "c",
                useReplacements ? "r" : "wor"), csrec, "nd");
        this.mih = useSummaryHandler ? String.format("handler=inlining;maxrec=%d;bot={handler=summary;csmaxrec=%d;bot=basic}",
                csrec, scsrec) : String.format("handler=inlining;maxrec=%d;bot=basic",
                csrec);
        this.algo = algo;
        this.useSimplifiedEdgeHeuristic = useSimplifiedEdgeHeuristic;
        this.useReplacements = useReplacements;
    }

    @Override
    public void check() {
        checkExistence(JAR_PATH);
    }

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        return createDirectPacket(program, this.writeOrDie(folder, "code.nd", program.program.toPrettyString()));
    }

    @Override
    public AnalysisPacket createDirectPacket(TestProgram program, Path testFile) {
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter, Duration timeLimit) {
                int freeMB = (int) (Runtime.getRuntime().maxMemory() / 1024L / 1024L * 0.95);
                String javaConf = String.format("-Xmx%dm", freeMB);
                return String.format("taskset -c 0,1 %s %s --handler \"%s\" --algo \"%s\" --%suseSimplifiedEdgeHeuristic" +
                                " --%suseReplacements",
                        String.format("java %s -jar %s", javaConf, formatter.format(JAR_PATH)),
                        formatter.format(testFile), mih, algo.name(),
                        useSimplifiedEdgeHeuristic ? "" : "no-",
                        useReplacements ? "" : "no-");
            }

            @Override
            public LeakageParser getLeakageParser() {
                return (out, errs) -> {
                    String[] tks = out.split(" ");
                    return Float.parseFloat(tks[tks.length - 1]);
                };
            }
        };
    }
}

package nildumu.eval.tools;

import java.nio.file.*;
import java.time.Duration;

import nildumu.MinCut;
import nildumu.eval.*;

/**
 * Tool that uses the analysis of this project
 */
public class Nildumu extends AbstractTool {

    private static final Path JAR_PATH = Paths.get("eval-programs/nildumu.jar");

    private final String mih;

    private final MinCut.Algo algo;

    public Nildumu(int unwind, MinCut.Algo algo) {
        this(unwind, 0, algo);
    }

    public Nildumu(int unwind, boolean summaryUnwind, MinCut.Algo algo) {
        this(unwind, summaryUnwind ? unwind : 0, algo);
    }

    /**
     *  @param csrec maximum recursion depth for the call string handler
     * @param scsrec maximum recusion depth for the call string handler used by the summary handler
     */
    public Nildumu(int csrec, int scsrec, MinCut.Algo algo){
        super(String.format("nildumu%02d_%02d_%s", csrec, scsrec, algo.shortName), csrec, "nd");
        this.mih = String.format("handler=inlining;maxrec=%d;bot={handler=summary;csmaxrec=%d;bot=basic}",
                csrec, scsrec);
        this.algo = algo;
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
                int freeMB = (int) (Runtime.getRuntime().maxMemory() / 1024L / 1024L * 0.9);
                String javaConf = String.format("-Xmx%dm", freeMB);
                return String.format("taskset -c 0,1 %s %s --handler \"%s\" --algo \"%s\"",
                        String.format("java %s -jar %s", javaConf, formatter.format(JAR_PATH)),
                        formatter.format(testFile), mih, algo.name());
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

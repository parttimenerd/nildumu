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
    private static final Path NATIVE_PATH = Paths.get("eval-programs/nildumu");

    private final String mih;

    private final MinCut.Algo algo;

    private final boolean useNative;

    public Nildumu(int unwind, MinCut.Algo algo, boolean useNative) {
        this(unwind, 1, algo, useNative);
    }

    /**
     *  @param csrec maximum recursion depth for the call string handler
     * @param scsrec maximum recusion depth for the call string handler used by the summary handler
     * @param useNative
     */
    public Nildumu(int csrec, int scsrec, MinCut.Algo algo, boolean useNative){
        super(String.format("nildumu%s_%02d_%02d_%s", useNative ? "n" : "", csrec, scsrec, algo.shortName), csrec, "nd");
        this.useNative = useNative;
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
                return String.format("%s %s --handler \"%s\" --algo \"%s\"",
                        useNative ? formatter.format(NATIVE_PATH) : String.format("java -jar %s", formatter.format(JAR_PATH)),
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

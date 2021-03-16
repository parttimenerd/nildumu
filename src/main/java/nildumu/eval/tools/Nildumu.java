package nildumu.eval.tools;

import java.nio.file.*;
import java.time.Duration;

import nildumu.NildumuError;
import nildumu.eval.*;

/**
 * Tool that uses the analysis of this project
 */
public class Nildumu extends AbstractTool {

    private static final Path JAR_PATH = Paths.get("eval-programs/nildumu.jar");

    private final String mih;

    public Nildumu(int unwind) {
        this(unwind, 1);
    }

    /**
     *
     * @param csrec maximum recursion depth for the call string handler
     * @param scsrec maximum recusion depth for the call string handler used by the summary handler
     */
    public Nildumu(int csrec, int scsrec){
        super(String.format("nildumu_%d_%d", csrec, scsrec), csrec, "nd");
        this.mih = String.format("handler=inlining;maxrec=%d;bot={handler=summary;csmaxrec=%d;bot=basic}",
                csrec, scsrec);
    }

    @Override
    public void check() {
        checkExistence(JAR_PATH);
    }

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        Path file = this.writeOrDie(folder, "code.nd",
                program.program.toPrettyString().replaceAll("\\|\\|", "\\|")
                                                .replaceAll("&&", "&"));
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter, Duration timeLimit) {
                return String.format("java -jar %s %s --handler \"%s\"",
                        formatter.format(JAR_PATH),
                        formatter.format(file), mih);
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

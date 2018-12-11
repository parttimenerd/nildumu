package nildumu.eval.tools;

import java.nio.file.*;
import java.time.Duration;

import nildumu.eval.*;

/**
 * Tool that uses the analysis of this project
 */
public class NildumuDemoTool extends AbstractTool {

    private static final Path JAR_PATH = Paths.get("../eval-programs/nildumu-demo.jar");

    private final String mih;

    public NildumuDemoTool() {
        this("inlining");
    }

    /**
     *
     * @param csrec maximum recursion depth for the call string handler
     * @param scsrec maximum recusion depth for the call string handler used by the summary handler
     */
    public NildumuDemoTool(int csrec, int scsrec){
        super(String.format("nildumu-demo_%d_%d", csrec, scsrec), true);
        this.mih = String.format("handler=inlining;maxrec=%d;bot={handler=summary;csmaxrec=%d}",
                csrec, scsrec);
    }

    public NildumuDemoTool(String mih) {
        super("nildumu-demo" + mih, true);
        this.mih = mih;
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

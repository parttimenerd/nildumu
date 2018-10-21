package nildumu.eval.tools;

import java.nio.file.*;

import nildumu.eval.*;

/**
 * Tool that uses the analysis of this project
 */
public class NildumuDemoTool extends AbstractTool {

    private static final Path JAR_PATH = Paths.get("../eval-programs/nildumu-demo.jar");

    private final String mih;

    public NildumuDemoTool() {
        this("call_string");
    }

    /**
     *
     * @param csrec maximum recursion depth for the call string handler
     * @param scsrec maximum recusion depth for the call string handler used by the summary handler
     */
    public NildumuDemoTool(int csrec, int scsrec){
        super(String.format("nildumu-demo_%d_%d", csrec, scsrec));
        this.mih = String.format("handler=call_string;maxrec=%d;bot={handler=summary;csmaxrec=%d}",
                csrec, scsrec);
    }

    public NildumuDemoTool(String mih) {
        super("nildumu-demo" + mih);
        this.mih = mih;
    }

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        Path file = this.writeOrDie(folder, "code.nd", "bit_width 32;\n" +
                program.program.toPrettyString());
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter) {
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

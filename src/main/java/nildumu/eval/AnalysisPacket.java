package nildumu.eval;

import org.apache.commons.lang3.StringEscapeUtils;

import java.nio.file.Path;
import java.time.Duration;

import nildumu.eval.tools.AbstractTool;

public abstract class AnalysisPacket {

    public final AbstractTool tool;
    public final TestProgram program;
    public final boolean emptyPacket;

    protected AnalysisPacket(AbstractTool tool, TestProgram program, boolean emptyPacket){
        this.tool = tool;
        this.program = program;
        this.emptyPacket = emptyPacket;
    }

    protected AnalysisPacket(AbstractTool tool, TestProgram program) {
        this(tool, program, false);
    }

    public String getShellCommand(Duration timeLimit){
        return getShellCommand(Path::toString, timeLimit);
    }

    public String getShellCommandWithAbsolutePaths(Duration timeLimit){
        return getShellCommand(p -> p.toAbsolutePath().toString(), timeLimit);
    }

    /**
     * Returns the shell command that can be used to start the analysis
     */
    public abstract String getShellCommand(PathFormatter formatter, Duration timeLimit);

    /**
     * Returns a function that parses the output of the shell command
     * and returns the leakage
     */
    public abstract LeakageParser getLeakageParser();

    @Override
    public String toString() {
        return String.format("%s with %s", program, tool.name);
    }

    public String toTemciConfigEntry(Duration timeLimit){
        return String.format("- attributes: {description: \"%s\"}\n",
                StringEscapeUtils.escapeJava(toString())) +
                "  run_config:\n" +
                String.format("    run_cmd: \"timeout -s9 %f %s\"",
                        timeLimit.toMillis() / 1000.0,
                        StringEscapeUtils.escapeJava(getShellCommandWithAbsolutePaths(timeLimit)));
    }

    public static AnalysisPacket empty(AbstractTool tool, TestProgram program){
        return new AnalysisPacket(tool, program, true) {
            @Override
            public String getShellCommand(PathFormatter formatter, Duration timeLimit) {
                return null;
            }

            @Override
            public LeakageParser getLeakageParser() {
                return null;
            }
        };
    }
}

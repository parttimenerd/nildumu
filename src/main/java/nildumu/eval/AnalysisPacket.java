package nildumu.eval;

import org.apache.commons.lang3.StringEscapeUtils;

import java.nio.file.Path;

import nildumu.eval.tools.AbstractTool;

public abstract class AnalysisPacket {

    public final AbstractTool tool;
    public final TestProgram program;

    protected AnalysisPacket(AbstractTool tool, TestProgram program) {
        this.tool = tool;
        this.program = program;
    }

    public String getShellCommand(){
        return getShellCommand(Path::toString);
    }

    public String getShellCommandWithAbsolutePaths(){
        return getShellCommand(p -> p.toAbsolutePath().toString());
    }

    /**
     * Returns the shell command that can be used to start the analysis
     */
    public abstract String getShellCommand(PathFormatter formatter);

    /**
     * Returns a function that parses the output of the shell command
     * and returns the leakage
     */
    public abstract LeakageParser getLeakageParser();

    @Override
    public String toString() {
        return String.format("%s with %s", program, tool.name);
    }

    public String toTemciConfigEntry(){
        return String.format("- attributes: {description: \"%s\"}\n",
                StringEscapeUtils.escapeJava(toString())) +
                "  run_config:\n" +
                String.format("    run_cmd: \"%s\"",
                        StringEscapeUtils.escapeJava(getShellCommandWithAbsolutePaths()));
    }
}

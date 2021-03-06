package nildumu.eval.tools;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Collections;
import java.util.stream.Collectors;

import nildumu.eval.*;

/**
 * Tool that uses nildumu implementation based on the JOANA framework
 */
public class Nildumu extends AbstractTool {

    final static String EXPORT_JAVA8_COMMAND =
            "export PATH=/usr/lib/jvm/java-1.8.0-openjdk-amd64/bin:$PATH";

    private static final Path NILDUMU_PATH = Paths.get(".");

    private static final String JAR_NAME = "joana.ifc.sdg.qifc.nildumu.jar";

    private final String mih;

    public Nildumu() {
        this("inlining");
    }

    /**
     *
     * @param csrec maximum recursion depth for the call string handler
     * @param scsrec maximum recusion depth for the call string handler used by the summary handler
     */
    public Nildumu(int csrec, int scsrec){
        super(String.format("nildumu_%d_%d", csrec, scsrec), true);
        this.mih = String.format("handler=inlining;maxrec=%d;bot={handler=summary;csmaxrec=%d}",
                csrec, scsrec);
    }

    public Nildumu(String mih) {
        super("nildumu" + mih, true);
        this.mih = mih;
    }

    public String generateJavaSourceCode(TestProgram program, String name) {
        final String GLOBAL_METHOD = "program";
        String global = String.format("@EntryPoint @Config(intWidth=%d)\n" +
                        "public static void %s(%s){",
                program.integerType.width,
                GLOBAL_METHOD,
                program.getInputVariablesWSec().stream()
                        .map(p -> String.format("@Source(level=Level.%s) %s %s", p.second.equals("h") ? "HIGH" : "LOW",
                                program.integerType.toJavaTypeName(), p.first))
                        .collect(Collectors.joining(", "))) +
                program.globalToJavaCode(input -> "",
                output -> String.format("leak(%s);", program.formatExpression(output.expression))) + "}";
        return "import edu.kit.joana.ui.annotations.Source;\n" +
                "import edu.kit.joana.ui.annotations.Level;\n" +
                "\n" +
                "import static edu.kit.joana.ifc.sdg.qifc.nildumu.ui.CodeUI.*;\n" +
                "import edu.kit.joana.ifc.sdg.qifc.nildumu.ui.*;\n" +
                "\n" +
                String.format("public class %s {\n", name) +
                "    public static void main(String[] args) {\n" +
                String.format("%s(%s);", GLOBAL_METHOD,
                        program.getInputVariables().stream()
                                .map(v -> "1")
                                .collect(Collectors.joining(", "))) +
                "    }\n\n" +
                global + "\n" +
                program.methodsToJavaCode("public static") +
                "}";
    }

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        String name = program.getUniqueCodeName("");
        String sourceCode = generateJavaSourceCode(program, name);
        Path sourceFile = folder.resolve(name + ".java");
        try {
            Files.write(sourceFile, Collections.singleton(sourceCode));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter, Duration timeLimit) {
                return String.format("cd %s; cp %s %s.java; %s; javac %s %s.java; " +
                                "java -Xss100m -jar joana.ifc.sdg.qifc.nildumu.jar %s --classpath . --handler \"%s\"",
                        formatter.format(NILDUMU_PATH),
                        sourceFile.toAbsolutePath(),
                        name,
                        EXPORT_JAVA8_COMMAND,
                        String.format("-cp %s:.", JAR_NAME),
                        name,
                        name,
                        mih);
            }

            @Override
            public LeakageParser getLeakageParser() {
                return LeakageParser.forLinePart(this.tool, "l: ", " bit");
            }

            public String toTemciConfigEntry(Duration timeLimit){
                return String.format("- attributes: {description: \"%s\"}\n",
                        StringEscapeUtils.escapeJava(toString())) +
                        "  run_config:\n" +
                        String.format("    run_cmd: '%s'", getShellCommandWithAbsolutePaths(timeLimit));
            }
        };
    }
}

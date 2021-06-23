package nildumu;
import nildumu.mih.MethodInvocationHandler;
import picocli.CommandLine;
import static picocli.CommandLine.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static nildumu.Processor.*;

/**
 * Runs the program on the command line
 */
@Command(description = "Run nildumu on the command line. Example programs are given in the folders 'eval-specimen' " +
        "and 'examples'. The syntax of the paper is accepted too.",
        showDefaultValues = true, mixinStandardHelpOptions = true, name = "./run")
public class Main implements Runnable {

    @Parameters(description = "program file to analyze, or '-' to read from standard in. " +
            "Nildumu files usually end with '.nd'.", defaultValue = "-")
    private String programPath = "-";

    @Option(names = "--handler", description = "Method invocation handler configuration, " +
            "for paper: 'handler=inlining;maxrec=INLINING;bot=summary'")
    private String handler = "handler=inlining;maxrec=32;bot=summary";

    @Option(names = "--algo", description = "Used leakage computation algorithm, default is GraphT_PP based")
    private LeakageAlgorithm.Algo algo = LeakageAlgorithm.usedAlgo;

    @Option(names = "--useSimplifiedEdgeHeuristic", description = "Use the simplified edge selection heuristic, " +
            "ignored if the solver is PMSAT based", negatable = true)
    private boolean useSimplifiedEdgeHeuristic;

    @Option(names = {"-tp", "--transformPlus"},
            description = "Transform plus into bit wise operators in the preprocessing step", defaultValue = "false")
    boolean transformPlus = false;

    @Override
    public void run() {
        try {
            int opts = (transformPlus ? TRANSFORM_PLUS : 0) | TRANSFORM_LOOPS |
                    (algo.capability(LeakageAlgorithm.Algo.SUPPORTS_ALTERNATIVES) ? RECORD_ALTERNATIVES : 0) |
                    (useSimplifiedEdgeHeuristic ? USE_SIMPLIFIED_HEURISTIC : 0);
            Context context =
                    Processor.process(String.join("\n", programPath.equals("-") ?
                                    new BufferedReader(new InputStreamReader(System.in)).lines().collect(Collectors.toList()) :
                                    Files.readAllLines(Paths.get(programPath))),
                            Context.Mode.EXTENDED, MethodInvocationHandler.parse(handler), opts);
            System.out.println("Leakage: " + context.computeLeakage(algo).get(Lattices.BasicSecLattice.LOW).maxFlow);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new Main());
        commandLine.registerConverter(LeakageAlgorithm.Algo.class, LeakageAlgorithm.Algo::from);
        commandLine.execute(args);
    }
}

package nildumu;
import nildumu.mih.MethodInvocationHandler;
import picocli.CommandLine;
import static picocli.CommandLine.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static nildumu.Processor.*;

/**
 * Runs the program on the command line
 */
@Command(description = "Basic quantitative information flow analysis")
public class Main implements Runnable {

    @Parameters(description = "program file to analyze")
    private String programPath;

    @Option(names = "--handler", description = "Method invocation handler configuration, see README")
    private String handler = "handler=inlining;maxrec=5;bot=summary";

    @Option(names = "--algo", description = "Used leakage computation algorithm")
    private MinCut.Algo algo = MinCut.usedAlgo;

    @Option(names = {"-tp", "--transformPlus"}, arity = "1")
    boolean transformPlus = false;

    @Option(names = {"-tl", "--transformLoops"}, arity = "1")
    boolean transformLoops = true;

    @Option(names = {"-ra", "--recordAlternatives"}, arity = "1")
    boolean recordAlternatives = true;

    @Override
    public void run() {
        try {
            if (!algo.supportsAlternatives && recordAlternatives){
                System.err.printf("Disabling the recording of alternatives as %s does not support it\n", algo);
                recordAlternatives = false;
            }
            int opts = (transformPlus ? TRANSFORM_PLUS : 0) | (transformLoops ? TRANSFORM_LOOPS : 0) |
                    (recordAlternatives ? RECORD_ALTERNATIVES : 0);
            Context context =
                    Processor.process(String.join("\n", Files.readAllLines(Paths.get(programPath))),
                            transformLoops ? Context.Mode.EXTENDED : Context.Mode.LOOP , MethodInvocationHandler.parse(handler), opts);
            System.out.println("Leakage: " + context.computeLeakage(algo).get(Lattices.BasicSecLattice.LOW).maxFlow);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new Main());
        commandLine.registerConverter(MinCut.Algo.class, MinCut.Algo::from);
        commandLine.execute(args);
    }
}

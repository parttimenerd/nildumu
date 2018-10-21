package nildumu;


import com.beust.jcommander.*;
import com.beust.jcommander.converters.PathConverter;

import java.io.IOException;
import java.nio.file.*;

/**
 * Runs the program on the command line
 */
@Parameters(commandDescription="Basic quantitative information flow analysis")
public class Main {

    @Parameter(names="--handler", description="Method invocation handler configuration, see README")
    private String handler = "handler=call_string;bot=summary";

    @Parameter(description="program file to analyze", converter = PathConverter.class, required = true)
    private String programPath;

    public static void main(String[] args) {
        Main main = new Main();
        JCommander.newBuilder().addObject(main).build().parse(args);

        DotRegistry.get().disable();

        try {
            Context context =
                    Processor.process(String.join("\n",Files.readAllLines(Paths.get(main.programPath))),
                            Context.Mode.LOOP , MethodInvocationHandler.parse(main.handler));
            System.out.println("Leakage: " + context.computeLeakage().get(Lattices.BasicSecLattice.LOW).maxFlow);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

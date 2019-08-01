package nildumu;


import com.beust.jcommander.*;
import com.beust.jcommander.converters.PathConverter;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Runs the program on the command line
 */
@Parameters(commandDescription="Basic quantitative information flow analysis")
public class Main {

    private static class AlgoConverter implements IStringConverter<MinCut.Algo> {

        @Override
        public MinCut.Algo convert(String s) {
            try {
                return MinCut.Algo.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException ex){
                throw new ParameterException(String.format("%s is not a valid algorithm, use one of %s",
                        s, Arrays.stream(MinCut.Algo.values()).map(MinCut.Algo::toString).collect(Collectors.joining(", "))));
            }
        }
    }

    @Parameter(names="--handler", description="Method invocation handler configuration, see README")
    private String handler = "handler=call_string;bot=summary";

    @Parameter(description="program file to analyze", converter = PathConverter.class, required = true)
    private String programPath;

    @Parameter(names="--algo", description="Used leakage computation algorithm", converter=AlgoConverter.class)
    private MinCut.Algo algo = MinCut.Algo.GRAPHT_PP;

    public static void main(String[] args) {
        Main main = new Main();
        JCommander.newBuilder().addObject(main).build().parse(args);

        DotRegistry.get().disable();

        try {
            Context context =
                    Processor.process(String.join("\n",Files.readAllLines(Paths.get(main.programPath))),
                            Context.Mode.LOOP , MethodInvocationHandler.parse(main.handler));
            System.out.println("Leakage: " + context.computeLeakage(main.algo).get(Lattices.BasicSecLattice.LOW).maxFlow);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package nildumu;

import nildumu.typing.TypeTransformer;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static nildumu.ProcessingPipeline.Stage.wrap;

/**
 * Models the pipeline of processing which consists of multiple phases that consume and produce
 * {@link Parser.ProgramNode} instances
 */
public class ProcessingPipeline {

    @FunctionalInterface
    public interface Stage {
        Parser.ProgramNode process(Parser.ProgramNode program);

        default String process(String program) {
            return process(Parser.parse(program)).toPrettyString();
        }

        static Stage wrap(Consumer<Parser.ProgramNode> func) {
            return program -> {
                func.accept(program);
                return program;
            };
        }
    }

    private final List<Stage> stages;

    private ProcessingPipeline(List<Stage> stages) {
        this.stages = stages;
    }

    private ProcessingPipeline(Stage... stages) {
        this.stages = Arrays.asList(stages);
    }

    public static ProcessingPipeline create() {
        return create(false);
    }

    public static ProcessingPipeline create(boolean transformPlus) {
        return new ProcessingPipeline(createTillBeforeSSAResolution().stage(),
                wrap(SSAResolution2::process),
                program -> new MetaOperatorTransformator(program.context.maxBitWidth, transformPlus).process(program));
    }

    public static ProcessingPipeline createTillBeforeTypeTransformation() {
        return new ProcessingPipeline(wrap(LoopTransformer::process), wrap(ReturnTransformer::process));
    }

    public static ProcessingPipeline createTillBeforeSSAResolution() {
        return new ProcessingPipeline(
                createTillBeforeTypeTransformation().stage(),
                p -> {
                    new NameResolution(p).resolve();
                    return TypeTransformer.process(p);
        });
    }

    public Stage stage() {
        return new Stage() {
            @Override
            public Parser.ProgramNode process(Parser.ProgramNode program) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String process(String program) {
                return justProcess(program);
            }
        };
    }

    public Parser.ProgramNode process(String program) {
        return process(program, true);
    }

    public Parser.ProgramNode process(String program, boolean resetCounters) {
        Parser.ProgramNode programNode = Parser.parse(justProcess(program, resetCounters));
        new NameResolution(programNode).resolve();
        return programNode;
    }

    String justProcess(String program) {
        return justProcess(program, true);
    }

    String justProcess(String program, boolean resetCounters) {
        for (Stage stage : stages) {
            if (resetCounters) {
                Parser.MJNode.resetIdCounter();
                Lattices.Bit.resetNumberOfCreatedBits();
            }
            Lattices.ValueLattice.get().bitWidth = 32;
            try {
                program = stage.process(program);
            } catch (NildumuError | ClassCastException err) {
                System.err.println("---- prior to state ---");
                System.err.println(program);
                System.err.println("----");
                throw new NildumuError(err);
            }
        }
        return program;
    }

}

package nildumu;

import nildumu.typing.TypeTransformer;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
        return create(false, true);
    }

    public static ProcessingPipeline create(boolean transformPlus, boolean transformLoops) {
        return new ProcessingPipeline(wrap(LoopTransformer::process), p -> {
                    new NameResolution(p).resolve();
                    return TypeTransformer.process(p);
                },
                wrap(SSAResolution2::process),
                program -> new MetaOperatorTransformator(program.context.maxBitWidth, transformPlus).process(program));
    }

    public static ProcessingPipeline createTillBeforeTypeTransformation() {
        return new ProcessingPipeline(wrap(LoopTransformer::process));
    }

    public static ProcessingPipeline createTillBeforeSSAResolution() {
        return new ProcessingPipeline(wrap(LoopTransformer::process), p -> {
            new NameResolution(p).resolve();
            return TypeTransformer.process(p);
        });
    }

    public Parser.ProgramNode process(String program) {
        return process(program, true);
    }

    public Parser.ProgramNode process(String program, boolean resetCounters) {
        for (Stage stage : stages) {
            if (resetCounters) {
                Parser.MJNode.resetIdCounter();
                Lattices.Bit.resetNumberOfCreatedBits();
            }
            Lattices.ValueLattice.get().bitWidth = 32;
            try {
                System.out.println(program);
                program = stage.process(program);
                System.out.println(program);
            } catch (NildumuError | ClassCastException err) {
                System.err.println("---- prior to state ---");
                System.err.println(program);
                System.err.println("----");
                throw new NildumuError(err);
            }
        }
        Parser.ProgramNode programNode = Parser.parse(program);
        new NameResolution(programNode).resolve();
        return programNode;
    }

}

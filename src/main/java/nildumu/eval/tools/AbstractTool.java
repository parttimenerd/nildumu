package nildumu.eval.tools;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import nildumu.NildumuError;
import nildumu.eval.*;

import static java.util.function.Function.identity;

/**
 * Abstract class for analysis tools.
 */
public abstract class AbstractTool implements Comparable<AbstractTool> {

    public static final int DEFAULT_UNWIND = 32;
    public final String name;
    public final int unwind;
    public final String fileEnding;

    protected AbstractTool(String name, int unwind, String fileEnding) {
        this.name = name;
        this.unwind = unwind;
        this.fileEnding = fileEnding;
        check();
    }

    /**
     * Create a packet and place all generated files into the passed folder.
     */
    public abstract AnalysisPacket createPacket(TestProgram program, Path folder);

    protected Path writeOrDie(Path folder, String filename, String content){
        Path path = folder.resolve(filename);
        try {
            Files.write(path, Collections.singleton(content));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
        return path;
    }

    @Override
    public String toString() {
        return name;
    }

    public static List<AbstractTool> getDefaultTools(int... unwindLimits){
        return Arrays.stream(unwindLimits).mapToObj(unwind -> Stream.of(new Nildumu(unwind), new ApproxFlow(unwind)))
                .flatMap(identity()).collect(Collectors.toList());
    }

    @Override
    public int compareTo(AbstractTool o) {
        return name.compareTo(o.name);
    }

    public static void checkExistence(Path path) {
        if (!path.toFile().exists()) {
            throw new NildumuError(String.format("%s does not exist, with pwd %s", path, System.getProperty("user.dir")));
        }
    }

    /** check that the required files are present */
    public abstract void check();
}

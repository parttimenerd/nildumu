package nildumu.solver;

import nildumu.util.InputStreamWithActionOnClose;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PMSATSolverImpl<V> extends PMSATSolver<V> {


    private final Path binary;
    private final String options;

    /**
     * Creates a new instance
     *
     * @param maximize maximize the weight?
     */
    public PMSATSolverImpl(Path binary, String options, boolean maximize) {
        super(maximize, true);
        this.binary = binary;
        this.options = options;
        if (!binary.toFile().exists()) {
            System.err.println(String.format("Path %s does not exist", binary));
        }
    }

    public PMSATSolverImpl(String binaryPathInSolversFolder, String options, boolean maximize) {
        this(Paths.get(String.format("dist/solvers/%s", binaryPathInSolversFolder)), options, maximize);
    }

    @Override
    public InputStreamReader solveAndRead() {
        File file;
        try {
            file = Files.createTempFile("pmsat", "wdimacs").toFile();
            file.deleteOnExit();
            writeInWDIMACSFormat(new FileWriter(file));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot write into tmp file");
            return null;
        }
        String message = "Unable to run solver, be sure to run the download_solvers script";
        try {
            List<String> params = new ArrayList<>();
            params.add(binary.toString());
            if (options.length() > 0) {
                params.addAll(Arrays.asList(options.split(" ")));
            }
            params.add(file.getAbsolutePath());
            Process proc = new ProcessBuilder().command(params.toArray(new String[0])).start();
            return new InputStreamReader(new InputStreamWithActionOnClose(proc.getInputStream(), file::delete));
        } catch (IOException ex){
            System.err.println(ex.getMessage());
            System.err.println(message);
        }
        return null;
    }
}

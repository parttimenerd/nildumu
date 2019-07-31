package nildumu.solver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Uses the RC2 solver from https://github.com/pysathq/pysat
 *
 * Be sure to install the python package first. {@code pip3 install python-sat}
 */
public class RC2Solver<V, E> extends PMSAT<V, E> {

    /**
     * Creates a new instance
     *
     * @param maximize maximize the weight?
     * @param roundUp  round up the weight (for solvers that do only allow integer weights, by default
     */
    public RC2Solver(boolean maximize) {
        super(maximize, true);
    }

    @Override
    public InputStreamReader solveAndRead() {
        File file;
        try {
            file = Files.createTempFile("pmsat", "wdimacs").toFile();
            writeInWDIMACSFormat(new FileWriter(file));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot write into tmp file");
            return null;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("python3", "-c", "import pysat.examples.rc2; print(pysat.examples.rc2.__file__)");
            Process proc = builder.start();
            proc.waitFor();
            String rc2 = new BufferedReader(new InputStreamReader(proc.getInputStream())).readLine().trim();
            proc = new ProcessBuilder().command("python3", rc2, "").start();
            return new InputStreamReader(proc.getInputStream());
        } catch (IOException | InterruptedException ex){
            ex.printStackTrace();
            System.err.println("Unable to run pysat, install it via 'python3 install python-sat'");
        }
        return null;
    }
}

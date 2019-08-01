package nildumu.solver;

import java.io.*;
import java.nio.file.Files;

/**
 * Uses the RC2 solver from https://github.com/pysathq/pysat
 *
 * Be sure to install the python package first. {@code pip3 install python-sat}
 */
public class RC2Solver<V> extends PMSATSolver<V> {

    /**
     * Creates a new instance
     *
     * @param maximize maximize the weight?
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
            Process proc = new ProcessBuilder().command("python3", "dist/rc2.py", file.getAbsolutePath()).start();
            if (proc.waitFor() > 0){
                System.err.println("Unable to run pysat, install it via 'python3 install python-sat' and ensure " +
                        "that the current working directory of this process is the project main directory");
                return null;
            }
            return new InputStreamReader(proc.getInputStream());
        } catch (IOException | InterruptedException ex){
            ex.printStackTrace();
            System.err.println("Unable to run pysat, install it via 'python3 install python-sat'");
        }
        return null;
    }
}

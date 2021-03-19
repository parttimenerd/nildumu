package nildumu.solver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;

/**
 * Uses the OpenWBO solver
 *
 * Be sure to place the content of https://maxsat-evaluations.github.io/2018/mse18-solver-src/complete/Open-WBO.zip
 * into the dist folder
 */
public class OpenWBOSolver<V> extends PMSATSolver<V> {

    /**
     * Creates a new instance
     *
     * @param maximize maximize the weight?
     */
    public OpenWBOSolver(boolean maximize) {
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
        String message = "Unable to run OpenWBO, be sure to place the content of " +
                "https://maxsat-evaluations.github.io/2018/mse18-solver-src/complete/Open-WBO.zip into the " +
                "dist folder and to run this tool in the main project directory";
        try {
            System.err.println("Running solver with formula" + file.getAbsolutePath());
            ProcessBuilder builder = new ProcessBuilder();
            Process proc = new ProcessBuilder().command("dist/Open-WBO/bin/open-wbo-gluc", file.getAbsolutePath()).start();
            proc.waitFor();
            return new InputStreamReader(proc.getInputStream());
        } catch (IOException | InterruptedException ex){
            System.err.println(ex.getMessage());
            System.err.println(message);
        }
        return null;
    }
}

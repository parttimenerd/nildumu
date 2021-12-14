package nildumu.eval.tools;

import nildumu.eval.*;
import swp.util.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static nildumu.util.Util.p;

/**
 * Uses the ApproxFlow tool (of "Scalable Approximation of Quantitative Information Flow in Programs")
 * from https://github.com/approxflow/approxflow/ (actually the modified version from
 * https://github.com/parttimenerd/approxflow/)
 * with the modified CBMC from https://github.com/parttimenerd/cbmc/
 */
public class ModifiedApproxFlow extends ApproxFlow {
    public ModifiedApproxFlow() {
        super("MApproxFlow");
    }

    public ModifiedApproxFlow(int unwindLimit) {
        super("MApproxFlow", unwindLimit);
    }

    public ModifiedApproxFlow(int unwindLimit, double epsilon, double delta) {
        super("MApproxFlow", unwindLimit, epsilon, delta);
    }

    @Override
    public void check() {
        super.check();
        checkExistence(getCBMC());
    }

    public Path getCBMC() {
        return Paths.get("bin/modified_cbmc/build/bin/cbmc").toAbsolutePath();
    }

    @Override
    public List<Pair<String, Object>> getEnvVariables() {
        // see explanation in README of modified CBMC
        return Arrays.asList(p("UNWIND", unwind + 2), p("REC", unwind - 1), p("CBMC", getCBMC()));
    }
}
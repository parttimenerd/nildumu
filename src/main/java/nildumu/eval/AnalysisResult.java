package nildumu.eval;

import java.time.Duration;

public class AnalysisResult {

    public final boolean valid;
    public final float leakage;
    public final Duration runtime;

    public AnalysisResult(boolean valid, float leakage, Duration runtime) {
        this.valid = valid;
        this.leakage = leakage;
        this.runtime = runtime;
    }

    public float getLeakage() {
        return leakage;
    }

    public Duration getRuntime() {
        return runtime;
    }

    public boolean isValid() {
        return valid;
    }
}

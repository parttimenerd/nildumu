package nildumu.eval;

import java.time.Duration;

public class AnalysisResult {

    public final boolean valid;
    public final float leakage;
    public final Duration runtime;
    public final boolean hasTimeout;

    public AnalysisResult(boolean valid, float leakage, Duration runtime, boolean hasTimeout) {
        this.valid = valid;
        this.leakage = leakage;
        this.runtime = runtime;
        this.hasTimeout = hasTimeout;
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

    public boolean hasTimeout(){
        return hasTimeout;
    }
}

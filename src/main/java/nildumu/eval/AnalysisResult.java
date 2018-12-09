package nildumu.eval;

import java.time.Duration;

public class AnalysisResult {

    public final boolean valid;
    public final float leakage;
    public final Duration runtime;
    public final boolean hasTimeout;
    public final float leakageStddev;
    public final float runtimeStddev;

    public AnalysisResult(boolean valid, float leakage, Duration runtime, boolean hasTimeout){
        this(valid, leakage, runtime, hasTimeout, 0f, 0f);
    }

    public AnalysisResult(boolean valid, float leakage, Duration runtime, boolean hasTimeout, float leakageStddev, float runtimeStddev) {
        this.valid = valid;
        this.leakage = leakage;
        this.runtime = runtime;
        this.hasTimeout = hasTimeout;
        this.leakageStddev = leakageStddev;
        this.runtimeStddev = runtimeStddev;
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

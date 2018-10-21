package nildumu.eval;

import java.util.*;
import java.util.stream.Collectors;

public class AnalysisResults {
    public final Map<AnalysisPacket, AnalysisResult> results;

    public AnalysisResults(Map<AnalysisPacket, AnalysisResult> results) {
        this.results = Collections.unmodifiableMap(results);
    }

    @Override
    public String toString() {
        return results.keySet().stream().sorted(Comparator.comparing(p -> p.tool.toString()))
                .map(p -> String.format("%40s, %10.5f",p.tool.name, results.get(p)))
                .collect(Collectors.joining("\n"));
    }

    public AggregatedAnalysisResults aggregate(){
        return new AggregatedAnalysisResults(this);
    }
}

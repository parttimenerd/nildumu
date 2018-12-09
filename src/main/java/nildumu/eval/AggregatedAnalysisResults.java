package nildumu.eval;

import org.apache.commons.lang3.StringEscapeUtils;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import nildumu.eval.tools.AbstractTool;

public class AggregatedAnalysisResults {

    public static final AnalysisResultFormatter LEAKAGE =
            r -> {
                if (r.hasTimeout){
                    return "(timeout)";
                }
                if (!r.isValid()){
                    return "-";
                }
                return String.format("%.3f +-%.3f", r.leakage, r.leakageStddev);
            };
    public static final AnalysisResultFormatter RUNTIME =
            r -> {
                if (r.hasTimeout){
                    return "(timeout)";
                }
                if (!r.isValid()){
                    return "-";
                }
                long seconds = r.runtime.getSeconds();
                long millis = r.runtime.toMillis() - seconds * 1000;
                return String.format("%d.%03ds +-%.3f", seconds, millis, r.runtimeStddev);
            };

    public final Map<AbstractTool, Map<TestProgram, AnalysisResult>> perTool;
    public final Map<TestProgram, Map<AbstractTool, AnalysisResult>> perProgram;

    public AggregatedAnalysisResults(AnalysisResults results) {
        perTool = results.results.entrySet().stream()
                .collect(Collectors.groupingBy(e -> e.getKey().tool,
                        Collectors.toMap(e -> e.getKey().program, Map.Entry::getValue)));
        perProgram = results.results.entrySet().stream()
                .collect(Collectors.groupingBy(e -> e.getKey().program,
                        Collectors.toMap(e -> e.getKey().tool, Map.Entry::getValue)));
    }

    public String toStringPerTool(AnalysisResultFormatter formatter){
        return toString(perTool, formatter);
    }

    public String toStringPerProgram(AnalysisResultFormatter formatter){
        return toString(perProgram, formatter);
    }

    /**
     * Returns a string representation of the sorted table in a CSV format
     *
     * @param map table
     * @param formatter call formatter
     * @param <A> row type
     * @param <B> column type
     * @return csv representation
     */
    public static <A extends Comparable<A>,
            B extends Comparable<B>> String toString(Map<A, Map<B, AnalysisResult>> map,
                                                     AnalysisResultFormatter formatter){
        List<List<String>> lines = new ArrayList<>();
        List<A> sortedRowHeaders = map.keySet().stream()
                .sorted().collect(Collectors.toList());
        sortedRowHeaders.forEach(h -> lines.add(new ArrayList<>()));
        lines.add(new ArrayList<>());
        List<B> sortedColumnHeaders = map.get(sortedRowHeaders.get(0)).keySet().stream()
                .sorted().collect(Collectors.toList());

        Consumer<List<String>> addColumn = col -> {
            List<String> escaped = col.stream()
                    .map(StringEscapeUtils::escapeCsv)
                    .collect(Collectors.toList());
            int maxWidth = escaped.stream().mapToInt(String::length).max().getAsInt();
            for (int i = 0; i < lines.size(); i++) {
                lines.get(i).add(String.format("%" + maxWidth + "s", escaped.get(i)));
            }
        };

        BiConsumer<String, List<String>> addColumnWHeader = (header, col) -> {
            addColumn.accept(
                    Stream.concat(Stream.of(header), col.stream())
                    .collect(Collectors.toList()));
        };

        // add the row header column
        addColumnWHeader.accept("",
                sortedRowHeaders.stream()
                        .map(Object::toString)
                        .collect(Collectors.toList()));
        // add all the other columns
        for (B columnHeader : sortedColumnHeaders) {
            addColumnWHeader.accept(columnHeader.toString(),
                    sortedRowHeaders.stream()
                            .map(map::get)
                            .map(m -> m.get(columnHeader))
                            .map(r -> {
                                return formatter.format(r);
                            })
                            .collect(Collectors.toList()));
        }
        return lines.stream()
                .map(cols -> String.join(",",cols))
                .collect(Collectors.joining("\n"));
    }
}

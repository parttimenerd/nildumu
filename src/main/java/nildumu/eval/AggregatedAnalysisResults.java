package nildumu.eval;

import com.kitfox.svg.A;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciithemes.TA_GridThemes;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import nildumu.eval.tools.AbstractTool;
import org.apache.commons.lang3.StringUtils;
import swp.util.Utils;

public class AggregatedAnalysisResults {

    public static final AnalysisResultFormatter LEAKAGE =
            r -> {
                if (r == null) {
                    return "";
                }
                if (r.hasTimeout) {
                    return "(timeout)";
                }
                if (!r.isValid()) {
                    return "-";
                }
                return String.format("%.3f +-%02.01f", r.leakage, r.leakageStddev * 100) + "%";
            };
    public static final AnalysisResultFormatter RUNTIME =
            r -> {
                if (r == null) {
                    return "";
                }
                if (r.hasTimeout) {
                    return "(timeout)";
                }
                if (!r.isValid()) {
                    return "-";
                }
                long seconds = r.runtime.getSeconds();
                long millis = r.runtime.toMillis() - seconds * 1000;
                return String.format("%d.%03ds +-%02.01f", seconds, millis, r.runtimeStddev * 100) + "%";
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

    enum Mode {
        NICE,
        CSV
    }

    public String toStringPerTool(AnalysisResultFormatter formatter, Mode... modes) {
        return toString(perTool, formatter, modes);
    }

    public String toStringPerProgram(AnalysisResultFormatter formatter, Mode... modes) {
        return toString(perProgram, formatter, modes);
    }

    static class Expected {
        public final String program;
        public final float expected;
        public final float maximum;

        Expected(String program, float expected, float maximum) {
            this.program = program;
            this.expected = expected;
            this.maximum = maximum;
        }
    }

    static Map<String, Expected> loadExpected(Path file) throws IOException {
        return Files.lines(file).map(line -> {
            String[] parts = line.split(", ?");
            return new Utils.Triple<>(parts[0], Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
        }).collect(Collectors.toMap(t -> t.first, t -> new Expected(t.first, t.second, t.third)));
    }

    enum LaTexMode {
        LEAKAGE,
        RUNTIME
    }

    public String toLaTexTable(LaTexMode mode, Map<String, Expected> expected, float allowedDeviation) {
        Function<Float, String> f = x -> StringUtils.leftPad(String.format("%.1f", x), 5);
        List<List<String>> lines = new ArrayList<>();
        List<AbstractTool> tools = perProgram.values().iterator().next().keySet().stream().sorted().collect(Collectors.toList());
        lines.addAll(perProgram.keySet().stream().sorted().map(program -> {
            Map<AbstractTool, AnalysisResult> resultMap = perProgram.get(program);
            if (mode == LaTexMode.LEAKAGE) {
                Optional<Expected> exp = Optional.ofNullable(expected.getOrDefault(program.name, null));
                List<String> line = new ArrayList<>(Arrays.asList(program.name, f.apply(exp.map(e -> e.expected).orElse(-1f)), f.apply(exp.map(e -> e.maximum).orElse(-1f))));
                for (AbstractTool tool : tools) {
                    float value = resultMap.get(tool).leakage;
                    char mod = exp.map(e -> {
                        if (e.expected + allowedDeviation >= value && e.expected - allowedDeviation <= value) {
                            return 'n';
                        } else if (e.expected + allowedDeviation < value) {
                            return 'o';
                        } else if (e.expected - allowedDeviation > value) {
                            return 'u';
                        }
                        return 'n';
                    }).orElse('n');
                    line.add(String.format("\\%sa{%s}", mod, f.apply(value)));
                }
                return line;
            } else {
                List<String> line = new ArrayList<>(Collections.singletonList(program.name));
                for (AbstractTool tool : tools) {
                    float value = resultMap.get(tool).runtime.toMillis() / 1000.0f;
                    line.add(f.apply(value));
                }
                return line;
            }
        }).collect(Collectors.toList()));
        List<String> result = new ArrayList<>();
        List<String> header = new ArrayList<>(mode == LaTexMode.LEAKAGE ? Arrays.asList("", "$I$ [bit]", "$I_{max}$ [bit]") : Collections.singletonList(""));
        header.addAll(tools.stream().map(AbstractTool::toString).collect(Collectors.toList()));
        result.add(String.join(" & ", header));
        for (List<String> line : lines) {
            String res = StringUtils.rightPad(line.get(0), 30) + "&" + line.subList(1, line.size()).stream().map(l -> String.format("%10s", l)).collect(Collectors.joining(" & "));
            result.add(res);
        }
        return String.join("\\\\\n", result);
    }

    /**
     * Returns a string representation of the sorted table in a CSV format
     *
     * @param map       table
     * @param formatter call formatter
     * @param <A>       row type
     * @param <B>       column type
     * @return csv representation
     */
    public static <A extends Comparable<A>,
            B extends Comparable<B>> String toString(Map<A, Map<B, AnalysisResult>> map,
                                                     AnalysisResultFormatter formatter, Mode... modes) {
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
        return Arrays.stream(modes).map(mode -> {
            if (mode == Mode.NICE) {
                AsciiTable at = new AsciiTable();
                at.setTextAlignment(TextAlignment.RIGHT);
                at.addRule();
                for (List<String> line : lines) {
                    at.addRow(line);
                    at.addRule();
                }
                at.setTextAlignment(TextAlignment.RIGHT);
                at.getContext().setGridTheme(TA_GridThemes.TOPBOTTOM);
                return at.render(150);
            } else {
                return lines.stream()
                        .map(cols -> String.join(",", cols))
                        .collect(Collectors.joining("\n"));
            }
        }).collect(Collectors.joining("\n\n"));
    }
}

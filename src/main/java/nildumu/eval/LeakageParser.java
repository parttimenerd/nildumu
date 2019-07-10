package nildumu.eval;

import java.util.*;
import java.util.stream.Collectors;

import nildumu.eval.tools.AbstractTool;

/**
 * Parser of the output of leakage analysis tools
 */
@FunctionalInterface
public interface LeakageParser {
    float parse(String out, String err) throws LeakageParserException;

    static LeakageParser forLine(AbstractTool tool, String lineStart, String lineEnd){
        return (out, err) -> {
            String combined = out + "\n" + err;
            List<String> leakLines = Arrays.stream(combined.split("\n"))
                    .filter(l -> l.startsWith(lineStart) && l.endsWith(lineEnd))
                    .map(l -> l.substring(lineStart.length(),
                            l.length() - lineEnd.length())).collect(Collectors.toList());
            if (leakLines.size() > 0){
                String leakLine = leakLines.get(leakLines.size() - 1);
                try {
                    return Float.parseFloat(leakLine);
                } catch (NumberFormatException ex) {}
            }
            throw new LeakageParserException(tool, out, err);
        };
    }

    static LeakageParser forLinePart(AbstractTool tool, String start, String end){
        return (out, err) -> {
            String combined = out + "\n" + err;
            List<String> leakLines = Arrays.stream(combined.split("\n"))
                    .filter(l -> l.contains(start) && l.contains(end))
                    .filter(l -> l.indexOf(start) < l.indexOf(end) - 1)
                    .map(l -> l.substring(l.indexOf(start) + start.length(),
                            l.indexOf(end))).collect(Collectors.toList());
            if (leakLines.size() > 0){
                String leakLine = leakLines.get(leakLines.size() - 1);
                try {
                    return Float.parseFloat(leakLine);
                } catch (NumberFormatException ex) {}
            }
            throw new LeakageParserException(tool, out, err);
        };
    }
}

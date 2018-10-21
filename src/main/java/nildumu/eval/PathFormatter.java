package nildumu.eval;

import java.nio.file.Path;

@FunctionalInterface
public interface PathFormatter {
    public String format(Path path);
}

package nildumu.eval;

import java.nio.file.Path;

@FunctionalInterface
public interface PathFormatter {
    String format(Path path);
}

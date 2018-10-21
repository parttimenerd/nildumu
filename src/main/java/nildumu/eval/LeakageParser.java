package nildumu.eval;

@FunctionalInterface
public interface LeakageParser {
    public float parse(String out, String errs);
}

package nildumu.eval;

import nildumu.eval.tools.AbstractTool;

/**
 * Exceptions thrown if a leakage parser found no leakage information
 */
public class LeakageParserException extends Exception {
    public LeakageParserException(AbstractTool tool, String out, String err){
        super(String.format("No leakage information in the output of tool %s\nOUT: %S\nERR: %s",
                tool.name, out, err));
    }
}

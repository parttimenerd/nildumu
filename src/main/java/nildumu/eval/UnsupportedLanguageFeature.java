package nildumu.eval;

import nildumu.eval.tools.AbstractTool;

public class UnsupportedLanguageFeature extends RuntimeException {
    public UnsupportedLanguageFeature(AbstractTool tool, String feature){
        super(String.format("%s does not support %s", tool.name, feature));
    }
}

package nildumu.eval;

import nildumu.eval.tools.AbstractTool;

public class UnsupportedLanguageFeatureException extends RuntimeException {
    public UnsupportedLanguageFeatureException(AbstractTool tool, String feature){
        super(String.format("%s does not support %s", tool.name, feature));
    }
}

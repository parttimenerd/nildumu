package swp.lexer;

import java.util.*;

import swp.LocatedSWPException;
import swp.util.Utils;

/**
 * Created by parttimenerd on 03.08.16.
 */
public class LexerError extends LocatedSWPException {

	public LexerError(Token errorToken, String message, String source) {
		super(errorToken, message, source);
	}

	public static LexerError create(Token errorToken, Collection<Integer> expectedTokens, String source){
		StringBuilder builder = new StringBuilder();
        List<Integer> list = new ArrayList<>(expectedTokens);
		builder.append(errorToken.terminalSet.typesToString(list));
		String errorTokenStr = "";
		if (errorToken.type >= Utils.MIN_CHAR && errorToken.type <= Utils.MAX_CHAR) {
			errorTokenStr = errorToken.terminalSet.typeToString(errorToken.type);
		} else {
			errorTokenStr = "<unsupported character " + (char) (errorToken.type + Utils.MIN_CHAR) + ">";
		}
		return new LexerError(errorToken, String.format("Expected one of %s but got %s at %s", builder.toString(), errorTokenStr, errorToken.location), source);
	}
}

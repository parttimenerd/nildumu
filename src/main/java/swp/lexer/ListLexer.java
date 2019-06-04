package swp.lexer;

import java.util.List;

/**
 * Created by parttimenerd on 29.07.16.
 */
public class ListLexer extends BufferingLexer {

	private List<Token> tokens;
	private String source;

	public ListLexer(TerminalSet terminalSet, int[] ignoredTokenTypes, List<Token> tokens, String source){
		super(terminalSet, ignoredTokenTypes);
		this.tokens = tokens;
		this.source = source;
	}

	public ListLexer(TerminalSet terminalSet, String[] ignoredTokenTypes, List<Token> tokens, String source){
		super(terminalSet, new int[]{});
		this.tokens = tokens;
		this.source = source;
		for (String ignored : ignoredTokenTypes){
			ignore(terminalSet.stringToType(ignored));
		}
	}

	@Override
	protected void initTokens() {
		for (Token token : tokens){
			addTokenIfNotIgnored(token);
		}
	}

	@Override
	public String getSource() {
		return source;
	}
}

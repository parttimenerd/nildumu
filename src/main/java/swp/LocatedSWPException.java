package swp;

import swp.lexer.*;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LocatedSWPException extends SWPException {

	private static final int SHOWN_CONTEXT_LINES = 3;

	public final String message;
	public final String source;
	public final Token errorToken;
	public final Location errorLocation;

	public LocatedSWPException(Token errorToken, String message, String source) {
		super(generateLongMessage(errorToken, message, source));
		this.message = message;
		this.source = source;
		this.errorToken = errorToken;
		if (errorToken != null) {
			this.errorLocation = errorToken.location;
		} else {
			this.errorLocation = new Location(0, 0);
		}
	}

	static String generateLongMessage(Token errorToken, String message, String source){
		String[] lines = source.split("\n");
		int startContextIndex = Math.max(0, errorToken.location.line - SHOWN_CONTEXT_LINES);
		int endContextIndex = errorToken.location.line;
		String[] shownLines = Arrays.copyOfRange(lines, startContextIndex, endContextIndex);
		String shown = IntStream.range(0, shownLines.length)
				.mapToObj(i -> String.format("%5d | %s", i + startContextIndex, shownLines[i]))
				.collect(Collectors.joining("\n"));
		String arrow = "        " + IntStream.range(1, errorToken.location.column + ((errorToken.value.length() + 1) / 2)).mapToObj(i -> "—")
				.collect(Collectors.joining("")) + "^";
		return message + "\n" + shown + "\n" + arrow;
	}
}

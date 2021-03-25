package swp.parser.lr;

import java.io.*;
import java.util.*;

import swp.grammar.*;
import swp.lexer.TerminalSet;
import swp.util.Pair;

/**
 * Created by parttimenerd on 15.05.16.
 */
public class LRParserTable implements Serializable {

	public final Grammar grammar;

	public static class RawTables implements Serializable {
		/**
		 * Mapping of non terminal to next state (for each state).
		 */
		public List<Map<NonTerminal, Integer>> gotoTable;

		public Map<Integer, Pair<NonTerminal, Integer>> productionInformation;

		/**
		 * Mapping of terminal id to actions for each state.
		 */
		public List<Map<Integer, LRParserTable.Action>> actionTable;

		public Map<Integer, Integer> prodToReductionId = new HashMap<>();
		
		public int[] _ignoredTerminals = new int[0];

		public RawTables(List<Map<NonTerminal, Integer>> gotoTable, Map<Integer, Pair<NonTerminal, Integer>> productionInformation,
						 List<Map<Integer, LRParserTable.Action>> actionTable) {
			this.gotoTable = gotoTable;
			this.productionInformation = productionInformation;
			this.actionTable = actionTable;
		}

		public static RawTables load(File file) throws IOException, ClassNotFoundException {
			try (ObjectInput oi = new ObjectInputStream(new FileInputStream(file))) {
				return (RawTables) oi.readObject();
			}
		}

		public void store(File file) throws IOException {
			try (ObjectOutput oo = new ObjectOutputStream(new FileOutputStream(file))) {
				oo.writeObject(this);
			}
		}
	}

	public RawTables rt;


	public LRParserTable(Grammar grammar,
	                     RawTables rt) {
		this.grammar = grammar;
		this.rt = rt;
		this.rt.prodToReductionId.putAll(grammar.prodToReductionId);
	}

	public LRParserTable(Grammar grammar){
		this(grammar, new RawTables(new ArrayList<>(),
				new HashMap<>(), new ArrayList<>()));
	}

	public static class Action implements Serializable {

		public String name() {
			return "";
		}
	}

	public static class ShiftAction extends Action {

		public final int stateToBeShifted;

		public ShiftAction(int stateToBeShifted) {
			this.stateToBeShifted = stateToBeShifted;
		}

		@Override
		public String toString() {
			return "shift(" + stateToBeShifted + ")";
		}

		@Override
		public String name() {
			return "shift";
		}
	}

	public static class ReduceAction extends Action {

		public final int productionId;

		public ReduceAction(int productionId) {
			this.productionId = productionId;
		}

		@Override
		public String toString() {
			return "reduce(" + productionId + ")";
		}

		@Override
		public String name() {
			return "reduce";
		}
	}

	public static class Accept extends Action {

		@Override
		public String toString() {
			return "accept()";
		}

		@Override
		public String name() {
			return "accept";
		}
	}

	private void initState(int state){
		while (state >= rt.actionTable.size()){
			int s = rt.actionTable.size();
			rt.actionTable.add(new HashMap<>());
			rt.gotoTable.add(new HashMap<>());
		}
	}

	private void insert(State state, Terminal terminal, Action action){
		initState(state.id);
		Map<Integer, Action> row = rt.actionTable.get(state.id);
		if (row.containsKey(terminal.id)){
			Action cur = row.get(terminal.id);
			printError(state, terminal, action, cur);
			row.put(terminal.id, chooseAtError(cur, action));
		} else {
			row.put(terminal.id, action);
		}
	}

	private void printError(State state, Terminal terminal, Action action1, Action action2){
		//System.err.printf("Something is wrong in state %d at terminal %s: %s %s conflict\n", state.id, terminal, action1.name(),
		//		action2.name());
	}

	private Action chooseAtError(Action action1, Action action2){
		if (action1 instanceof ShiftAction){
			return action1;
		}
		if (action2 instanceof ShiftAction){
			return action2;
		}
		return action1;
	}

	public void addShift(State state, Terminal terminal, State newState){
		insert(state, terminal, new ShiftAction(newState.id));
	}

	public void addReduce(State state, Terminal terminal, Production production){
		rt.productionInformation.put(production.id, new Pair<>(production.left, production.rightSize()));
		insert(state, terminal, new ReduceAction(production.id));
	}

	public void addAccept(State state, Terminal terminal){
		insert(state, terminal, new Accept());
	}

	public void addGoto(State state, NonTerminal nonTerminal, State newState){
		initState(state.id);
		rt.gotoTable.get(state.id).put(nonTerminal, newState.id);
	}

	public String toString(TerminalSet set) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < rt.actionTable.size(); i++){
			if (i != 0){
				builder.append("\n");
			}
			builder.append(String.format("State = %5d: ", i));
			builder.append(" Actions = [");
			Map<Integer, Action> row = rt.actionTable.get(i);
			Integer[] keys = new Integer[row.keySet().size()];
			row.keySet().toArray(keys);
			Arrays.sort(keys);
			for (int j = 0; j < keys.length; j++){
				if (j != 0){
					builder.append(", ");
				}
				builder.append(set.typeToString(keys[j]));
				builder.append(" = ");
				builder.append(row.get(keys[j]));
			}
			builder.append("] GOTO = ");
			builder.append(rt.gotoTable.get(i));
		}
		return builder.toString();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < rt.actionTable.size(); i++){
			if (i != 0){
				builder.append("\n");
			}
			builder.append(String.format("State = %5d: ", i));
			builder.append(" Actions = ");
			builder.append(rt.actionTable.get(i));
			builder.append(" GOTO = ");
			builder.append(rt.gotoTable.get(i));
		}
		return builder.toString();
	}
}

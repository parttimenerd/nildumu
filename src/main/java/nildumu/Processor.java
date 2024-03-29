package nildumu;

import nildumu.mih.MethodInvocationHandler;
import nildumu.util.DefaultMap;
import swp.util.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nildumu.Lattices.*;
import static nildumu.Lattices.B.*;
import static nildumu.Operator.*;
import static nildumu.Parser.*;
import static nildumu.util.Util.p;

public class Processor {

    public static boolean transformPlus = false;

    public static final int TRANSFORM_PLUS           = 0b000001;
    public static final int TRANSFORM_LOOPS          = 0b000010;
    public static final int RECORD_ALTERNATIVES      = 0b000100;
    public static final int USE_SIMPLIFIED_HEURISTIC = 0b001000;
    public static final int USE_REPLACEMENTS         = 0b010000;

    public static boolean containsOpt(int opts, int opt) {
        return (opts & opt) != 0;
    }

    public static Context process(String program) {
        return process(program, Context.Mode.BASIC);
    }

    public static Context process(String program, Context.Mode mode) {
        return process(program, mode, MethodInvocationHandler.createDefault(), 0);
    }

    public static Context process(String program, Context.Mode mode, MethodInvocationHandler handler, int opts) {
        return process(Parser.process(program, containsOpt(opts, TRANSFORM_PLUS)),
                mode, handler, containsOpt(opts, RECORD_ALTERNATIVES), containsOpt(opts, USE_SIMPLIFIED_HEURISTIC),
                containsOpt(opts, USE_REPLACEMENTS));
    }

    public static Context process(ProgramNode node, MethodInvocationHandler handler) {
        handler.setup(node);
        return process(node.context.forceMethodInvocationHandler(handler), node);
    }

    public static Context process(ProgramNode node, Context.Mode mode, MethodInvocationHandler handler) {
        return process(node, mode, handler, false, true, true);
    }

    public static Context process(ProgramNode node, Context.Mode mode, MethodInvocationHandler handler,
                                  boolean recordAlternatives, boolean useSimplifiedHeuristic, boolean useReplacements) {
        node.context.mode(mode).setRecordAlternatives(recordAlternatives).setUseSimplifiedHeuristic(useSimplifiedHeuristic).setUseReplacements(useReplacements);
        handler.setup(node);
        return process(node.context.forceMethodInvocationHandler(handler), node);
    }

    public static Context process(Context context, MJNode node) {

        final Set<StatementNode> statementNodesToOmitOneTime = new HashSet<>();
        final Set<Pair<Sec<?>, Variable>> outputVariables = new HashSet<>();

        FixpointIteration.worklist2(new NodeVisitor<Boolean>() {

            /**
             * conditional bits with their assumed value for each conditional statement body
             */
            final Map<BlockNode, Pair<Bit, Bit>> conditionalBits = new HashMap<>();

            final Map<BlockNode, Context.Branch> branchOfBlock = new HashMap<>();

            int unfinishedLoopIterations = 0;

            final Map<MJNode, Value> oldValues = new HashMap<>();

            final Stack<Long> nodeValueUpdatesAtCondition = new Stack<>();

            final Map<WhileStatementNode, PrintHistory.HistoryEntry> historyPerWhile = new HashMap<>();

            final Map<MJNode, Long> lastUpdateCounts = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<MJNode, Long>() {
                @Override
                public Long defaultValue(Map<MJNode, Long> map, MJNode key) {
                    return 0L;
                }
            });

            final Map<MJNode, Long> lastUpdateWOAppendValuedCounts = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<MJNode, Long>() {
                @Override
                public Long defaultValue(Map<MJNode, Long> map, MJNode key) {
                    return 0L;
                }
            });

            boolean didValueChangeAndUpdate(MJNode node, Value newValue){
                if (oldValues.containsKey(node) && oldValues.get(node) == newValue){
                    return false;
                }
                oldValues.put(node, newValue);
                return true;
            }

            @Override
            public Boolean visit(MJNode node) {
                return false;
            }

            @Override
            public Boolean visit(ProgramNode program) {
                return false;
            }

            @Override
            public Boolean visit(VariableDeclarationNode decl) {
                context.setVariableValue(decl.definition, decl.hasInitExpression() ?
                        context.nodeValue(decl.expression) : (decl.hasAppendValue ? AppendOnlyValue.createEmpty() : vl.parse(0) /* todo: correct */), decl.expression);
                return false;
            }

            @Override
            public Boolean visit(VariableAssignmentNode assignment) {
                context.setVariableValue(assignment.definition, context.nodeValue(assignment.expression), assignment.expression);
                return false;
            }

            @Override
            public Boolean visit(ArrayAssignmentNode assignment) {
                throw new NildumuError("Array assignments not supported in processor. Requires preprocessing.");
            }

            @Override
            public Boolean visit(MultipleVariableAssignmentNode assignment) {
                List<Value> values = context.nodeValue(assignment.expression).split(assignment.definitions.size());
                for (int i = 0; i < values.size(); i++) {
                    context.setVariableValue(assignment.definitions.get(i), values.get(i), assignment.expression);
                }
                return false;
            }

            @Override
            public Boolean visit(OutputVariableDeclarationNode outputDecl) {
                visit((VariableAssignmentNode) outputDecl);
                outputVariables.add(p(context.sl.parse(outputDecl.secLevel), outputDecl.definition));
                return false;
            }

            @Override
            public Boolean visit(TmpInputVariableDeclarationNode inputDecl) {
                visit((VariableAssignmentNode)inputDecl);
                context.addInputValue(context.sl.parse(inputDecl.secLevel), inputDecl.expression, context.nodeValue(inputDecl.expression));
                return false;
            }

            @Override
            public Boolean visit(InputVariableDeclarationNode inputDecl) {
                visit((VariableAssignmentNode) inputDecl);
                context.addInputValue(context.sl.parse(inputDecl.secLevel), inputDecl.expression, context.nodeValue(inputDecl.expression));
                return false;
            }

            @Override
            public Boolean visit(IfStatementNode ifStatement) {
                Value cond = context.nodeValue(ifStatement.conditionalExpression);
                context.assertAdditionalModsEmpty();
                Bit condBit = cond.get(1);
                Lattices.B condVal = condBit.val();
                if (condVal == U && unfinishedLoopIterations > 0){
                    weightCondBit(condBit);
                }
                if (condVal == ONE || condVal == U) {
                    conditionalBits.put(ifStatement.ifBlock, new Pair<>(condBit, bl.create(ONE)));
                    branchOfBlock.put(ifStatement.ifBlock,
                            new Context.Branch(ifStatement.conditionalExpression, true));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.ifBlock);
                }
                if (condVal == ZERO || condVal == U) {
                    conditionalBits.put(ifStatement.elseBlock, new Pair<>(condBit, bl.create(ZERO)));
                    branchOfBlock.put(ifStatement.elseBlock,
                            new Context.Branch(ifStatement.conditionalExpression, false));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.elseBlock);
                }
                return didValueChangeAndUpdate(ifStatement, cond);
            }

            @Override
            public Boolean visit(BlockNode block) {
                if (conditionalBits.containsKey(block)){
                    Context.Branch branch = branchOfBlock.get(block);
                    context.pushBranch(branch);
                    context.initModsForBranch(branch);
                    nodeValueUpdatesAtCondition.push(context.getNodeVersionWOAppendValuedUpdateCount());
                }
                if (lastUpdateCounts.get(block) == context.getNodeVersionWOAppendValuedUpdateCount()) {
                    return false;
                }
                lastUpdateCounts.put(block, context.getNodeVersionWOAppendValuedUpdateCount());
                return true;
            }

            @Override
            public Boolean visit(WhileStatementNode whileStatement) {
                if (!context.inLoopMode()){
                    throw new NildumuError("while-statements are only supported in modes starting at loop mode");
                }
                Value cond = context.nodeValue(whileStatement.conditionalExpression);
                context.assertAdditionalModsEmpty();
                Bit condBit = cond.get(1);
                weightCondBit(condBit);
                if (cond.mightBe(true)) {
                    conditionalBits.put(whileStatement.body, new Pair<>(condBit, bl.create(ONE)));
                    branchOfBlock.put(whileStatement.body,
                            new Context.Branch(whileStatement.conditionalExpression, true));
                    unfinishedLoopIterations++;
                } else {
                    statementNodesToOmitOneTime.add(whileStatement.body);
                }
                PrintHistory.HistoryEntry newHist = createHistoryEntryForWhileStmt(whileStatement);
                PrintHistory.ReduceResult<Map<Variable, AppendOnlyValue>> reduceResult = reduceAppendOnlyVariables(newHist);

                // assumes that the first argument of the phi is the inner loop end SSA variable
                whileStatement.getPreCondVarAss().stream()
                        .filter(a -> a.definition.hasAppendValue)
                        .forEach(a -> Stream.of(a.definition, ((PhiNode)a.expression).joinedVariables.get(0).definition)
                                            .forEach(v -> {
                                                Value val = reduceResult.value.get(a.definition).clone();
                                                context.setVariableValue(v, val);
                                                context.nodeValue(a.expression, val);
                                            }));

                historyPerWhile.put(whileStatement, PrintHistory.HistoryEntry.create(reduceResult.value, newHist.prev, newHist));
                if (lastUpdateCounts.get(whileStatement) == context.getNodeVersionUpdateCount()) {
                    return false; // that is the common case without prints
                } else {
                    if (lastUpdateWOAppendValuedCounts.get(whileStatement) == context.getNodeVersionWOAppendValuedUpdateCount()
                    && reduceResult.finished()){
                        // this is the case if nothing else changes, except the append only variables
                        // basic idea: just create a star as for summary graphs
                        return false;
                    }
                    nodeValueUpdatesAtCondition.push(context.getNodeVersionUpdateCount());
                    lastUpdateCounts.put(whileStatement, context.getNodeVersionUpdateCount());
                    lastUpdateWOAppendValuedCounts.put(whileStatement, context.getNodeVersionWOAppendValuedUpdateCount());
                    return true;
                }
            }

            private PrintHistory.HistoryEntry createHistoryEntryForWhileStmt(WhileStatementNode whileStatement){
                Set<String> outerVars = node.accept(new NodeVisitor<Set<String>>() {
                    @Override
                    public Set<String> visit(MJNode node) {
                        return null;
                    }

                    @Override
                    public Set<String> visit(MethodNode method) {
                        return method.getVariablesDefinedOutside(whileStatement);
                    }

                    @Override
                    public Set<String> visit(BlockNode block) {
                        return block.getVariablesDefinedOutside(whileStatement);
                    }

                    @Override
                    public Set<String> visit(ProgramNode program) {
                        return visit(program.globalBlock);
                    }
                });
                Set<Bit> outsideBits = outerVars.stream() // includes the input bits
                        .flatMap(v -> context.getVariableValue(v).stream())
                        .filter(Bit::isAtLeastUnknown).collect(Collectors.toSet());
                outsideBits.addAll(context.getNewlyIntroducedInputs().getBits());
                return PrintHistory.HistoryEntry.create(whileStatement.getPreCondVarAss().stream()
                                .filter(a -> a.definition.hasAppendValue)
                                .collect(Collectors.toMap(a -> a.definition, a -> context.getVariableValue(a.variable).asAppendOnly().clone())),
                        historyPerWhile.containsKey(whileStatement) ? Optional.of(historyPerWhile.get(whileStatement)) : Optional.empty(),
                        v -> bl.reachableBits(v.bits, outsideBits));
            }

            private PrintHistory.ReduceResult<Map<Variable, AppendOnlyValue>>
                reduceAppendOnlyVariables(PrintHistory.HistoryEntry history) {
                return PrintHistory.ReduceResult.create(
                                history.map.keySet().stream().collect(Collectors.toMap(v -> v, v -> {
                                    return history.map.get(v).reduceAppendOnly(context::weight);
                                })));
            }


            @Override
            public Boolean visit(IfStatementEndNode ifEndStatement) {
                context.popBranch();
                return nodeValueUpdatesAtCondition.pop() != context.getNodeVersionWOAppendValuedUpdateCount();
            }

            @Override
            public Boolean visit(WhileStatementEndNode whileEndStatement) {
                unfinishedLoopIterations--;
                context.popBranch();
                return nodeValueUpdatesAtCondition.pop() != context.getNodeVersionUpdateCount();
            }

            @Override
            public Boolean visit(MethodInvocationNode methodInvocation) {
                System.out.printf("%s(%s)", methodInvocation.definition.name, methodInvocation.arguments.arguments.stream().map(context::nodeValue).map(Value::toString).collect(Collectors.joining(", ")));
                return false;
            }

            @Override
            public Boolean visit(ReturnStatementNode returnStatement) {
                if (returnStatement.hasReturnExpression()) {
                    context.setReturnValue(context.nodeValue(returnStatement.expression));
                }
                return false;
            }

            @Override
            public Boolean visit(VariableAccessNode variableAccess) {
                return false;
            }

            private void weightCondBit(Bit bit){
                bl.walkBits(bit, b -> {
                    if (bit.isAtLeastUnknown()){
                        context.weight(bit, Context.INFTY);
                    }
                    if (bit.value() != null && bit.value().node() != null){
                        MJNode node = bit.value().node();
                        if (node.getOperator() == EQUALS || node.getOperator() == UNEQUALS || node.getOperator() == LESS){
                            return;
                        }
                    }
                }, b -> false);
            }
        }, context::evaluate, node, statementNodesToOmitOneTime, b -> {
            if (b.operator == LexerTerminal.AND) {
                if (context.nodeValue(b.left).mightBe(true)) {
                    context.pushMiscMods(context.repl(context.nodeValue(b.left).get(1), bl.create(ONE)));
                    return true;
                }
                return false;
            }
            if (b.operator == LexerTerminal.OR) {
                if (context.nodeValue(b.left).mightBe(false)) {
                    context.pushMiscMods(context.repl(context.nodeValue(b.left).get(1), bl.create(ZERO)));
                    return true;
                }
                return false;
            }
            return true;
        }, b -> {
            if ((b.operator == LexerTerminal.AND && context.nodeValue(b.left).mightBe(true)) ||
                    (b.operator == LexerTerminal.OR && context.nodeValue(b.left).mightBe(false))) {
                context.popMiscMods();
            }
        });
        for (Pair<Sec<?>, Variable> pair : outputVariables) {
            context.addOutputValue(pair.first, context.getVariableValue(pair.second));
        }
        return context;
    }
}

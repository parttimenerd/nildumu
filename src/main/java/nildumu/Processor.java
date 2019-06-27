package nildumu;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import nildumu.util.DefaultMap;
import swp.util.Pair;

import static nildumu.Lattices.B.*;
import static nildumu.Lattices.*;
import static nildumu.Operator.EQUALS;
import static nildumu.Operator.LESS;
import static nildumu.Operator.UNEQUALS;
import static nildumu.Parser.*;

public class Processor {

    public static boolean transformPlus = false;

    public static Context process(String program){
        return process(program, Context.Mode.BASIC);
    }

    public static Context process(String program, Context.Mode mode){
        return process(program, mode, MethodInvocationHandler.createDefault());
    }

    public static Context process(String program, Context.Mode mode, MethodInvocationHandler handler){
        return process(program, mode, handler, transformPlus);
    }

    public static Context process(String program, Context.Mode mode, MethodInvocationHandler handler, boolean transformPlus){
        return process(Parser.process(program, transformPlus), mode, handler);
    }

    public static Context process(ProgramNode node, MethodInvocationHandler handler){
        handler.setup(node);
        return process(node.context.forceMethodInvocationHandler(handler), node);
    }

    public static Context process(ProgramNode node, Context.Mode mode, MethodInvocationHandler handler){
        node.context.mode(mode);
        handler.setup(node);
        return process(node.context.forceMethodInvocationHandler(handler), node);
    }

    public static Context process(Context context, MJNode node) {

        final Set<StatementNode> statementNodesToOmitOneTime = new HashSet<>();

        FixpointIteration.worklist2(new NodeVisitor<Boolean>() {

            /**
             * conditional bits with their assumed value for each conditional statement body
             */
            Map<BlockNode, Pair<Bit, Bit>> conditionalBits = new HashMap<>();

            Map<BlockNode, Context.Branch> branchOfBlock = new HashMap<>();

            int unfinishedLoopIterations = 0;

            final Map<MJNode, Value> oldValues = new HashMap<>();

            final Stack<Long> nodeValueUpdatesAtCondition = new Stack<>();

            final Map<WhileStatementNode, PrintHistory.HistoryEntry> historyPerWhile = new HashMap<>();

            final Map<MJNode, Long> lastUpdateCounts = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<MJNode, Long>() {
                @Override
                public Long defaultValue(Map<MJNode, Long> map, MJNode key) {
                    return 0l;
                }
            });

            final Map<MJNode, Long> lastUpdateWOAppendValuedCounts = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<MJNode, Long>() {
                @Override
                public Long defaultValue(Map<MJNode, Long> map, MJNode key) {
                    return 0l;
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
                        context.nodeValue(decl.expression) : (decl.hasAppendValue ? AppendOnlyValue.createEmpty() : Value.createEmpty()), decl.expression);
                return false;
            }

            @Override
            public Boolean visit(VariableAssignmentNode assignment) {
                context.setVariableValue(assignment.definition, context.nodeValue(assignment.expression), assignment.expression);
                return false;
            }

            @Override
            public Boolean visit(OutputVariableDeclarationNode outputDecl) {
                visit((VariableAssignmentNode)outputDecl);
                context.addOutputValue(context.sl.parse(outputDecl.secLevel), context.getVariableValue(outputDecl.definition));
                return false;
            }

            @Override
            public Boolean visit(TmpInputVariableDeclarationNode inputDecl) {
                visit((VariableAssignmentNode)inputDecl);
                context.addInputValue(context.sl.parse(inputDecl.secLevel), ((IntegerLiteralNode)inputDecl.expression).value);
                return false;
            }

            @Override
            public Boolean visit(IfStatementNode ifStatement) {
                Value cond = context.nodeValue(ifStatement.conditionalExpression);
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
                    Pair<Bit, Bit> bitPair = conditionalBits.get(block);
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
                System.out.println("###########" + context.getNodeVersionUpdateCount() + "  " + context.getNodeVersionWOAppendValuedUpdateCount());
                Value cond = context.nodeValue(whileStatement.conditionalExpression);
                Bit condBit = cond.get(1);
                Lattices.B condVal = condBit.val();
                weightCondBit(condBit);
                if (condVal == ONE || condVal == U) {
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
                    System.out.println("-> Return bla");
                    return false; // that is the common case without prints
                } else {
                    if (lastUpdateWOAppendValuedCounts.get(whileStatement) == context.getNodeVersionWOAppendValuedUpdateCount()
                    && reduceResult.finished()){
                        // this is the case if nothing else changes, except the append only variables
                        // basic idea: just create a star as for summary graphs
                        // TODO improve
                        System.out.println("-> Return");
                        return false;
                    }
                    System.out.println("-> Return bla2");
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
                outsideBits.addAll(innerLoopInputs(whileStatement));
                return PrintHistory.HistoryEntry.create(whileStatement.getPreCondVarAss().stream()
                                .filter(a -> a.definition.hasAppendValue)
                                .collect(Collectors.toMap(a -> a.definition, a -> context.getVariableValue(a.variable).asAppendOnly().clone())),
                        historyPerWhile.containsKey(whileStatement) ? Optional.of(historyPerWhile.get(whileStatement)) : Optional.empty(),
                        v -> bl.reachableBits(v.bits, outsideBits));
            }

            private Set<Bit> innerLoopInputs(WhileStatementNode whileStatement){
                return whileStatement.accept(new NodeVisitor<Set<Bit>>() {
                    @Override
                    public Set<Bit> visit(MJNode node) {
                        return visitChildren(node).stream().flatMap(Set::stream).collect(Collectors.toSet());
                    }

                    @Override
                    public Set<Bit> visit(TmpInputVariableDeclarationNode inputDecl) {
                        return new HashSet<>(context.getVariableValue(inputDecl.definition).bits);
                    }
                });
            }

            private PrintHistory.ReduceResult<Map<Variable, AppendOnlyValue>>
                reduceAppendOnlyVariables(PrintHistory.HistoryEntry history) {
                return PrintHistory.ReduceResult.create(
                                history.map.keySet().stream().collect(Collectors.toMap(v -> v, v -> history.map.get(v).reduceAppendOnly(context::weight))));
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
                if (returnStatement.hasReturnExpression()){
                    context.setReturnValue(context.nodeValue(returnStatement.expression));
                }
                return false;
            }

            @Override
            public Boolean visit(VariableAccessNode variableAccess) {
                return false;
            }

            private void weightCondBit(Bit bit){
                if (bit.isAtLeastUnknown()){
                    context.weight(bit, Context.INFTY);
                }
                if (bit.value() != null && bit.value().node() != null){
                    MJNode node = bit.value().node();
                    if (node.getOperator() == EQUALS || node.getOperator() == UNEQUALS || node.getOperator() == LESS){
                        return;
                    }
                }
                bit.deps().forEach(this::weightCondBit);
            }
        }, context::evaluate, node, statementNodesToOmitOneTime);
        return context;
    }

    private static boolean isLogicalOpOrPhi(MJNode node){
        return node.accept(new NodeVisitor<Boolean>() {
            @Override
            public Boolean visit(MJNode node) {
                return false;
            }

            @Override
            public Boolean visit(BinaryOperatorNode binaryOperator) {
                return binaryOperator.operator == LexerTerminal.AND ||
                        binaryOperator.operator == LexerTerminal.OR ||
                        binaryOperator.operator == LexerTerminal.XOR;
            }

            @Override
            public Boolean visit(UnaryOperatorNode unaryOperator) {
                return true;
            }

            @Override
            public Boolean visit(PhiNode phi) {
                return true;
            }

            @Override
            public Boolean visit(VariableAccessNode variableAccess) {
                return variableAccess.definingExpression != null &&
                        isLogicalOpOrPhi(variableAccess.definingExpression);
            }
        });
    }
}

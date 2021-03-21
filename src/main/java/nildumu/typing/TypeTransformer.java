package nildumu.typing;

import nildumu.NameResolution;
import nildumu.Parser;
import nildumu.Variable;
import swp.util.Pair;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static nildumu.Lattices.vl;
import static nildumu.Parser.*;
import static nildumu.util.Util.enumerate;
import static nildumu.util.Util.p;
import static swp.lexer.Location.ZERO;
import static swp.util.Utils.Triple;

/**
 * Gets a program and replaces all complex types with int variables
 * <p>
 * Requires name and type resolution
 */
public class TypeTransformer implements Parser.NodeVisitor<TypeTransformer.VisRet> {


    /**
     * Result of visiting a statement
     */
    static class VisRet {

        static final VisRet DEFAULT = new VisRet(false, Collections.emptyList(), Collections.emptyList());

        final boolean removeCurrentStatement;

        final List<Parser.StatementNode> statementsToAdd;

        final List<Parser.StatementNode> statementsToPrepend;

        VisRet(boolean removeCurrentStatement, List<Parser.StatementNode> statementsToAdd, List<Parser.StatementNode> statementsToPrepend) {
            this.removeCurrentStatement = removeCurrentStatement;
            this.statementsToAdd = statementsToAdd;
            this.statementsToPrepend = statementsToPrepend;
        }

        VisRet(boolean removeCurrentStatement, Parser.StatementNode... statementsToAdd) {
            this(removeCurrentStatement, Arrays.asList(statementsToAdd), Collections.emptyList());
        }
    }

    static class RequiredAccessor {
        final String name;
        final Type type;

        RequiredAccessor(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RequiredAccessor)) return false;
            RequiredAccessor that = (RequiredAccessor) o;
            return Objects.equals(name, that.name) &&
                    Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type);
        }
    }

    private final Types types;
    private final Map<Variable, List<Variable>> blastedVariablesPerVariable;
    private final Map<Triple<Integer, Integer, Integer>, MethodNode> setters;
    private final Map<Triple<Integer, Integer, Integer>, MethodNode> getters;
    private final Map<String, Type> methodReturnTypes;

    public TypeTransformer(Types types, MethodNode method, Map<Triple<Integer, Integer, Integer>, MethodNode> setters, Map<Triple<Integer, Integer, Integer>, MethodNode> getters, Map<String, Type> methodReturnTypes) {
        this.types = types;
        this.methodReturnTypes = methodReturnTypes;
        this.blastedVariablesPerVariable = new HashMap<>();
        this.setters = setters;
        this.getters = getters;
    }

    public List<MethodNode> resolve(Parser.MJNode node) {
        node.accept(this);
        return getCreatedMethods();
    }

    public List<MethodNode> getCreatedMethods() {
        return Stream.concat(getters.values().stream(), setters.values().stream()).collect(Collectors.toList());
    }

    public List<MethodNode> resolveGlobalBlock(Parser.BlockNode node) {
        visit(node);
        return getCreatedMethods();
    }

    @Override
    public VisRet visit(Parser.MJNode node) {
        visitChildrenDiscardReturn(node);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(Parser.ProgramNode program) {
        visitChildrenDiscardReturn(program);
        return VisRet.DEFAULT;
    }

    private List<Variable> getBlasted(Variable variable) {
        int number = variable.getType().getNumberOfBlastedVariables();
        return blastedVariablesPerVariable.computeIfAbsent(variable, v -> {
            if (number == 0) {
                return Collections.emptyList();
            } else if (v.getType() == types.INT) {
                return Collections.singletonList(variable);
            } else {
                return enumerate(variable.getType().getBlastedTypes(), (i, t) -> new Variable("__bl_" + v.name + "_" + i, t));
            }
        });
    }

    /**
     * returns a method (cached) that gets the transformed index as its first argument and the blasted variables as the second
     * and returns the expected number
     */
    private MethodNode getGetter(Triple<Integer, Integer, Integer> sizeElemSizePackets) {
        return getters.computeIfAbsent(sizeElemSizePackets, t -> {
            int size = sizeElemSizePackets.first;
            int elemSize = sizeElemSizePackets.second;
            int packets = sizeElemSizePackets.third;
            List<Variable> parameters = IntStream.range(0, size + 1).mapToObj(i -> new Variable("a" + i, types.INT)).collect(Collectors.toList());
            Variable index = parameters.get(0);
            VariableAccessNode indexAccess = new VariableAccessNode(ZERO, index);
            List<Variable> blastedVars = parameters.subList(1, parameters.size());
            Type returnType = types.getOrCreateTupleType(types.INT, elemSize);
            Variable returnVariable = new Variable("__br", returnType);
            List<StatementNode> body = new ArrayList<>();
            body.add(new VariableDeclarationNode(ZERO, returnVariable, null));
            for (int i = 0; i < size; i += elemSize) {
                body.add(new IfStatementNode(ZERO, new BinaryOperatorNode(indexAccess, literal(i), LexerTerminal.EQUALS),
                        new BlockNode(ZERO, Collections.singletonList(new VariableAssignmentNode(ZERO, returnVariable, new TupleLiteralNode(ZERO, IntStream.range(i, i + elemSize).mapToObj(j -> new VariableAccessNode(ZERO, blastedVars.get(j))).collect(Collectors.toList())))))));
            }
            body.add(new ReturnStatementNode(ZERO, new VariableAccessNode(ZERO, returnVariable)));
            return new MethodNode(ZERO, "__blasted_get_" + size + "_" + elemSize + "_" + packets, returnType,
                    new ParametersNode(parameters),
                    new BlockNode(ZERO, body), new GlobalVariablesNode(ZERO, new HashMap<>()));
        });
    }

    /**
     * returns a method (cached) that gets the transformed index as its first argument, the blasted variables as the second
     * and the new values as the third (all blasted)
     * and returns all variables, modifying the requested
     */
    private MethodNode getSetter(Triple<Integer, Integer, Integer> sizeElemSizePackets) {
        return setters.computeIfAbsent(sizeElemSizePackets, t -> {
            int size = sizeElemSizePackets.first;
            int elemSize = sizeElemSizePackets.second;
            int packets = sizeElemSizePackets.third;
            List<Variable> parameters = IntStream.range(0, size + 1 + elemSize).mapToObj(i -> new Variable("a" + i, types.INT)).collect(Collectors.toList());
            Variable index = parameters.get(0);
            List<Variable> blastedVars = parameters.subList(1, size + 1);
            List<ExpressionNode> blastedVarAccesses = blastedVars.stream().map(v -> new VariableAccessNode(ZERO, v)).collect(Collectors.toList());
            List<Variable> blastedNewValues = parameters.subList(size + 1, parameters.size());
            VariableAccessNode indexAccess = new VariableAccessNode(ZERO, index);
            Type returnType = types.getOrCreateTupleType(IntStream.range(0, size).mapToObj(i -> types.INT).collect(Collectors.toList()));
            List<StatementNode> body = new ArrayList<>();
            // set the return value to the array content
            for (int i = 0; i < size; i += elemSize) {
                body.add(new IfStatementNode(ZERO,
                        new BinaryOperatorNode(indexAccess, literal(i), LexerTerminal.EQUALS),
                        new BlockNode(ZERO, IntStream.range(i, i + elemSize).mapToObj(j -> new VariableAssignmentNode(ZERO, blastedVars.get(j), new VariableAccessNode(ZERO, blastedNewValues.get(j % elemSize)))).collect(Collectors.toList()))));
            }
            body.add(new ReturnStatementNode(ZERO, new TupleLiteralNode(ZERO, blastedVarAccesses)));
            return new MethodNode(ZERO, "__blasted_set_" + size + "_" + elemSize + "_" + packets, returnType,
                    new ParametersNode(parameters),
                    new BlockNode(ZERO, body), new GlobalVariablesNode(ZERO, new HashMap<>()));
        });
    }

    @Override
    public VisRet visit(VariableDeclarationNode variableDeclaration) {
        return visit(variableDeclaration, (v, e) -> new VariableDeclarationNode(ZERO, v.name, v.getType(), e));
    }

    public VisRet visit(VariableDeclarationNode variableDeclaration, BiFunction<Variable, ExpressionNode, VariableDeclarationNode> constructor) {
        List<Variable> blasted = getBlasted(variableDeclaration.definition);
        StatementNode assignment = variableDeclaration.expression != null ? visit((VariableAssignmentNode) variableDeclaration).statementsToAdd.get(0) : null;
        List<StatementNode> decls;
        if (assignment instanceof VariableAssignmentNode) {
            VariableAssignmentNode ass = (VariableAssignmentNode) assignment;
            decls = Collections.singletonList(constructor.apply(ass.definition, ass.expression));
        } else {
            decls = new ArrayList<>(enumerate(blasted, (i, v) -> constructor.apply(v, null)));
            if (assignment != null) {
                decls.add(assignment);
            }
        }
        return new VisRet(true, decls, Collections.emptyList());
    }

    @Override
    public VisRet visit(OutputVariableDeclarationNode decl) {
        return visit(decl, (v, e) -> {
            OutputVariableDeclarationNode node = new OutputVariableDeclarationNode(ZERO, v.name, v.getType(), e, decl.secLevel);
            node.definition = v;
            return node;
        });
    }

    @Override
    public VisRet visit(InputVariableDeclarationNode decl) {
        BiFunction<Variable, ExpressionNode, VariableDeclarationNode> constructor = (v, e) -> {
            InputVariableDeclarationNode node = new InputVariableDeclarationNode(ZERO, v.name, v.getType(), (IntegerLiteralNode) e, decl.secLevel);
            node.definition = v;
            return node;
        };
        List<Variable> blasted = getBlasted(decl.definition);
        StatementNode assignment = decl.expression != null ? visit((VariableAssignmentNode) decl).statementsToAdd.get(0) : null;
        List<StatementNode> decls;
        if (assignment instanceof VariableAssignmentNode) {
            VariableAssignmentNode ass = (VariableAssignmentNode) assignment;
            decls = blasted.stream().map(v -> constructor.apply(v, ass.expression)).collect(Collectors.toList());
        } else {
            decls = new ArrayList<>(enumerate(blasted, (i, v) -> constructor.apply(v, null)));
            if (assignment != null) {
                decls.add(assignment);
            }
        }
        return new VisRet(true, decls, Collections.emptyList());
    }

    @Override
    public VisRet visit(TmpInputVariableDeclarationNode decl) {
        return visit(decl, (v, e) -> {
            TmpInputVariableDeclarationNode node = new TmpInputVariableDeclarationNode(ZERO, v.name, v.getType(), (IntegerLiteralNode) e, decl.secLevel);
            node.definition = v;
            return node;
        });
    }

    @Override
    public VisRet visit(AppendOnlyVariableDeclarationNode decl) {
        return visit(decl, (v, e) -> {
            AppendOnlyVariableDeclarationNode node = new AppendOnlyVariableDeclarationNode(ZERO, v.name, v.getType(), decl.secLevel);
            node.definition = v;
            return node;
        });
    }

    @Override
    public VisRet visit(VariableAssignmentNode assignment) {
        if (assignment.expression.type != types.INT) {
            Type.TupleLikeType type = (Type.TupleLikeType) assignment.expression.type;
            List<Variable> blasted = getBlasted(assignment.definition);
            List<ExpressionNode> op = transform(new UnpackOperatorNode(assignment.expression));
            StatementNode node;
            if (op.get(0) instanceof UnpackOperatorNode) {
                node = new MultipleVariableAssignmentNode(ZERO, blasted, (UnpackOperatorNode) op.get(0));
            } else {
                node = new VariableAssignmentNode(ZERO, blasted.get(0), op.get(0));
            }
            return new VisRet(true, Collections.singletonList(node), Collections.emptyList());
        }
        ExpressionNode expressionNode = transform(assignment.expression).get(0);
        assert expressionNode instanceof UnpackOperatorNode || expressionNode.type != null;
        if (expressionNode.type != types.INT){
            return new VisRet(true, new MultipleVariableAssignmentNode(ZERO, getBlasted(assignment.definition), new UnpackOperatorNode(expressionNode)));
        }
        return new VisRet(true, Collections.singletonList(new VariableAssignmentNode(assignment.location, assignment.definition, expressionNode)), Collections.emptyList());
    }

    @Override
    public VisRet visit(MultipleVariableAssignmentNode assignment) {
        if (assignment.expression.expression.type instanceof Type.TupleLikeType && ((Type.TupleLikeType) assignment.expression.expression.type).hasOnlyIntElements()) {
            ExpressionNode expr = transform(assignment.expression).get(0);
            if (expr instanceof UnpackOperatorNode) {
                return new VisRet(true, new MultipleVariableAssignmentNode(assignment.location, assignment.definitions, (UnpackOperatorNode) expr));
            }
            return new VisRet(true, new VariableAssignmentNode(assignment.location, assignment.definitions.get(0), expr));
        }
        List<StatementNode> stmts = new ArrayList<>();
        HashSet<String> varNames = new HashSet<>(assignment.variables);
        String varName = "__bl_";
        while (varNames.contains(varName)) {
            varName += "_";
        }
        Variable variable = new Variable(varName, assignment.expression.expression.type);
        stmts.add(new VariableDeclarationNode(assignment.location, variable, assignment.expression.expression));
        enumerate(assignment.definitions, (i, v) -> {
            ExpressionNode bracketed = new BracketedAccessOperatorNode(
                    new VariableAccessNode(assignment.location, variable),
                    literal(i)).setExpressionType(v.getType());
            VariableAssignmentNode ass = new VariableAssignmentNode(assignment.location, v, bracketed);
            return visit(ass).statementsToAdd.get(0);
        }).forEach(stmts::add);
        BlockNode block = new BlockNode(assignment.location, stmts);
        block.accept(this);
        return new VisRet(true, block);
    }

    @Override
    public VisRet visit(ReturnStatementNode returnStatement) {
        ExpressionNode retExpr = null;
        if (returnStatement.expression != null) {
            List<ExpressionNode> expressions = transform(returnStatement.expression);
            Type expectedReturnType = methodReturnTypes.get(returnStatement.parentMethod.name);
            if (expectedReturnType == types.INT) {
                retExpr = expressions.get(0);
                if (retExpr instanceof TupleLiteralNode) {
                    retExpr = ((TupleLiteralNode)retExpr).elements.get(0);
                }
            } else {
                if (expressions.size() == 1 && expressions.get(0).type == expectedReturnType) {
                    retExpr = expressions.get(0);
                } else {
                    retExpr = new TupleLiteralNode(returnStatement.location, expressions);
                    retExpr.type = types.getOrCreateTupleType(expressions.stream()
                            .flatMap(e -> {
                                if (e instanceof UnpackOperatorNode) {
                                    return ((Type.TupleType) ((UnpackOperatorNode) e).expression.type).elementTypes.stream();
                                }
                                return Stream.of(e.type);
                            }).collect(Collectors.toList()));
                }
            }
        }
        return new VisRet(true, new ReturnStatementNode(returnStatement.location, retExpr));
    }

    public VisRet visit(Parser.BlockNode block) {
        List<Parser.StatementNode> blockPartNodes = new ArrayList<>();
        for (Parser.StatementNode child : block.statementNodes) {
            VisRet ret = child.accept(this);
            blockPartNodes.addAll(ret.statementsToPrepend);
            if (!ret.removeCurrentStatement) {
                blockPartNodes.add(child);
            }
            blockPartNodes.addAll(ret.statementsToAdd);
        }
        block.statementNodes.clear();
        block.addAll(blockPartNodes);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(IfStatementNode ifStatement) {
        visit(ifStatement.ifBlock);
        visit(ifStatement.elseBlock);
        return new VisRet(true, new IfStatementNode(ifStatement.location, transform(ifStatement.conditionalExpression).get(0), ifStatement.ifBlock, ifStatement.elseBlock));
    }

    @Override
    public VisRet visit(WhileStatementNode whileStatement) {
        visit(whileStatement.body);
        return new VisRet(true, new WhileStatementNode(whileStatement.location, whileStatement.getPreCondVarAss().stream().flatMap(v -> v.accept(this).statementsToAdd.stream().map(s -> v)).collect(Collectors.toList()), transform(whileStatement.conditionalExpression).get(0), whileStatement.body));
    }

    public static MethodNode process(TypeTransformer parent, MethodNode method) {
        TypeTransformer resolution = new TypeTransformer(parent.types, method, parent.setters, parent.getters, parent.methodReturnTypes);
        resolution.resolve(method.parameters);
        resolution.resolve(method.body);
        MethodNode methodNode = new MethodNode(method.location, method.name, null, new ParametersNode(method.parameters.parameterNodes.stream().flatMap(p -> resolution.getBlasted(p.definition).stream()).collect(Collectors.toList())), method.body, method.globals);
        methodNode.setReturnType(method.hasReturnValue() ? resolution.methodReturnTypes.get(method.name) : resolution.types.INT);
        return methodNode;
    }

    void fillMethodReturnTypes(Collection<MethodNode> methods) {
        for (MethodNode method : methods) {
            if (method.getReturnType().getNumberOfBlastedVariables() == 1) {
                methodReturnTypes.put(method.name, types.INT);
            } else {
                methodReturnTypes.put(method.name, types.getOrCreateTupleType(types.INT, method.getReturnType().getNumberOfBlastedVariables()));
            }
        }
    }

    public static ProgramNode process(Parser.ProgramNode program) {
        TypeTransformer resolution = new TypeTransformer(program.types, null, new HashMap<>(), new HashMap<>(), new HashMap<>());
        resolution.fillMethodReturnTypes(program.methods());
        resolution.resolveGlobalBlock(program.globalBlock);
        List<MethodNode> newMethods = program.methods().stream().map(m -> TypeTransformer.process(resolution, m)).collect(Collectors.toList());
        program.methods().clear();
        newMethods.forEach(program::addMethod);
        resolution.getCreatedMethods().forEach(program::addMethod);
        if (resolution.getCreatedMethods().size() > 0) {
            program = (ProgramNode) generator.parse(program.toPrettyString());
            new NameResolution(program).resolve();
            return process(program);
        }
        return program;
    }

    @Override
    public VisRet visit(Parser.ParameterNode parameter) {
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(ArrayAssignmentNode arrayAssignment) {
        Type.TupleLikeType type = (Type.TupleLikeType) arrayAssignment.definition.getType();
        ExpressionNode indexExpression = arrayAssignment.arrayIndex;
        List<Variable> blasted = getBlasted(arrayAssignment.definition);
        if (indexExpression instanceof IntegerLiteralNode && ((IntegerLiteralNode) indexExpression).value.isConstant()) {
            int index = (int)((IntegerLiteralNode) indexExpression).value.asLong();
            if (!(arrayAssignment.expression instanceof MethodInvocationNode)) {
                List<ExpressionNode> ret = transform(arrayAssignment.expression);
                //return new VisRet(true, type.getBlastedIndexes(index).stream().map(i -> blasted.get(i)).collect(Collectors.toList()), Collections.emptyList());
            }
        }
        Pair<Triple<Integer, Integer, Integer>, ExpressionNode> accessorIdAndIndexExpr = getAccessorIdAndIndexExpr(type, indexExpression);
        return new VisRet(true, new MultipleVariableAssignmentNode(arrayAssignment.location,
                blasted, new UnpackOperatorNode(new MethodInvocationNode(arrayAssignment.location,
                getSetter(accessorIdAndIndexExpr.first), accessorIdAndIndexExpr.second,
                Stream.concat(blasted.stream().map(v -> (ExpressionNode) new VariableAccessNode(arrayAssignment.location, v)), transform(arrayAssignment.expression).stream()).collect(Collectors.toList())))));
    }

    List<ExpressionNode> transform(ExpressionNode expression) {
        return expression.accept(new ExpressionTransformer());
    }

    public IntegerLiteralNode literal(int num) {
        IntegerLiteralNode node = new IntegerLiteralNode(ZERO, vl.parse(num));
        node.type = types.INT;
        return node;
    }

    class ExpressionTransformer implements ExpressionVisitor<List<ExpressionNode>> {

        @Override
        public List<ExpressionNode> visit(ExpressionNode expression) {
            visitChildrenDiscardReturn(expression);
            return Collections.singletonList(expression);
        }

        @Override
        public List<ExpressionNode> visit(BinaryOperatorNode binaryOperator) {
            return Collections.singletonList(new BinaryOperatorNode(binaryOperator.left.accept(this).get(0),
                    binaryOperator.right.accept(this).get(0), binaryOperator.operator).setExpressionType(binaryOperator.type));
        }

        @Override
        public List<ExpressionNode> visit(BitPlaceOperatorNode bitPlacement) {
            return Collections.singletonList(new BitPlaceOperatorNode(visit(bitPlacement.expression).get(0), bitPlacement.index).setExpressionType(bitPlacement.type));
        }

        @Override
        public List<ExpressionNode> visit(UnaryOperatorNode unaryOperator) {
            return Collections.singletonList(new UnaryOperatorNode(visit(unaryOperator.expression).get(0), unaryOperator.operator).setExpressionType(unaryOperator.type));
        }

        @Override
        public List<ExpressionNode> visit(VariableAccessNode variableAccess) {
            return getBlasted(variableAccess.definition).stream().map(v -> new VariableAccessNode(variableAccess.location, v)).collect(Collectors.toList());
        }

        @Override
        public List<ExpressionNode> visit(TupleLikeLiteralNode literal) {
            return collect(literal.elements);
        }

        @Override
        public List<ExpressionNode> visit(UnpackOperatorNode unpackOperator) {
            List<ExpressionNode> l = unpackOperator.expression.accept(this);
            if (l.size() == 1) {
                if (l.get(0) instanceof UnpackOperatorNode) {
                    return l;
                } else if (l.get(0).type instanceof Type.TupleLikeType) {
                    return Collections.singletonList(new UnpackOperatorNode(l.get(0)));
                } else {
                    return Collections.singletonList(l.get(0));
                }
            }
            return Collections.singletonList(new UnpackOperatorNode(new TupleLiteralNode(unpackOperator.location, l).setExpressionType(unpackOperator.expression.type)));
        }

        List<ExpressionNode> collect(List<ExpressionNode> expressions) {
            return expressions.stream().flatMap(e -> e.accept(this).stream()).collect(Collectors.toList());
        }

        @Override
        public List<ExpressionNode> visit(MethodInvocationNode methodInvocation) {
            if (methodInvocation.definition instanceof PredefinedMethodNode && methodInvocation.definition.name.equals("length")) {
                return Collections.singletonList(literal(collect(methodInvocation.arguments.arguments).size()));
            } else {
                visitChildrenDiscardReturn(methodInvocation);
                if (methodInvocation.arguments.size() == 0){
                    return Collections.singletonList(methodInvocation);
                }
                return Collections.singletonList(new MethodInvocationNode(methodInvocation.location, methodInvocation.method, new ArgumentsNode(ZERO, transform(new UnpackOperatorNode(new TupleLiteralNode(ZERO, methodInvocation.arguments.arguments.stream().flatMap(a -> a.accept(this).stream()).collect(Collectors.toList())))))).setExpressionType(methodReturnTypes.get(methodInvocation.method)));
            }
        }

        @Override
        public List<ExpressionNode> visit(BracketedAccessOperatorNode bracketedAccess) {
            if (bracketedAccess.left.type == types.INT) {
                return Collections.singletonList(bracketedAccess);
            }
            Type.TupleLikeType type = (Type.TupleLikeType) bracketedAccess.left.type;
            if (bracketedAccess.right instanceof IntegerLiteralNode && ((IntegerLiteralNode) bracketedAccess.right).value.isConstant()) {
                int index = (int)((IntegerLiteralNode) bracketedAccess.right).value.asLong();
                if (!(bracketedAccess.left instanceof MethodInvocationNode)) {
                    List<ExpressionNode> ret = bracketedAccess.left.accept(this);
                    return type.getBlastedIndexes(index).stream().map(ret::get).collect(Collectors.toList());
                }
            }
            Pair<Triple<Integer, Integer, Integer>, ExpressionNode> accessorIdAndIndexExpr = getAccessorIdAndIndexExpr(type, bracketedAccess.right);
            return Collections.singletonList(new MethodInvocationNode(bracketedAccess.location,
                    getGetter(accessorIdAndIndexExpr.first), accessorIdAndIndexExpr.second,
                    new UnpackOperatorNode(bracketedAccess.left).accept(this)));
        }
    }

    Pair<Triple<Integer, Integer, Integer>, ExpressionNode> getAccessorIdAndIndexExpr(Type.TupleLikeType type, ExpressionNode indexExpression) {
        int size = type.getNumberOfBlastedVariables();
        int elemLength;
        int packets;
        ExpressionNode transformedIndexExpr;
        if (type instanceof Type.TupleType) {
            int index = (int)((IntegerLiteralNode) indexExpression).value.asLong();
            elemLength = type.getBracketAccessResult(index).getNumberOfBlastedVariables();
            packets = 1;
            transformedIndexExpr = literal(type.getBlastedStartIndex(index));
        } else {
            Type.FixedLengthArrayType at = (Type.FixedLengthArrayType) type;
            elemLength = at.getElementType().getNumberOfBlastedVariables();
            packets = elemLength;
            transformedIndexExpr = elemLength == 1 ? indexExpression : new BinaryOperatorNode(indexExpression, literal(elemLength), LexerTerminal.MULTIPLY);
        }
        return p(new Triple<>(size, elemLength, packets), transformedIndexExpr);
    }
}

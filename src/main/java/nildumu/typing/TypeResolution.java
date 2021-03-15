package nildumu.typing;

import nildumu.NameResolution;
import nildumu.NildumuError;
import nildumu.Parser;
import nildumu.Variable;
import swp.util.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nildumu.Parser.*;
import static nildumu.util.Util.p;
import static nildumu.util.Util.zip;

/**
 * It assigns every expression a type and does the typechecking. It also resolves "var" types to correct types
 */
public class TypeResolution implements Parser.NodeVisitor<List<TypeResolution.WrongTypeMessage>> {

    public static class WrongTypeMessage {

        public final String message;

        public WrongTypeMessage(MJNode node, Type expected, Type actual) {
            this.message = String.format("at %s (%s): Expected type %s, got %s", node, node.location, expected, actual);
        }

        public WrongTypeMessage(String prefix, MJNode node, Type expected, Type actual) {
            this.message = String.format("%s at %s (%s): Expected type %s, got %s", prefix, node, node.location, expected, actual);
        }

        public WrongTypeMessage(String message) {
            this.message = message;
        }
    }

    private final ProgramNode program;
    private final NameResolution resolution;

    public TypeResolution(ProgramNode program, NameResolution resolution) {
        this.program = program;
        this.resolution = resolution;
    }

    private List<WrongTypeMessage> visitChildrenAndCollect(MJNode node) {
        return node.children().stream().flatMap(c -> ((MJNode) c).accept(this).stream()).collect(Collectors.toList());
    }

    public List<WrongTypeMessage> resolve() {
        return program.accept(this);
    }

    public void resolveAndThrow() {
        List<WrongTypeMessage> messages = resolve();
        if (messages.size() > 0) {
            throw new NildumuError(messages.stream().map(m -> m.message).collect(Collectors.joining("\n")));
        }
    }

    @Override
    public List<WrongTypeMessage> visit(Parser.MJNode node) {
        return visitChildrenAndCollect(node);
    }

    @Override
    public List<WrongTypeMessage> visit(ProgramNode program) {
        return visitChildrenAndCollect(program);
    }

    @Override
    public List<WrongTypeMessage> visit(AppendOnlyVariableDeclarationNode appendDecl) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(appendDecl);
        if (appendDecl.expression != null && appendDecl.expression.type != program.INT) {
            messages.add(new WrongTypeMessage(appendDecl.toString(), appendDecl.expression, program.INT,
                    appendDecl.expression.type));
        }
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(VariableDeclarationNode variableDeclaration) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(variableDeclaration);
        if (variableDeclaration.getVarType().isVar()) {
            if (variableDeclaration.expression == null) {
                messages.add(new WrongTypeMessage(String.format("Cannot use type var without expression in %s at %s",
                        variableDeclaration, variableDeclaration.location)));
            } else {
                variableDeclaration.definition.setType(variableDeclaration.expression.type);
            }
        } else {
            if (variableDeclaration.expression != null) {
                assertType(messages, variableDeclaration.expression, variableDeclaration.definition.getType());
            }
        }
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(VariableAssignmentNode assignment) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(assignment);
        assertType(messages, assignment.expression, assignment.definition.getType());
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(MultipleVariableAssignmentNode assignment) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(assignment);
        Type.TupleType tupleType = (Type.TupleType) assignment.expression.expression.type;
        Type.TupleType expectedType = new Type.TupleType(program.types, assignment.definitions.stream()
                .map(Variable::getType).collect(Collectors.toList()));
        if (!tupleType.equals(expectedType)) {
            messages.add(new WrongTypeMessage(String.format("Exppected expression of type %s in %s at %s, got expression of type %s",
                    expectedType, assignment, assignment.location, tupleType)));
        }
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(UnpackOperatorNode unpackOperator) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(unpackOperator);
        if (!(unpackOperator.expression.type instanceof Type.TupleType)) {
            messages.add(new WrongTypeMessage(String.format("Expected tuple typed expression for unpack operator %s at %s, got expression of type %s", unpackOperator, unpackOperator.location, unpackOperator.expression.type)));
        }
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(VariableAccessNode variableAccess) {
        assert !variableAccess.definition.getType().isVar();
        variableAccess.type = variableAccess.definition.getType();
        return Collections.emptyList();
    }

    @Override
    public List<WrongTypeMessage> visit(ParameterAccessNode variableAccess) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(variableAccess);
        variableAccess.type = variableAccess.definition.getType();
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(BinaryOperatorNode binaryOperator) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(binaryOperator);
        assertType(messages, binaryOperator.left, program.INT);
        assertType(messages, binaryOperator.right, program.INT);
        binaryOperator.type = program.INT;
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(UnaryOperatorNode unaryOperator) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(unaryOperator);
        assertType(messages, unaryOperator.expression, program.INT);
        unaryOperator.type = program.INT;
        return messages;
    }

    /**
     * does set the type if valid
     */
    private Pair<List<WrongTypeMessage>, Optional<Type>> checkArrayAccess(BracketedAccessOperatorNode access) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(access);
        ExpressionNode array = access.left;
        ExpressionNode arrayIndex = access.right;
        if (arrayIndex.type != program.INT) {
            messages.add(new WrongTypeMessage(String.format("Index in %s at %s should be an integer", access, access.location)));
        }
        Optional<Type> elementType = Optional.empty();
        if (array.type instanceof Type.FixedLengthArrayType) {
            elementType = Optional.of(((Type.FixedLengthArrayType) array.type).getElementType());
        } else if (array.type instanceof Type.TupleType) {
            Type.TupleType type = (Type.TupleType) array.type;
            if (arrayIndex instanceof IntegerLiteralNode) {
                int index = ((IntegerLiteralNode) arrayIndex).value.asInt();
                if (index < 0 || index >= type.elementTypes.size()) {
                    messages.add(new WrongTypeMessage(String.format("Index %s is not out of bounds in tuple access %s at %s", arrayIndex, access, access.location)));
                } else {
                    elementType = Optional.of(type.elementTypes.get(index));
                }
            } else {
                messages.add(new WrongTypeMessage(String.format("Index %s is not constant for tuple access %s at %s", arrayIndex, access, access.location)));
            }
        } else {
            elementType = Optional.of(program.INT);
        }
        elementType.ifPresent(t -> access.type = t);
        return p(messages, elementType);
    }

    @Override
    public List<WrongTypeMessage> visit(ArrayAssignmentNode assignment) {
        BracketedAccessOperatorNode accessNode = new BracketedAccessOperatorNode(new VariableAccessNode(assignment.location, assignment.variable), assignment.arrayIndex);
        Pair<List<WrongTypeMessage>, Optional<Type>> tp = checkArrayAccess(accessNode);
        List<WrongTypeMessage> messages = tp.first;
        Optional<Type> elementType = tp.second;
        messages.addAll(assignment.expression.accept(this));

        if (assignment.definition.getType() instanceof Type.FixedLengthArrayType) {
            if (assignment.expression.type != elementType.get()) {
                messages.add(new WrongTypeMessage(String.format("Expression %s assigned to array %s at %s should have type %s not %s", assignment.expression, assignment.definition, assignment.location, elementType, assignment.expression.type)));
            }
        } else if (assignment.definition.getType() instanceof Type.TupleType) {
            Type.TupleType type = (Type.TupleType) assignment.definition.getType();
            if (assignment.arrayIndex instanceof IntegerLiteralNode) {
                int index = ((IntegerLiteralNode) assignment.arrayIndex).value.asInt();
                if (index < 0 || index >= type.elementTypes.size()) {
                    messages.add(new WrongTypeMessage(String.format("Index %s is not out of bounds in tuple assignment %s at %s", assignment.arrayIndex, assignment, assignment.location)));
                }
                if (elementType.isPresent() && elementType.get() != assignment.expression.type) {
                    messages.add(new WrongTypeMessage(String.format("Expression %s assigned to tuple %s at %s should have type %s not %s", assignment.expression, assignment.definition, assignment.location, type, assignment.expression.type)));
                }
            } else {
                messages.add(new WrongTypeMessage(String.format("Index %s is not constant for tuple assignment %s at %s", assignment.arrayIndex, assignment, assignment.location)));
            }
        } else {
            messages.add(new WrongTypeMessage(String.format("%s is neither an array not a tuple at %s", assignment.definition, assignment.location)));
        }
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(BracketedAccessOperatorNode bracketedAccess) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(bracketedAccess.left);
        if (bracketedAccess.left.type == program.INT) {
            bracketedAccess.type = program.INT;
        } else {
            messages = checkArrayAccess(bracketedAccess).first;
        }
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(TupleLiteralNode primaryExpression) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(primaryExpression);
        primaryExpression.type =
                program.types.getOrCreateTupleType(primaryExpression.elements.stream()
                        .flatMap(e -> {
                            if (e instanceof UnpackOperatorNode) {
                                return ((Type.TupleType) ((UnpackOperatorNode) e).expression.type).elementTypes.stream();
                            }
                            return Stream.of(e.type);
                        }).collect(Collectors.toList()));
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(ArrayLiteralNode primaryExpression) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(primaryExpression);
        TupleLiteralNode tuple = new TupleLiteralNode(primaryExpression.location, primaryExpression.elements);
        messages.addAll(visit(tuple));
        List<Type> elementTypes = ((Type.TupleType) tuple.type).elementTypes;
        Type.FixedLengthArrayType type = (Type.FixedLengthArrayType) program.types.getOrCreateFixedArrayType(elementTypes.get(0), Collections.singletonList(elementTypes.size()));
        for (Type elementType : elementTypes) {
            if (elementType != type.getElementType()) {
                messages.add(new WrongTypeMessage(String.format("type %s not supported for element array %s with type %s at %s", elementType, primaryExpression, type, primaryExpression.location)));
            }
        }
        primaryExpression.type = type;
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(IntegerLiteralNode literal) {
        literal.type = program.INT;
        return Collections.emptyList();
    }

    @Override
    public List<WrongTypeMessage> visit(ReturnStatementNode returnStatement) {
        if (!returnStatement.hasReturnExpression()) {
            return Collections.emptyList();
        }
        List<WrongTypeMessage> messages = visitChildrenAndCollect(returnStatement);
        Type type = returnStatement.expression.type;
        if (returnStatement.parentMethod.getReturnType().isVar()) {
            returnStatement.parentMethod.setReturnType(type);
        } else if (type != returnStatement.parentMethod.getReturnType()) {
            messages.add(new WrongTypeMessage(String.format("return type of method %s, %s, conflicts with return statement %s at %s",
                    returnStatement.parentMethod.name, returnStatement.parentMethod.getReturnType(), returnStatement, returnStatement.location)));
        }
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(MethodNode method) {
        return visitChildrenAndCollect(method);
    }

    @Override
    public List<WrongTypeMessage> visit(ParameterNode parameter) {
        parameter.definition.setType(parameter.type);
        return Collections.emptyList();
    }

    @Override
    public List<WrongTypeMessage> visit(MethodInvocationNode methodInvocation) {
        TupleLiteralNode tuple = new Parser.TupleLiteralNode(methodInvocation.location, methodInvocation.arguments.arguments);
        List<WrongTypeMessage> messages = visit(tuple);
        List<Type> argTypes = ((Type.TupleType) tuple.type).elementTypes;
        MethodNode method = methodInvocation.definition;
        method.getNumberOfParameters().ifPresent(n -> {
            if (methodInvocation.arguments.size() != n) {
                throw new NameResolution.WrongNumberOfArgumentsError(methodInvocation, String.format("Expected %d arguments got %d", method.parameters.size(), methodInvocation.arguments.size()));
            }
            zip(argTypes, methodInvocation.definition.parameters.parameterNodes, (t, p) -> {
                if (t != p.type) {
                    return new WrongTypeMessage(String.format("Argument for parameter %s of method %s should have type %s, got type %s at %s",
                            p.name, method.name, p.type, t, methodInvocation.location));
                }
                return null;
            }).stream().filter(Objects::nonNull).forEach(messages::add);
        });
        methodInvocation.type = method.getReturnType();
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(WhileStatementNode whileStatement) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(whileStatement);
        if (whileStatement.conditionalExpression.type != program.INT) {
            messages.add(new WrongTypeMessage(String.format("Condition %s of while loop at %s should result in an int",
                    whileStatement.conditionalExpression, whileStatement.location)));
        }
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(IfStatementNode ifStatement) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(ifStatement);
        if (ifStatement.conditionalExpression.type != program.INT) {
            messages.add(new WrongTypeMessage(String.format("Condition %s of if-then-else at %s should result in an int",
                    ifStatement.conditionalExpression, ifStatement.location)));
        }
        return messages;
    }

    @Override
    public List<WrongTypeMessage> visit(PhiNode phi) {
        List<WrongTypeMessage> messages = visitChildrenAndCollect(phi);
        Type type = phi.joinedVariables.get(0).type;
        for (ExpressionNode assignment : phi.joinedVariables) {
            if (assignment.type != type) {
                messages.add(new WrongTypeMessage(String.format("%s has type %s, expected %s in %s at %s", assignment, assignment.type, type, phi, phi.location)));
            }
        }
        phi.type = type;
        return messages;
    }

    public void assertInt(List<WrongTypeMessage> messages, ExpressionNode expression) {
        assertType(messages, expression, program.INT);
    }

    public void assertType(List<WrongTypeMessage> messages, ExpressionNode expression, Type type) {
        if (expression.type != type) {
            messages.add(new WrongTypeMessage(expression, type, expression.type));
        }
    }
}

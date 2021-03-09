package nildumu;

import swp.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static nildumu.Parser.*;
import static nildumu.util.Util.p;

/**
 * A simple name resolution that sets the {@code definition} variable in {@link VariableAssignmentNode},
 * {@link VariableDeclarationNode}, {@link VariableAccessNode} and {@link ParameterNode}.
 *
 * Also connects the {@link MethodInvocationNode} with the correct {@link MethodNode}.
 */
public class NameResolution implements Parser.NodeVisitor<Object> {

    public static class WrongNumberOfArgumentsError extends NildumuError {
        public WrongNumberOfArgumentsError(MethodInvocationNode invocation, String msg) {
            super(String.format("%s: %s", invocation, msg));
        }
    }

    private SymbolTable symbolTable;
    private final ProgramNode program;
    private final Set<Variable> appendVariables;
    private final Map<MJNode, SymbolTable.Scope> scopePerNode;
    private final boolean captureScopes;

    public NameResolution(ProgramNode program) {
        this(program, false);
    }

    /**
     * @param captureScopes capture the scope each node belongs to?
     */
    public NameResolution(ProgramNode program, boolean captureScopes) {
        this.program = program;
        this.symbolTable = new SymbolTable();
        this.appendVariables = new HashSet<>();
        this.scopePerNode = new HashMap<>();
        this.captureScopes = captureScopes;
    }

    public void resolve() {
        program.accept(this);
    }

    /**
     * only valid if captureScopes == true, returns the scope that each node belongs to
     */
    public Map<MJNode, SymbolTable.Scope> getScopePerNode() {
        assert captureScopes;
        return Collections.unmodifiableMap(scopePerNode);
    }

    private void captureScope(MJNode node) {
        scopePerNode.put(node, symbolTable.getCurrentScope());
    }

    @Override
    public Object visit(Parser.MJNode node) {
        captureScope(node);
        visitChildrenDiscardReturn(node);
        return null;
    }

    @Override
    public Object visit(ProgramNode program) {
        captureScope(program);
        Map<String, Variable> appendToVar = new HashMap<>();
        program.globalBlock.children().forEach(n -> ((MJNode)n).accept(new NodeVisitor<Object>() {
            @Override
            public Object visit(MJNode node) {
                return null;
            }

            @Override
            public Object visit(AppendOnlyVariableDeclarationNode appendDecl) {
                Variable definition = new Variable(appendDecl.variable, false, false, true, true);
                appendDecl.definition = definition;
                appendVariables.add(definition);
                appendToVar.put(definition.name, definition);
                return null;
            }
        }));
        visitChildrenDiscardReturn(program);
        NodeVisitor<Object> visitor = new NodeVisitor<Object>(){


            @Override
            public Object visit(MJNode node) {
                return null;
            }

            @Override
            public Object visit(VariableDeclarationNode variableDeclaration) {
                Variable var = variableDeclaration.definition;
                if (var.isInput){
                    program.addInputVariable(var);
                }
                if (var.isOutput){
                    program.addOuputVariable(var);
                }
                return null;
            }
        };
        program.globalBlock.children().forEach(n -> ((MJNode)n).accept(visitor));
        program.methods().forEach(m -> {
            m.globalDefs = m.globalStringDefs.entrySet().stream().collect(Collectors.toMap(e -> appendToVar.get(e.getKey()), Map.Entry::getValue));
        });
        return null;
    }

    @Override
    public Object visit(AppendOnlyVariableDeclarationNode appendDecl) {
        captureScope(appendDecl);
        symbolTable.insert(appendDecl.variable, appendDecl.definition);
        return null;
    }

    @Override
    public Object visit(VariableDeclarationNode variableDeclaration) {
        captureScope(variableDeclaration);
        if (symbolTable.isDirectlyInCurrentScope(variableDeclaration.variable)){
            throw new MJError(String.format("Variable %s already defined in scope", variableDeclaration.variable));
        }
        Variable definition = new Variable(variableDeclaration.variable,
                variableDeclaration instanceof InputVariableDeclarationNode,
                variableDeclaration instanceof OutputVariableDeclarationNode,
                variableDeclaration instanceof AppendOnlyVariableDeclarationNode,
                variableDeclaration.hasAppendValue);
        symbolTable.insert(variableDeclaration.variable, definition);
        variableDeclaration.definition = definition;
        return visit((VariableAssignmentNode)variableDeclaration);
    }

    @Override
    public Object visit(VariableAssignmentNode assignment) {
        captureScope(assignment);
        symbolTable.throwIfNotInCurrentScope(assignment.variable);
        assignment.definition = symbolTable.lookup(assignment.variable);
        if (assignment.expression != null) {
            assignment.expression.accept(this);
        }
        return null;
    }

    @Override
    public Object visit(MultipleVariableAssignmentNode assignment) {
        captureScope(assignment);
        assignment.definitions = assignment.variables.stream().map(v -> {
            symbolTable.throwIfNotInCurrentScope(v);
            return symbolTable.lookup(v);
        }).collect(Collectors.toList());
        if (assignment.expression != null) {
            assignment.expression.accept(this);
        }
        return null;
    }

    @Override
    public Object visit(VariableAccessNode variableAccess) {
        captureScope(variableAccess);
        symbolTable.throwIfNotInCurrentScope(variableAccess.ident);
        variableAccess.definition = symbolTable.lookup(variableAccess.ident);
        return null;
    }

    @Override
    public Object visit(BlockNode block) {
        captureScope(block);
        symbolTable.enterScope();
        visitChildrenDiscardReturn(block);
        symbolTable.leaveScope();
        return null;
    }

    @Override
    public Object visit(MethodNode method) {
        captureScope(method);
        SymbolTable oldSymbolTable = symbolTable;
        symbolTable = new SymbolTable();
        symbolTable.enterScope();
        Map<String, Pair<Variable, Variable>> defs = new HashMap<>();
        method.globals.globalVarSSAVars.forEach((v, p) -> {
            if (p.first.equals(p.second)){
                return;
            }
            Variable pre = new Variable(p.first, false, false, false, true);
            symbolTable.insert(pre.name, pre);
            Variable post = new Variable(p.second, false, false, false, true);
            symbolTable.insert(post.name, post);
            defs.put(v, p(pre, post));
        });
        method.globalStringDefs = defs;
        appendVariables.forEach(v -> symbolTable.insert(v.name, v));
        visitChildrenDiscardReturn(method);
        symbolTable.leaveScope();
        symbolTable = oldSymbolTable;
        return null;
    }

    @Override
    public Object visit(ParameterNode parameter) {
        captureScope(parameter);
        if (symbolTable.isDirectlyInCurrentScope(parameter.name)) {
            throw new MJError(String.format("A parameter with the name %s already is already defined for the method", parameter.name));
        }
        Variable definition = new Variable(parameter.name);
        symbolTable.insert(parameter.name, definition);
        parameter.definition = definition;
        return null;
    }

    @Override
    public Object visit(MethodInvocationNode methodInvocation) {
        captureScope(methodInvocation);
        if (!program.hasMethod(methodInvocation.method)){
            throw new MJError(String.format("%s: No such method %s", methodInvocation, methodInvocation.method));
        }
        MethodNode method = program.getMethod(methodInvocation.method);
        methodInvocation.definition = method;
        visitChildrenDiscardReturn(methodInvocation);
        if (methodInvocation.arguments.size() != method.parameters.size()){
            throw new WrongNumberOfArgumentsError(methodInvocation, String.format("Expected %d arguments got %d", method.parameters.size(), methodInvocation.arguments.size()));
        }
        methodInvocation.globalDefs = methodInvocation.globals.globalVarSSAVars.entrySet().stream()
                .collect(Collectors.toMap(e -> symbolTable.lookup(e.getKey()), e -> p(symbolTable.lookup(e.getValue().first),
                        symbolTable.lookup(e.getValue().second))));
        return null;
    }

    @Override
    public Object visit(WhileStatementNode whileStatement) {
        captureScope(whileStatement);
        whileStatement.getPreCondVarAss().forEach(this::visit);
        whileStatement.conditionalExpression.accept(this);
        visit(whileStatement.body);
        return null;
    }

    @Override
    public Object visit(IfStatementNode ifStatement) {
        captureScope(ifStatement);
        ifStatement.conditionalExpression.accept(this);
        ifStatement.ifBlock.accept(this);
        ifStatement.elseBlock.accept(this);
        return null;
    }

    @Override
    public Object visit(PhiNode phi) {
        captureScope(phi);
        phi.controlDeps.forEach(e -> e.accept(this));
        phi.joinedVariables.forEach(e -> e.accept(this));
        visit((ExpressionNode)phi);
        return null;
    }
}

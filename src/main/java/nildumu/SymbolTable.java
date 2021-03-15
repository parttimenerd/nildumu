package nildumu;

import nildumu.typing.Type;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Relates identifiers to concrete definitions or variables,
 * see compiler lab
 */
public class SymbolTable {

    /**
     * Relates identifiers to concrete definitions or variables in a scope
     */
    public static class Scope {

        private Map<String, Variable> defs;

        /**
         * Might be null for top level scope
         */
        final Scope parent;

        public Scope(Scope parent) {
            this.parent = parent;
            defs = new HashMap<>();
        }

        Variable lookup(String name) {
            if (defs.containsKey(name)) return defs.get(name);
            else if (parent != null) return parent.lookup(name);
            else return null;
        }

        boolean contains(String name) {
            return lookup(name) != null;
        }

        void insert(String name, Variable def) {
            if (defs.containsKey(name)) {
                throw new Parser.MJError("Scope already contains a definition " +
                        String.format("for a variable named %s", name));
            }
            defs.put(name, def);
        }

        Set<Variable> getDirectlyDefinedVariables() {
            return new HashSet<>(defs.values());
        }

        /**
         * Create a new list with only the defined variables
         */
        public List<String> filter(List<String> variables) {
            return variables.stream().filter(this::contains).collect(Collectors.toList());
        }

        public List<Variable> filterVariables(List<Variable> variables) {
            return variables.stream().filter(v -> contains(v.name)).collect(Collectors.toList());
        }
    }

    private Scope current = null;

    Variable lookup(String name) {
        Variable res = current.lookup(name);
        return res;
    }
    void insert(String name, Variable def) {
        current.insert(name, def);
    }
    void enterScope() {
        Scope s = new Scope(current);
        current = s;
    }
    void leaveScope() {
        current = current.parent;
    }

    boolean inCurrentScope(String name) {
        return current.defs.get(name) != null;
    }

    void throwIfNotInCurrentScope(String name) {
        if (lookup(name) == null) {
            throw new Parser.MJError(String.format("Variable %s isn't defined in the current or parent scopes", name));
        }
    }

    Type lookupType(String name) {
        return lookup(name).type;
    }

    boolean isDirectlyInCurrentScope(String name) {
        return current.defs.containsKey(name);
    }

    Scope getGlobalScope() {
        Scope cur = current;
        while (cur.parent != null) {
            cur = cur.parent;
        }
        return cur;
    }

    Scope getCurrentScope() {
        return current;
    }
}

package nildumu;

import nildumu.typing.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Known as <em>Definition</em> in the compiler lab.
 * Only {@code int} is allowed as a type so it's omitted.
 */
public class Variable {


    /**
     * The name of the variable
     */
    public final String name;
    Type type;
    /**
     * Is this variable an public output variable, through which information gets potentially leaked?
     */
    final boolean isOutput;
    /**
     * Is this a secret or public input variable
     */
    final boolean isInput;

    final boolean isAppendOnly;

    final boolean hasAppendValue;

    private boolean isAppendableInput;

    public Variable(String name, Type type, boolean isInput, boolean isOutput, boolean isAppendOnly, boolean hasAppendValue) {
        this.name = name;
        this.type = type;
        this.isOutput = isOutput;
        this.isInput = isInput;
        this.isAppendOnly = isAppendOnly;
        this.hasAppendValue = hasAppendValue;
        this.isAppendableInput = name.startsWith("input") || name.contains("_input");
    }

    public Variable(String name, Type type) {
        this(name, type, false, false, false, false);
    }


    public Variable(String name) {
        this(name, null, false, false, false, false);
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add(type.toString());
        if (isInput) {
            parts.add("input");
        }
        if (isOutput){
            parts.add("output");
        }
        parts.add(name);
        return String.join(" ", parts);
    }

    public String name(){
        return name;
    }

    public boolean isAppendableInput() {
        return isAppendableInput;
    }

    public boolean needsToBePreprocessed() {
        return type.forPreprocessingOnly();
    }

    public boolean hasType() {
        return type != null;
    }

    public void setType(Type type) {
        if (this.type != null && !this.type.isVar() && this.type != type) {
            throw new NildumuError(String.format("Variable %s cannot have both type %s and %s", name, this.type, type));
        }
        this.type = type;
    }

    public Type getType() {
        assert hasType();
        return type;
    }
}

package nildumu;

import nildumu.util.DefaultMap;

import java.util.*;
import java.util.stream.Collectors;

import static nildumu.Lattices.*;

/**
 * State of the variables
 */
class State extends GenericState {

    static class OutputState extends GenericState {

        private DefaultMap<Sec<?>, Set<String>> valuesPerSec = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<Sec<?>, Set<String>>() {
            @Override
            public Set<String> defaultValue(Map<Sec<?>, Set<String>> map, Sec<?> key) {
                return new HashSet<>();
            }
        });

        public OutputState() {
            super(key -> {
                throw new NoSuchElementException(key);
            });
        }

        @Override
        public void set(String variable, Value value) {
            if (!has(variable)) {
                throw new UnsupportedOperationException();
            }
            super.set(variable, value);
        }

        public void add(Sec<?> lattice, String variable) {
            valuesPerSec.get(lattice).add(variable);
            super.set(variable, AppendOnlyValue.createEmpty());
        }

        @Override
        public String toString() {
            return valuesPerSec.entrySet().stream()
                    .map(e -> String.format("%s => {%s}", e.getKey(), e.getValue().stream()
                            .map(v -> String.format("%s = %s", v, get(v)))
                            .collect(Collectors.joining(", "))))
                    .collect(Collectors.joining("\n"));
        }

        public Value getBits(Sec s) {
            return valuesPerSec.get(s).stream().flatMap(v -> get(v).stream()).collect(Value.collector());
        }
    }

    private Value returnValue = vl.bot();
    final OutputState outputState;

    public State(OutputState outputState) {
        super(key -> vl.bot());
        this.outputState = outputState;
    }

    public Value getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Value value) {
        this.returnValue = value;
    }

    public Lattices.Value get(String variable) {
        if (outputState.has(variable)) {
            return outputState.get(variable);
        }
        return super.get(variable);
    }

    public void set(Variable variable, Lattices.Value value){
        if (variable.hasAppendValue){
            value = value.asAppendOnly();
        }
        if (variable.isAppendOnly){
            outputState.set(variable, value);
        }
        set(variable.name, value);
    }

    @Override
    public String toString() {
        return super.toString() + outputState.toString();
    }
}
package nildumu;

import nildumu.util.DefaultMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GenericState {
    private final DefaultMap<String, Lattices.Value> map;

    public GenericState(Function<String, ? extends Lattices.Value> botSupplier){
        map = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<String, Lattices.Value>() {
            @Override
            public Lattices.Value defaultValue(Map<String, Lattices.Value> map, String key) {
                return botSupplier.apply(key);
            }
        });
    }

    public Lattices.Value get(String variable){
        return map.get(variable);
    }

    public Lattices.Value get(Variable variable){
        return get(variable.name);
    }

    public void set(Variable variable, Lattices.Value value){
        set(variable.name, value);
    }

    public void set(String variable, Lattices.Value value){
        this.map.put(variable, value);
    }

    @Override
    public String toString() {
        return map.entrySet().stream().map(e -> String.format("%s => %s",e.getKey(), e.getValue().repr())).collect(Collectors.joining("\n"));
    }

    public Set<String> variableNames(){
        return map.keySet();
    }

    public boolean has(String variable){
        return map.containsKey(variable);
    }
}

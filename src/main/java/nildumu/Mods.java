package nildumu;

import java.util.*;
import java.util.stream.*;

import nildumu.intervals.Interval;

import static nildumu.Lattices.Bit;

/**
 * A simple set of modifications
 */
public class Mods {

    private final Map<Bit, Bit> replacements;

    private final Map<Interval, Interval> intervalReplacements;

    public Mods(Map<Bit, Bit> replacements, Map<Interval, Interval> intervalReplacements) {
        this.replacements = replacements;
        this.intervalReplacements = intervalReplacements;
    }

    public Mods(Bit orig, Bit repl){
        this(new HashMap<>(), new HashMap<>());
        add(orig, repl);
    }

    public Mods add(Bit orig, Bit repl){
        this.replacements.put(orig, repl);
        return this;
    }

    public Mods add(Interval orig, Interval repl){
        this.intervalReplacements.put(orig, repl);
        return this;
    }

    public Mods add(Mods otherMods){
        for (Map.Entry<Bit, Bit> entry : otherMods.replacements.entrySet()) {
            if (replacements.containsKey(entry.getKey()) && entry.getValue() == replacements.get(entry.getKey())){
                replacements.remove(entry.getKey());
            }
            replacements.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Interval, Interval> entry : otherMods.intervalReplacements.entrySet()) {
            if (intervalReplacements.containsKey(entry.getKey()) && entry.getValue() == intervalReplacements.get(entry.getKey())){
                intervalReplacements.remove(entry.getKey());
            }
            intervalReplacements.put(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public Mods overwrite(Mods otherMods){
        for (Map.Entry<Bit, Bit> entry : otherMods.replacements.entrySet()) {
            replacements.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Interval, Interval> entry : otherMods.intervalReplacements.entrySet()) {
            intervalReplacements.put(entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public String toString() {
        return "(" + Stream.concat(replacements.entrySet().stream(), intervalReplacements.entrySet().stream())
                .map(e -> String.format("%s ↦ %s", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", ")) + ")";
    }

    public boolean definedFor(Bit bit){
        return replacements.containsKey(bit);
    }

    public Bit replace(Bit bit){
        assert definedFor(bit);
        return replacements.get(bit);
    }

    public boolean definedFor(Interval interval){
        return intervalReplacements.containsKey(interval);
    }

    public Interval replace(Interval interval){
        assert definedFor(interval);
        return intervalReplacements.get(interval);
    }

    public static Mods empty(){
        return new Mods(new HashMap<>(), new HashMap<>());
    }

    public static Collector<Mods, ?, Mods> collector(){
        return Collectors.collectingAndThen(Collectors.toList(), xs -> {
            Mods mods = Mods.empty();
            xs.forEach(mods::add);
            return mods;
        });
    }

    public Mods merge(Mods other) {
        for (Map.Entry<Bit, Bit> entry : other.replacements.entrySet()) {
            if (!replacements.containsKey(entry.getKey())){
                replacements.put(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<Interval, Interval> entry : other.intervalReplacements.entrySet()) {
            if (!intervalReplacements.containsKey(entry.getKey())) {
                intervalReplacements.put(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }
}

package nildumu;

import nildumu.util.DefaultMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static nildumu.Lattices.*;

public class InputBits {

    private final DefaultMap<Sec<?>, Set<Bit>> bitsPerSec = new DefaultMap<>((m, s) -> new HashSet<>());
    private final Set<Bit> bits = new HashSet<>();

    public Set<Bit> get(Sec<?> sec){
        return bitsPerSec.get(sec);
    }

    public void put(Sec<?> sec, Bit bit){
        if (bit.isAtLeastUnknown()) {
            bitsPerSec.get(sec).add(bit);
            bits.add(bit);
        }
    }

    public void put(Sec<?> sec, Value value){
        value.stream().forEach(b -> put(sec, b));
    }

    public void putAll(InputBits other){
        bitsPerSec.putAll(other.bitsPerSec);
        bits.addAll(other.bits);
    }

    public void putAll(Sec<?> sec, Set<Bit> bits){
        bits.stream().forEach(b -> put(sec, b));
    }

    public Set<Bit> getBits(){
        return bits;
    }

    public InputBits map(Function<Bit, Bit> transformer){
        InputBits inputBits = new InputBits();
        bitsPerSec.forEach((k, v) -> inputBits.putAll(k, v.stream().map(transformer).collect(Collectors.toSet())));
        return inputBits;
    }
}

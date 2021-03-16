package nildumu.typing;

import nildumu.NildumuError;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Set of types
 */
public class Types extends AbstractMap<String, Type> implements Serializable {

    public Type INT = new Type(this, "int", false) {
        @Override
        public boolean forPreprocessingOnly() {
            return false;
        }

        @Override
        public String getName() {
            return "int";
        }

        @Override
        public int getNumberOfBlastedVariables() {
            return 1;
        }
    };

    /**
     * only used for parsing
     */
    public Type AINT = new Type(this, "aint", false) {
        @Override
        public boolean forPreprocessingOnly() {
            return false;
        }

        @Override
        public String getName() {
            return "aint";
        }

        @Override
        public int getNumberOfBlastedVariables() {
            return 1;
        }
    };

    public Type VAR = new Type(this, "var", false) {
        @Override
        public int getNumberOfBlastedVariables() {
            throw new NildumuError("not supported for var type");
        }

        @Override
        public boolean isVar() {
            return true;
        }
    };

    private final Map<String, Type> types = new HashMap<>();

    {
        add(INT);
        add(VAR);
    }

    private final Map<String, Type> fixedArrayTypes = new HashMap<>();

    public void add(Type type) {
        if (type instanceof Type.FixedLengthArrayType) {
            fixedArrayTypes.put(type.getName(), type);
        }
        types.put(type.getName(), type);
    }

    @Override
    public Set<Entry<String, Type>> entrySet() {
        return types.entrySet();
    }

    public Type getOrCreateFixedArrayType(Type elementType, List<Integer> lengths) {
        if (lengths.isEmpty()) {
            return elementType;
        }
        if (lengths.size() == 1) {
            String name = elementType.toString() + "[" + lengths.get(0) + "]";
            if (!containsKey(name)) {
                add(new Type.FixedLengthArrayType(this, elementType, lengths.get(0)));
            }
            return types.get(name);
        }
        Type subType = getOrCreateFixedArrayType(elementType, lengths.subList(0, lengths.size() - 1));
        return getOrCreateFixedArrayType(subType, Collections.singletonList(lengths.get(lengths.size() - 1)));
    }

    public Type.TupleType getOrCreateTupleType(List<Type> elementTypes) {
        Type type = new Type.TupleType(this, elementTypes);
        return (Type.TupleType) types.computeIfAbsent(type.getName(), n -> type);
    }

    public Type getOrCreateTupleType(Type elementType, int size) {
        return getOrCreateTupleType(IntStream.range(0, size).mapToObj(i -> INT).collect(Collectors.toList()));
    }
}

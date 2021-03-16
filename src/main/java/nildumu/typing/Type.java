package nildumu.typing;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class Type implements Serializable {

    private final Types types;
    private final String name;
    private final boolean forPreprocessingOnly;

    public Type(Types types, String name, boolean forPreprocessingOnly) {
        this.types = types;
        this.name = name;
        this.forPreprocessingOnly = forPreprocessingOnly;
    }

    public boolean forPreprocessingOnly() {
        return forPreprocessingOnly;
    }

    public String getName() {
        return name;
    }

    public Types getTypes() {
        return types;
    }

    public boolean isGeneric() {
        return false;
    }

    /**
     * returns the number of int variables after blasting, e.g. int → 1, int[3] → 3, int[3][3] → 9,
     * only -1 if generic
     */
    public abstract int getNumberOfBlastedVariables();

    public Type getBracketAccessResult(int i) {
        return this;
    }

    public void checkInvariants() {
        //assert isGeneric() == !(getNumberOfBlastedVariables();
    }

    @Override
    public String toString() {
        return name;
    }

    public List<Type> getBlastedTypes() {
        return Collections.singletonList(this);
    }

    public static abstract class TupleLikeType extends Type {

        public final int length;
        protected int blastCache = -1;

        public TupleLikeType(Types types, String name, boolean forPreprocessingOnly, int length) {
            super(types, name, forPreprocessingOnly);
            this.length = length;
        }

        public int getBlastedStartIndex(int index) {
            return IntStream.range(0, index).map(i -> getBracketAccessResult(i).getNumberOfBlastedVariables()).sum();
        }

        @Override
        public int getNumberOfBlastedVariables() {
            if (blastCache == -1) {
                blastCache = IntStream.range(0, length).map(t -> getBracketAccessResult(t).getNumberOfBlastedVariables()).sum();
            }
            return blastCache;
        }

        @Override
        public List<Type> getBlastedTypes() {
            return IntStream.range(0, length).mapToObj(i -> {
                Type type = getBracketAccessResult(i);
                if (type instanceof TupleLikeType) {
                    return ((TupleLikeType) type).getBlastedTypes();
                }
                return Collections.singletonList(type);
            }).flatMap(List::stream).collect(Collectors.toList());
        }

        public int getLength() {
            return length;
        }

        public String getSanitizedName() {
            return getName().replace(',', '_').replace("(", "__").replace(")", "__").replace("[", "___").replace("]", "___");
        }

        public List<Integer> getBlastedIndexes(int index) {
            int start = getBlastedStartIndex(index);
            return IntStream.range(start, start + getBracketAccessResult(index).getNumberOfBlastedVariables()).mapToObj(i -> i).collect(Collectors.toList());
        }

        public abstract boolean hasOnlyIntElements();
    }

    /**
     * an array with a predefined length, works only with non generic sub elements
     */
    public static class FixedLengthArrayType extends TupleLikeType {

        private final Type elementType;
        public FixedLengthArrayType(Types types, Type elementType, int length) {
            super(types, String.format("%s[%d]", elementType, length), true, length);
            this.elementType = elementType;
            assert !elementType.isGeneric();
        }

        public Type getElementType() {
            return elementType;
        }

        public int getBlastedStartIndex(int index) {
            return elementType.getNumberOfBlastedVariables() * index;
        }

        @Override
        public int getNumberOfBlastedVariables() {
            if (blastCache == -1) {
                blastCache = elementType.getNumberOfBlastedVariables() * length;
            }
            return blastCache;
        }

        @Override
        public Type getBracketAccessResult(int i) {
            return elementType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FixedLengthArrayType)) return false;
            FixedLengthArrayType that = (FixedLengthArrayType) o;
            return length == that.length &&
                    Objects.equals(elementType, that.elementType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(elementType, length);
        }

        @Override
        public boolean hasOnlyIntElements() {
            return elementType == getTypes().INT;
        }
    }

    public static class TupleType extends TupleLikeType {

        public final List<Type> elementTypes;

        public TupleType(Types types, List<Type> elementTypes) {
            super(types, String.format("(%s)", elementTypes.stream().map(Type::toString).collect(Collectors.joining(", "))), true, elementTypes.size());
            this.elementTypes = elementTypes;
            assert elementTypes.stream().noneMatch(Type::isGeneric);
        }

        @Override
        public Type getBracketAccessResult(int i) {
            return elementTypes.get(i);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TupleType)) return false;
            TupleType tupleType = (TupleType) o;
            return Objects.equals(elementTypes, tupleType.elementTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(elementTypes);
        }

        @Override
        public boolean hasOnlyIntElements() {
            return elementTypes.stream().allMatch(t -> t == getTypes().INT);
        }
    }

    public boolean isVar() {
        return false;
    }
}

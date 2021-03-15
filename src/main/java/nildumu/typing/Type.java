package nildumu.typing;

import nildumu.Parser;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
     * only empty if generic
     */
    public abstract Optional<Integer> getNumberOfBlastedVariables();

    public Type getBracketAccessResult(int i) {
        return this;
    }

    public void checkInvariants() {
        assert isGeneric() == !getNumberOfBlastedVariables().isPresent();
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * an array with a predefined length, works only with non generic sub elements
     */
    public static class FixedLengthArrayType extends Type {

        private final Type elementType;
        private final int length;

        public FixedLengthArrayType(Types types, Type elementType, int length) {
            super(types, String.format("%s[%d]", elementType, length), true);
            this.elementType = elementType;
            this.length = length;
            assert !elementType.isGeneric();
        }

        public int getLength() {
            return length;
        }

        public Type getElementType() {
            return elementType;
        }

        @Override
        public Optional<Integer> getNumberOfBlastedVariables() {
            return Optional.of(length * elementType.getNumberOfBlastedVariables().get());
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
    }

    public static class TupleType extends Type {

        public final List<Type> elementTypes;

        public TupleType(Types types, List<Type> elementTypes) {
            super(types, String.format("(%s)", elementTypes.stream().map(Type::toString).collect(Collectors.joining(", "))), true);
            this.elementTypes = elementTypes;
            assert elementTypes.stream().noneMatch(Type::isGeneric);
        }

        @Override
        public Optional<Integer> getNumberOfBlastedVariables() {
            return Optional.of(elementTypes.stream().mapToInt(t -> t.getNumberOfBlastedVariables().get()).sum());
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
    }

    public boolean isVar() {
        return false;
    }
}

package nildumu.eval;

/**
 * The type that integer types will be expressed as
 */
public enum IntegerType {
    INT(32),
    SHORT(16),
    BYTE(8);

    public final int width;
    IntegerType(int width) {
        this.width = width;
    }

    public String toJavaTypeName(){
        return name().toLowerCase();
    }
}

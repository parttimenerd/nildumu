package nildumu;

public class NildumuError extends RuntimeException {

    public NildumuError(String message) {
        super(message);
    }

    public NildumuError(RuntimeException error) {
        super(error);
    }
}

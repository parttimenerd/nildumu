package nildumu.mih;

import nildumu.NildumuError;

public class MethodInvocationHandlerInitializationError extends NildumuError {

    public MethodInvocationHandlerInitializationError(String message) {
        super("Error initializing the method invocation handler: " + message);
    }
}

package nildumu.util;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamWithActionOnClose extends InputStream {

    private InputStream input;
    private Runnable actionOnClose;

    public InputStreamWithActionOnClose(InputStream input, Runnable actionOnClose) {
        this.input = input;
        this.actionOnClose = actionOnClose;
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return input.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return input.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return input.skip(n);
    }

    @Override
    public int available() throws IOException {
        return input.available();
    }

    @Override
    public void mark(int readlimit) {
        input.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        input.reset();
    }

    @Override
    public boolean markSupported() {
        return input.markSupported();
    }

    @Override
    public void close() throws IOException {
        input.close();
        actionOnClose.run();
    }
}

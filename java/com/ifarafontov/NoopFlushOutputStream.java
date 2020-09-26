package com.ifarafontov;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The sole purpose of this class is to make flush() call a no-op </br>
 * because Transit writer calls flush() after each write </br>
 * effectively disabling buffering. See </br>
 * https://github.com/cognitect/transit-java/blob/master/src/main/java/com/cognitect/transit/impl/WriterFactory.java </br>
 * getJsonInstance() method.
 */
public final class NoopFlushOutputStream extends OutputStream {
    private final BufferedOutputStream m_out;

    public NoopFlushOutputStream(BufferedOutputStream out) {
        m_out = out;
    }

    public int hashCode() {
        return m_out.hashCode();
    }

    public void write(int b) throws IOException {
        m_out.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        m_out.write(b, off, len);
    }

    public void write(byte[] b) throws IOException {
        m_out.write(b);
    }

    public boolean equals(Object obj) {
        return m_out.equals(obj);
    }

    public void flush() throws IOException {
       //no-op
    }

    public void realFlush() throws IOException {
        m_out.flush();
    }

    public void close() throws IOException {
        m_out.close();
    }

    public String toString() {
        return m_out.toString();
    }
    

}

package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import org.teavm.classlib.java.nio.channels.spi.TAbstractInterruptibleChannel;

/** compat: java.nio.channels.Channels (missing in TeaVM 0.16.0): stream/channel adapters. */
public final class TChannels {
    private TChannels() {
    }

    public static ReadableByteChannel newChannel(InputStream in) {
        if (in == null) throw new NullPointerException("in");
        return new ReadableByteChannelImpl(in);
    }

    public static WritableByteChannel newChannel(OutputStream out) {
        if (out == null) throw new NullPointerException("out");
        return new WritableByteChannelImpl(out);
    }

    public static InputStream newInputStream(ReadableByteChannel ch) {
        return new InputStream() {
            private final byte[] one = new byte[1];

            @Override
            public int read() throws IOException {
                int n = read(one, 0, 1);
                return n <= 0 ? -1 : one[0] & 0xff;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) return 0;
                int n = ch.read(ByteBuffer.wrap(b, off, len));
                return n == 0 ? -1 : n;
            }

            @Override
            public void close() throws IOException {
                ch.close();
            }
        };
    }

    public static OutputStream newOutputStream(WritableByteChannel ch) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[] {(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ByteBuffer buf = ByteBuffer.wrap(b, off, len);
                while (buf.hasRemaining()) ch.write(buf);
            }

            @Override
            public void close() throws IOException {
                ch.close();
            }
        };
    }

    public static Reader newReader(ReadableByteChannel ch, String csName) {
        return new java.io.InputStreamReader(newInputStream(ch), Charset.forName(csName));
    }

    public static Writer newWriter(WritableByteChannel ch, String csName) {
        return new java.io.OutputStreamWriter(newOutputStream(ch), Charset.forName(csName));
    }

    static final class ReadableByteChannelImpl extends TAbstractInterruptibleChannel implements ReadableByteChannel {
        private final InputStream in;
        private byte[] buf = new byte[0];

        ReadableByteChannelImpl(InputStream in) {
            this.in = in;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (!isOpen()) throw new java.nio.channels.ClosedChannelException();
            int len = dst.remaining();
            if (len == 0) return 0;
            if (dst.hasArray()) {
                int n = in.read(dst.array(), dst.arrayOffset() + dst.position(), len);
                if (n > 0) dst.position(dst.position() + n);
                return n;
            }
            if (buf.length < len) buf = new byte[len];
            int n = in.read(buf, 0, len);
            if (n > 0) dst.put(buf, 0, n);
            return n;
        }

        @Override
        protected void implCloseChannel() throws IOException {
            in.close();
        }
    }

    static final class WritableByteChannelImpl extends TAbstractInterruptibleChannel implements WritableByteChannel {
        private final OutputStream out;

        WritableByteChannelImpl(OutputStream out) {
            this.out = out;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (!isOpen()) throw new java.nio.channels.ClosedChannelException();
            int len = src.remaining();
            if (src.hasArray()) {
                out.write(src.array(), src.arrayOffset() + src.position(), len);
                src.position(src.position() + len);
            } else {
                byte[] b = new byte[len];
                src.get(b);
                out.write(b);
            }
            return len;
        }

        @Override
        protected void implCloseChannel() throws IOException {
            out.close();
        }
    }
}

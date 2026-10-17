package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.teavm.classlib.java.nio.channels.spi.TAbstractInterruptibleChannel;

/**
 * compat: java.nio.channels.FileChannel (missing in TeaVM 0.16.0). Abstract shape only (no open()/map());
 * concrete channels are provided by the application (e.g. over RandomAccessFile).
 */
public abstract class TFileChannel extends TAbstractInterruptibleChannel
        implements java.nio.channels.SeekableByteChannel, TGatheringByteChannel, TScatteringByteChannel {

    protected TFileChannel() {
    }

    @Override
    public abstract int read(ByteBuffer dst) throws IOException;

    @Override
    public abstract long read(ByteBuffer[] dsts, int offset, int length) throws IOException;

    @Override
    public final long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public abstract int write(ByteBuffer src) throws IOException;

    @Override
    public abstract long write(ByteBuffer[] srcs, int offset, int length) throws IOException;

    @Override
    public final long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public abstract long position() throws IOException;

    @Override
    public abstract TFileChannel position(long newPosition) throws IOException;

    @Override
    public abstract long size() throws IOException;

    @Override
    public abstract TFileChannel truncate(long size) throws IOException;

    public abstract void force(boolean metaData) throws IOException;

    public abstract long transferTo(long position, long count, java.nio.channels.WritableByteChannel target) throws IOException;

    public abstract long transferFrom(java.nio.channels.ReadableByteChannel src, long position, long count) throws IOException;

    public abstract int read(ByteBuffer dst, long position) throws IOException;

    public abstract int write(ByteBuffer src, long position) throws IOException;

    public abstract TFileLock lock(long position, long size, boolean shared) throws IOException;

    public final TFileLock lock() throws IOException {
        return lock(0L, Long.MAX_VALUE, false);
    }

    public abstract TFileLock tryLock(long position, long size, boolean shared) throws IOException;

    public final TFileLock tryLock() throws IOException {
        return tryLock(0L, Long.MAX_VALUE, false);
    }

    public static class MapMode {
        public static final MapMode READ_ONLY = new MapMode("READ_ONLY");
        public static final MapMode READ_WRITE = new MapMode("READ_WRITE");
        public static final MapMode PRIVATE = new MapMode("PRIVATE");

        private final String name;

        private MapMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

package org.teavm.classlib.java.nio.channels;

import java.io.IOException;

/** compat: java.nio.channels.FileLock (missing in TeaVM 0.16.0). */
public abstract class TFileLock implements AutoCloseable {
    private final TFileChannel channel;
    private final long position;
    private final long size;
    private final boolean shared;

    protected TFileLock(TFileChannel channel, long position, long size, boolean shared) {
        this.channel = channel;
        this.position = position;
        this.size = size;
        this.shared = shared;
    }

    public final TFileChannel channel() {
        return channel;
    }

    public java.nio.channels.Channel acquiredBy() {
        return channel;
    }

    public final long position() {
        return position;
    }

    public final long size() {
        return size;
    }

    public final boolean isShared() {
        return shared;
    }

    public final boolean overlaps(long position, long size) {
        return position + size > this.position && this.position + this.size > position;
    }

    public abstract boolean isValid();

    public abstract void release() throws IOException;

    @Override
    public final void close() throws IOException {
        release();
    }
}

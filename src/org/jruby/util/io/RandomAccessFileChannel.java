package org.jruby.util.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * TEAVM: a FileChannel implemented on top of java.io.RandomAccessFile.
 *
 * Used where upstream JRuby calls FileInputStream/FileOutputStream/RandomAccessFile.getChannel() or
 * Files.newByteChannel(file.toPath(), ...), none of which exist in TeaVM's class library. In the
 * browser the file lives in TeaVM's in-memory virtual file system.
 */
public final class RandomAccessFileChannel extends FileChannel {
    private final RandomAccessFile raf;
    private final boolean readable, writable, append;

    public RandomAccessFileChannel(RandomAccessFile raf, boolean readable, boolean writable, boolean append) {
        this.raf = raf;
        this.readable = readable;
        this.writable = writable;
        this.append = append;
    }

    /** Open a file following Ruby/POSIX open flags (create/truncate/append are applied here). */
    public static RandomAccessFileChannel open(File file, ModeFlags flags) throws IOException {
        boolean writable = flags.isWritable();
        if (!writable && !file.exists()) throw new FileNotFoundException(file.getPath());
        RandomAccessFile raf = new RandomAccessFile(file, writable ? "rw" : "r");
        if (writable && flags.isTruncate()) raf.setLength(0);
        return new RandomAccessFileChannel(raf, flags.isReadable(), writable, flags.isAppendable());
    }

    public static RandomAccessFileChannel forReading(File file) throws IOException {
        return new RandomAccessFileChannel(new RandomAccessFile(file, "r"), true, false, false);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (!readable) throw new NonReadableChannelException();
        int len = dst.remaining();
        if (len == 0) return 0;
        byte[] b = new byte[len];
        int n = raf.read(b, 0, len);
        if (n > 0) dst.put(b, 0, n);
        return n;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            int n = read(dsts[i]);
            if (n < 0) return total == 0 ? -1 : total;
            total += n;
            if (dsts[i].hasRemaining()) break;
        }
        return total;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!writable) throw new NonWritableChannelException();
        if (append) raf.seek(raf.length());
        int len = src.remaining();
        byte[] b = new byte[len];
        src.get(b);
        raf.write(b, 0, len);
        return len;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; i++) total += write(srcs[i]);
        return total;
    }

    @Override
    public long position() throws IOException {
        return raf.getFilePointer();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        raf.seek(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return raf.length();
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        if (!writable) throw new NonWritableChannelException();
        if (size < raf.length()) {
            long pos = raf.getFilePointer();
            raf.setLength(size);
            if (pos > size) raf.seek(size);
        }
        return this;
    }

    @Override
    public void force(boolean metaData) {
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        long saved = raf.getFilePointer();
        try {
            raf.seek(position);
            long left = Math.min(count, raf.length() - position), done = 0;
            byte[] b = new byte[8192];
            while (left > 0) {
                int n = raf.read(b, 0, (int) Math.min(b.length, left));
                if (n <= 0) break;
                ByteBuffer buf = ByteBuffer.wrap(b, 0, n);
                while (buf.hasRemaining()) target.write(buf);
                left -= n;
                done += n;
            }
            return done;
        } finally {
            raf.seek(saved);
        }
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        long saved = raf.getFilePointer();
        try {
            raf.seek(position);
            long done = 0;
            ByteBuffer buf = ByteBuffer.allocate(8192);
            while (done < count) {
                buf.clear();
                if (count - done < buf.capacity()) buf.limit((int) (count - done));
                int n = src.read(buf);
                if (n <= 0) break;
                raf.write(buf.array(), 0, n);
                done += n;
            }
            return done;
        } finally {
            raf.seek(saved);
        }
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        long saved = raf.getFilePointer();
        try {
            raf.seek(position);
            return read(dst);
        } finally {
            raf.seek(saved);
        }
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        long saved = raf.getFilePointer();
        try {
            raf.seek(position);
            int len = src.remaining();
            byte[] b = new byte[len];
            src.get(b);
            raf.write(b, 0, len);
            return len;
        } finally {
            raf.seek(saved);
        }
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) {
        return new NoopLock(this, position, size, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) {
        return new NoopLock(this, position, size, shared);
    }

    @Override
    public java.nio.MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        throw new IOException("memory-mapped files are not supported (TeaVM)");
    }

    @Override
    protected void implCloseChannel() throws IOException {
        raf.close();
    }

    private static final class NoopLock extends FileLock {
        private boolean valid = true;

        NoopLock(FileChannel channel, long position, long size, boolean shared) {
            super(channel, position, size, shared);
        }

        @Override
        public boolean isValid() {
            return valid && channel().isOpen();
        }

        @Override
        public void release() {
            valid = false;
        }
    }
}

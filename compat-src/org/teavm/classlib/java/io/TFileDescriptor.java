package org.teavm.classlib.java.io;

/** compat: java.io.FileDescriptor (missing in TeaVM 0.16.0). Only the three standard descriptors are "valid". */
public final class TFileDescriptor {
    public static final TFileDescriptor in = new TFileDescriptor(0);
    public static final TFileDescriptor out = new TFileDescriptor(1);
    public static final TFileDescriptor err = new TFileDescriptor(2);

    private final int fd;

    public TFileDescriptor() {
        this.fd = -1;
    }

    private TFileDescriptor(int fd) {
        this.fd = fd;
    }

    public boolean valid() {
        return fd >= 0;
    }

    public void sync() {
    }
}

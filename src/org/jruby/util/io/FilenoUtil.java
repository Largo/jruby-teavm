/*
 * TEAVM: FilenoUtil without reflection into sun.nio.ch / java.io.FileDescriptor and without native
 * (jnr-enxio, jnr-unixsocket, msvcrt) channels. In the browser there are no real file descriptors:
 * every IO gets a "fake" fileno from the internal counter and is tracked in the map below.
 * The original lives in upstream JRuby (core/src/main/java/org/jruby/util/io/FilenoUtil.java).
 */
package org.jruby.util.io;

import java.io.FileDescriptor;
import java.nio.channels.Channel;
import java.util.HashMap;

import jnr.posix.POSIX;

/**
 * Utilities for working with native fileno and Java structures that wrap them.
 */
public class FilenoUtil {
    public FilenoUtil(POSIX posix) {
    }

    public static FileDescriptor getDescriptorFromChannel(Channel channel) {
        return new FileDescriptor();
    }

    public ChannelFD getWrapperFromFileno(int fileno) {
        return filenoMap.get(fileno);
    }

    public void registerWrapper(int fileno, ChannelFD wrapper) {
        if (fileno == -1) return;
        filenoMap.put(fileno, wrapper);
    }

    public void unregisterWrapper(int fileno) {
        if (fileno == -1) return;
        filenoMap.remove(fileno);
    }

    // Used by testing. See test/jruby/test_io.rb, test_io_copy_stream_does_not_leak_io_like_objects
    public int getNumberOfWrappers() {
        return filenoMap.size();
    }

    public int getNewFileno() {
        return internalFilenoIndex++;
    }

    public static boolean isFake(int fileno) {
        return fileno < 0 || fileno >= FIRST_FAKE_FD;
    }

    public static int filenoFrom(Channel channel) {
        return -1;
    }

    public static int filenoFrom(FileDescriptor fd) {
        return -1;
    }

    public int filenoFromHandleIn(Channel channel, int flags) {
        return -1;
    }

    public int closeFilenoHandle(int fd) {
        return -1;
    }

    public static final int FIRST_FAKE_FD = 100000;
    protected int internalFilenoIndex = FIRST_FAKE_FD;
    private final HashMap<Integer, ChannelFD> filenoMap = new HashMap<>();
}

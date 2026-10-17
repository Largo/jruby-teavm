/*
 * TEAVM: ShellLauncher reduced to a throwing facade.
 *
 * A browser cannot start processes, so every launch entry point raises NotImplementedError.
 * The public API used by RubyKernel/RubyProcess/RubyIO/PopenExecutor is kept so callers compile.
 * The original implementation lives in upstream JRuby (core/src/main/java/org/jruby/util/ShellLauncher.java).
 */
package org.jruby.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channel;
import java.util.Collection;
import java.util.Map;

import org.jruby.Ruby;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.io.ModeFlags;

public class ShellLauncher {
    private static RuntimeException unsupported() {
        return Unsupported.feature("starting subprocesses");
    }

    public static String[] getCurrentEnv(Ruby runtime) { return new String[0]; }
    public static String[] getModifiedEnv(Ruby runtime, Collection mergeEnv, boolean clearEnv) { return new String[0]; }
    public static String[] getModifiedEnv(ThreadContext context, Collection mergeEnv, boolean clearEnv) { return new String[0]; }

    public static File findPathExecutable(Ruby runtime, String fname) { return null; }
    public static File findPathExecutable(Ruby runtime, String fname, IRubyObject pathObject) { return null; }
    public static File findPathExecutable(ThreadContext context, String fname, IRubyObject pathObject) { return null; }

    public static int runAndWait(Ruby runtime, IRubyObject[] rawArgs) { throw unsupported(); }
    public static long[] runAndWaitPid(Ruby runtime, IRubyObject[] rawArgs) { throw unsupported(); }
    public static long runWithoutWait(Ruby runtime, IRubyObject[] rawArgs) { throw unsupported(); }
    public static int runExternalAndWait(Ruby runtime, IRubyObject[] rawArgs, Map mergeEnv) { throw unsupported(); }
    public static long runExternalWithoutWait(Ruby runtime, IRubyObject env, IRubyObject prog, IRubyObject options, IRubyObject args) { throw unsupported(); }
    public static long runExternal(Ruby runtime, IRubyObject env, IRubyObject prog, IRubyObject options, IRubyObject args, boolean wait) { throw unsupported(); }
    public static String changeDirInsideJar(final Ruby runtime, final String arg) { return null; }
    public static long runExternalWithoutWait(Ruby runtime, IRubyObject[] rawArgs) { throw unsupported(); }
    public static int execAndWait(Ruby runtime, IRubyObject[] rawArgs) { throw unsupported(); }
    public static int execAndWait(Ruby runtime, IRubyObject[] rawArgs, Map mergeEnv) { throw unsupported(); }
    public static int runAndWait(Ruby runtime, IRubyObject[] rawArgs, OutputStream output) { throw unsupported(); }
    public static int runAndWait(Ruby runtime, IRubyObject[] rawArgs, OutputStream output, boolean doExecutableSearch) { throw unsupported(); }
    public static long[] runAndWaitPid(Ruby runtime, IRubyObject[] rawArgs, OutputStream output, boolean doExecutableSearch) { throw unsupported(); }

    public static long getPidFromProcess(Process process) {
        return -1;
    }

    public static POpenProcess popen(Ruby runtime, IRubyObject string, ModeFlags modes) throws IOException { throw unsupported(); }
    public static POpenProcess popen(Ruby runtime, IRubyObject[] strings, Map env, ModeFlags modes) throws IOException { throw unsupported(); }
    public static POpenProcess popen(Ruby runtime, IRubyObject string, Map env, ModeFlags modes) throws IOException { throw unsupported(); }

    public static boolean shouldUseShell(String command) { return false; }

    /** TEAVM: never instantiated (popen raises); keeps the shape RubyIO uses. */
    public static abstract class POpenProcess extends Process {
        public abstract Channel getInput();
        public abstract Channel getOutput();
        public abstract Channel getError();
        public abstract boolean hasOutput();
    }
}

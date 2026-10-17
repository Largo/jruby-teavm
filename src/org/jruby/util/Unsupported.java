package org.jruby.util;

import org.jruby.Ruby;
import org.jruby.exceptions.RaiseException;

/**
 * TEAVM: helper for features that cannot work in a browser build of JRuby (TeaVM): subprocesses,
 * native I/O, sockets, runtime class generation, Java integration. Raises NotImplementedError in
 * Ruby when a runtime is available, otherwise a plain UnsupportedOperationException.
 */
public final class Unsupported {
    private Unsupported() {}

    public static RuntimeException feature(String what) {
        String msg = what + " is not supported in this build of JRuby (TeaVM/browser)";
        Ruby runtime = Ruby.isGlobalRuntimeReady() ? Ruby.getGlobalRuntime() : null;
        if (runtime != null) return runtime.newNotImplementedError(msg);
        return new UnsupportedOperationException(msg);
    }
}

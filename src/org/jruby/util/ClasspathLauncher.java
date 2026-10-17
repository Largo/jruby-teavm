package org.jruby.util;

import org.jruby.Ruby;

/**
 * TEAVM: there is no JVM to launch in the browser; this facade only returns a descriptive command string.
 * The original implementation lives in upstream JRuby (core/src/main/java/org/jruby/util/ClasspathLauncher.java).
 */
public class ClasspathLauncher {
    public static String jrubyCommand(ClassLoader classLoader) {
        return "jruby";
    }

    public static String jrubyCommand(Ruby runtime) {
        return "jruby";
    }
}

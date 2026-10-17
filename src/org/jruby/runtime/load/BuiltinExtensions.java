package org.jruby.runtime.load;

/**
 * TEAVM: static table of the Java extension libraries that are compiled into this build.
 *
 * Upstream JRuby loads extensions by class name (JRuby::Util.load_ext, LoadService.reflectedLoad,
 * ClassExtensionLibrary) through Class.forName + reflection. An ahead-of-time compiler such as TeaVM
 * cannot follow computed names, so every loadable extension is listed here explicitly.
 */
public final class BuiltinExtensions {
    private BuiltinExtensions() {}

    /** @return a Library for the given extension class name, or null if it is not part of this build */
    public static Library forClassName(String className) {
        switch (className) {
            case "org.jruby.ext.jruby.JRubyLibrary": return new org.jruby.ext.jruby.JRubyLibrary();
            case "org.jruby.ext.jruby.JRubyUtilLibrary": return new org.jruby.ext.jruby.JRubyUtilLibrary();
            case "org.jruby.ext.rbconfig.RbConfigLibrary": return new org.jruby.ext.rbconfig.RbConfigLibrary();
            case "org.jruby.ext.date.DateLibrary": return new org.jruby.ext.date.DateLibrary();
            case "org.jruby.ext.coverage.CoverageLibrary": return new org.jruby.ext.coverage.CoverageLibrary();
            case "org.jruby.ext.zlib.ZlibLibrary": return new org.jruby.ext.zlib.ZlibLibrary();
            case "org.jruby.ext.etc.EtcLibrary": return new org.jruby.ext.etc.EtcLibrary();
            case "org.jruby.ext.fcntl.FcntlLibrary": return new org.jruby.ext.fcntl.FcntlLibrary();
            case "org.jruby.ext.cgi.escape.CGIEscape": return new org.jruby.ext.cgi.escape.CGIEscape();
            case "org.jruby.ext.pathname.PathnameLibrary": return new org.jruby.ext.pathname.PathnameLibrary();
            case "org.jruby.ext.bigdecimal.BigDecimalLibrary": return new org.jruby.ext.bigdecimal.BigDecimalLibrary();
            case "org.jruby.ext.monitor.MonitorLibrary": return (runtime, wrap) -> org.jruby.ext.monitor.MonitorLibrary.load(runtime);
            case "org.jruby.ext.io.nonblock.IONonBlock": return (runtime, wrap) -> org.jruby.ext.io.nonblock.IONonBlock.load(runtime);
            // Java integration, loaded by require 'java' (lib/ruby/stdlib/java.rb in stdlib.zip)
            case "org.jruby.javasupport.Java": return new org.jruby.javasupport.Java();
            // default gems with Java extensions, compiled from their sources (see gems-src/)
            case "org.jruby.ext.stringio.StringIOLibrary": return new org.jruby.ext.stringio.StringIOLibrary();
            case "org.jruby.ext.strscan.StringScannerLibrary": return new org.jruby.ext.strscan.StringScannerLibrary();
            default: return null;
        }
    }
}

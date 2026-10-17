package jrubyweb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jruby.Ruby;
import org.jruby.RubyInstanceConfig;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.builtin.IRubyObject;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Browser entry point for JRuby compiled with TeaVM.
 *
 * JS usage (ES module output):
 *   import { main, mountZip, bootAsync, rubyEvalAsync } from "./jruby.js";
 *   main([]);                                  // sets JRuby system properties
 *   mountZip(new Int8Array(await (await fetch("stdlib.zip")).arrayBuffer()), "/jruby");
 *   bootAsync(json => ...);                    // creates the Ruby runtime on the "ruby-main" green thread
 *   rubyEvalAsync("puts 1 + 1", json => ...)   // {"out", "result", "error"}, evaluated on "ruby-main"
 * boot()/rubyEval() are synchronous variants that run on the caller's JS turn (no blocking Ruby code).
 */
public class Main {
    public static final String HOME = "/jruby";

    private static Ruby runtime;
    private static final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    public static void main(String[] args) {
        // Read by org.jruby.util.cli.Options and RubyInstanceConfig when they are first initialized.
        System.setProperty("jruby.compile.mode", "OFF");
        System.setProperty("jruby.native.enabled", "false");
        System.setProperty("jruby.compile.invokedynamic", "false");
        System.setProperty("jruby.jit.threshold", "-1");
        System.setProperty("jruby.parser.prism", "false");
        System.setProperty("jruby.reify.variables", "false");
        // Java interfaces implemented in Ruby (blocks, Iface.impl, include Iface) use java.lang.reflect.Proxy, whose
        // classes TeaVM generates at build time; JRuby's default generates bytecode at runtime
        System.setProperty("jruby.interfaces.useProxy", "true");
        System.setProperty("os.name", "Linux");
        System.setProperty("os.arch", "x86_64");
        System.setProperty("os.version", "browser");
        System.setProperty("java.vm.name", "TeaVM");
        System.setProperty("java.vendor", "TeaVM");
        System.setProperty("java.version", "21");
        System.setProperty("java.specification.version", "21");
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("line.separator", "\n");
        System.setProperty("user.home", "/home");
        System.setProperty("user.name", "web");
        System.setProperty("user.dir", "/home");
        System.setProperty("java.io.tmpdir", "/tmp");
        System.setProperty("jruby.home", HOME);
        // uri:classloader: resources (JRuby's kernel .rb files) are served from this directory
        System.setProperty("jruby.classloader.root", HOME + "/classpath");
        // jcodings character tables (read by the patched org.jcodings.util.ArrayReader)
        System.setProperty("jcodings.tables.dir", HOME + "/jcodings/tables");
        // joda-time reads its compiled tz database from this folder (shipped in stdlib.zip, see scripts/mkstdlib.py);
        // the default zone is the browser's zone
        System.setProperty("org.joda.time.DateTimeZone.Folder", HOME + "/joda/tzdata");
        String zone = browserTimeZone();
        System.setProperty("user.timezone", zone == null || zone.isEmpty() ? "UTC" : zone);
        new File("/home").mkdirs();
        new File("/tmp").mkdirs();
    }

    @org.teavm.jso.JSBody(script = "try { return Intl.DateTimeFormat().resolvedOptions().timeZone || null; } catch (e) { return null; }")
    private static native String browserTimeZone();

    /** Unpack a zip (as JS Int8Array) into TeaVM's in-memory file system below {@code root}. */
    @JSExport
    public static int mountZip(Int8Array data, String root) {
        byte[] bytes = data.copyToJavaArray();
        int files = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buf = new byte[65536];
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File target = new File(root + "/" + entry.getName());
                if (entry.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                try (FileOutputStream out = new FileOutputStream(target)) {
                    int n;
                    while ((n = zip.read(buf)) > 0) out.write(buf, 0, n);
                }
                files++;
            }
        } catch (IOException e) {
            throw new RuntimeException("mountZip failed: " + e, e);
        }
        return files;
    }

    /** Create the Ruby runtime. Returns RUBY_DESCRIPTION-like text. */
    @JSExport
    public static String boot() {
        if (runtime != null) return describe();
        RubyInstanceConfig config = new RubyInstanceConfig();
        config.setCompileMode(RubyInstanceConfig.CompileMode.OFF);
        config.setNativeEnabled(false);
        config.setJitThreshold(-1);
        config.setDisableGems(true);
        config.setDisableDidYouMean(true);
        config.setDisableErrorHighlight(true);
        config.setDisableSyntaxSuggest(true);
        config.setLoadGemfile(false);
        config.setObjectSpaceEnabled(false);
        config.setCurrentDirectory("/home");
        config.setJRubyHome(HOME);
        PrintStream out = new PrintStream(new Tee(captured, System.out), true);
        PrintStream err = new PrintStream(new Tee(captured, System.err), true);
        System.setOut(out); // Java's System.out (java.lang.System.out.println from Ruby) goes to the output too
        config.setInput(new ByteArrayInputStream(new byte[0]));
        config.setOutput(out);
        config.setError(err);
        runtime = Ruby.newInstance(config);
        runtime.evalScriptlet(LAZY_JAVA);
        return describe();
    }

    /**
     * JRuby's Java integration (the classes of java-classes.txt) is loaded on first use instead of at boot, where it
     * would cost about 0.2 s: by require 'java', by the Java constant, or by Kernel#java/javax/javafx/com/org and
     * java_import, whose stubs below are replaced by the real methods when the integration loads.
     */
    private static final String LAZY_JAVA = String.join("\n",
            "Object.autoload :Java, 'java'",
            "module Kernel",
            "  %i[java javax javafx com org java_import].each do |name|",
            "    define_method(name) { |*args, &block| require 'java'; __send__(name, *args, &block) }",
            "  end",
            "end");

    private static String describe() {
        return runtime.evalScriptlet("RUBY_DESCRIPTION").toString();
    }

    /** Evaluate Ruby code; returns JSON {"out": stdout+stderr, "result": inspect or null, "error": message or null}. */
    @JSExport
    public static String rubyEval(String code) {
        if (runtime == null) boot();
        captured.reset();
        String result = null, error = null;
        try {
            IRubyObject value = runtime.evalScriptlet(code);
            result = value.inspect().toString();
        } catch (RaiseException e) {
            error = e.getException().getMetaClass().getName() + ": " + e.getException().message(runtime.getCurrentContext());
            try {
                IRubyObject bt = e.getException().backtrace();
                if (bt != null && !bt.isNil()) error += "\n" + bt.callMethod(runtime.getCurrentContext(), "join", runtime.newString("\n"));
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            error = javaError(t);
        }
        runtime.getGlobalVariables().get("$stdout").callMethod(runtime.getCurrentContext(), "flush");
        String out = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        return "{\"out\":" + json(out) + ",\"result\":" + json(result) + ",\"error\":" + json(error) + "}";
    }

    /** A Java exception that reached the top level, named like JRuby does: "Java::JavaLang::ArithmeticException: / by zero". */
    private static String javaError(Throwable t) {
        if (runtime.getObject().getConstantNoConstMissing(runtime.getCurrentContext(), "JavaUtilities") == null) return "Java " + t; // not loaded
        try {
            String name = JavaUtil.convertJavaToUsableRubyObject(runtime, t).getMetaClass().getName();
            String message = t.getMessage();
            return message == null || message.isEmpty() ? name : name + ": " + message;
        } catch (Throwable ignored) {
            return "Java " + t;
        }
    }

    /** Callback receiving the JSON result of {@link #rubyEvalAsync}. */
    @JSFunctor
    public interface ResultCallback extends JSObject {
        void accept(String json);
    }

    private static final class Request {
        final String code; // null = boot only
        final ResultCallback callback;

        Request(String code, ResultCallback callback) {
            this.code = code;
            this.callback = callback;
        }
    }

    private static final ArrayBlockingQueue<Request> requests = new ArrayBlockingQueue<>(1024);
    private static Thread rubyMain;

    /**
     * All asynchronous requests run, in order, on one long-lived TeaVM (green) thread, which is Ruby's main
     * thread. Blocking Ruby code (Fiber, Enumerator#next, Thread#join, Queue#pop, Mutex, sleep) needs a thread:
     * TeaVM can only suspend inside one, not inside a plain call from JavaScript.
     */
    private static void ensureRubyMain() {
        if (rubyMain != null) return;
        rubyMain = new Thread(() -> {
            while (true) {
                Request request;
                try {
                    request = requests.take();
                } catch (InterruptedException e) {
                    continue;
                }
                String result;
                try {
                    result = request.code == null ? "{\"description\":" + json(boot()) + "}" : rubyEval(request.code);
                } catch (Throwable t) {
                    result = "{\"out\":\"\",\"result\":null,\"error\":" + json("Java " + t) + "}";
                }
                request.callback.accept(result);
            }
        }, "ruby-main");
        rubyMain.start();
    }

    /** Boot on the Ruby main thread; the callback receives {"description": RUBY_DESCRIPTION}. */
    @JSExport
    public static void bootAsync(ResultCallback callback) {
        ensureRubyMain();
        requests.offer(new Request(null, callback));
    }

    /** Evaluate on the Ruby main thread (boots first if needed); callback receives the rubyEval JSON. */
    @JSExport
    public static void rubyEvalAsync(String code, ResultCallback callback) {
        ensureRubyMain();
        requests.offer(new Request(code, callback));
    }

    private static String json(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Writes to the capture buffer and forwards to the console. */
    static final class Tee extends OutputStream {
        private final OutputStream a, b;

        Tee(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int c) throws IOException {
            a.write(c);
            b.write(c);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {
            a.write(buf, off, len);
            b.write(buf, off, len);
        }

        @Override
        public void flush() throws IOException {
            a.flush();
            b.flush();
        }
    }
}

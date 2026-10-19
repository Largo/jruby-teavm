# NOTES: JRuby 10.1.2.0 on TeaVM 0.16.0, source-fork approach

Work log of every stub, patch and pruning step, and why. Everything in JRuby's own sources carries a
`// TEAVM:` comment. The JRuby part is in `jruby-teavm.patch` + `jruby-teavm.prune.txt`; dependency changes
are in `deps-patches/deps.patch`; JDK gap fillers for TeaVM are in `compat-src/` and `compat-ext/`.

## Layout of the fix

| Where | What | Why there |
|---|---|---|
| `jruby-teavm.patch` (92 files) | edits to JRuby core + 6 new JRuby classes + launcher | JRuby design issues (invokedynamic, runtime codegen, reflection by name, native IO) |
| `jruby-teavm.prune.txt` (301 files) | deleted areas: JIT backend, JMX, embed/JSR223/OSGi, FFI, sockets, Java codegen, main, ant | not usable in a browser; deleting beats stubbing |
| `compat-src/org/teavm/classlib/java/**` (35 classes) | missing JDK classes as TeaVM `T`-classes, plus `TCompatParker` (green-thread parking) | JDK API gaps of TeaVM; upstreamable to TeaVM |
| `compat-ext/**.java.add` (9), `**.java.sed` (7) | members appended to TeaVM's own `TRuntime`, `TThread`, `TCollections`, `TClass`, `TClassLoader`, `TPackage`, `TMethod`, `TArray`, `TCharset`; bug fixes in `TFileInputStream`, `TThread`, `TArrayBlockingQueue`, `TClass`, `TMethod`, `TConstructor`; JDK charset aliases in `TCharset` | missing methods on classes TeaVM has. Classes earlier on TeaVM's `-p` win (it reads class files in `-p` order), so the extended copies replace the stock ones |
| `deps-patches/src` (4 files) | patched copies of `jnr.posix.JavaFileStat`, `jnr.posix.JavaLibCHelper`, `jnr.constants.ConstantSet`, `org.jcodings.util.ArrayReader` | dependency code JRuby can't fix from its side; they precede the jars on `-p` |
| `gems-src/` | `stringio` 3.2.0 and `strscan` 3.1.8 Java sources (default gems) | their jars in jruby-stdlib have no `$POPULATOR`s; compiling them with JRuby's AnnotationBinder makes them bindable without reflection. `StringIO` drops its MethodHandle compatibility shims |
| `teavm-plugin/` | TeaVM compiler plugins (`META-INF/services`): `NoAssertionsPlugin`, `JavaClassesPolicy` (ReflectionPolicy), `JavaIntegrationPlugin` | TeaVM's `desiredAssertionStatus()` is `true`, so JRuby's `assert`s would run in the browser; reflection metadata, Proxy classes and `getEnclosingClass` analysis for the Java integration |
| `java-classes.txt` | the Java classes Ruby can use (JDK basics, demo classes, JBox2D, ZXing) | read by `scripts/gen-registry.sh` (class registry) and `JavaClassesPolicy` (reflection) |
| `demo-src/` | `demo.Mandelbrot`, `demo.Listener`; `demo.Canvas2D` (+ `Frame`, `Click`), `demo.Page` | the custom Java classes of the Java-integration demo and of the visual demo (canvas bridge over TeaVM JSO), compiled with JRuby |
| `setup/tools/resolve-demo-libs` | Gradle project: JBox2D 2.2.1.1 + ZXing core 3.5.4 -> `shared/jars/demo-libs` | third-party jars of the visual demo; `build.sh` copies them into `lib/runtime` (javac + TeaVM class path) |
| `web/` | REPL page, Java-interop page (`java.html`), visual demo (`visual.html`), smoke page, wasm page | |
| `test/screenshots.js` | Playwright script: both presets of `visual.html` -> `build/screenshots/*.png` + fps | |
| `THIRD-PARTY-NOTICES.txt` | JBox2D (BSD-2-Clause) and ZXing (Apache-2.0) notices, copied into `dist/` | their code is compiled into `jruby.js` |

## Build pipeline (scripts/compile.sh)

1. compat + `compat-ext` (TeaVM source + appended members) -> `build/compat`
2. deps-patches -> `build/deps-patches`
3. JRuby's annotation processor (`org.jruby.anno.AnnotationBinder`, compiled first, as in core/pom.rb)
4. javac of the whole patched tree + `gems-src` with `-processor org.jruby.anno.AnnotationBinder`
   (generates `org/jruby/gen/*$POPULATOR.java` + `annotated_classes.txt`)
5. `InvokerGenerator` writes the 2888 `$INVOKER$` classes. It is pruned from our tree together with its
   ASM-based `DumpingInvocationMethodFactory`, so it runs from the stock `jruby-base` jar placed *after* our
   classes on the classpath (build time only; our patched classes are what it inspects).
6. `scripts/gen-registry.sh` writes `org.jruby.gen.PopulatorRegistryImpl` (switch over 184 populator names,
   minus Ripper, Digest, JRubyObjectInputStream, `$CLASSPATH` and the java.time extensions) and
   `org.jruby.gen.JavaClassRegistryImpl` (switch over the names of `java-classes.txt`, returning class literals)
   plus their `META-INF/services` entries; then they compile.

Also part of the build: step 4 compiles `demo-src/` together with JRuby; `[0a]` copies `java-classes.txt` next to
the TeaVM plugin classes (`JavaClassesPolicy` reads it from TeaVM's `-p` classpath); `scripts/mkstdlib.py` adds
`lib/ruby/stdlib/java.rb` (`JRuby::Util.load_ext('org.jruby.javasupport.Java')`); `scripts/teavm.sh` patches one
line of TeaVM's JS runtime in the output (runtime fix 16). `build.sh` copies the third-party jars of the visual demo
(`shared/jars/demo-libs`: JBox2D, ZXing core) into `lib/runtime`, which puts them on javac's class path (for
`demo-src/`) and on TeaVM's `-p`, next to JRuby's own dependency jars.

## TeaVM analysis error rounds (JS target)

| Round | Errors | Distinct | What changed |
|---|---|---|---|
| 0 unmodified tree, minimal embed | 37 | 21 | (`Ruby.newInstance(...).evalScriptlet`) |
| 1 Ruby.java statics + JIT/JMX/embed/FFI/socket/codegen pruned, facades | 37 | 18 | analysis still stopped early |
| 2 compat locks/Semaphore/queues/atomic array/EIIE/Process | 181 | 76 | deeper boot visible |
| 3 static PopulatorRegistry (ServiceLoader) | 276 | 107 | all core methods reachable |
| 4 VarHandle/MethodHandle removal, StaticScope, invalidators, NBHM, IO channels compat, FilenoUtil/ShellLauncher/SelectExecutor facades | 113 | 65 | |
| 5 jnr-posix/jnr-constants deps-patches, Runtime/Thread compat-ext, BuiltinExtensions, uri:classloader -> VFS, many single sites | 22 | 15 | |
| 6 StringIO direct calls, joda `toTimeZone` paths, Watchable, IGV, rbconfig | 2 | 1 | |
| 7 `RubyDateFormatter.formatWeekYear` without GregorianCalendar | **0** | 0 | jruby.js written (64 MB debug) |

wasm-gc afterwards: 1 error (`URLClassLoader` super class of the JRubyClassLoader family must resolve for
wasm's static types) -> `TURLClassLoader` shape-only compat -> 0.

Java integration (branch `java-integration`), JS target:

| Round | Errors | What changed |
|---|---|---|
| J0 `Java.load` at boot, `getProxyClass`/`getInstance` restored, Java populators registered, class list + ReflectionPolicy | 14 | `MethodHandles.lookup`/`invokeExact` (reified Ruby subclasses), `ClassLoader.loadClass`/`getResource`/`registerAsParallelCapable`, `Method.isDefault`, `Class.getModule` (backport9), `Class.isAnonymousClass`, `Array.newInstance(Class, int[])`, `Package.isSealed`, `ObjectOutputStream`/`ObjectInputStream` (Marshal of Java objects) |
| J1 compat-ext members, ConcreteJavaProxy/JavaProxy/Java.isAccessible edits | 2 | `Double.isNaN`/`isFinite` "native method has no implementation": reflectable natives |
| J2 policy skips natives without a generated body | **0** | jruby.js 20.3 MB |

wasm-gc: 3 errors (`Long.divideUnsigned` & co. "not annotated with Import": natives again) -> on wasm-gc the
policy makes no native method reflectable (`JavaIntegrationPlugin` records the target) -> 0.

Visual demo (branch `visual`): JBox2D 2.2.1.1 and ZXing core 3.5.4 on the class path, 27 more lines in
`java-classes.txt` (`demo.Canvas2D` + 2 interfaces, `demo.Page`, 10 JBox2D classes, 13 ZXing classes) and the
`TCharset` compat members (runtime fix 25): **0** errors on the first round, JS and wasm-gc. Neither library uses
reflection, class loading or AWT. TeaVM's JS analysis took 126 to 222 s in three builds against 162 s for `main` on
the same busy machine, i.e. no measurable cost.
`jruby.js` grew by 472,016 bytes (20,377,404 -> 20,849,420; gzip -9: 3,112,069 -> 3,229,558, +117 KB).

## Runtime fixes (headless Chromium), in the order they appeared

1. `LoggerFactory` builds loggers by reflection -> always `StandardErrorLogger`.
2. `RubyModule.checkAliasFrameAccesses` reads `@JRubyMethod` reflectively -> skipped when metadata is missing.
3. `OutputStrings.getVersionString` does `REVISION.substring(0, 10)` -> generated `Constants.java` carries the full SHA.
4. TeaVM's `Collections.EMPTY_MAP.clear()` throws (JDK: no-op) -> `RubyClass.addInvalidatorsAndFlush` skips empty caches.
5. **Booted**: `puts 1 + 1` -> `2`.
6. jcodings tables (`/tables/*.bin`, `getResourceAsStream`) -> patched `ArrayReader` falls back to
   `${jcodings.tables.dir}`; the tables ship inside `stdlib.zip` (`/jruby/jcodings/tables`).
7. `Sprintf` float formatting used `NumberFormat`, which in TeaVM loses the integer digits for `Locale.US`
   (`1.5` printed as `0.0`) -> `Double.toString` feeds the same digit scanner.
8. Blocking code (Fiber, `Enumerator#next`, `Thread#value`) needs a TeaVM thread: `rubyEvalAsync` runs the
   eval on a `java.lang.Thread` (TeaVM green thread) and calls back with the JSON result.
9. `-O 2` (ADVANCED) miscompiles some paths ("Cannot read properties of undefined (reading 'cIP')" in
   `String#upcase` with non-ASCII and in ERB). The production build uses the default SIMPLE level + `-m`.
10. wasm-gc: `Helpers.errnoFromException` rethrow-and-multi-catch produced invalid `br_on_cast` -> instanceof chain.
11. wasm-gc: the loader's `createWeakRef` does `new WeakRef(null)` for `new WeakReference(null)` -> the copied
    loader returns an empty ref for null (sed in build.sh).
12. joda-time: no tz database resources. `DateTimeZone.setProvider` needs `SecurityManager.checkPermission`
    (missing), so instead the compiled tz database from the joda jar ships in `stdlib.zip` and
    `org.joda.time.DateTimeZone.Folder` points at it; `user.timezone` is the browser's zone (`Intl`).
13. TeaVM bug: `FileInputStream.read()` sign-extends (`buffer[0]` instead of `buffer[0] & 0xFF`), so
    `DataInputStream` hit EOF on any byte >= 0x80 (joda zone files) -> `compat-ext/.../TFileInputStream.java.sed`.

14. Threads, round 2. Ruby's main thread is now one long-lived TeaVM thread ("ruby-main") that takes eval
    requests from a queue, so `Thread.main == Thread.current` and thread locals persist between evals. Locks,
    conditions and semaphores really block (parking in `TCompatParker`), which made `Queue#pop`, `Mutex`
    held across `sleep`, `ConditionVariable` and `Thread#wakeup` work. Finding the causes took several TeaVM
    0.16.0 bugs and pitfalls:
    * `ArrayBlockingQueue`'s wait handler resumes a thread without restoring `Thread.currentThread()`. It
      resumes an interrupted waiter synchronously on the interrupter's stack, and it clears the interrupt
      handler of the wrong thread. Fixed in `compat-ext/.../TArrayBlockingQueue.java.sed`.
    * `Thread.sleep` resumes after an interrupt with the wrong current thread. On the JS backend,
      `Thread.start()` leaves `currentThread` pointing at the new thread in the starter. Both are fixed in
      `TThread.java.sed`.
    * `Object.wait()` does not re-acquire the monitor when interrupted, so the next monitor exit throws
      IllegalMonitorStateException. The compat locks therefore avoid `Object.wait` altogether.
    * A compat helper class that references `TThread` must itself be named `T...`. A plain class
      (`CompatParker`) is not run through TeaVM's classlib renaming and gets its own unrelated copy of
      `TThread`, with a separate `currentThread`.
    * `Semaphore.tryAcquire(Long.MAX_VALUE, ms)`, which JRuby uses for `sleep` without a timeout, overflowed
      the deadline.
15. Assertions: `teavm-plugin/NoAssertionsPlugin` installs TeaVM's own `AssertionRemoval` transformer. One JRuby
    assert (`Thread.currentThread() == getNativeThread()`) had fired.

Java integration (branch `java-integration`):

16. Every eval failed with "Cannot read properties of undefined (reading 'length')" in `Class.getDeclaredClasses`
    (JRuby's `MethodGatherer.installInnerClasses`): TeaVM 0.16.0's JS runtime (`$rt_reflection`) sets
    `reflection.innerClasses` only for classes that have inner classes, unlike fields/methods -> `scripts/teavm.sh`
    adds `innerClasses: []` to the runtime's default reflection object in the output (checked by grep).
17. `java.lang.Math` was "missing class name java.lang..Math": TeaVM's `Class.getPackage()` names the package
    `java.lang.` (trailing dot), JRuby builds its package modules from it -> `TClass.java.sed`.
18. `Java::JavaLang::Class` extensions alias `get_resource`/`get_field`, so java.lang.Class itself must be
    reflectable -> listed with a member list; `TClass.getResource` (null) and `isAnonymousClass` added.
19. `java.util.Collections` failed: proxying the class of `Collections.EMPTY_LIST` proxies its enclosing class
    `java.util.TemplateCollections`, whose `getName()` was null. TeaVM's dependency analysis does not model
    `getEnclosingClass`/`getDeclaringClass`, so no name metadata was emitted -> `JavaIntegrationPlugin`
    propagates the owner classes (not for `org.teavm.*`, see 23).
20. `Integer.parseInt('abc')` raised TypeError "expected [java.lang.String]": TeaVM's `Method.invoke` lets the
    target's NumberFormatException escape unwrapped, and JRuby takes an IllegalArgumentException from `invoke` for
    an argument mismatch -> `TMethod.java.sed`/`TConstructor.java.sed` wrap in InvocationTargetException (JDK).
21. `Comparator.impl { }` failed with "reading '$allocate2'" in the `jruby.interfaces.useProxy` path: the
    anonymous impl class (`RubyClass.newClass`) has no allocator yet -> `JavaInterfaceTemplate` uses the
    superclass's.
22. Default methods called on a Ruby implementation (`comparator.reversed`) cannot work with Proxy (the Ruby
    object is not an instance; passing the proxy would recurse through the handler) -> documented TypeError.
23. wasm-gc: "Compiling function Fiber$PlatformObject::<init> failed": modelling `getEnclosingClass` let
    TeaVM runtime classes (whose objects sit on the coroutine stacks) reach `Class.getDeclaredClasses` and
    `Class.newInstance`, which linked `PlatformObject.<init>` (a JS reference type on wasm-gc) -> owners in
    `org.teavm.*` are skipped. Then "Import teavm.currentTimeMillis requires a callable": reflective callers of
    natives are not provided on wasm-gc -> no reflectable natives there (`Math.sqrt` works on JS only).
24. `Java.load` at boot cost 180-190 ms (proxies for Object, Class, Field, Method, Constructor, ENV_JAVA, the
    Ruby part) -> loaded on first use: `java.rb` in stdlib.zip, `Object.autoload :Java`, Kernel stubs in
    `jrubyweb.Main`. Boot is back to about 3.0 s.

Visual demo (branch `visual`: `web/visual.html`, `demo.Canvas2D`, JBox2D, ZXing):

25. ZXing's `CharacterSetECI` registers the QR ECIs in its static initializer with `Charset.isSupported(name())`,
    where the names are the JDK's historical ones (`UTF8`, `ISO8859_1`, `ASCII`, `UnicodeBigUnmarked`). TeaVM
    0.16.0's `Charset` has no `isSupported`, and its registry knows the six standard charsets only by canonical name
    (`UTF-8`). Without aliases no ECI would be registered, so `CHARACTER_SET => "UTF-8"` would produce QR codes
    without the UTF-8 ECI (decoders then guess the charset) and the decoder would reject codes that have one
    (`FormatException` for an unknown ECI) -> `compat-ext/.../nio/charset/TCharset.java.add` (`isSupported`,
    alias table) + `.sed` (registers the aliases). Checked: the demo's codes carry the ECI (JVM ZXing reads the
    screenshots with symbology identifier `]Q2`) and "Grüezi from JRuby ✓ 日本" round-trips (test case).
26. Nothing else needed a fix. Two points that were open questions and turned out to work unchanged: JRuby adopts
    the TeaVM green threads that call Ruby blocks (the canvas animation thread `canvas-<id>` appears in
    `Thread.list`; a click without an animation runs its block on a short-lived thread of its own), and a
    `requestAnimationFrame` callback (plain JS, outside any thread) can wake a parked green thread through the
    compat `Semaphore`, whose wake-up goes through `TCompatParker` (postponed, right `Thread.currentThread()`).
27. Running a snippet again redefines its constants (`SCALE`, `Look`), which prints "warning: already initialized
    constant" into the output -> `visual.html` sets `$VERBOSE = nil` once after boot.

Found later, by the chunkybacon course (pages left open between evaluations):

28. TeaVM bug ([konsoletyper/teavm#1331](https://github.com/konsoletyper/teavm/issues/1331)): its
    `WeakReferenceDependencyListener` makes a new dependency node on every `methodReached` and connects the queue
    argument of `WeakReference.<init>` instead of the reference, so to the analysis `ReferenceQueue.poll()` returns
    nothing, and a method called only on polled references is left out of the class. JRuby's
    `ObjectProxyCache.Segment.expunge` calls `((EntryRef) queue.poll()).hash()`: after a JS garbage collection
    with Java proxies in the cache, the next evaluation failed with `TypeError: d.bcN is not a function` (29 of 30
    runs of 2000 singleton-classed Java objects with 400 ms gaps). `ObjectSpace`, `WeakIdentityHashMap`,
    `WeakValuedMap`, `WeakKeyValueIdentityMap`, `ConcurrentWeakHashMap` and `JarCache` poll the same way ->
    `JavaIntegrationPlugin.QueuedReferences` connects every Weak/Soft/PhantomReference made with a queue to what
    `poll()`/`remove()` return. The test cases "java: proxies after an idle gap" wait 500 ms first (`delay` in
    cases.json): back-to-back cases never give the garbage collector a turn.

## JRuby source changes, by area (all marked `// TEAVM:`)

**Runtime class generation / JIT (pruned)**: `compiler/*` except small interfaces (`JITCompiler` is an inert
facade: methods always stay interpreted), `ir/targets/**`, `ir/Compiler`, `java/codegen/*` except `Reified`,
`anno/IndyBinder`, `anno/InvokerGenerator`, `InvocationMethodFactory`, `DumpingInvocationMethodFactory`,
`InvokeDynamicMethodFactory`, `DynamicScopeGenerator`, `MathLinker`, SwitchPoint invalidators.
`RubyClass.reify` is a no-op; `RubyObjectSpecializer` returns the plain allocator; `MethodFactory.createFactory`
throws; `IRClosure`/`IRRuntimeHelpers` always create interpreted bodies; `Ruby.tryCompile`/`runNormally`
bytecode paths removed; `CompiledScriptLoader` raises.

**java.lang.invoke**: VarHandle fields in `Ruby`, `RubyModule` (via `scripts/varhandle.py`), `RubyHashLinkedBuckets`,
`VariableAccessor`/`AtomicVariableTable`/`VariableTableManager` (plain fields + `casStamp`), `StringSupport`/
`SipHashInline` (`readLongLE` byte loop instead of `byteArrayViewVarHandle`), `NonBlockingHashMapLong`
(HashMap-backed, same API), `TraceEventManager` (no MutableCallSite), `BlockBody`/`RubyModule` id-test handles,
`Helpers.LOOKUP`, `RubyProcess` NativeThread handles, `ManyVarsDynamicScope.CONSTRUCTOR`,
`StaticScope.construct` = `new ManyVarsDynamicScope(this, parent)`, `OptoFactory` (no MethodHandle constants,
`ObjectIdentityInvalidator`/`GenerationInvalidator`), `ConstantInvalidator` extends `ObjectIdentityInvalidator`.

**Ruby constructor**: no JRubyClassLoader, JMX beans, ThreadPoolExecutors (an `Executor` that starts a new
green thread), SecurityHelper, bundler, subspawn; `POSIXFactory.getJavaPOSIX`; Prism off in `ParserManager`;
`initJavaSupport` loads only `JRubyUtilLibrary` (Java integration loads on `require 'java'`).

**Reflection by name**: `RubyModule.loadPopulatorFor` -> `PopulatorRegistry` (ServiceLoader, resolved by TeaVM
at compile time); `BuiltinExtensions` table for `JRuby::Util.load_ext`/`LoadService.reflectedLoad`
(jruby, rbconfig, date, coverage, zlib, etc, fcntl, cgi/escape, pathname, bigdecimal, monitor, io/nonblock,
stringio, strscan); `ClassExtensionLibrary.tryFind` returns null; `CompilerPass.createPassInstance` switch;
`@JRubyConstant` fields of `RubyEncoding`, `RubyConverter`, `RubyIOBuffer`, `RubyBigDecimal` become generated
`defineJRubyConstants` methods (`scripts/gen-constants.py`); `LoggerFactory`; `RubySignal` facade;
`EncodingService` console charset; `SeekableByteChannelImpl` reflective fields off; `Java.trySetAccessible` false.

**Java integration** (for the classes of `java-classes.txt`): JRuby's integration runs unchanged on TeaVM
reflection, except: `JavaSupport.loadJavaClass` looks names up in the generated `JavaClassRegistry` (new; closed
world, NameError hint "not in this build's java-classes.txt"); interface implementations use
java.lang.reflect.Proxy (`jruby.interfaces.useProxy`, set by the launcher; `newProxyInterfaceImpl` passes a non-null
loader); `RealClassGenerator`/`BlockInterfaceGenerator` stay pruned, so Ruby subclasses of Java classes raise
TypeError in `ConcreteJavaProxy.NewMethod`; `MethodGatherer` and `Java.isAccessible` without `java.lang.Module`;
`JavaInterfaceTemplate` allocator fallback (runtime fix 21); `JavaProxy.marshal_dump/load` raise (no Java
serialization); `Java.load` skips `$CLASSPATH` and the java.time extensions; `BuiltinExtensions` has
`org.jruby.javasupport.Java` for `require 'java'`.

**IO**: `RandomAccessFileChannel` (new: FileChannel over RandomAccessFile) replaces `getChannel()` /
`Files.newByteChannel(file.toPath())` in `RegularFileResource`, `NullDeviceResource`; `LoadServiceResource`
and `Parser` read through streams; `RubyGlobal.initSTDIO` always wraps the configured Java streams;
`ChannelFD.nativeDeviceChannel` raises (no jnr-enxio devices); `FilenoUtil` facade (fake filenos only);
`SelectExecutor` facade (everything is ready); `SelectorFactory`, `PosixShim.pipe`, `IO::Buffer.map` raise;
`ShellLauncher` facade + `Helpers.execWithoutReturn` raise (no processes); `ClasspathLauncher` facade.

**Resources**: `uri:classloader:` maps onto `${jruby.classloader.root}` (`URLResource`, `LoadService.getClassPathResource`),
so JRuby's kernel `.rb` files (`core/src/main/ruby`) are plain files in TeaVM's VFS (`/jruby/classpath`). This
needed 3 small patches instead of a TeaVM ResourceSupplier plugin plus URL emulation.

**Backtraces**: `NoStackWalker` (empty Java stack) + `BacktraceData` builds Ruby backtraces from JRuby's own
frame stack when no Java frames exist.

**Misc**: `Math.multiplyHigh` inlined in `RubyFixnum`; `System.getenv()` -> empty environment; `RubyGC`/`RubyProcess.times`
without `java.lang.management`; `RubyTime`/`RubyDate` without `java.sql`, `Calendar` conversions and
`TimeZone`; `RubyDateFormatter.formatWeekYear` computed directly; `RubyEtc.uname` nodename `localhost`;
`ModeFlags.NOSHARE_DELETE` null; `OpenFile.rbW32WriteConsole` uses System.out; `JarFileResource` times null;
`RegularFileResource.canExecute`/`isSymLink` false; `RubyFileTest.identical?` via canonical paths;
`Helpers.exit` hard exit -> `MainExitException`.

## compat (TeaVM JDK gaps)

`java.lang`: ExceptionInInitializerError, Process, ThreadGroup, UnsatisfiedLinkError, IllegalThreadStateException,
ClassFormatError, UnsupportedClassVersionError; `Runtime.exec*` (throws IOException), `addShutdownHook`,
`maxMemory`, `halt`; `Thread.State`, `getState`, `threadId`, `setContextClassLoader`, `getThreadGroup`, `dumpStack`.
`java.util.concurrent`: locks.{Lock, ReadWriteLock, Condition, ReentrantLock, ReentrantReadWriteLock}. They
track an owner and a hold count with no atomics, because green threads only switch at suspension points.
Contended `lock()`, `Condition.await` and `Semaphore.acquire` park in `java.lang.TCompatParker` (FIFO,
timeouts, interrupt-safe), plus Semaphore, ConcurrentLinkedQueue,
CopyOnWriteArraySet, atomic.AtomicReferenceArray; `Collections.unmodifiableSortedSet`.
`java.nio.channels`: Channels (stream adapters), FileChannel/FileLock/MapMode (abstract), SelectableChannel,
Selector, SelectionKey, SocketChannel, spi.{AbstractInterruptibleChannel, AbstractSelectableChannel,
SelectorProvider}, Interruptible/Gathering/ScatteringByteChannel, a few exceptions. `java.io.FileDescriptor`,
`java.net.URLClassLoader` (shape only).
Reflection (Java integration): `Method.invoke`/`Constructor.newInstance` wrap exceptions in
InvocationTargetException, `Method.isDefault`, `Array.newInstance(Class, int...)`, `ClassLoader.loadClass`
(= `Class.forName`), `ClassLoader.getResource`/`Class.getResource` (null), `ClassLoader.registerAsParallelCapable`,
`Package.isSealed`, `Class.isAnonymousClass`, `Class.getPackage()` without trailing dot.
`java.nio.charset` (ZXing): `Charset.isSupported`, and the JDK's aliases of the six standard charsets (`UTF8`,
`ISO8859_1`, `latin1`, `ASCII`, `UnicodeBigUnmarked`, ...) for `Charset.forName`.

## deps-patches

* `jnr.posix.JavaFileStat`: java.io.File attributes only (no PosixFileAttributes/DosFileAttributes/toPath).
* `jnr.posix.JavaLibCHelper.getpid`: 1 (no ProcessHandle/ManagementFactory).
* `jnr.constants.ConstantSet`: static switch over the Linux constant enums (no getResource/Class.forName).
* `org.jcodings.util.ArrayReader`: tables from `${jcodings.tables.dir}` when not a resource.

## Known gaps

* Java integration only for the classes of `java-classes.txt` (no class loading at runtime); no Ruby subclasses
  of Java classes; one Java interface per Ruby object; no default methods on Ruby implementations; natives of
  TeaVM's class library partly (JS) or not (wasm-gc) reflectable; no java.time. No subprocesses, sockets, FFI,
  `IO.pipe`, `IO.select` on real fds, signals, `ObjectSpace.each_object` (disabled), Digest/OpenSSL/JSON/Psych/Ripper
  (Java exts not registered or excluded).
* Ruby threads are TeaVM green threads (cooperative). A busy loop in one thread starves the others until it
  blocks or sleeps.
* Code that blocks must run through `rubyEvalAsync` (the REPL and tests do it). `rubyEval` is synchronous.
* wasm-gc boots and passes all 54 non-blocking test cases (29 + 20 Java + 5 visual demo) through the synchronous API.
  Any suspension of a JRuby thread (Fiber, `Enumerator#next`, Queue, sleep, `Canvas2D#animate`, and the async eval
  worker itself) crashes TeaVM 0.16.0's wasm coroutine runtime with `Maximum call stack size exceeded` in
  `org.teavm.runtime.Fiber.reversePush`.
* Backtraces have file:line but every frame is labelled like a method (no `<class:...>`/`block in`), because the
  Java frames that carry that information do not exist.

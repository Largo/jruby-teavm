# JRuby in the browser (TeaVM): source-fork build

JRuby 10.1.2.0 (Ruby 4.0.0), compiled to JavaScript with TeaVM 0.16.0. It runs as the real JRuby interpreter
in the page, with the pure-Ruby standard library mounted from a zip into TeaVM's in-memory file system.
`require 'set'`, `'ostruct'`, `'erb'`, `'date'`, `'stringio'`, `'strscan'`, `'forwardable'`, `'shellwords'`
and others work. Ruby code can also call Java, for a fixed list of Java classes chosen at build time (JDK basics
and a demo class, see [Java integration](#java-integration-ruby-calling-java)), which CRuby and ruby.wasm
cannot do. That includes real third-party Java libraries from Maven Central: `visual.html` has Ruby drive a
JBox2D physics simulation and ZXing QR codes on a canvas (see [Visual demo](#visual-demo-ruby-driving-java-libraries)).
A WebAssembly GC build also boots (see Known gaps).

The approach is upstream-friendly. It is a patch against JRuby's own core sources (`jruby-teavm.patch`, plus
`jruby-teavm.prune.txt` listing the deleted files), compiled with plain `javac` and JRuby's own annotation
processor. No Maven and no bytecode rewriting are involved. The gaps in TeaVM's JDK emulation are filled in
`compat-src/` and `compat-ext/`, and four dependency classes are patched in `deps-patches/`.

## Reproduce

```bash
setup/fetch.sh --test       # once: downloads the inputs into ./shared (jars, JRuby + TeaVM sources, Playwright)
                            # (a ./shared from before the visual demo needs only: setup/fetch.sh --libs)
./build.sh --fresh          # pristine JRuby 10.1.2.0 sources + patch -> dist/   (about 4 to 6 min)
./build.sh --fresh --wasm   # also the WebAssembly GC variant in dist/wasm/
test/run-tests.sh           # headless Chromium on 127.0.0.1, checks the cases in test/cases.json
node test/screenshots.js    # visual.html in headless Chromium: both demos -> build/screenshots/*.png, fps
python3 -m http.server -b 127.0.0.1 -d dist 8080   # then open http://127.0.0.1:8080/ (REPL page)
                                                   # or http://127.0.0.1:8080/java.html (Ruby calling Java)
                                                   # or http://127.0.0.1:8080/visual.html (JBox2D + ZXing)
```

After `setup/fetch.sh` the build needs no network access. The inputs live in `./shared` (or wherever `SHARED=` points):
the JRuby source tag, the jruby-base and dependency jars, jruby-stdlib, the TeaVM CLI, and the two Java libraries of
the visual demo (`shared/jars/demo-libs`, resolved by the Gradle project `setup/tools/resolve-demo-libs`). Fetching
needs JDK 21, gradle, git, curl and unzip (plus npm for `--test`); building needs JDK 21 and python3. Heaps are capped
(`javac -J-Xmx4g`, `TEAVM_XMX=6g`).

`dist/` contains:

| File | Size | |
|---|---|---|
| `jruby.js` | 20.8 MB (3.2 MB gzip) | ES module, minified. Exports `main`, `mountZip`, `bootAsync`, `rubyEvalAsync`, `boot`, `rubyEval` |
| `stdlib.zip` | 2.0 MB | kernel `.rb` files, pure-Ruby stdlib and a few bundled gems (ostruct, logger, prime, matrix, ...), jcodings tables, joda tz data |
| `index.html` | | REPL: textarea, Run button (Ctrl+Enter), output |
| `java.html` | | Ruby calling Java: preset snippets, editable code, output, a canvas for the Java-computed Mandelbrot |
| `visual.html` | | Ruby driving Java libraries on a canvas: JBox2D physics (animated, click to drop boxes) and ZXing QR codes |
| `THIRD-PARTY-NOTICES.txt` | | licenses of JBox2D (BSD-2-Clause) and ZXing (Apache-2.0), which are compiled into `jruby.js` |
| `java-classes.txt` | | the Java class list of the build (shown on `java.html`) |
| `smoke.html` | | `smoke.html?code=...` evaluates one snippet and logs the result |
| `wasm/` | 43.8 MB `jruby.wasm` (9.6 MB gzip) | optional (`--wasm`). Same API, page uses the synchronous calls |

## Using it from JavaScript

```js
const jr = await import('./jruby.js');
const call = (f, ...a) => new Promise(r => f(...a, json => r(JSON.parse(json))));
jr.main([]);                                              // system properties for JRuby
jr.mountZip(new Int8Array(await (await fetch('stdlib.zip')).arrayBuffer()), '/jruby');
await call(jr.bootAsync);                                 // {description: "jruby 10.1.2.0 (4.0.0) ..."}, about 1 s
await call(jr.rubyEvalAsync, 'puts 1 + 1');               // {out: "2\n", result: "nil", error: null}
```

`bootAsync` and `rubyEvalAsync` queue work for one long-lived TeaVM green thread, "ruby-main", which is
Ruby's `Thread.main`. Requests run in order. Running on a thread is what lets Fiber, `Enumerator#next`,
`Thread`, `Queue`, `Mutex`, `ConditionVariable` and `sleep` block. `boot()` and `rubyEval(code)` are
synchronous variants that run on the caller's JS turn, for code that never blocks.

## Java integration (Ruby calling Java)

JRuby's own Java integration (`org.jruby.javasupport`, proxies, overload resolution, conversions) runs in the
page for the classes listed in `java-classes.txt`:

```ruby
require 'java'           # optional: java.*, Java::, java_import also load the integration on first use (~0.2 s)
list = java.util.ArrayList.new([5, 3, 9])
java.util.Collections.sort(list) { |a, b| b <=> a }   # a Ruby block as a java.util.Comparator
p list.to_a                                           # => [9, 5, 3]   (Java collections are Enumerable)

java_import java.math.BigDecimal
puts BigDecimal.new("0.1").add(BigDecimal.new("0.2")) # => 0.3

m = Java::Demo::Mandelbrot.new(72, 24)                # a Java class compiled into this build (demo-src/)
m.render { |y, row| puts row.map { |n| n > 20 ? "#" : "." }.join }   # Java calls the block for every row

begin
  java.lang.Integer.parseInt("forty-two")
rescue java.lang.NumberFormatException => e           # Java exceptions are rescuable
  puts e.class                                        # => Java::JavaLang::NumberFormatException
end
```

Covered by `test/cases.json` (the `java:` cases) and by the presets of `java.html`: `java.util.ArrayList.new`,
`java_import`, `Java::JavaLang::StringBuilder`, constructors and static/instance methods with JRuby's overload
resolution, static fields (`java.lang.Integer::MAX_VALUE`), conversion of String, Integer, Float, true/false and
nil both ways (BigInteger results become Ruby Integers, as on the JVM), collections as Enumerable (`each`, `map`,
`each_pair`, `to_a`, `[]`), Java exceptions (`rescue java.lang.ArithmeticException => e`, a bare `rescue`, uncaught
ones reported as `Java::JavaLang::X: message`), BigDecimal/BigInteger arithmetic, `AtomicLong`, Java arrays
(`[1, 2].to_java(:int)`, `Java::int[3, 4].new`), `java.lang.System.out.println` (into the page output), blocks
and procs where a Java interface is expected (`Collections.sort(list) { }`, `forEach { }`, `computeIfAbsent { }`),
`Iface.impl { }`, Ruby classes that `include` a Java interface, and Java code calling those Ruby objects back.
The same 20 cases also pass on wasm-gc.

How it works:

* **Class list** `java-classes.txt`: one binary class name per line, optionally followed by the names of the
  members to expose (`java.lang.System currentTimeMillis getProperty ...`). `scripts/gen-registry.sh` turns it
  into `org.jruby.gen.JavaClassRegistryImpl`, a switch from name to class literal that JRuby finds through
  `ServiceLoader` (`org.jruby.javasupport.JavaClassRegistry`, like the populator registry). With a registry
  installed, `JavaSupport.loadJavaClass` loads only those classes (closed world).
* **Reflection metadata** `teavm-plugin/jrubyweb/teavm/JavaClassesPolicy` is a TeaVM `ReflectionPolicy`: for every
  listed class and all its superclasses and superinterfaces, the public constructors, methods and fields become
  reflectable, so JRuby's unchanged reflective binding (`MethodGatherer`, `Method.invoke`,
  `Constructor.newInstance`, `Field.get`) works. Every listed interface also gets a java.lang.reflect.Proxy class,
  generated at build time for the interface list JRuby uses (`[Iface, RubyObjectHolderProxy]`).
* **Ruby implementations of Java interfaces** (blocks, `Iface.impl`, `include Iface`) go through
  java.lang.reflect.Proxy (`jruby.interfaces.useProxy`), so the runtime bytecode generators stay pruned.
* **Lazy loading**: the integration is not loaded at boot (it costs about 0.2 s). `require 'java'` (a two-line
  `java.rb` in stdlib.zip), the `Java` constant (autoload) and stubs for `Kernel#java`/`javax`/`javafx`/`com`/`org`
  and `java_import` (in `jrubyweb.Main`) load it.
* TeaVM fixes needed on the way (all small, see NOTES.md): `Method.invoke`/`Constructor.newInstance` wrap
  exceptions in InvocationTargetException, `Class.getPackage()` without the trailing dot, a JS-runtime default for
  `Class.getDeclaredClasses()` (scripts/teavm.sh), dependency modelling of `getEnclosingClass`/`getDeclaringClass`
  and of what `ReferenceQueue.poll()` returns ([teavm#1331](https://github.com/konsoletyper/teavm/issues/1331):
  without it JRuby's proxy cache failed after a garbage collection) (`teavm-plugin/.../JavaIntegrationPlugin`),
  and a few missing JDK methods (`Method.isDefault`,
  `Array.newInstance(Class, int...)`, `ClassLoader.loadClass`, ...).

**Adding a class**: append its binary name to `java-classes.txt` (nested classes with `$`, e.g.
`java.util.Map$Entry`), put your own Java sources under `demo-src/` (any package; they are compiled with JRuby)
and run `./build.sh`. The class must exist in TeaVM's class library (it has no `java.time`, so
`java.time.LocalDate` is not available). Classes of a third-party library: add its Maven coordinates to
`setup/tools/resolve-demo-libs/build.gradle` and run `setup/fetch.sh --libs` (the jars land in
`shared/jars/demo-libs`, which `build.sh` puts on javac's and TeaVM's class path), then list the classes. The library
must be pure Java within TeaVM's class library: no AWT/Swing/`java.awt.image`, no class loading by name. Each class
costs size: its public methods are compiled in. The integration itself adds 1.7 MB to `jruby.js` (0.2 MB gzip), the
48 classes of the list as first measured another 0.55 MB (0.1 MB gzip); `jruby.wasm` grows by 8.3 MB (1.6 MB gzip)
in all. The visual demo's 27 classes, with the library code they pull in, add 0.47 MB (0.12 MB gzip).

**Limits**:
* Only listed classes can be loaded by name: `java.util.LinkedList` raises NameError ("not in this build's
  java-classes.txt"). Objects of other classes returned by Java code still work through their listed supertypes
  and interfaces.
* No Ruby subclasses of Java classes (`class Foo < java.util.ArrayList` needs a class generated at runtime).
* A Ruby object can implement one listed Java interface. A block passed where an unlisted interface is expected
  (`list.removeIf { }` wants `java.util.function.Predicate`), or a class that includes two interfaces, raises
  TypeError ("cannot implement ... in Ruby"): there is no precompiled proxy for it. Calling an interface default
  method (`comparator.reversed`) on a Ruby implementation raises TypeError. As in JRuby, `Iface.impl { }` also
  routes default methods to the block (`Iface.impl(:compare) { }` limits it to the named ones).
* TeaVM natives: on JS, methods with a generated body work (`Math.sqrt`), inlined intrinsics do not
  (`Double.isNaN`); wasm-gc reflects no native method (so `Math.sqrt` is missing there).
* No Java serialization (Marshal of Java objects), no `$CLASSPATH`, TeaVM's `String.format` lacks `%n`, and TeaVM's
  exception messages differ from the JDK's.

## Visual demo: Ruby driving Java libraries

`visual.html` runs Ruby code that drives two unmodified third-party Java libraries from Maven Central, compiled into
`jruby.js` by the same TeaVM build. It has two presets; the Ruby code is in an editable textarea and Run
(Ctrl+Enter) runs the edited code.

* **Physics (JBox2D)**: Ruby builds a [JBox2D](https://github.com/jbox2d/jbox2d) 2.2.1.1 `World` (floor, two walls,
  a pyramid of 36 boxes, nine balls), steps it on every animation frame and draws every body on a `<canvas>`. A
  click on the canvas drops a new box; a Ruby block handles the click.
* **QR code (ZXing)**: Ruby encodes the text of an input box with [ZXing](https://github.com/zxing/zxing) core
  3.5.4's `QRCodeWriter` (UTF-8, error correction M) and draws the `BitMatrix` module by module. It then decodes
  the code again with ZXing's own `QRCodeReader`, using an `RGBLuminanceSource` built from the BitMatrix with
  3 x 3 pixels a module, marks the finder and alignment patterns the decoder found, and prints
  "decoded back: ...". Typing in the box runs the code again.

The core of the physics preset (`look` is a Ruby Struct that Ruby stored in the Java `Body`):

```ruby
world = World.new(Vec2.new(0, -10))        # gravity in m/s²
canvas = Java::Demo::Canvas2D.new("stage")
canvas.on_click do |x, y|                  # canvas pixels -> metres
  box(world, x / SCALE, (height - y) / SCALE, 1.2, 1.2, colors.sample)
end
canvas.animate do                          # once per animation frame
  world.step(1.0 / 60, 8, 3)               # JBox2D: 1/60 s of physics
  canvas.clear("#f6f3ea")
  body = world.body_list
  while body                               # walk JBox2D's list of bodies
    pos, look = body.position, body.user_data
    x, y = pos.x * SCALE, height - pos.y * SCALE
    canvas.color = look.color
    if look.r
      canvas.fill_circle(x, y, look.r * SCALE)
    else
      canvas.fill_box(x, y, look.w * SCALE, look.h * SCALE, -body.angle)
    end
    body = body.next
  end
end
```

How the parts fit together:

* **Jars**: `setup/tools/resolve-demo-libs` (Gradle; neither library has runtime dependencies) copies
  `org.jbox2d:jbox2d-library:2.2.1.1` and `com.google.zxing:core:3.5.4` to `shared/jars/demo-libs`. `build.sh` puts
  them into `lib/runtime`, which is on javac's and TeaVM's class path. `java-classes.txt` lists the 10 JBox2D and 13
  ZXing classes the presets use. JBox2D's `World` is restricted to the methods the demo needs, so joints, ray casts
  and debug drawing are not compiled in.
* **Canvas bridge** `demo.Canvas2D` (`demo-src/`) is plain Java over TeaVM's JSO browser bindings: `clear`,
  `color=`, `fill_rect`, `fill_box` (a rectangle rotated about its centre), `fill_circle`, `fill_polygon`,
  `line`, `text`, `font=`, `alpha=`, `line_width=`, `text_align=`, the canvas transforms, `pixel(x, y)`, and
  `width`/`height`. It sizes the canvas buffer by `devicePixelRatio`, so drawings stay sharp on HiDPI screens while
  Ruby works in canvas pixels. `demo.Page.value("qr-text")` reads a form field.
* **Animation**: `canvas.animate { |dt| }` starts a TeaVM green thread. The thread parks on a `Semaphore` that a
  `requestAnimationFrame` callback releases, then calls the block. The block reaches Java as the interface
  `Canvas2D.Frame` (a java.lang.reflect.Proxy), and JRuby adopts the thread as a Ruby thread. Presses
  (`pointerdown`) are queued and handed to the `on_click` block (`Canvas2D.Click`) on the same thread just before
  the next frame, so Ruby code never runs during a frame of another thread. Without an animation, a click runs
  the block on a short-lived thread of its own. The animation ends with a new `Canvas2D` for the same element,
  with `canvas.stop`, or when the page starts a new run (it changes the canvas's `data-run` attribute). An exception
  in a block also stops it; the message reaches the page as a `rubyerror` DOM event and appears under the canvas.
* **TeaVM gap**: ZXing's `CharacterSetECI` calls `Charset.isSupported` and looks charsets up by their historical
  Java names (`UTF8`, `ISO8859_1`). TeaVM 0.16.0 has no `isSupported` and knows the standard charsets only by their
  canonical names. `compat-ext/.../nio/charset/TCharset.java.add` and `.sed` add `isSupported` and the JDK's aliases
  of the six standard charsets. The QR codes therefore carry a UTF-8 ECI, and text with ü, 日本語 or ✓ round-trips.

**Performance** (headless Chromium 148 on a shared 8-core machine with a load average of 7 to 10; the page shows the
live numbers under the canvas). Every frame is Ruby code: one `world.step` call into JBox2D, then a Ruby loop with
seven or eight Ruby-to-Java calls per body (`position`, `x`, `y`, `user_data`, `color=`, `angle`, the draw call,
`next`). A Java helper that batches the drawing turned out to be unnecessary. Medians of three runs, with the range
in brackets (`test/screenshots.js` reports the same numbers for the preset):

| bodies | fps | ms per frame in the `animate` block | `world.step` alone | drawing alone (no step) |
|---|---|---|---|---|
| 48 (the preset) | 60 | 4.5 (3.9 to 4.8) | 1.0 (0.8 to 1.1) | 4.0 (3.6 to 4.6) |
| 148 (+100 boxes) | 60 | 11.3 (10.2 to 11.9) | 3.7 (3.0 to 4.9) | 11.2 (10.9 to 17.0) |
| 248 (+200 boxes) | 42 (35 to 52) | 22.8 (17.8 to 27.0) | 6.2 (6.0 to 9.8) | 17.7 (17.6 to 26.1) |

The Ruby drawing loop costs 70 to 85 µs per body, about 10 µs per Ruby-to-Java call including the Ruby code around
it, so the drawing, not JBox2D, sets the limit: 60 fps up to about 150 bodies. A QR run takes 0.2 to 0.5 s:
encoding, about 1,700 `matrix.get` and 550 `fill_rect` calls for a version-4 code (33 x 33 modules plus the margin),
the luminance array and the decoding. Boot is unchanged apart from parsing 2 % more JavaScript; under this load the
page was ready after 3.5 to 5.6 s (the timings further down come from a quieter machine).

**Size**, against a build of `main` on the same machine: `jruby.js` grows by 472 KB (20,377,404 -> 20,849,420
bytes, +2.3 %), 117 KB gzipped (3,112,069 -> 3,229,558 bytes with `gzip -9`, +3.8 %). That covers JBox2D's solver,
collision code and broad phase, ZXing's QR encoder and decoder (Reed-Solomon, binarizer, detector), the reflection
metadata of the 27 new classes in `java-classes.txt`, and `demo.Canvas2D`. `jruby.wasm` grows by 1.5 MB (42,264,185 ->
43,798,877 bytes; gzipped 9,264,827 -> 9,657,266). TeaVM's JS analysis took 126 to 222 s in three builds on this busy
machine against 162 s for `main`, so its cost is within the noise; `./build.sh --fresh --wasm` took 413 s.

**Screenshots**: `node test/screenshots.js` opens `visual.html` in headless Chromium (served on 127.0.0.1) and saves
`build/screenshots/visual-physics.png`, `visual-physics-clicked.png` (after 25 clicks: 73 bodies), `visual-qr.png`,
`visual-qr-typed.png` and `visual-phone.png` (400 px wide), and prints fps, frame times and the QR runs. JVM ZXing
decodes the QR codes in these screenshots, with symbology identifier `]Q2` (the code has an ECI).

**Limits**: `canvas.animate` needs the JS build. On wasm-gc, TeaVM 0.16.0 cannot suspend threads (see Known gaps);
the rest of Canvas2D, JBox2D and ZXing works there (the five non-blocking `visual:` test cases pass on wasm-gc).
Output printed inside an animation or click block goes to the browser console and to the start of the next run's
output, because the page captures output per run. Ruby can only name the listed classes: JBox2D joints, ray casts or
contact listeners, and ZXing's other formats (PDF417, Data Matrix, ...), need more lines in `java-classes.txt`,
which cost size.

## Test output (headless Chromium 148, `test/run-tests.sh`, production `jruby.js`, from a `--fresh` build)

```
browser-run status=ok rc=0 elapsed=7673
boot (fetch+parse+mount+boot): 4185 ms
PASS  puts 1+1                                 59 ms
PASS  map                                      13 ms
PASS  fib(20)                                  305 ms
PASS  class + method                           7 ms
PASS  hash                                     5 ms
PASS  raise/rescue                             8 ms
PASS  RUBY_VERSION                             6 ms
PASS  utf-8 upcase                             25 ms
PASS  require set                              20 ms
PASS  require ostruct                          185 ms
PASS  require erb                              285 ms
PASS  uncaught error is reported               8 ms
PASS  string formatting                        35 ms
PASS  bignum                                   10 ms
PASS  regexp                                   10 ms
PASS  struct + sort_by                         15 ms
PASS  Comparable/Enumerable                    14 ms
PASS  Time/strftime                            27 ms
PASS  require json-less stdlib (shellwords)    32 ms
PASS  StringIO                                 23 ms
PASS  File in VFS                              22 ms
PASS  Enumerator#next (fiber)                  145 ms
PASS  Fiber                                    130 ms
PASS  Thread#value                             19 ms
PASS  Data + pattern matching                  24 ms
PASS  Marshal round trip                       17 ms
PASS  floats                                   10 ms
PASS  require date                             269 ms
PASS  require strscan                          10 ms
PASS  require forwardable/delegate             69 ms
PASS  time zones (joda tz data)                20 ms
PASS  Queue#pop waits for producer             36 ms
PASS  Mutex held across sleep                  44 ms
PASS  ConditionVariable                        21 ms
PASS  Thread#wakeup                            31 ms
PASS  Thread.main is the eval thread           7 ms
PASS  sleep with timeout                       63 ms
PASS  java: loads on first use                 203 ms
PASS  java: JRUBY_VERSION + java.vendor        23 ms
PASS  java: ArrayList is Enumerable            16 ms
PASS  java: java_import HashMap, each_pair     34 ms
PASS  java: overloads + static calls           40 ms
PASS  java: Ruby <-> Java conversions          19 ms
PASS  java: rescue Java exceptions             26 ms
PASS  java: uncaught Java exception            6 ms
PASS  java: class not in java-classes.txt      8 ms
PASS  java: BigDecimal + BigInteger            20 ms
PASS  java: TreeMap keeps keys sorted          23 ms
PASS  java: blocks as Java interfaces          56 ms
PASS  java: Iface.impl + include Iface         27 ms
PASS  java: demo.Mandelbrot calls a Ruby block 41 ms
PASS  java: AtomicLong                         12 ms
PASS  java: Java arrays                        22 ms
PASS  java: System.out goes to the output      16 ms
PASS  java: no Ruby subclasses of Java classes 10 ms
PASS  java: Map#forEach, merge, compute        22 ms
PASS  java: unlisted interface: clear error    12 ms
PASS  visual: JBox2D body falls, then rests    132 ms
PASS  visual: ZXing QR round trip (UTF-8)      233 ms
PASS  visual: ZXing ReaderException rescue     14 ms
PASS  visual: Canvas2D draws (pixel read back) 21 ms
PASS  visual: Canvas2D.animate runs a block    173 ms
PASS  visual: Canvas2D without a canvas        7 ms
63/63 passed
```

wasm-gc (`SKIP_BLOCKING=1 test/run-tests.sh dist/wasm`, which skips the 9 blocking cases):

```
boot (fetch+parse+mount+boot): 1920 ms
54/54 passed
```

**Timing** on this box (headless Chromium 148, shared CPU), measured on a quieter day than the test run above, which
booted in 4.2 s at a load average of 8:
* The page is ready in 2.9 to 3.3 s. That covers fetching and parsing the 20 MB `jruby.js`, mounting the
  1347-file `stdlib.zip` (about 0.4 s) and `Ruby.newInstance` (1.0 to 1.5 s). The Java integration is not part
  of it; its first use costs about 0.2 s (`java.html` loads it right after boot).
* After that, `puts 1+1` takes 35 ms, `fib(20)` about 230 ms, and the first `require 'erb'` about 270 ms.
  Compiling the Java integration in costs plain Ruby code a little speed: `fib(24)` took 0.68 to 0.75 s here
  against 0.63 to 0.71 s for a build of the `main` branch in the same session (noisy, shared machine).
* A full `./build.sh --fresh --wasm` takes 5 to 7 min (javac about 1 min, TeaVM JS and wasm-gc 140 to 225 s each:
  the reflection analysis that the Java integration brings in roughly tripled TeaVM's time). With the visual demo it
  took 413 s on a busy machine.

## How it works (short)

1. **Prune** what cannot work in a browser: the JVM bytecode JIT (`ir/targets`, `compiler/*`), invokedynamic
   support, JMX, embed/JSR223/OSGi, FFI, sockets, Java class generation and `main`. This removes 301 files.
   `JITCompiler`, `ShellLauncher`, `SelectExecutor`, `FilenoUtil`, `ClasspathLauncher` and `RubyObjectSpecializer`
   stay as small facades.
2. **Remove `java.lang.invoke`**: VarHandles become plain fields, and `StaticScope.construct` builds a
   `ManyVarsDynamicScope` directly instead of a generated scope class. SwitchPoint invalidators become object
   identity, and the lock-free `NonBlockingHashMapLong` is backed by a HashMap.
3. **Bind methods without reflection.** JRuby's own `AnnotationBinder` and `InvokerGenerator` pregenerate the
   `$POPULATOR`/`$INVOKER$` classes. A generated `PopulatorRegistry` (switch over class names, found through
   `ServiceLoader`, which TeaVM resolves at compile time) replaces `getResource` and `Class.forName`. Extension
   libraries come from a static `BuiltinExtensions` table.
4. **IO on TeaVM's VFS**: a RandomAccessFile-backed `FileChannel`, stream-based stdio, `Channels` and NIO
   channel types in compat. `uri:classloader:` maps onto `/jruby/classpath` in the VFS.
5. **Threads**: real blocking locks, conditions and semaphores for TeaVM green threads (`TCompatParker`), plus
   fixes for TeaVM 0.16.0 thread-identity bugs in `ArrayBlockingQueue` and `Thread`.
6. **Java integration** for a build-time class list: reflection metadata and Proxy classes from a TeaVM
   ReflectionPolicy, a generated class registry, java.lang.reflect.Proxy for Ruby implementations of interfaces.
   Third-party jars (JBox2D, ZXing) go through the same build; `demo.Canvas2D` lets Ruby draw on a canvas.
7. **Launcher** `jrubyweb.Main`: sets properties, unpacks `stdlib.zip` with `java.util.zip`, then boots and
   evaluates on the "ruby-main" thread.

`NOTES.md` has the full list of patches, the error count per TeaVM round and every runtime fix.

## Known gaps

* Java integration covers only the classes of `java-classes.txt`, with the limits listed above. There are no
  subprocesses, no sockets, no FFI, no `IO.pipe` and no signals. Digest, OpenSSL, JSON, Psych and Ripper are not
  included. Each of these raises NotImplementedError or LoadError.
* Ruby threads are TeaVM green threads. They are cooperative: a thread switches only when it blocks or sleeps.
* Ruby backtraces show `file:line:in 'name'` but lack the `block in` and `<class:...>` labels.
* Production builds must use TeaVM's default optimization level. `-O 2` miscompiles a few paths.
* wasm-gc runs all non-blocking code: 54 of 54 test cases (including the 20 Java and 5 visual-demo cases) pass
  through the synchronous API. Any thread suspension, such as Fiber, Queue, sleep or `canvas.animate`, crashes
  inside TeaVM 0.16.0's wasm coroutine runtime (`Fiber.reversePush` stack overflow). The pages use the JS build.
* The first `require` of a big library parses Ruby source in the interpreter. `require 'erb'` takes about 0.2 s.

## License

`src/` and `ruby/` (once built) and `jruby-teavm.patch` are derived from JRuby, which is tri-licensed
EPL-2.0 / GPL-2.0 / LGPL-2.1; see `COPYING.jruby`. TeaVM is Apache-2.0. The visual demo compiles two libraries into
`jruby.js` and `jruby.wasm`: JBox2D 2.2.1.1 (BSD-2-Clause, Copyright (c) 2013 Daniel Murphy) and ZXing core 3.5.4
(Apache-2.0, ZXing authors); their notices are in `THIRD-PARTY-NOTICES.txt`, which `build.sh` copies into `dist/`.

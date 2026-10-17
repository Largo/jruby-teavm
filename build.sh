#!/bin/bash
# build.sh - build JRuby 10.1.2.0 for the browser with TeaVM 0.16.0 (JavaScript; optional WebAssembly GC).
#
#   ./build.sh            incremental: uses the patched sources in src/ and ruby/
#   ./build.sh --fresh    from scratch: re-copies JRuby's pristine core sources (tag 10.1.2.0), deletes the files
#                         in jruby-teavm.prune.txt, applies jruby-teavm.patch, then builds
#   ./build.sh --debug    keep TeaVM debug info + source maps, no minification (much bigger jruby.js)
#   ./build.sh --wasm     additionally build the WebAssembly GC variant into dist/wasm/
#
# Output: dist/  (index.html REPL, java.html Java-interop demo, visual.html JBox2D/ZXing demo, smoke.html,
#         jruby.js, stdlib.zip [, wasm/])
# Test:   test/run-tests.sh
# Needs:  JDK 21, python3, and the inputs in ./shared (JRuby/TeaVM jars, JRuby source checkout, TeaVM CLI
#         wrapper, headless-Chromium harness). Create them once with setup/fetch.sh, or point SHARED= elsewhere.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd); cd "$here"
SHARED=${SHARED:-$here/shared}; export SHARED
[ -d "$SHARED/jars" ] || { echo "missing $SHARED: run setup/fetch.sh first (or set SHARED=)"; exit 3; }
JRUBY_SRC=$SHARED/src/jruby-10.1.2.0
FRESH=0; DEBUG=0; WASM=0
for a in "$@"; do case $a in --fresh) FRESH=1;; --debug) DEBUG=1;; --wasm) WASM=1;; *) echo "unknown option $a"; exit 3;; esac; done
t0=$(date +%s)

# 1. sources -------------------------------------------------------------------------------------------
gen_constants() {  # Constants.java is a Maven-filtered template upstream
  local sha; sha=$(git -C "$JRUBY_SRC" rev-parse HEAD 2>/dev/null || echo 36cf8bbe8792c000ceae5382e71f9072abbe2cd8)
  sed -e 's/@version.ruby.major@/4.0/; s/@version.ruby@/4.0.0/; s/@build.date@/2026-09-21/; s/@version.jruby@/10.1.2.0/;
          s/@base.java.version@/21/; s/@base.javac.version@/21/; s/@joda.time.version@/2.14.0/; s/@tzdata.version@/2019c/;
          s/@jruby.revision@/'"$sha"'/' "$JRUBY_SRC/core/src/main/resources/org/jruby/runtime/Constants.java" > src/org/jruby/runtime/Constants.java
}
if [ $FRESH = 1 ]; then
  echo "== fresh sources from $JRUBY_SRC + jruby-teavm.patch"
  rm -rf src ruby
  cp -r "$JRUBY_SRC/core/src/main/java" src
  cp -r "$JRUBY_SRC/core/src/main/ruby" ruby
  gen_constants
  xargs rm -f < jruby-teavm.prune.txt          # pruned areas (JIT, JMX, embed, FFI, sockets, ...)
  find src -type d -empty -delete
  git apply -p1 jruby-teavm.patch              # TEAVM: edits + new files
fi
[ -f src/org/jruby/runtime/Constants.java ] || gen_constants

# 2. dependency jars --------------------------------------------------------------------------------------
# lib/runtime: on TeaVM's classpath (compiled to JS);  lib/compile: javac only (pruned code still references them)
if [ ! -d lib/runtime ] || [ $FRESH = 1 ]; then
  echo "== dependency jars from $SHARED/jars/jruby-base-deps"
  rm -rf lib; mkdir -p lib/compile lib/runtime
  for j in "$SHARED"/jars/jruby-base-deps/*.jar; do
    case $(basename "$j") in
      jruby-base-*) ;;
      joni-*|jcodings-*|joda-time-*|jzlib-*|jnr-posix-*|jnr-constants-*|jnr-ffi-*|jnr-enxio-*|backport9-*|fastdoubleparser-*|dirgra-*|options-*) cp "$j" lib/runtime/;;
      *) cp "$j" lib/compile/;;
    esac
  done
  cp "$SHARED"/jars/teavm/lib/teavm-{jso,jso-apis,interop,classlib,platform,core}-0.16.0.jar lib/compile/
fi
# third-party Java libraries of the visual demo (web/visual.html: JBox2D, ZXing core), compiled to JS like the rest
ls "$SHARED"/jars/demo-libs/*.jar > /dev/null 2>&1 || { echo "missing $SHARED/jars/demo-libs: run setup/fetch.sh --libs"; exit 3; }
cp -u "$SHARED"/jars/demo-libs/*.jar lib/runtime/

# 3. javac: compat layer, dependency patches, JRuby core + launcher (+ AnnotationBinder), invokers, populators
echo "== javac"
scripts/compile.sh

# 4. stdlib.zip: kernel .rb files, pure-Ruby stdlib, a few bundled gems, jcodings tables -------------------
echo "== stdlib.zip"
rm -rf dist; mkdir -p dist
python3 scripts/mkstdlib.py "$SHARED/jars/jruby/jruby-stdlib-10.1.2.0.jar" ruby dist/stdlib.zip lib/runtime/jcodings-1.0.64.jar lib/runtime/joda-time-2.14.0.jar

# 5. TeaVM -> JavaScript (ES module) ---------------------------------------------------------------------
echo "== TeaVM (JS)"
if [ $DEBUG = 1 ]; then
  OUT_SUFFIX=-debug scripts/teavm.sh js
  cp build/out-js-debug/jruby.js* dist/
else
  # NOTE: -O 2 (ADVANCED) miscompiles some paths ("Cannot read properties of undefined"); SIMPLE + minify is fine
  OUT_SUFFIX=-prod JS_DEBUG="" TEAVM_OPTS="-m" scripts/teavm.sh js
  cp build/out-js-prod/jruby.js dist/
fi
[ -s dist/jruby.js ] || { echo "TeaVM failed, see build/teavm-js-*.log"; exit 1; }
cp web/index.html web/smoke.html web/java.html web/visual.html java-classes.txt THIRD-PARTY-NOTICES.txt dist/

# 6. optional: WebAssembly GC -----------------------------------------------------------------------------
if [ $WASM = 1 ]; then
  echo "== TeaVM (wasm-gc)"
  scripts/teavm.sh wasm-gc
  mkdir -p dist/wasm
  cp build/out-wasm-gc/jruby.wasm dist/wasm/
  # TeaVM's wasm-gc loader calls new WeakRef(null) for `new WeakReference(null)`, which throws; return an empty ref instead
  sed 's/createWeakRef(e,t,n){if(n!==null)/createWeakRef(e,t,n){if(e===null){return{deref(){return void 0}}}if(n!==null)/' \
    "$SHARED/jars/teavm/wasm-gc-runtime/teavm-wasm-gc-runtime.min.js" > dist/wasm/jruby.wasm-runtime.js
  grep -q 'if(e===null){return{deref' dist/wasm/jruby.wasm-runtime.js || { echo "wasm runtime patch did not apply"; exit 1; }
  cp web/wasm.html dist/wasm/index.html
  cp dist/stdlib.zip dist/wasm/
fi

echo "== done in $(( $(date +%s) - t0 )) s"; ls -la dist
echo "test: test/run-tests.sh    serve: python3 -m http.server -b 127.0.0.1 -d dist 8080"

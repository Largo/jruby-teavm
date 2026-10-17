#!/bin/bash
# fetch.sh - download the build inputs into ./shared (once; the build itself needs no network).
#
#   setup/fetch.sh            jars (Gradle, Maven Central) + JRuby sources (tag 10.1.2.0) + TeaVM classlib sources
#   setup/fetch.sh --test     also Playwright + headless Chromium for test/run-tests.sh (needs npm)
#   setup/fetch.sh --libs     only the demo libraries (shared/jars/demo-libs), for a ./shared made before they existed
#
# Needs: JDK 21, gradle, git, curl, unzip. Result (~250 MB):
#   shared/bin/                 teavm (CLI wrapper), browser-run (headless-Chromium harness)
#   shared/jars/jruby-base-deps jruby-base 10.1.2.0 + its runtime dependency jars
#   shared/jars/jruby/          jruby-stdlib 10.1.2.0
#   shared/jars/teavm/          TeaVM 0.16.0 CLI + class library jars, wasm-gc JS loader
#   shared/jars/demo-libs/      JBox2D 2.2.1.1 + ZXing core 3.5.4 (pure-Java libraries used by web/visual.html)
#   shared/src/jruby-10.1.2.0   JRuby sources at tag 10.1.2.0 (patched by build.sh --fresh)
#   shared/src/teavm-0.16.0     TeaVM class library sources (compat-ext/ extends some of them)
set -euo pipefail
here=$(cd "$(dirname "$0")/.." && pwd)
S=${SHARED:-$here/shared}
M=https://repo1.maven.org/maven2

demo_libs() {
  echo "== demo libraries: JBox2D, ZXing core (Gradle)"
  mkdir -p "$S"/tools && cp -r "$here"/setup/tools/resolve-demo-libs "$S"/tools/
  (cd "$S"/tools/resolve-demo-libs && gradle -q --no-daemon copyDeps)
  ls "$S"/jars/demo-libs
}
if [ "${1:-}" = --libs ]; then demo_libs; exit 0; fi

mkdir -p "$S"/bin "$S"/tools "$S"/jars/jruby "$S"/jars/teavm/wasm-gc-runtime "$S"/src
cp "$here"/setup/bin/* "$S"/bin/
cp -r "$here"/setup/tools/* "$S"/tools/

echo "== jruby-base + dependency jars, TeaVM CLI jars (Gradle)"
(cd "$S"/tools/resolve-jruby-base && gradle -q --no-daemon copyDeps)
(cd "$S"/tools/resolve-teavm && gradle -q --no-daemon copyDeps)
demo_libs

echo "== jruby-stdlib"
[ -s "$S"/jars/jruby/jruby-stdlib-10.1.2.0.jar ] ||
  curl -fsSL -o "$S"/jars/jruby/jruby-stdlib-10.1.2.0.jar $M/org/jruby/jruby-stdlib/10.1.2.0/jruby-stdlib-10.1.2.0.jar

echo "== TeaVM wasm-gc loader"
unzip -p "$S"/jars/teavm/lib/teavm-core-0.16.0.jar org/teavm/backend/wasm/wasm-gc-runtime.min.js \
  > "$S"/jars/teavm/wasm-gc-runtime/teavm-wasm-gc-runtime.min.js

echo "== JRuby sources (tag 10.1.2.0)"
[ -d "$S"/src/jruby-10.1.2.0/.git ] ||
  git clone -q --depth 1 --branch 10.1.2.0 https://github.com/jruby/jruby.git "$S"/src/jruby-10.1.2.0

echo "== TeaVM class library sources (0.16.0)"
c="$S"/src/teavm-0.16.0/classlib/src/main/java
if [ ! -d "$c" ]; then
  mkdir -p "$c"
  curl -fsSL -o "$S"/teavm-classlib-sources.jar $M/org/teavm/teavm-classlib/0.16.0/teavm-classlib-0.16.0-sources.jar
  unzip -q "$S"/teavm-classlib-sources.jar -d "$c"; rm "$S"/teavm-classlib-sources.jar
fi

if [ "${1:-}" = --test ]; then
  echo "== Playwright + Chromium (for test/run-tests.sh)"
  npm install --silent --prefix "$S"/tools playwright@1.60.0
  (cd "$S"/tools && npx playwright install chromium)
fi
echo "== done: $S"

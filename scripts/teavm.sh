#!/bin/bash
# teavm.sh [js|wasm-gc] : run TeaVM on build/classes + build/compat + runtime dep jars. Log -> build/teavm-<t>.log
set -u
here=$(cd "$(dirname "$0")/.." && pwd); cd "$here"
SHARED=${SHARED:-$here/shared}
t=${1:-js}; out=build/out-$t${OUT_SUFFIX:-}
if [ "$t" = js ]; then f=jruby.js; extra="--js-module-type es2015 ${JS_DEBUG---debug --sourcemaps}"; else f=jruby.wasm; extra=""; fi
rm -rf $out; mkdir -p $out
P=(build/teavm-plugin build/compat build/deps-patches build/classes lib/runtime/*.jar)
TEAVM_XMX=${TEAVM_XMX:-6g} "$SHARED"/bin/teavm jrubyweb.Main -t $t -d $out -f $f $extra ${TEAVM_OPTS:-} -p "${P[@]}" > build/teavm-$t${OUT_SUFFIX:-}.log 2>&1
rc=$?
if [ "$t" = js ] && [ -s $out/$f ]; then
  # TeaVM 0.16.0 JS runtime bug: a class with reflection metadata but no inner classes gets no
  # reflection.innerClasses, so Class.getDeclaredClasses() (JRuby's proxy setup) throws a TypeError;
  # default it to [] like the runtime does for fields/methods
  sed -i -E 's/(reflection ?= ?\{ ?annotations: ?\[\], ?fields: ?\[\], ?methods: ?\[\], ?typeParameters: ?\[\])/\1, innerClasses: []/' $out/$f
  grep -q 'typeParameters: \?\[\], innerClasses: \[\]' $out/$f || { echo "teavm.sh: innerClasses runtime fix did not apply"; rm -f $out/$f; }
fi
grep -c '^ERROR' build/teavm-$t${OUT_SUFFIX:-}.log | sed 's/^/errors: /'
grep '^ERROR' build/teavm-$t${OUT_SUFFIX:-}.log | sed 's/ERROR: //' | sort | uniq -c | sort -rn > build/teavm-$t${OUT_SUFFIX:-}.missing.txt
echo "distinct: $(wc -l < build/teavm-$t${OUT_SUFFIX:-}.missing.txt) rc=$rc"; ls -la $out 2>/dev/null | tail -n +2

#!/bin/bash
# run-tests.sh [dist-dir]: serve dist-dir's jruby.js (or jruby.wasm) on 127.0.0.1 only, open test/test.html in
# headless Chromium, which boots JRuby and evaluates every case of test/cases.json, then check stdout / errors.
#   test/run-tests.sh                              JS build in dist/
#   SKIP_BLOCKING=1 test/run-tests.sh dist/wasm    wasm-gc build; skips the "blocking" cases (Fiber/Thread/Queue/
#                                                  sleep), which crash TeaVM 0.16.0's wasm-gc coroutines
here=$(cd "$(dirname "$0")/.." && pwd)
SHARED=${SHARED:-$here/shared}
dist=${1:-$here/dist}
# the cases run inside the page (test/test.html) so that the harness only reads window.__results at the end
t=$here/build/test-dist; rm -rf "$t"; mkdir -p "$t"
if [ -f "$dist/jruby.wasm" ]; then cp "$dist"/jruby.wasm "$dist"/jruby.wasm-runtime.js "$dist"/stdlib.zip "$t"/; q="wasm=1"; else cp "$dist"/jruby.js "$dist"/stdlib.zip "$t"/; q=""; fi
[ -n "${SKIP_BLOCKING:-}" ] && q="$q&skipBlocking=1"
cp "$here/test/test.html" "$here/test/cases.json" "$t"/
"$SHARED"/bin/browser-run "$t" --page "test.html?$q" --done-global __testsDone --timeout 600000 \
  --idle 600000 --eval 'window.__results' --json > "$here/build/test-result.json"
rc=$?
python3 - "$here/test/cases.json" "$here/build/test-result.json" "$rc" <<'PY'
import json, re, sys
import os
cases = [x for x in json.load(open(sys.argv[1])) if not (os.environ.get('SKIP_BLOCKING') and x.get('blocking'))]
res = json.load(open(sys.argv[2]))
ev = res.get('eval') or {}
print(f"browser-run status={res.get('status')} rc={sys.argv[3]} elapsed={res.get('elapsedMs', res.get('elapsed'))}")
for e in res.get('events', []):
    if e.get('kind') in ('pageerror', 'console.error', 'harness-error'):
        print('  [%s] %s' % (e.get('kind'), str(e.get('text', e))[:300]))
print(f"boot (fetch+parse+mount+boot): {ev.get('bootMs')} ms")
if ev.get('error'): print('BOOT ERROR', ev['error'])
results = ev.get('results') or []
fails = 0
for c, r in zip(cases, results + [None] * (len(cases) - len(results))):
    ok = r is not None
    if ok and 'out' in c: ok = r['out'] == c['out'] and not r['error']
    if ok and 'error_re' in c: ok = bool(r['error'] and re.search(c['error_re'], r['error']))
    fails += not ok
    got = 'no result' if r is None else (r['out'] + ('' if not r['error'] else ' ERROR ' + r['error'].splitlines()[0]))
    print(('PASS' if ok else 'FAIL') + f"  {c['name']:<40} {('%d ms' % r['ms']) if r else ''}" + ('' if ok else f"\n      expected {c.get('out', c.get('error_re'))!r}\n      got      {got!r}"))
print(f"{len(cases) - fails}/{len(cases)} passed")
sys.exit(1 if fails else 0)
PY

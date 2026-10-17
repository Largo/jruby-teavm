#!/bin/bash
# smoke.sh [code]: rebuild (compile+TeaVM JS), copy into dist/, run smoke.html in headless Chromium
cd "$(dirname "$0")/.."; SHARED=${SHARED:-$PWD/shared}
# NOBUILD=1: run against whatever is in dist/; otherwise build a debug jruby.js into build/smoke-dist
if [ -z "${NOBUILD:-}" ]; then
  scripts/round.sh js | grep "errors\|distinct" || exit 1
  mkdir -p build/smoke-dist && cp build/out-js/jruby.js* web/*.html build/smoke-dist/ && cp dist/stdlib.zip build/smoke-dist/
  d=build/smoke-dist
else
  d=dist
fi
code=${1:-puts 1 + 1}
q=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))' "$code")
"$SHARED"/bin/browser-run "$d" --page "smoke.html?code=$q" --timeout 240000 --wait-for 'total|FAILED' 2>&1 | cut -c1-${W:-300} | head -${N:-60}

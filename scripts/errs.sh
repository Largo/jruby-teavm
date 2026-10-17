#!/bin/bash
# errs.sh: summarize javac errors from build/javac.log
cd "$(dirname "$0")/.."; SHARED=${SHARED:-$PWD/shared}
grep 'error:' -A1 build/javac.log | sed "s|$PWD/src/||" | grep -v '^--' | paste - - | cut -c1-${W:-220} | head -${N:-80}; echo "total: $(grep -c error: build/javac.log)"

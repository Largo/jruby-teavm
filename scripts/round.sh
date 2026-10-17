#!/bin/bash
# round.sh [js|wasm-gc]: compile + TeaVM, print error summary
cd "$(dirname "$0")/.."; SHARED=${SHARED:-$PWD/shared}
scripts/compile.sh > build/compile.log 2>&1 || { tail -30 build/compile.log; scripts/errs.sh; exit 1; }
scripts/teavm.sh ${1:-js}

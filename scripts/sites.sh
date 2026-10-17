#!/bin/bash
# sites.sh [pattern] : list "missing item <== first frame" from the last TeaVM log
cd "$(dirname "$0")/.."; SHARED=${SHARED:-$PWD/shared}
awk '/^ERROR: /{e=substr($0,8); getline; sub(/^ *at /,""); print e " <== " $0}' build/teavm-${T:-js}.log | sort -u | grep -E "${1:-.}" | sed 's/ was not found//; s/Ljava\/lang\///g'

#!/bin/bash
# mkpatch.sh: regenerate jruby-teavm.patch (against JRuby 10.1.2.0 core/src/main/{java,ruby}) and
# deps-patches/deps.patch (patched dependency/gem sources vs. their pristine release sources).
set -eu
here=$(cd "$(dirname "$0")/.." && pwd); cd "$here"
base=$(git rev-list --max-parents=0 HEAD)
# Constants.java is generated from Maven's template by build.sh, so it is not part of the patch
# The pruned (deleted) files are listed in jruby-teavm.prune.txt instead of being spelled out in the patch.
git diff --binary --diff-filter=AM "$base" -- src ruby ':!src/org/jruby/runtime/Constants.java' > jruby-teavm.patch
git diff --name-only --diff-filter=D "$base" -- src ruby > jruby-teavm.prune.txt
echo "jruby-teavm.patch: $(grep -c '^diff --git' jruby-teavm.patch) files, $(wc -c < jruby-teavm.patch) bytes; pruned: $(wc -l < jruby-teavm.prune.txt) files"
# pristine dependency sources (Maven Central source jars / GitHub tags), cached in deps-src/
fetch() { [ -f "deps-src/$2" ] || curl -sSfL -o "deps-src/$2" "$1"; }
mkdir -p deps-src/gems
fetch https://repo1.maven.org/maven2/com/github/jnr/jnr-posix/3.2.4/jnr-posix-3.2.4-sources.jar jnr-posix-sources.jar
fetch https://repo1.maven.org/maven2/com/github/jnr/jnr-constants/0.11.2/jnr-constants-0.11.2-sources.jar jnr-constants-0.11.2-sources.jar
fetch https://repo1.maven.org/maven2/org/jruby/jcodings/jcodings/1.0.64/jcodings-1.0.64-sources.jar jcodings-1.0.64-sources.jar
fetch https://raw.githubusercontent.com/ruby/stringio/v3.2.0/ext/java/org/jruby/ext/stringio/StringIO.java gems/StringIO.java
out=deps-patches/deps.patch; : > $out
pristine=$(mktemp -d)
(cd $pristine && unzip -q -o "$here/deps-src/jnr-posix-sources.jar" && unzip -q -o "$here/deps-src/jnr-constants-0.11.2-sources.jar" && unzip -q -o "$here/deps-src/jcodings-1.0.64-sources.jar")
for f in $(cd deps-patches/src && find . -name '*.java' | sort); do
  diff -u --label "a/${f#./}" --label "b/${f#./}" "$pristine/$f" "deps-patches/src/$f" >> $out || true
done
diff -u --label a/org/jruby/ext/stringio/StringIO.java --label b/org/jruby/ext/stringio/StringIO.java deps-src/gems/StringIO.java gems-src/org/jruby/ext/stringio/StringIO.java >> $out || true
rm -rf $pristine
echo "$out: $(grep -c '^+++ ' $out) files"

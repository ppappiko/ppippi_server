#!/usr/bin/env bash
# Build PipiLife from dev/pipi-rpg/src on top of the newest plugins/PipiLife-*.jar.
# Run from anywhere (incl. git worktrees). Never modifies plugins/.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO"
SOR="C:/Users/user/Documents/Codex/2026-11-04/sor"
MAIN="C:/Users/user/Desktop/pipi-server"
SRC=dev/pipi-rpg/src
OUT=dev/pipi-rpg/out

# jars are gitignored: in a worktree use the main checkout's plugins/
if ls plugins/PipiLife-*.jar >/dev/null 2>&1; then PLUGINS="$REPO/plugins"; else PLUGINS="$MAIN/plugins"; fi

CP="$SOR/work/pipi-rpg/compile-api"
# compile-api 폴더가 없는 PC: 서버가 받아 둔 libraries/의 Paper API(paper-remap.jar)와 라이브러리로 대신 컴파일
if [ ! -d "$CP" ]; then
  API="$REPO/libraries/com/mohistmc/installation/data/paper-remap.jar"
  [ -f "$API" ] || { echo "compile-api not found and $API missing: run the server once so it downloads libraries/" >&2; exit 1; }
  echo "compile-api not found, using server libraries/"
  CP="$(cygpath -m "$API" 2>/dev/null || echo "$API")"
  while IFS= read -r j; do CP="$CP;$(cygpath -m "$j" 2>/dev/null || echo "$j")"; done < <(find "$REPO/libraries" -name '*.jar')
fi
for j in "$PLUGINS"/*.jar; do
  [ -f "$j" ] || continue
  case "$j" in */PipiLife-*) continue;; esac
  CP="$CP;$(cygpath -m "$j" 2>/dev/null || echo "$j")"
done

BASE=""
for f in $(ls "$PLUGINS"/PipiLife-*.jar 2>/dev/null | grep -v '\.bak' | sort -V); do BASE="$f"; done
if [ -z "$BASE" ]; then
  for f in $(ls "$PLUGINS"/PipiLife-*.jar* 2>/dev/null | grep -v '\.failed' | sort -V); do BASE="$f"; done
fi
[ -n "$BASE" ] || { echo "no PipiLife-*.jar found in $PLUGINS" >&2; exit 1; }
echo "base jar: $BASE"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/classes" "$TMP/jar" "$OUT"

javac --release 21 -proc:none -encoding UTF-8 -nowarn -cp "$CP" -d "$TMP/classes" $SRC/*.java

( cd "$TMP/jar" && jar xf "$BASE" )

rm -f "$TMP"/jar/dev/pipi/life/*.class
mkdir -p "$TMP/jar/dev/pipi/life"
cp -r "$TMP/classes/." "$TMP/jar/"

YML="$TMP/jar/plugin.yml"
OLD="$(grep -m1 -oE '^version: *[0-9]+\.[0-9]+\.[0-9]+' "$YML" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
NEW="${OLD%.*}.$(( ${OLD##*.} + 1 ))"
sed -i "0,/^version: *$OLD/s//version: $NEW/" "$YML"

OUTJAR="$REPO/$OUT/PipiLife-$NEW.jar"
rm -f "$OUTJAR"
# Mojang-mapped plugin: tells the Paper/Youer plugin remapper not to rewrite our net.minecraft reflection names (runtime is Mojang-named)
rm -f "$TMP/jar/META-INF/MANIFEST.MF"
printf "Manifest-Version: 1.0
paperweight-mappings-namespace: mojang
" > "$TMP/mf.txt"
( cd "$TMP/jar" && jar cfm "$OUTJAR" "$TMP/mf.txt" . )
echo "built $OUTJAR (version $OLD -> $NEW)"

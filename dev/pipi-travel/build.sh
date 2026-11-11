#!/usr/bin/env bash
# Build PipiTravel from dev/pipi-travel/src on top of the newest mods/PipiTravel-*.jar.
# Run from the repo root. Never modifies mods/.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO"
SOR="C:/Users/user/Documents/Codex/2026-11-04/sor"
SRC=dev/pipi-travel/src
OUT=dev/pipi-travel/out

CP="$SOR/work/hud-minecraft-named.jar"
while IFS= read -r j; do
  case "$j" in *sources*|*srgutils*|*-all.jar|*fatjar*|*userdev*) continue;; esac
  CP="$CP;$j"
done < <(find "$SOR/work/gradle-cache/caches/modules-2" -name '*.jar' | sort)

BASE=""
for f in $(ls mods/PipiTravel-*.jar 2>/dev/null | grep -v '\.bak' | sort -V); do BASE="$f"; done
if [ -z "$BASE" ]; then
  for f in $(ls mods/PipiTravel-*.jar* 2>/dev/null | sort -V); do BASE="$f"; done
fi
[ -n "$BASE" ] || { echo "no mods/PipiTravel-*.jar found" >&2; exit 1; }
echo "base jar: $BASE"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/classes" "$TMP/jar" "$OUT"

javac --release 21 -proc:none -encoding UTF-8 -nowarn -cp "$CP" -d "$TMP/classes" $SRC/*.java

BASE_ABS="$(cd "$(dirname "$BASE")" && pwd)/$(basename "$BASE")"
( cd "$TMP/jar" && jar xf "$BASE_ABS" )

cp -r "$TMP/classes/." "$TMP/jar/"
if [ -d dev/pipi-travel/assets ]; then mkdir -p "$TMP/jar/assets" && cp -r dev/pipi-travel/assets/. "$TMP/jar/assets/"; fi

if [ -f dev/pipi-travel/pipitravel.mixins.json ]; then cp dev/pipi-travel/pipitravel.mixins.json "$TMP/jar/pipitravel.mixins.json"; fi

TOML="$TMP/jar/META-INF/neoforge.mods.toml"
OLD="$(grep -m1 -oE '^version="[0-9]+\.[0-9]+\.[0-9]+"' "$TOML" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
NEW="${OLD%.*}.$(( ${OLD##*.} + 1 ))"
sed -i "0,/^version=\"$OLD\"/s//version=\"$NEW\"/" "$TOML"

OUTJAR="$REPO/$OUT/PipiTravel-$NEW.jar"
rm -f "$OUTJAR"
( cd "$TMP/jar" && jar cf "$OUTJAR" . )
echo "built $OUTJAR (version $OLD -> $NEW)"

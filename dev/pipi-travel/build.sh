#!/usr/bin/env bash
# Build PipiTravel from dev/pipi-travel/src on top of the newest mods/PipiTravel-*.jar.
# Run from the repo root. Never modifies mods/.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO"
SOR="C:/Users/user/Documents/Codex/2026-11-04/sor"
SRC=dev/pipi-travel/src
OUT=dev/pipi-travel/out

CPARGS="$(mktemp)"; CPARGS="$(cygpath -m "$CPARGS" 2>/dev/null || echo "$CPARGS")"
# 친구 PC와 같은 클래스패스(런처 NeoForge, AT 적용)를 우선 사용, 없을 때만 sor 작업 폴더
if [ -f "$SOR/work/hud-minecraft-named.jar" ] && ! ls -d "$APPDATA/.minecraft/versions"/neoforge-21.1.* >/dev/null 2>&1; then
  CP="$SOR/work/hud-minecraft-named.jar"
  while IFS= read -r j; do
    case "$j" in *sources*|*srgutils*|*-all.jar|*fatjar*|*userdev*) continue;; esac
    CP="$CP;$j"
  done < <(find "$SOR/work/gradle-cache/caches/modules-2" -name '*.jar' | sort)
  printf -- '-cp
"%s"
' "$CP" > "$CPARGS"
else
  # sor 작업 폴더가 없는 PC: 마인크래프트 런처에 설치된 NeoForge 1.21.1 로 컴파일
  echo "sor workspace not found, using the local Minecraft launcher NeoForge install"
  python dev/mc-classpath.py "$CPARGS" 2>/dev/null || node dev/mc-classpath.cjs "$CPARGS"
fi

# compile-only libraries (e.g. Simple Voice Chat API for VoiceTalk): provided at runtime by the mod itself, not bundled
for j in dev/pipi-travel/lib/*.jar; do
  [ -f "$j" ] || continue
  J="$(cygpath -m "$REPO/$j" 2>/dev/null || echo "$REPO/$j")"
  sed -i "2s|\"\$|;$J\"|" "$CPARGS"
done

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

javac --release 21 -proc:none -encoding UTF-8 -nowarn @"$CPARGS" -d "$TMP/classes" $SRC/*.java
rm -f "$CPARGS"

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

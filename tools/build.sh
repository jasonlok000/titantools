#!/usr/bin/env bash
# Builds Titan Tools in WSL (atlas-build, as dev), in a build copy, so this repo is never edited by a build.
#   bash /mnt/x/TitanTools/tools/build.sh [check|release]
#   check    typecheck and the logic checks
#   release  check, then a signed release APK in the build copy's dist/
# Titan Tools uses the same patched Ink as TitanKeys (tools/ink/titankeys.patch, a copy of TitanKeys' own), found or made by
# TitanKeys' tools/dev/build/ink.sh, and takes TitanKeys' build lock so the two never build at once.
set -euo pipefail
MODE="${1:-release}"
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TK_BUILD="${TK_BUILD:-$HOME/build}"
TK_REPO="${TK_REPO:-/mnt/x/Atlas/.dev/wt-test21}"
B="${TT_BUILD:-$TK_BUILD/titantools}"
[ -f "$HOME/titankeys-env.sh" ] && . "$HOME/titankeys-env.sh"
export PATH="$HOME/.bun/bin:$HOME/android-sdk/build-tools/36.0.0:$PATH"
export KOTLIN_HOME="$TK_BUILD/kotlin-2.1.20/kotlinc"

mkdir -p "$TK_BUILD/tk-tools"
exec 9>"$TK_BUILD/tk-tools/.lock"
flock 9

. "$TK_REPO/tools/dev/build/ink.sh"
mkdir -p "$B"
find "$B" -mindepth 1 -maxdepth 1 ! -name node_modules ! -name .ink ! -name signing ! -name dist -exec rm -rf {} +
tar -C "$SRC" --exclude=.git --exclude=node_modules --exclude=dist --exclude=.ink --exclude=signing -cf - . | tar -xf - -C "$B"
tk_ink_for "$B/tools/ink/titankeys.patch"
ln -sfn "$TK_INK_DIR" "$TK_BUILD/tt-ink"
echo "Ink: $TK_INK_DIR"

cd "$B"
[ -d node_modules/ink ] || bun install
bun run check
bash tests/logic/run.sh
[ "$MODE" = check ] && { echo "RESULT: PASS"; exit 0; }

# The NightKeys key, the same as TitanKeys': copied once, never printed.
if [ ! -f signing/nightkeys-release.p12 ]; then
  mkdir -p signing
  cp "$TK_BUILD/titankeys/signing/nightkeys-release.p12" "$TK_BUILD/titankeys/signing/credentials.env" signing/
fi
set +x
. signing/credentials.env
export INK_KEYSTORE_PASSWORD
export INK_KEY_PASSWORD="${INK_KEY_PASSWORD:-$INK_KEYSTORE_PASSWORD}"
bun run build
ls -t dist/*.apk | head -1
echo "RESULT: PASS"

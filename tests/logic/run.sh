#!/usr/bin/env bash
# The pure logic's checks: the light rule, link freshness, the firmware key light settings, Hold Symbols' pause rule, the old apps, and
# the reused Scale Toggle, Hold Symbols and NightKeys logic. Runs on the JVM, no phone needed.
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
src=android/kotlin/com/everyonesniche/titantools
KOTLINC="${KOTLIN_HOME:-$HOME/build/kotlin-2.1.20/kotlinc}/bin/kotlinc"
javac --release 17 -d "$work" $src/scale/ScaleMath.java $src/scale/ScaleSettings.java $src/scale/WmOutput.java \
  $src/scale/ScaleController.java $src/hold/HoldEngine.java $src/hold/SymbolMap.java $src/light/LightPolicy.java
"$KOTLINC" -nowarn -cp "$work" -include-runtime -d "$work/checks.jar" $src/light/LightRule.kt $src/light/VendorKeys.kt \
  $src/light/VendorReply.kt $src/light/LightLevels.kt $src/hold/HoldPause.kt $src/core/OldApps.kt $src/rotate/RotationRule.kt $src/quick/NightRule.kt $src/quick/WidgetList.kt $src/quick/KillRule.kt tests/logic/Checks.kt 2>&1 | grep -v '^warning' || true
java -cp "$work/checks.jar:$work" ChecksKt

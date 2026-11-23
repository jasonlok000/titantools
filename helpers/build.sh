#!/usr/bin/env bash
# Builds and signs the helper apps (plain Java, no libraries) into $1. Runs in WSL, from tools/build.sh, with the signing
# variables (INK_KEYSTORE_PASSWORD, INK_KEY_PASSWORD) set and the keystore at $2.
set -euo pipefail
OUT="$1"
KEYSTORE="$2"
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RES="$H/../android/src/main/res"
BT="$HOME/android-sdk/build-tools/36.0.0"
JAR="$HOME/android-sdk/platforms/android-36/android.jar"
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
mkdir -p "$OUT" "$W/classes" "$W/dex"
javac --release 17 -nowarn -cp "$JAR" -d "$W/classes" "$H/src/com/everyonesniche/titantools/helper/Go.java"
"$BT/d8" --release --min-api 34 --lib "$JAR" --output "$W/dex" $(find "$W/classes" -name '*.class')

# id|label|action|slot|icon foreground
while IFS='|' read -r id label action slot icon; do
  [ -z "$id" ] && continue
  d="$W/$id"
  mkdir -p "$d/res/drawable" "$d/res/mipmap-anydpi-v26" "$d/res/values"
  cp "$RES/drawable/$icon.xml" "$d/res/drawable/fg.xml"
  printf '<resources><color name="bg">#EF804E</color></resources>\n' > "$d/res/values/colors.xml"
  printf '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/bg"/><foreground android:drawable="@drawable/fg"/><monochrome android:drawable="@drawable/fg"/></adaptive-icon>\n' > "$d/res/mipmap-anydpi-v26/ic.xml"
  cat > "$d/AndroidManifest.xml" <<MEOF
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.everyonesniche.titantools.$id">
    <permission android:name="com.everyonesniche.titantools.permission.TRIGGER" android:protectionLevel="signature" />
    <uses-permission android:name="com.everyonesniche.titantools.permission.TRIGGER" />
    <queries><package android:name="com.everyonesniche.titantools" /></queries>
    <application android:label="$label" android:icon="@mipmap/ic" android:roundIcon="@mipmap/ic" android:allowBackup="false" android:hasCode="true">
        <activity android:name="com.everyonesniche.titantools.helper.Go" android:exported="true" android:excludeFromRecents="true"
            android:noHistory="true" android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <meta-data android:name="action" android:value="$action" />
            <meta-data android:name="slot" android:value="$slot" />
        </activity>
    </application>
</manifest>
MEOF
  "$BT/aapt2" compile --dir "$d/res" -o "$d/res.zip"
  "$BT/aapt2" link -I "$JAR" --manifest "$d/AndroidManifest.xml" -o "$d/base.apk" "$d/res.zip" \
    --min-sdk-version 34 --target-sdk-version 36 --version-code 2 --version-name 1.0.0
  cp "$W/dex/classes.dex" "$d/classes.dex"
  (cd "$d" && zip -q base.apk classes.dex)
  "$BT/zipalign" -f -p 4 "$d/base.apk" "$d/aligned.apk"
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-type PKCS12 --ks-key-alias nightkeys \
    --ks-pass env:INK_KEYSTORE_PASSWORD --key-pass env:INK_KEY_PASSWORD --out "$OUT/$id.apk" "$d/aligned.apk"
  echo "helper $id.apk"
done <<LIST
layout|TT - Switch layout|layout|0|scale_toggle_foreground
rotate|TT - Rotate|rotate|0|rotate_foreground
flashlight|TT - Flashlight|flashlight|0|flashlight_foreground
night|TT - Night mode|night|0|night_foreground
playpause|TT - Play/Pause|playpause|0|play_foreground
widget1|TT - Widget 1|widget|1|widget_foreground
widget2|TT - Widget 2|widget|2|widget_foreground
widget3|TT - Widget 3|widget|3|widget_foreground
widget4|TT - Widget 4|widget|4|widget_foreground
widget5|TT - Widget 5|widget|5|widget_foreground
LIST

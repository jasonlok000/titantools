# Releasing Titan Tools

The GitHub repo `jasonlok000/titantools` is **private**. Keep it private; releases are only visible to people with access.

## Build

From Windows PowerShell:

    wsl -d atlas-build -u dev bash /mnt/x/TitanTools/tools/build.sh release

The build ends with `RESULT: PASS` and writes `~/build/titantools/dist/titan-tools-<versionName>.apk` in WSL. It is signed with the NightKeys key on the build machine; never copy key files or passwords out of it.

## Check the APK

In WSL:

    ~/android-sdk/build-tools/36.0.0/aapt dump badging <apk> | head -1
    ~/android-sdk/build-tools/36.0.0/apksigner verify --print-certs <apk> | grep 'Signer #1 certificate'

Expected signer:

- DN: `CN=NightKeys community release, O=Titan Tools`
- SHA-256: `bb769da3c4aa70bf9e50604df662153998ce64baf410ee0cd3e7579afafa9420`

## Cut the release

Copy the APK to a Windows path, then:

    gh release create v<versionName> <apk> --repo jasonlok000/titantools --prerelease --title "Titan Tools <versionName>" --notes "<short notes>"

Bump versionCode for every build you hand out. Use `--prerelease` until a build has been used daily without bugs.

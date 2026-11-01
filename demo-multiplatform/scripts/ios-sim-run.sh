#!/bin/bash
# Builds the iOS check app for the simulator, puts the .app together (no Xcode project needed: the whole app is Kotlin, see
# iosMain/Main.kt), installs it on the booted simulator and starts it.
# Usage: [CONFIG=debug|release] demo-multiplatform/scripts/ios-sim-run.sh [simulator name or UDID, default: the booted one]
# A debug Kotlin/Native build is far slower than a release one: measure frame rates with CONFIG=release.
set -e
cd "$(dirname "$0")/../.."
DEVICE="${1:-booted}"
BUNDLE_ID="com.tcsappdev.worldwinddraw.demomp"
CONFIG="${CONFIG:-debug}"
MODULE="${MODULE:-demo-multiplatform}"
CAP="$(echo "${CONFIG:0:1}" | tr a-z A-Z)${CONFIG:1}"

./gradlew ":$MODULE:link${CAP}ExecutableIosSimulatorArm64" ${WWK_VERSION:+-PwwkVersion=$WWK_VERSION}
KEXE="$MODULE/build/bin/iosSimulatorArm64/${CONFIG}Executable/$MODULE.kexe"
APP=demo-multiplatform/build/ios-app/DrawEngineCheck.app

rm -rf "$APP"
mkdir -p "$APP"
cp "$KEXE" "$APP/DrawEngineCheck"
cat > "$APP/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key><string>DrawEngineCheck</string>
    <key>CFBundleIdentifier</key><string>$BUNDLE_ID</string>
    <key>CFBundleName</key><string>Draw Engine Check</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>MinimumOSVersion</key><string>15.0</string>
    <key>CFBundleSupportedPlatforms</key><array><string>iPhoneSimulator</string></array>
    <key>UIDeviceFamily</key><array><integer>1</integer><integer>2</integer></array>
    <key>UILaunchScreen</key><dict/>
    <key>CADisableMinimumFrameDurationOnPhone</key><true/>
    <key>UIRequiredDeviceCapabilities</key><array><string>arm64</string></array>
</dict>
</plist>
PLIST
# WorldWind's data files (the geoid, the stars, the atmosphere...) are moko-resources bundles inside its klibs (the main one and worldwind-assets),
# which an Xcode build would copy into the app. Without Xcode this does it: each bundle becomes <identifier>.bundle in the .app, flat as iOS
# wants it (the klib has the macOS layout).
WWK_VERSION="${WWK_VERSION:-$(sed -n 's/^worldwind = "\(.*\)"/\1/p' gradle/libs.versions.toml)}"
CACHE=~/.gradle/caches/modules-2
KLIBS=$(find "$CACHE" -name "worldwind-iosSimulatorArm64Main-$WWK_VERSION.klib"; find "$CACHE" -name 'worldwind-assets-iosSimulatorArm64Main-*.klib' | sort | tail -1)
if [ -z "$KLIBS" ]; then echo "WorldWind klibs not found in the Gradle cache" >&2; exit 1; fi
WORK=$(mktemp -d)
i=0
for KLIB in $KLIBS; do
    i=$((i + 1))
    mkdir -p "$WORK/$i"
    unzip -q "$KLIB" 'default/resources/*' -d "$WORK/$i" || true
    for BUNDLE in "$WORK/$i"/default/resources/*.bundle; do
        [ -d "$BUNDLE" ] || continue
        ID=$(plutil -extract CFBundleIdentifier raw "$BUNDLE/Contents/Info.plist")
        mkdir -p "$APP/$ID.bundle"
        cp -R "$BUNDLE/Contents/Resources/." "$APP/$ID.bundle/"
        cp "$BUNDLE/Contents/Info.plist" "$APP/$ID.bundle/Info.plist"
    done
done
rm -rf "$WORK"

codesign --force --sign - "$APP"

xcrun simctl terminate "$DEVICE" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install "$DEVICE" "$APP"
xcrun simctl launch "$DEVICE" "$BUNDLE_ID"

# BNS Warehouse Kiosk (Android)

A thin, full-screen wrapper around the handheld web app - not a rewrite of
anything. It's a WebView pointed at your warehouse system's `/handheld` URL,
set up so it can be locked to a dedicated warehouse scanner: no browser chrome,
no way to swipe away to another app.

This folder is Android *source code* - a complete, real Android Studio
project. It hasn't been compiled into an installable `.apk` file, because that
needs Gradle to download the Android SDK and a bunch of build dependencies,
which needs internet access this environment doesn't have. Building it
yourself is genuinely just a few clicks in Android Studio once it's open -
nothing to code.

## Before you build - one edit required

Open `app/src/main/java/uk/co/bns/warehouse/kiosk/MainActivity.kt` and change
this line near the top to wherever your warehouse system is actually
reachable on your LAN:

```kotlin
private const val WAREHOUSE_URL = "http://192.168.1.245:8081/handheld"
```

If your server's IP is assigned by DHCP and might change, either update this
and rebuild when it does, or (better) set a static/reserved IP for the server
in your router's settings so this URL never needs touching again.

## Building it

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. File > Open, select this `android-app` folder.
3. Let it sync (first time will download the Android SDK and Gradle
   dependencies - needs internet, takes a few minutes).
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. The finished `app-debug.apk` will be under `app/build/outputs/apk/debug/`.

For an actual production install (not just testing), use **Build > Generate
Signed Bundle / APK** instead, which walks you through creating a signing key.
A debug APK works fine for trying it out, but Android will warn about it being
an unsigned/debug build.

If sync fails with something like "Minimum supported Gradle version is X" -
this project deliberately doesn't ship its own Gradle wrapper (avoids needing
a binary file that's awkward to hand-author correctly), so it builds using
whatever Gradle version your installed Android Studio already has bundled.
The Android Gradle Plugin version in `build.gradle.kts` (project-level, not
the one under `app/`) needs to be one whose minimum Gradle requirement your
Studio's bundled Gradle actually meets - check the compatibility table at
https://developer.android.com/build/releases/about-agp and lower that version
number if needed. 8.2.2 (what's set here) needs only Gradle 8.2, which should
cover any reasonably current Android Studio install.

## Installing on the device

1. On the Android device, enable Developer Options (Settings > About Phone >
   tap "Build number" 7 times) and turn on "USB debugging" and "Install via
   USB" / "Unknown sources" as needed for your Android version.
2. Either `adb install app-debug.apk` from a PC with the phone plugged in, or
   copy the APK onto the device and open it there to install directly.

## Locking it down

Once installed, two things need doing on the device itself - not something
buildable into the APK, since Android requires you to explicitly choose these:

1. **Set it as the Home app.** Press the Home button/gesture - Android will
   ask which app to use as Home (or go to Settings > Apps > Default apps >
   Home app and pick "BNS Warehouse" directly). This means pressing Home
   always lands back on this app, not the normal launcher.
2. **Nothing else needed for basic locking** - the app pins itself
   automatically (Android's built-in "Screen Pinning" / Lock Task Mode) the
   moment it opens. The very first time, Android shows a one-off system
   dialog explaining pinning; after that it's silent. While pinned, Home and
   Recents are disabled - a swipe-up-to-home genuinely won't leave the app.

### If you want it fully locked with zero dialogs, ever

The above (steps 1-2) needs no special device setup and works on any regular
Android device - but Android will still show that first-run pinning
explanation once, and a determined user *can* still get out via the standard
"hold Back + Recents" unpin gesture if they know it exists.

For a genuinely bulletproof kiosk - no unpin gesture works, no dialogs, ever -
Android has a stronger mode called **Device Owner**, but it comes with real
constraints: it only works on a **factory-reset device with no Google account
signed in yet**, and it's set up via a one-time `adb` command during that
initial setup, not from within the app itself. Worth doing if you're setting
up a dedicated device that will only ever run this, not worth it for a device
you might reuse for anything else. This is a separate, deliberate setup step
if/when you want it - not something this build does automatically.

## What this does and doesn't change

This app doesn't touch the warehouse system itself at all - it's purely a
shell. Every login, every screen, every feature works exactly as it does in a
normal browser, because it *is* the normal web app, just wrapped full-screen.
Any future update to the handheld web app (new features, bug fixes) shows up
here automatically the next time the device loads the page - no APK rebuild
needed, unless the URL itself changes.

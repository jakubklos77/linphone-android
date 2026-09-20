# linphone-android devcontainer

This devcontainer builds [linphone-android](https://github.com/BelledonneCommunications/linphone-android)
(cloned into this repo's root) via its normal Gradle path — it does **not**
build the native `linphone-sdk` C/C++ engine from source. By default the app
pulls a prebuilt `linphone-sdk-android` AAR from Belledonne's Maven
repository, so a JDK + Android SDK is all that's required. See
["Building the native SDK instead" below](#building-the-native-sdk-instead-advanced)
if you actually want that.

The image mirrors upstream's own CI image
([`docker-files/bc-dev-android-36`](../docker-files/bc-dev-android-36), used
by [`.gitlab-ci-files/job-android.yml`](../.gitlab-ci-files/job-android.yml)):
Debian trixie, OpenJDK 21, and the Android command line tools. The only
deviation is installing platform `android-37` instead of `36`, to match this
checkout's current `compileSdk`/`targetSdk` (`app/build.gradle.kts`).

## Usage

1. Open this folder in VS Code and reopen in the container (`Dev Containers: Reopen in Container`).
2. On first create, `postCreateCommand` runs `./gradlew --version`, which
   downloads the pinned Gradle 9.7.1 wrapper distribution — this needs
   outbound internet access (`services.gradle.org`, `dl.google.com`,
   `maven.google.com`/`google()`, `repo.maven.apache.org`, and
   `download.linphone.org`).
3. Build the debug APK:
   ```
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/`.
4. Install to a connected/emulated device (needs `adb`, not included in this
   image — run from the host, or add `platform-tools`' `adb` usage via a
   forwarded USB/emulator setup if you need on-device testing from inside
   the container):
   ```
   ./gradlew installDebug
   ```

### Release builds

`./gradlew assembleRelease` will fail out of the box: `keystore.properties`
is checked into the repo but points at a keystore file
(`bc-android.keystore`) that isn't included. To build a signed release,
either provide your own keystore and edit `keystore.properties` to match, or
generate a debug-style keystore for local testing.

`app/google-services.json` (Firebase, for push notifications) **is** checked
into the repo already, so that part works unmodified unless you want your
own Firebase project.

### Native debug symbols / App Bundles

The upstream README notes that producing a release App Bundle with embedded
native (`.so`) debug symbols requires an installed NDK and an
`ANDROID_NDK_HOME` env var. This devcontainer's CI-mirrored image doesn't
install one (upstream's own `job-android` build doesn't either). If you need
this, add to the Dockerfile:
```
RUN sdkmanager --install "ndk;27.2.12479018"
ENV ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.2.12479018
```
(pick whatever NDK revision you need — check `linphone-sdk`'s Android
Dockerfiles for the revision it currently builds with).

## Building the native SDK instead (advanced)

If your real goal is rebuilding `liblinphone`/`mediastreamer2`/etc. from
source (not just the Android app shell), that's a separate, much heavier
project: [`linphone-sdk`](https://github.com/BelledonneCommunications/linphone-sdk),
built with CMake presets (`android-sdk`) and taking a long time (many C/C++
submodules cross-compiled for multiple ABIs). It needs a different, bigger
toolchain: CMake ≥3.22, Ninja, Meson, Perl, yasm, nasm, doxygen, Python 3 with
`pystache`/`six`, and the Android NDK (upstream currently builds with NDK
r30 — see `linphone-sdk/docker-files/bc-dev-android-r30`). Once built, point
`LinphoneSdkBuildDir` in your Gradle user properties
(`~/.gradle/gradle.properties`) at its build output, per the main
[README.md](../README.md#building-a-local-sdk) `Building a local SDK`
section, and this app will link against your local build instead of the
Maven one. This isn't set up in this devcontainer — say the word if you want
a second, dedicated container for it.

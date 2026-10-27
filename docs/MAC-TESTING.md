# Connect the development server to the Mac

Run the connection script **inside a local cmux terminal on the Mac**, not inside the remote Linux terminal:

```sh
git clone https://github.com/baymac/plotus.git
cd plotus
bash scripts/connect-mac.sh user@development-server
```

Use the same SSH destination you normally use to reach the development server. The script discovers the Mac cmux socket and forwards it to a fresh Unix socket on the development server through SSH. It prints the path to paste into the coding session. No public TCP listener, password, API token, or private key is added to this repository. Keep the terminal open; Ctrl-C disconnects it. The forwarded socket grants the development-server account control of cmux for this testing session.

The old saved relay may be stale. The current `cmux-tui` Linux session is not proof of connectivity to macOS. Native cmux's normal remote relay also scopes commands to remote terminals; this explicit, user-started Mac control connection is for the authorized local device-testing workflow.

On Linux, after the user provides the socket path:

```sh
~/.cmux/bin/cmux --socket /tmp/plotus-mac-<printed-id>.sock ping
~/.cmux/bin/cmux --socket /tmp/plotus-mac-<printed-id>.sock new-workspace --name 'Plotus iOS' --focus false
```

Retain the returned workspace/surface IDs and target only that dedicated workspace. Use `send` then `send-key Enter`; inspect output with `read-screen`. Do not send commands into a user's existing active shell. Begin with `sw_vers`, `xcodebuild -version`, and `xcrun devicectl list devices`. Clone this public repo on the Mac, generate the Xcode project, build for Simulator, then select the connected iPhone in Xcode after signing is configured.

If local socket access is disabled or the API rejects access, report it and use a user-configured SSH connection instead. Do not change cmux access settings automatically.

Source: [cmux relay architecture](https://github.com/manaflow-ai/cmux/blob/main/daemon/remote/README.md).

## Kotlin app workspace

The independent app checkout is `/Users/parichay/dev/plotus`. The private checkout `/Users/parichay/dev/naksha` is only a build-configuration reference and stays outside the submission.

```sh
cd /Users/parichay/dev/plotus
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :composeApp:linkDebugFrameworkIosArm64
cd iosApp
xcodegen generate
open Plotus.xcodeproj
```

The Xcode pre-build phase invokes `:composeApp:embedAndSignAppleFrameworkForXcode`. All screens are Compose, hosted by a minimal UIKit application delegate. Signing settings belong to the local Xcode build, not the public repository.

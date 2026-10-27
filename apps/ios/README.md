# Plotus iOS

Native SwiftUI application, iOS 17+. Requires a Mac with Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```sh
brew install xcodegen
cd apps/ios
xcodegen generate
open Plotus.xcodeproj
```

Choose your Apple development team under Signing & Capabilities, select the connected iPhone, and Run. Use a unique bundle identifier if your account requires it. Signing identity is intentionally not committed.

Simulator build without signing:

```sh
xcodebuild -project Plotus.xcodeproj -scheme Plotus -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

The scaffold has no live authentication or backend persistence. Do not treat local preview state as a session. Device testing requires a live Mac connection and is not implied by Linux checks.

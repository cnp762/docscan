# DocScan iOS (`cam.docscan`)

SwiftUI shell with AVFoundation / Vision / StoreKit stubs.

## Open in Xcode

```bash
open DocScan.xcodeproj
```

1. Select team under Signing & Capabilities for target **DocScan**
2. Bundle Identifier must stay **`cam.docscan`**
3. Run on simulator or device

## Info.plist keys

Already set:

- `NSCameraUsageDescription`
- `NSPhotoLibraryUsageDescription` (7-day suggest)

## Store upload

See [../docs/store-upload.md](../docs/store-upload.md). Keep ASC `.p8` keys outside git (`secrets/`).

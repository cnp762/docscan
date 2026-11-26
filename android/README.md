# DocScan Android (`cam.docscan`)

```bash
# From android/ — requires JDK 17 + Android SDK
./gradlew assembleDebug
./gradlew assembleRelease   # after keystore.properties
./gradlew bundleRelease     # Play AAB
```

Copy `keystore.properties.example` → `keystore.properties` (gitignored) for release signing.

Shell includes CameraX, Play Billing, ML Kit Text Recognition dependencies, and an in-memory page repository (Room/KSP in a later slice). UI flows: see `../docs/screens/`.

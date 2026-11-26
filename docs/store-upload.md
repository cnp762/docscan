# Store upload & deploy

## Android (`cam.docscan`)

1. Create Play Console app with package **`cam.docscan`**
2. Copy `android/keystore.properties.example` → `android/keystore.properties` and point at a local JKS (never commit)
3. Build:

```bash
cd android
./gradlew bundleRelease
# output: app/build/outputs/bundle/release/app-release.aab
```

4. Upload AAB to Play Console → Internal testing → Production
5. Configure Play Billing products: `pro_monthly`, `pro_quarterly`, `pro_yearly`
6. For Photo/Video policy: declare 7-day library suggest as core document digitization use case

### Debug APK

```bash
./gradlew assembleDebug
```

## iOS (`cam.docscan`)

1. Create App ID / app in App Store Connect with Bundle ID **`cam.docscan`**
2. Open `ios/DocScan.xcodeproj`, set Development Team, enable iCloud if used (no Sign in with Apple for MVP — device ID only)
3. Archive in Xcode → Distribute App → App Store Connect / TestFlight
4. Optional CI with ASC API key (`.p8` gitignored):

```bash
# Example — adjust paths; do not commit AuthKey_*.p8
xcrun altool --upload-app -f DocScan.ipa -t ios \
  --apiKey "$APPLE_KEY_ID" --apiIssuer "$APPLE_ISSUER_ID"
```

5. StoreKit products: `pro_monthly`, `pro_quarterly`, `pro_yearly`

## LDP (Cloudflare Pages)

```bash
cd ldp
npm install
npm run build
npx wrangler pages deploy dist --project-name=docscan
```

Attach custom domain **docscan.cam**.

## Server + Tunnel

```bash
cd server
docker compose up --build
# With tunnel profile after CLOUDFLARE_TUNNEL_TOKEN is set:
docker compose --profile tunnel up
```

Route **api.docscan.cam** → the API service.

# Local storage layout

All paths under the app sandbox (Android app-specific storage / iOS Documents or Application Support).

```
DocScan/
  batches/
    {batchId}/
      pages/{pageId}.jpg
      thumbs/{pageId}.jpg
      originals/{pageId}.jpg   # optional pre-crop
  standalone/
    {pageId}.jpg
    thumbs/{pageId}.jpg
    originals/{pageId}.jpg
  models/                     # reserved if models ever leave assets; MVP = bundled assets
  export/                     # temp exports (clear after share)
  db/
    docscan.sqlite
```

## Google Drive mirror

```
DocScan/
  batches/{batchNameOrId}/
    page-001.jpg …
  standalone/
    …
```

## Google Photos

App-created album (e.g. `DocScan`); media items are page images created by the app (`photoslibrary.appendonly`).

## iCloud Drive (iOS)

Same hierarchy under the app’s iCloud container Documents folder.

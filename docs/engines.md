# Engines & kit choices

See also:

- [engines/contracts.md](engines/contracts.md)
- [engines/resource-limits.md](engines/resource-limits.md)
- [engines/nlp-metadata-resource-plan.md](engines/nlp-metadata-resource-plan.md)
- [engines/cs-capability-map.md](engines/cs-capability-map.md)
- [engines/data-usability-report.md](engines/data-usability-report.md)
- [engines/model-manifest.md](engines/model-manifest.md)

## Policy

- **Never ship** CamScanner `.so` binaries; rewrite inference in DocScan.
- CamScanner `.data` may be used **after** offline decrypt/normalize; until then MVP uses **REWRITE** stacks (ML Kit / Vision / heuristics / planned ONNX).
- Models bundled in-app (no R2). Summary generative is in-scope (weights optional; extractive fallback always on).

## Active MVP stack

| Need | Android | iOS |
|------|---------|-----|
| Detect | Heuristic / full-frame + planned ONNX | Vision document segmentation |
| OCR | ML Kit Text Recognition | Vision OCR |
| Lang | Unicode script + compact text detector | Same |
| Tag / classify | Heuristic taxonomy → doc_type + tags | Same port |
| Enhance | Bitmap ColorMatrix presets | Core Image presets |
| Summary | Extractive + gated generative stub | Same |
| Metadata | EXIF/XMP best-effort + `.docscan.json` | ImageIO + sidecar |
| Runtime | Shared priority WorkQueue + systemOk DeviceCapability | Same (FreshScan > UserSave > Suggest) |

Update [NOTICE](../NOTICE) when pinning third-party weights.

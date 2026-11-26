# Engine resource limits

Hard caps so on-device work does not lag, ANR, or OOM. Heavy work runs on a **shared background WorkQueue**.

## Shared WorkQueue

| Rule | Value |
|------|-------|
| Scope | Save NLP steps **and** 7-day library suggest scan/score |
| Parallelism | **1** (serial worker) |
| UI thread | Never run OCR / NLP / enhance / suggest score |
| Preemption | In-flight step **never** preempted; next job picked by priority |

### Priority bands

Sort: `(priorityBand desc, enqueuedAt desc)` — newer scans jump ahead within the same band.

| Band | Value | Sources |
|------|-------|---------|
| FreshScan | 100 | New capture / import / suggest→Save NLP chain |
| UserSave | 80 | Re-edit Save / batch summarize |
| Suggest | 40 | `SuggestScanChunk`, `SuggestScoreItem` |
| Deferred | 10 | Re-NLP, cleanup, backfill |

## Per-step timeouts

| Step | Timeout |
|------|---------|
| Suggest media chunk / score one candidate | **5s** |
| Enhance / detect (page) | **8s** |
| OCR | **15s** |
| Lang | **2s** |
| Classify | **2s** |
| Extractive summary | **2s** |
| Generative summary | **8s** then fallback extractive |
| Metadata write | **3s** |

## Image / text budgets (always on)

| Rule | Value |
|------|-------|
| NLP bitmap long edge | ≤ **1600px** |
| Working set target | ≤ ~**12MB** for NLP bitmaps |
| Batch summary input | ≤ **8k** chars |
| Page summary output | ≤ **500** chars |

## System OK (no min-RAM gate)

Do **not** gate on free/available RAM MB or a minimum total-RAM class.

| Signal | Behavior |
|--------|----------|
| Weights missing under `models/summary` | Extractive only |
| Android `MemoryInfo.lowMemory` (OS flag) | Pause heavy / skip generative |
| Thermal ≥ severe / serious | Skip generative; may soft-pause suggest |
| Step timeout | Skip or fallback that step only |

`systemOk()` = `!osLowMemory && !thermalThrottled`.

## Stepped Save pipeline

Each step is a separate queue job; `PageEntity` updates incrementally (ocr → lang → doc_type/tags → summary → metadata).

## Re-run

Re-run NLP on re-edit only when image hash or enhance preset changed (UserSave priority).

## Implementation

- Android: `cam.docscan.engine.runtime.WorkQueue`, `WorkJob`, `DeviceCapability`
- iOS: shared `WorkQueue` actor + equivalent priorities / `systemOk`

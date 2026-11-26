# Data usability report (CamScanner `.data`)

Date: 2026-11-26  
Decision policy: USE after offline decrypt+normalize into DocScan loader; else REWRITE.

## Shared finding

All inspected packs in `ref/data/` show **~8.0 bit/byte entropy**, no public MNN/ONNX/GGUF magic. Decrypt lives in native `model_cipher` + `INTSIG_AES::aes_256_cbc_decrypt` inside CS `.so` files — **not** in Java.

**Ghidra / deep decompile of `model_cipher::warp_key` is not available on this host** (`GHIDRA_INSTALL_DIR` unset). Therefore production path for MVP engines is **REWRITE**. USE remains open after cipher RE (see `rea-ghidra-setup.md`).

| File | MD5 (plan) | Encrypted? | Decision | Notes |
|------|------------|------------|----------|-------|
| `mobile_ocr.data` | `58e69a2f…` | Yes | **REWRITE** (pending USE) | Identical to `ocr3d1_mnn2d9_20241009c.data` |
| `ocr3d1_mnn2d9_20241009c.data` | same | Yes | **REWRITE** (dedup) | Name implies MNN after decrypt |
| `tagengine_20250113.data` | `c1577abf…` | Yes | **REWRITE** (pending USE) | Prefer V2 pack if USE later |
| `tagengine_20250624.data` | `e4b45625…` | Yes | **REWRITE** (pending USE) | Prefer for tagging |
| `zw_detect.data` | `8f667c4b…` | Yes | **REWRITE** (pending USE) | Identical to `zw_detect_v7_20_0.data` |
| `zw_detect_v7_20_0.data` | same | Yes | **REWRITE** (dedup) | |
| `doc_recog_mob_multimodal_*.data` | `67f4ef4e…` | Yes | **REWRITE** / later | Out of OCR MVP |

## Do not bundle

- Raw ciphertext `.data` into release assets **until** DocScan owns decrypt **or** ships decrypted members only.
- Any CS `.so`.

## Rewrite pins (MVP)

See [model-manifest.md](model-manifest.md).

# REA / Ghidra setup (cipher pass)

Required to flip `.data` decisions from REWRITE → USE.

## Host checklist

1. Install **Ghidra 12.2+**
2. Export `GHIDRA_INSTALL_DIR` to the install root
3. Ensure REA MCP native provider can launch Ghidra headless
4. Open `ref/native/libOCREngine.so` (and TAG/magic copies if present)
5. Decompile `model_cipher::warp_key`, `decrypt`, `INTSIG_AES::aes_256_cbc_decrypt`
6. Document key/IV derivation; offline-decrypt packs → member list
7. Update `data-usability-report.md` to USE where members load with upstream MNN/ORT

## Current status

- `GHIDRA_INSTALL_DIR`: **unset** on analysis host (2026-11-26)
- MVP engines proceed on **REWRITE** path without blocking on Ghidra

# REA pass 1 ledger (short)

- Target: CamScanner 7.29.0.2611080000 / `com.intsig.camscanner`
- `.data` packs: encrypted (`model_cipher` + AES-256-CBC); CDN-sourced; MD5 matched in plan
- OCR engine depends on `libCVNN.so` + OpenCV; TAG uses MNN backend
- Java does not decrypt — only passes path to `LoadModel`
- Unknown residual: cipher key material + pack layout (`unk` until Ghidra pass 2)
- Decision for implementation sprint: **REWRITE** engines; keep USE path documented

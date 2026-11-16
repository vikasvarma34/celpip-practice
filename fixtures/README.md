# Synthetic importer fixtures

These 13 JSON units cover all 14 task formats. They are structural test data, not CELPIP-level practice or audited comprehension material. All sources use the canonical `AI_GENERATED` enum with null URL; synthetic provenance is recorded here rather than adding unsupported JSON fields. The three 16×16 colored PNGs test image handling only.

Run `python3 fixtures/generate.py` to reproduce them. Image paths are relative to this directory. Tests create ZIPs and multipart uploads from these files, including deliberately invalid variants, in isolated temporary directories.

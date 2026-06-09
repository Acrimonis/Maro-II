<!-- scope: feature -->

# Depth OOM Fix — Practical Steps

## What changes (4 files, 1 data regen)

### Step 1: Change the `.bin` file format

**Before (current):** Entire file is one big protobuf message with all 4M depth values packed inside.

**After:** 
```
[4 bytes LE] header_len
[header_len bytes] protobuf metadata (rows, cols, bbox, datum, source name, timestamps...)
[rows*cols*4 bytes] depths — raw float32 LE, row-major
[rows*cols*1 byte] source — raw uint8
[rows*cols*1 byte] confidence — raw uint8
```

The protobuf header has **no repeated fields** — just scalars. Tiny (~200 bytes).

### Step 2: Update `DepthSerializer`

- `serialize()`: write header_len + protobuf header, then dump `depths` FloatArray as raw bytes, then `source` ByteArray, then `confidence` ByteArray — all into one `ByteArray` output
- `deserialize()`: parse the first few bytes to get header_len, parse the protobuf header for dimensions, then use `ByteBuffer.wrap(bytes, offset, ...)` to create views into the three array regions, bulk-copy into `FloatArray`/`ByteArray` outputs
- Add a `deserializeHeader()` only (no bulk arrays) for the mmap path

### Step 3: Update `DepthRepository`

- On first load, copy `assets/depth/nice-frejus.bin` → `context.filesDir/depth/nice-frejus.bin`
- Open `FileChannel` → `map(READ_ONLY, 0, fileSize)` → `MappedByteBuffer`
- Call `DepthSerializer.deserializeHeader(mappedBuffer)` to get metadata
- Slice the buffer: `mappedBuffer.position(headerEnd); FloatBuffer depths = mappedBuffer.slice().asFloatBuffer()`
- Same for source/confidence as `ByteBuffer` slices
- Hold the `MappedByteBuffer` reference to prevent GC unmapping

### Step 4: Update `DepthGrid` (minimal change)

Option A (simplest): keep `FloatArray`/`ByteArray`. Bulk-copy from the mmap slices during construction. Peak heap drops from 66 MB → 24 MB. Fits.

Option B (zero-copy): change `depths` to `FloatBuffer`, `source`/`confidence` to `ByteBuffer`. No heap allocation for the arrays at all. But all callers (`depthAt()`, `DepthIsobaths`, `DepthBitmap`) need `.get(i)` instead of `[i]`. ~10 lines changed in `DepthGrid`, ~3 callers updated.

### Step 5: Regenerate the `.bin`

Run the prebake pipeline once to produce the new-format file:
```
apk-bake.bat depth
```
(or directly: `tools\bake-depth.bat`)

This calls `DepthPrebakeTest` which calls `DepthSerializer.serialize()` — once Step 2 is done, the output format changes automatically.

## What does NOT change

- `apk-bake.bat` / `apk-build.bat` / `apk-deploy.bat` — all work as before
- `DepthIsobaths`, `DepthBitmap`, `LowDepthWarningBitmap` — consume the same `DepthGrid` API
- Coastline pipeline — unrelated
- Settings scroll change — unrelated

## Peak memory comparison

| Phase | Before | After (Option A) | After (Option B) |
|-------|--------|----------|----------|
| File read | 22 MB heap | 0 MB (mmap off-heap) | 0 MB |
| Proto internals | 20 MB heap | 0 MB (no bulk proto) | 0 MB |
| Output arrays | 24 MB heap | 24 MB heap | 0 MB (views) |
| **Total** | **66 MB** | **24 MB** | **~2 MB** |
| Fits in 268 MB heap? | ❌ borderline | ✅ comfortably | ✅ trivially |

<!-- scope: reference -->
# Maro-II Spatial Engine Architecture Constraints

## Target Coordinates Boundary
All spatial algorithms must be hard-bounded to the Nice-to-Fréjus marine corridor: West 6.73°E, East 7.31°E, South 43.35°N, North 43.73°N. Reject or truncate any data ingestion outside this box.

## Memory-Mapped Architecture Enforcement
When writing Kotlin backend services for data tracking, you are FORBIDDEN from using standard Java `FileInputStream.readAllBytes()` or loading large floating-point arrays into JVM heap memory. You must strictly use native Java `FileChannel` and memory-mapped `ByteBuffers` to perform direct, index-calculated byte offsets (`ByteOffset = (Row * total_cols + Col) * 4L`).

## Asynchronous Visual Processing
Any implementation of Marching Squares (contour generation) or Bitmap color-ramp rendering must be isolated to a background Coroutine (`Dispatchers.Default`). The main UI thread must only receive completed `Bitmap` Ground Overlays or vector `PolylineOptions` ready for rendering.

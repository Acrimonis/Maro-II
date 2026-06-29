# FEAT_PLN: Notification Rendering Options (SDK 26+)

**Feature:** BoatTrace
**Date:** 2026-06-29
**Branch:** feature/notif-fix
**Status:** discussion

## Context

minSdk 26 (Android 8.0), targetSdk 36. Current implementation uses `InboxStyle` with `label: value` format. Proportional Roboto font prevents column alignment.

## Available Options

| Style | API | Columns | Complexity |
|-------|-----|---------|------------|
| `InboxStyle.addLine()` | 16+ | ❌ plain text | Trivial (current) |
| `BigTextStyle` | 16+ | ❌ single block | Trivial |
| HTML `<b>`/`<font>` in text | 24+ | ❌ inline only | Trivial |
| `DecoratedCustomViewStyle` + RemoteViews | 24+ | ✅ custom XML | Medium |
| `CustomContentView` + RemoteViews | 24+ | ✅ full control | High |

## Recommended: `DecoratedCustomViewStyle` + RemoteViews

Wraps custom layout in standard notification chrome (icon, app name, expand arrow). Gives pixel-perfect alignment regardless of font.

### Layout (`res/layout/notif_expanded.xml`)

```xml
<LinearLayout xmlns:android="..."
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="8dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">
        <TextView
            android:text="Speed:"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:textAlignment="textEnd"
            android:paddingEnd="8dp"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
        <TextView
            android:id="@+id/speed"
            android:layout_width="0dp"
            android:layout_weight="2"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
    </LinearLayout>

    <!-- repeat per row with same weight ratio -->
</LinearLayout>
```

### Usage

```kotlin
val expandedView = RemoteViews(packageName, R.layout.notif_expanded)
expandedView.setTextViewText(R.id.speed, "12.3 kn")
expandedView.setTextViewText(R.id.distance, "1.2 nm")
// ...

builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
builder.setCustomBigContentView(expandedView)
```

### Benefits
- Pixel-perfect 1:2 column alignment with any font
- Right-aligned labels (or fixed-width)
- Dark/light theme via `textAppearanceNotification`
- Works on minSdk 24 (we target 26)

### Trade-off
- XML layout file + RemoteViews setup
- More boilerplate per row
- Still simple (no custom Activity/Service)

## Alternative: Stay with InboxStyle

Current `label: value` format is simple, readable, and works everywhere. The formatting issue is cosmetic — the data is clear either way. Decision depends on how much visual polish is desired vs. implementation effort.

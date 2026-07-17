# FEAT_PLN: Notification — Custom RemoteViews Layout

**Feature:** BoatTrace
**Date:** 2026-06-29
**Branch:** feature/notif-fix
**Status:** plan — awaiting review

## Goal

Replace `InboxStyle.addLine()` with `DecoratedCustomViewStyle` + `RemoteViews` for pixel-perfect table formatting in the expanded notification.

## Architecture

```
TrackRecordingService.onStartCommand()
  └─ buildNotification(extras)
       ├─ setContentTitle / setContentText (collapsed, always plain text)
       ├─ setStyle(DecoratedCustomViewStyle)       ← new
       └─ setCustomBigContentView(expandedView)    ← new
            └─ RemoteViews from R.layout.notif_expanded
                 ├─ Row: Speed (conditional)
                 ├─ Row: Distance
                 ├─ Row: Elapsed
                 ├─ Row:   Navigating (indented)
                 ├─ Row:   Stationary (indented)
                 ├─ Row: Avg Speed (conditional)
                 ├─ Row: Max Speed (conditional)
                 └─ Row: Points
```

## Files

| File | Purpose |
|------|---------|
| `app/src/main/res/layout/notif_expanded.xml` | Column layout for expanded view |
| `app/.../data/track/TrackRecordingService.kt` | Populate RemoteViews, wire `DecoratedCustomViewStyle` |

## Layout: `res/layout/notif_expanded.xml`

Two-column layout: labels right-aligned (40% width), values left-aligned (60%). Indented sub-rows use `paddingStart="16dp"`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="8dp">

    <!-- Speed (shown when moving, hidden when idle) -->
    <LinearLayout
        android:id="@+id/row_speed"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
        <TextView
            android:layout_width="0dp"
            android:layout_weight="0.4"
            android:text="Speed:"
            android:textAlignment="textEnd"
            android:paddingEnd="8dp"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
        <TextView
            android:id="@+id/speed_value"
            android:layout_width="0dp"
            android:layout_weight="0.6"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
    </LinearLayout>

    <!-- Distance -->
    <LinearLayout
        android:id="@+id/row_distance"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
        <TextView
            android:layout_width="0dp"
            android:layout_weight="0.4"
            android:text="Distance:"
            android:textAlignment="textEnd"
            android:paddingEnd="8dp"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
        <TextView
            android:id="@+id/distance_value"
            android:layout_width="0dp"
            android:layout_weight="0.6"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
    </LinearLayout>

    <!-- Elapsed -->
    <LinearLayout
        android:id="@+id/row_elapsed"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
        <TextView
            android:layout_width="0dp"
            android:layout_weight="0.4"
            android:text="Elapsed:"
            android:textAlignment="textEnd"
            android:paddingEnd="8dp"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
        <TextView
            android:id="@+id/elapsed_value"
            android:layout_width="0dp"
            android:layout_weight="0.6"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
    </LinearLayout>

    <!-- Navigating/Moving (indented sub-row) -->
    <LinearLayout
        android:id="@+id/row_nav_time"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="16dp">
        <TextView
            android:id="@+id/nav_time_label"
            android:layout_width="0dp"
            android:layout_weight="0.4"
            android:textAlignment="textEnd"
            android:paddingEnd="8dp"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
        <TextView
            android:id="@+id/nav_time_value"
            android:layout_width="0dp"
            android:layout_weight="0.6"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
    </LinearLayout>

    <!-- Stationary (indented sub-row) -->
    <LinearLayout
        android:id="@+id/row_idle_time"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="16dp">
        <TextView
            android:layout_width="0dp"
            android:layout_weight="0.4"
            android:text="Stationary:"
            android:textAlignment="textEnd"
            android:paddingEnd="8dp"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
        <TextView
            android:id="@+id/idle_time_value"
            android:layout_width="0dp"
            android:layout_weight="0.6"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
    </LinearLayout>

    <!-- Avg Speed (shown when moving) -->
    <LinearLayout
        android:id="@+id/row_avg_speed"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
        <TextView
            android:layout_width="0dp"
            android:layout_weight="0.4"
            android:text="Avg Speed:"
            android:textAlignment="textEnd"
            android:paddingEnd="8dp"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
        <TextView
            android:id="@+id/avg_speed_value"
            android:layout_width="0dp"
            android:layout_weight="0.6"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
    </LinearLayout>

    <!-- Max Speed (shown when moving) -->
    <LinearLayout
        android:id="@+id/row_max_speed"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
        <TextView
            android:layout_width="0dp"
            android:layout_weight="0.4"
            android:text="Max Speed:"
            android:textAlignment="textEnd"
            android:paddingEnd="8dp"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
        <TextView
            android:id="@+id/max_speed_value"
            android:layout_width="0dp"
            android:layout_weight="0.6"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
    </LinearLayout>

    <!-- Points -->
    <LinearLayout
        android:id="@+id/row_points"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
        <TextView
            android:layout_width="0dp"
            android:layout_weight="0.4"
            android:text="Points:"
            android:textAlignment="textEnd"
            android:paddingEnd="8dp"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
        <TextView
            android:id="@+id/points_value"
            android:layout_width="0dp"
            android:layout_weight="0.6"
            android:textAppearance="?android:attr/textAppearanceNotification"/>
    </LinearLayout>

</LinearLayout>
```

## TrackRecordingService.kt — RemoteViews population

```kotlin
private fun buildExpandedView(extras: Bundle): RemoteViews {
    val recording = extras.getBoolean(EXTRA_RECORDING, false)
    val isMoving = extras.getBoolean(EXTRA_IS_MOVING, false)
    val speedKn = extras.getFloat(EXTRA_SPEED_KN, 0f)
    val distNm = extras.getFloat(EXTRA_DISTANCE_NM, 0f)
    val elapsedSec = extras.getLong(EXTRA_ELAPSED_SEC, 0L)
    val idleSec = extras.getLong(EXTRA_IDLE_SEC, 0L)
    val avgKn = extras.getFloat(EXTRA_AVG_SPEED_KN, 0f)
    val maxKn = extras.getFloat(EXTRA_MAX_SPEED_KN, 0f)
    val points = extras.getInt(EXTRA_POINT_COUNT, 0)
    val isOnWater = extras.getBoolean(EXTRA_ON_WATER, false)

    val rv = RemoteViews(packageName, R.layout.notif_expanded)

    if (recording) {
        // Speed — only when moving
        if (isMoving) {
            rv.setTextViewText(R.id.speed_value, "%.1f kn".format(speedKn))
        } else {
            rv.setViewVisibility(R.id.row_speed, View.GONE)
        }

        rv.setTextViewText(R.id.distance_value, "%.2f nm".format(distNm))
        rv.setTextViewText(R.id.elapsed_value, formatElapsed(elapsedSec))

        // Navigating/Moving time — label depends on state
        val navTimeLabel = if (isMoving) {
            if (isOnWater) "Navigating:" else "Moving:"
        } else {
            "Navigating:" // always show navigating time even when idle
        }
        rv.setTextViewText(R.id.nav_time_label, navTimeLabel)
        rv.setTextViewText(R.id.nav_time_value, formatElapsed(elapsedSec - idleSec))
        rv.setTextViewText(R.id.idle_time_value, formatElapsed(idleSec))

        // Avg/Max — only when moving
        if (isMoving) {
            rv.setTextViewText(R.id.avg_speed_value, "%.1f kn".format(avgKn))
            rv.setTextViewText(R.id.max_speed_value, "%.1f kn".format(maxKn))
        } else {
            rv.setViewVisibility(R.id.row_avg_speed, View.GONE)
            rv.setViewVisibility(R.id.row_max_speed, View.GONE)
        }

        rv.setTextViewText(R.id.points_value, "%d".format(points))
    } else {
        // Ready — hide all recording rows, show only speed
        val rows = listOf(R.id.row_distance, R.id.row_elapsed, R.id.row_nav_time,
            R.id.row_idle_time, R.id.row_avg_speed, R.id.row_max_speed, R.id.row_points)
        rows.forEach { rv.setViewVisibility(it, View.GONE) }

        if (isMoving) {
            rv.setTextViewText(R.id.speed_value, "%.1f kn".format(speedKn))
        } else {
            rv.setTextViewText(R.id.speed_value, "— kn")
        }
    }

    return rv
}

// In buildNotification:
builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
builder.setCustomBigContentView(buildExpandedView(extras))
```

## Collapsed view

Unchanged — still uses `setContentTitle` + `setContentText` with the 5-segment title:
```
Maro II • GPS • Navigating • Recording • On Water
```

## Rows visibility matrix

| Row | Recording + Moving | Recording + Idle | Ready |
|-----|-------------------|------------------|-------|
| Speed | ✅ | GONE | ✅ |
| Distance | ✅ | ✅ | GONE |
| Elapsed | ✅ | ✅ | GONE |
| Navigating/Moving time | ✅ | ✅ | GONE |
| Stationary time | ✅ | ✅ | GONE |
| Avg Speed | ✅ | GONE | GONE |
| Max Speed | ✅ | GONE | GONE |
| Points | ✅ | ✅ | GONE |

## Bonus: Dynamic adaptive icon — boat on state-colored background

Since `minSdk = 26`, we can use `Icon.createWithAdaptiveIconDrawable()` to render
the Maro boat over a dynamically colored background.

**Foreground:** `R.mipmap.ic_launcher_foreground` (existing boat PNG, all densities)
**Background:** `ColorDrawable` tinted per state

| State | Background Color | Hex |
|-------|-----------------|-----|
| Ready | White | `#FFFFFF` |
| Recording • Navigating | Green | `#4CAF50` |
| Recording • Idle | Blue | `#1565C0` (matches `statusGpsIdle`) |

```kotlin
// In buildNotification(), after determining recording/isMoving:

val fg = resources.getDrawable(R.mipmap.ic_launcher_foreground, null)
val bgColor = when {
    !recording -> android.graphics.Color.WHITE
    isMoving -> android.graphics.Color.parseColor("#4CAF50")
    else -> android.graphics.Color.parseColor("#1565C0")
}
val bg = android.graphics.drawable.ColorDrawable(bgColor)
val icon = android.graphics.drawable.Icon.createWithAdaptiveIconDrawable(fg, bg)
builder.setSmallIcon(icon)
```

No new drawable files needed. Replaces the current `setSmallIcon(android.R.drawable.ic_menu_compass)`.
The `Icon` API is available on API 26+, which matches the project's minSdk.

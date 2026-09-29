package ykws.android.maro.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val cancel: ImageVector
  get() {
    if (_cancel != null) {
      return _cancel!!
    }
    _cancel =
      ImageVector.Builder(
          name = "cancel",
          defaultWidth = 40.dp,
          defaultHeight = 40.dp,
          viewportWidth = 40f,
          viewportHeight = 40f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(13.83f, 28.11f)
            lineTo(20f, 21.94f)
            lineToRelative(6.17f, 6.17f)
            lineToRelative(1.94f, -1.94f)
            lineTo(21.94f, 20f)
            lineToRelative(6.17f, -6.17f)
            lineTo(26.17f, 11.89f)
            lineTo(20f, 18.06f)
            lineTo(13.83f, 11.89f)
            lineToRelative(-1.94f, 1.94f)
            lineTo(18.06f, 20f)
            lineToRelative(-6.17f, 6.17f)
            lineToRelative(1.94f, 1.94f)
            close()
            moveTo(20f, 36.67f)
            quadToRelative(-3.43f, 0f, -6.47f, -1.31f)
            reflectiveQuadTo(8.22f, 31.78f)
            reflectiveQuadTo(4.65f, 26.47f)
            reflectiveQuadTo(3.33f, 20f)
            quadToRelative(0f, -3.46f, 1.31f, -6.5f)
            reflectiveQuadTo(8.22f, 8.21f)
            reflectiveQuadTo(13.53f, 4.65f)
            reflectiveQuadTo(20f, 3.33f)
            quadToRelative(3.46f, 0f, 6.5f, 1.31f)
            reflectiveQuadToRelative(5.29f, 3.56f)
            reflectiveQuadToRelative(3.56f, 5.29f)
            reflectiveQuadTo(36.67f, 20f)
            quadToRelative(0f, 3.43f, -1.31f, 6.47f)
            reflectiveQuadToRelative(-3.56f, 5.31f)
            reflectiveQuadTo(26.5f, 35.35f)
            reflectiveQuadTo(20f, 36.67f)
            close()
            moveToRelative(0f, -2.78f)
            quadToRelative(5.81f, 0f, 9.85f, -4.06f)
            reflectiveQuadTo(33.89f, 20f)
            quadToRelative(0f, -5.81f, -4.04f, -9.85f)
            reflectiveQuadTo(20f, 6.11f)
            quadToRelative(-5.78f, 0f, -9.83f, 4.04f)
            reflectiveQuadTo(6.11f, 20f)
            quadToRelative(0f, 5.78f, 4.06f, 9.83f)
            reflectiveQuadTo(20f, 33.89f)
            close()
            moveTo(20f, 20f)
            close()
          }
        }
        .build()
    return _cancel!!
  }

private var _cancel: ImageVector? = null

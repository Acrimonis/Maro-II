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
public val Refresh: ImageVector
  get() {
    if (_Refresh != null) {
      return _Refresh!!
    }
    _Refresh =
      ImageVector.Builder(
          name = "Refresh",
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
            moveTo(20f, 33.33f)
            quadToRelative(-5.56f, 0f, -9.44f, -3.89f)
            reflectiveQuadTo(6.67f, 20f)
            reflectiveQuadToRelative(3.89f, -9.44f)
            reflectiveQuadTo(20f, 6.67f)
            quadToRelative(3.32f, 0f, 5.97f, 1.35f)
            reflectiveQuadToRelative(4.58f, 3.76f)
            verticalLineTo(6.67f)
            horizontalLineToRelative(2.78f)
            verticalLineTo(17.61f)
            horizontalLineTo(22.39f)
            verticalLineTo(14.83f)
            horizontalLineToRelative(7f)
            quadToRelative(-1.5f, -2.44f, -3.91f, -3.92f)
            reflectiveQuadTo(20f, 9.44f)
            quadToRelative(-4.42f, 0f, -7.49f, 3.07f)
            reflectiveQuadTo(9.44f, 20f)
            reflectiveQuadToRelative(3.07f, 7.49f)
            reflectiveQuadTo(20f, 30.56f)
            quadToRelative(3.38f, 0f, 6.15f, -1.93f)
            reflectiveQuadToRelative(3.88f, -5.1f)
            horizontalLineToRelative(2.89f)
            quadToRelative(-1.19f, 4.39f, -4.78f, 7.1f)
            reflectiveQuadTo(20f, 33.33f)
            close()
          }
        }
        .build()
    return _Refresh!!
  }

private var _Refresh: ImageVector? = null

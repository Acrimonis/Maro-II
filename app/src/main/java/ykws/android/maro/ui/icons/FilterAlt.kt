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
public val FilterAlt: ImageVector
  get() {
    if (_filter_alt != null) {
      return _filter_alt!!
    }
    _filter_alt =
      ImageVector.Builder(
          name = "filter_alt",
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
            moveTo(18.33f, 33.33f)
            quadToRelative(-0.71f, 0f, -1.19f, -0.48f)
            quadTo(16.67f, 32.38f, 16.67f, 31.67f)
            verticalLineToRelative(-10f)
            lineTo(6.81f, 9.08f)
            quadTo(6.21f, 8.33f, 6.63f, 7.5f)
            reflectiveQuadTo(8f, 6.67f)
            horizontalLineTo(32f)
            quadToRelative(0.94f, 0f, 1.37f, 0.83f)
            reflectiveQuadTo(33.19f, 9.08f)
            lineTo(23.33f, 21.67f)
            verticalLineToRelative(10f)
            quadToRelative(0f, 0.71f, -0.48f, 1.19f)
            quadToRelative(-0.48f, 0.48f, -1.19f, 0.48f)
            horizontalLineTo(18.33f)
            close()
            moveTo(20f, 21.39f)
            lineTo(29.42f, 9.44f)
            horizontalLineTo(10.58f)
            lineTo(20f, 21.39f)
            close()
            moveToRelative(0f, 0f)
            close()
          }
        }
        .build()
    return _filter_alt!!
  }

private var _filter_alt: ImageVector? = null

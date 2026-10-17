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
public val navigation: ImageVector
  get() {
    if (_navigation != null) {
      return _navigation!!
    }
    _navigation =
      ImageVector.Builder(
          name = "navigation",
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
            moveTo(8.06f, 35f)
            lineTo(6.67f, 33.61f)
            lineTo(20f, 3.33f)
            lineTo(33.33f, 33.61f)
            lineTo(31.94f, 35f)
            lineTo(20f, 29.67f)
            lineTo(8.06f, 35f)
            close()
            moveToRelative(3.06f, -4.44f)
            lineTo(20f, 26.67f)
            lineToRelative(8.89f, 3.89f)
            lineTo(20f, 10.17f)
            lineTo(11.11f, 30.56f)
            close()
            moveTo(20f, 26.67f)
            close()
          }
        }
        .build()
    return _navigation!!
  }

private var _navigation: ImageVector? = null

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
public val bolt: ImageVector
  get() {
    if (_bolt != null) {
      return _bolt!!
    }
    _bolt =
      ImageVector.Builder(
          name = "bolt",
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
            moveTo(16.78f, 32.19f)
            lineTo(27.4f, 19.44f)
            horizontalLineTo(20.18f)
            lineToRelative(1.4f, -11.1f)
            lineToRelative(-9.6f, 13.88f)
            horizontalLineToRelative(6.21f)
            lineToRelative(-1.42f, 9.97f)
            close()
            moveToRelative(-3.44f, 4.47f)
            lineTo(15f, 25f)
            horizontalLineTo(6.67f)
            lineToRelative(15f, -21.67f)
            horizontalLineTo(25f)
            lineTo(23.33f, 16.67f)
            horizontalLineToRelative(10f)
            lineToRelative(-16.67f, 20f)
            horizontalLineTo(13.33f)
            close()
            moveTo(19.71f, 20.25f)
            close()
          }
        }
        .build()
    return _bolt!!
  }

private var _bolt: ImageVector? = null

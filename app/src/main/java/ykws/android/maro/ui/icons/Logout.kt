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
public val logout: ImageVector
  get() {
    if (_logout != null) {
      return _logout!!
    }
    _logout =
      ImageVector.Builder(
          name = "logout",
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
            moveTo(7.78f, 35f)
            quadTo(6.65f, 35f, 5.83f, 34.17f)
            reflectiveQuadTo(5f, 32.22f)
            verticalLineTo(7.78f)
            quadTo(5f, 6.65f, 5.83f, 5.83f)
            reflectiveQuadTo(7.78f, 5f)
            horizontalLineTo(19.97f)
            verticalLineTo(7.78f)
            horizontalLineTo(7.78f)
            verticalLineTo(32.22f)
            horizontalLineTo(19.97f)
            verticalLineTo(35f)
            horizontalLineTo(7.78f)
            close()
            moveTo(27.39f, 27.64f)
            lineToRelative(-1.96f, -2f)
            lineToRelative(4.25f, -4.25f)
            horizontalLineTo(15f)
            verticalLineTo(18.61f)
            horizontalLineTo(29.63f)
            lineTo(25.38f, 14.36f)
            lineToRelative(1.96f, -2f)
            lineTo(35f, 20.03f)
            lineToRelative(-7.61f, 7.61f)
            close()
          }
        }
        .build()
    return _logout!!
  }

private var _logout: ImageVector? = null

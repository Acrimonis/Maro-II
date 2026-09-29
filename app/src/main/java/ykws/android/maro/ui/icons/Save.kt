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
public val save: ImageVector
  get() {
    if (_save != null) {
      return _save!!
    }
    _save =
      ImageVector.Builder(
          name = "save",
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
            moveTo(35f, 11.58f)
            verticalLineTo(32.22f)
            quadToRelative(0f, 1.12f, -0.83f, 1.95f)
            reflectiveQuadTo(32.22f, 35f)
            horizontalLineTo(7.78f)
            quadTo(6.65f, 35f, 5.83f, 34.17f)
            reflectiveQuadTo(5f, 32.22f)
            verticalLineTo(7.78f)
            quadTo(5f, 6.65f, 5.83f, 5.83f)
            reflectiveQuadTo(7.78f, 5f)
            horizontalLineTo(28.42f)
            lineTo(35f, 11.58f)
            close()
            moveToRelative(-2.78f, 1.22f)
            lineTo(27.19f, 7.78f)
            horizontalLineTo(7.78f)
            verticalLineTo(32.22f)
            horizontalLineTo(32.22f)
            verticalLineTo(12.81f)
            close()
            moveToRelative(-9f, 15.73f)
            quadToRelative(1.33f, -1.33f, 1.33f, -3.22f)
            reflectiveQuadTo(23.23f, 22.08f)
            reflectiveQuadTo(20.01f, 20.75f)
            reflectiveQuadToRelative(-3.23f, 1.33f)
            reflectiveQuadTo(15.44f, 25.3f)
            reflectiveQuadToRelative(1.33f, 3.23f)
            reflectiveQuadToRelative(3.22f, 1.33f)
            reflectiveQuadToRelative(3.23f, -1.33f)
            close()
            moveTo(9.81f, 16f)
            horizontalLineTo(24.75f)
            verticalLineTo(9.81f)
            horizontalLineTo(9.81f)
            verticalLineTo(16f)
            close()
            moveTo(7.78f, 12.81f)
            verticalLineTo(32.22f)
            verticalLineTo(7.78f)
            verticalLineToRelative(5.03f)
            close()
          }
        }
        .build()
    return _save!!
  }

private var _save: ImageVector? = null

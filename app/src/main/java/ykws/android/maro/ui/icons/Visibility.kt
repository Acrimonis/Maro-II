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
public val Visibility: ImageVector
  get() {
    if (_Visibility != null) {
      return _Visibility!!
    }
    _Visibility =
      ImageVector.Builder(
          name = "Visibility",
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
            moveTo(25.12f, 24.28f)
            quadToRelative(2.1f, -2.1f, 2.1f, -5.12f)
            reflectiveQuadToRelative(-2.1f, -5.12f)
            reflectiveQuadTo(20f, 11.94f)
            reflectiveQuadToRelative(-5.12f, 2.1f)
            reflectiveQuadToRelative(-2.1f, 5.12f)
            reflectiveQuadToRelative(2.1f, 5.12f)
            reflectiveQuadTo(20f, 26.39f)
            reflectiveQuadToRelative(5.12f, -2.1f)
            close()
            moveTo(16.73f, 22.44f)
            quadTo(15.39f, 21.1f, 15.39f, 19.17f)
            reflectiveQuadTo(16.73f, 15.9f)
            reflectiveQuadTo(20f, 14.56f)
            reflectiveQuadToRelative(3.27f, 1.34f)
            reflectiveQuadToRelative(1.34f, 3.27f)
            reflectiveQuadToRelative(-1.34f, 3.27f)
            reflectiveQuadTo(20f, 23.78f)
            reflectiveQuadTo(16.73f, 22.44f)
            close()
            moveTo(8.97f, 28.23f)
            quadTo(4.03f, 24.79f, 1.67f, 19.17f)
            quadTo(4.03f, 13.54f, 8.97f, 10.1f)
            reflectiveQuadTo(20f, 6.67f)
            reflectiveQuadTo(31.03f, 10.1f)
            reflectiveQuadToRelative(7.31f, 9.06f)
            quadToRelative(-2.36f, 5.62f, -7.31f, 9.06f)
            reflectiveQuadTo(20f, 31.67f)
            reflectiveQuadTo(8.97f, 28.23f)
            close()
            moveTo(20f, 19.17f)
            close()
            moveToRelative(9.06f, 7.08f)
            quadToRelative(4.13f, -2.65f, 6.3f, -7.08f)
            quadToRelative(-2.17f, -4.43f, -6.3f, -7.08f)
            reflectiveQuadTo(20f, 9.44f)
            reflectiveQuadToRelative(-9.06f, 2.65f)
            reflectiveQuadTo(4.61f, 19.17f)
            quadToRelative(2.19f, 4.43f, 6.33f, 7.08f)
            reflectiveQuadTo(20f, 28.89f)
            reflectiveQuadToRelative(9.06f, -2.65f)
            close()
          }
        }
        .build()
    return _Visibility!!
  }

private var _Visibility: ImageVector? = null

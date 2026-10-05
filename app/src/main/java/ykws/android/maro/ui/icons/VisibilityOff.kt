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
public val VisibilityOff: ImageVector
  get() {
    if (_VisibilityOff != null) {
      return _VisibilityOff!!
    }
    _VisibilityOff =
      ImageVector.Builder(
          name = "VisibilityOff",
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
            moveTo(26.42f, 22.42f)
            lineTo(24.39f, 20.39f)
            quadToRelative(0.85f, -2.63f, -1.13f, -4.5f)
            reflectiveQuadTo(18.78f, 14.78f)
            lineTo(16.75f, 12.75f)
            quadToRelative(0.71f, -0.42f, 1.53f, -0.61f)
            reflectiveQuadTo(20f, 11.94f)
            quadToRelative(3.01f, 0f, 5.12f, 2.1f)
            reflectiveQuadToRelative(2.1f, 5.12f)
            quadToRelative(0f, 0.89f, -0.21f, 1.73f)
            reflectiveQuadToRelative(-0.6f, 1.52f)
            close()
            moveToRelative(5.36f, 5.33f)
            lineTo(29.86f, 25.86f)
            quadToRelative(1.89f, -1.4f, 3.31f, -3.12f)
            reflectiveQuadToRelative(2.19f, -3.58f)
            quadTo(33.28f, 14.68f, 29.2f, 12.06f)
            reflectiveQuadTo(20.28f, 9.44f)
            quadToRelative(-1.57f, 0f, -3.18f, 0.28f)
            reflectiveQuadToRelative(-2.68f, 0.69f)
            lineTo(12.28f, 8.25f)
            quadTo(13.82f, 7.57f, 15.93f, 7.12f)
            reflectiveQuadTo(20.14f, 6.67f)
            quadToRelative(6.07f, 0f, 11f, 3.42f)
            reflectiveQuadToRelative(7.19f, 9.08f)
            quadToRelative(-1.04f, 2.6f, -2.7f, 4.77f)
            reflectiveQuadToRelative(-3.85f, 3.81f)
            close()
            moveToRelative(1.89f, 9.69f)
            lineToRelative(-7f, -6.89f)
            quadToRelative(-1.46f, 0.54f, -3.17f, 0.83f)
            reflectiveQuadTo(20f, 31.67f)
            quadToRelative(-6.15f, 0f, -11.1f, -3.42f)
            reflectiveQuadTo(1.67f, 19.17f)
            quadTo(2.51f, 16.99f, 3.94f, 14.98f)
            reflectiveQuadTo(7.36f, 11.22f)
            lineTo(2.33f, 6.17f)
            lineTo(4.28f, 4.19f)
            lineTo(35.53f, 35.44f)
            lineToRelative(-1.86f, 2f)
            close()
            moveTo(9.28f, 13.17f)
            quadTo(7.85f, 14.28f, 6.56f, 15.93f)
            reflectiveQuadTo(4.61f, 19.17f)
            quadToRelative(2.11f, 4.49f, 6.26f, 7.1f)
            reflectiveQuadToRelative(9.35f, 2.62f)
            quadToRelative(1.19f, 0f, 2.35f, -0.15f)
            reflectiveQuadToRelative(1.88f, -0.41f)
            lineTo(22.17f, 26.03f)
            quadToRelative(-0.46f, 0.18f, -1.04f, 0.27f)
            reflectiveQuadTo(20f, 26.39f)
            quadToRelative(-2.99f, 0f, -5.1f, -2.09f)
            reflectiveQuadTo(12.78f, 19.17f)
            quadToRelative(0f, -0.57f, 0.09f, -1.13f)
            reflectiveQuadTo(13.14f, 17f)
            lineTo(9.28f, 13.17f)
            close()
            moveToRelative(12.9f, 5.24f)
            close()
            moveToRelative(-5.32f, 2.65f)
            close()
          }
        }
        .build()
    return _VisibilityOff!!
  }

private var _VisibilityOff: ImageVector? = null

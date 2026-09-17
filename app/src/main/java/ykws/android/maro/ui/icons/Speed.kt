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
public val Speed: ImageVector
  get() {
    if (_speed_2 != null) {
      return _speed_2!!
    }
    _speed_2 =
      ImageVector.Builder(
          name = "speed_2",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
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
            moveTo(13.4f, 15.43f)
            quadToRelative(0.65f, -0.65f, 0.6f, -1.5f)
            quadToRelative(-0.05f, -0.85f, -0.75f, -1.4f)
            quadTo(11.28f, 10.98f, 9.15f, 9.65f)
            reflectiveQuadTo(4.98f, 6.95f)
            quadToRelative(1.35f, 2.07f, 2.68f, 4.19f)
            reflectiveQuadToRelative(2.8f, 4.19f)
            quadToRelative(0.5f, 0.73f, 1.4f, 0.74f)
            reflectiveQuadTo(13.4f, 15.43f)
            close()
            moveTo(5.13f, 20f)
            quadTo(4.58f, 20f, 4.11f, 19.76f)
            reflectiveQuadTo(3.38f, 19.05f)
            quadTo(2.68f, 17.85f, 2.33f, 16.54f)
            reflectiveQuadTo(1.98f, 13.85f)
            quadTo(2f, 12.45f, 2.44f, 11.13f)
            reflectiveQuadTo(3.65f, 8.6f)
            lineToRelative(1.2f, 1.9f)
            quadTo(4.43f, 11.3f, 4.2f, 12.16f)
            quadTo(3.98f, 13.02f, 3.98f, 13.9f)
            quadToRelative(0f, 1.1f, 0.29f, 2.14f)
            reflectiveQuadTo(5.13f, 18f)
            horizontalLineTo(18.9f)
            quadToRelative(0.52f, -0.9f, 0.81f, -1.91f)
            quadTo(20f, 15.08f, 20f, 14f)
            quadTo(20f, 10.68f, 17.66f, 8.34f)
            reflectiveQuadTo(12f, 6f)
            quadTo(11.08f, 6f, 10.19f, 6.22f)
            reflectiveQuadTo(8.5f, 6.85f)
            lineTo(6.6f, 5.65f)
            quadTo(7.8f, 4.85f, 9.16f, 4.42f)
            quadTo(10.53f, 4f, 12f, 4f)
            quadToRelative(2.08f, 0f, 3.89f, 0.79f)
            reflectiveQuadToRelative(3.17f, 2.15f)
            reflectiveQuadToRelative(2.15f, 3.17f)
            reflectiveQuadTo(22f, 14f)
            quadToRelative(0f, 1.35f, -0.35f, 2.63f)
            reflectiveQuadToRelative(-1f, 2.43f)
            quadToRelative(-0.28f, 0.47f, -0.75f, 0.71f)
            reflectiveQuadTo(18.9f, 20f)
            horizontalLineTo(5.13f)
            close()
            moveToRelative(6.85f, -5.95f)
            close()
          }
        }
        .build()
    return _speed_2!!
  }

private var _speed_2: ImageVector? = null

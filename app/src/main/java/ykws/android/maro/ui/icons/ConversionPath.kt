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
public val Conversion_path: ImageVector
  get() {
    if (_Conversion_path != null) {
      return _Conversion_path!!
    }
    _Conversion_path =
      ImageVector.Builder(
          name = "Conversion_path",
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
            moveTo(31.83f, 35f)
            quadToRelative(-1.68f, 0f, -2.96f, -0.99f)
            reflectiveQuadTo(27.21f, 31.56f)
            horizontalLineTo(18.42f)
            quadToRelative(-2.75f, 0f, -4.61f, -1.86f)
            reflectiveQuadTo(11.94f, 25.08f)
            reflectiveQuadToRelative(1.86f, -4.61f)
            reflectiveQuadToRelative(4.61f, -1.86f)
            horizontalLineToRelative(3.25f)
            quadToRelative(1.6f, 0f, 2.65f, -1.05f)
            reflectiveQuadToRelative(1.05f, -2.65f)
            reflectiveQuadTo(24.31f, 12.27f)
            reflectiveQuadTo(21.67f, 11.22f)
            horizontalLineTo(12.79f)
            quadToRelative(-0.43f, 1.46f, -1.69f, 2.45f)
            reflectiveQuadTo(8.17f, 14.67f)
            quadToRelative(-2.03f, 0f, -3.43f, -1.4f)
            reflectiveQuadTo(3.33f, 9.83f)
            reflectiveQuadTo(4.74f, 6.4f)
            reflectiveQuadTo(8.17f, 5f)
            quadTo(9.85f, 5f, 11.1f, 5.99f)
            reflectiveQuadToRelative(1.69f, 2.45f)
            horizontalLineToRelative(8.88f)
            quadToRelative(2.75f, 0f, 4.61f, 1.86f)
            reflectiveQuadToRelative(1.86f, 4.61f)
            reflectiveQuadToRelative(-1.86f, 4.61f)
            reflectiveQuadToRelative(-4.61f, 1.86f)
            horizontalLineTo(18.42f)
            quadToRelative(-1.6f, 0f, -2.65f, 1.05f)
            reflectiveQuadToRelative(-1.05f, 2.65f)
            reflectiveQuadToRelative(1.05f, 2.65f)
            reflectiveQuadToRelative(2.65f, 1.05f)
            horizontalLineToRelative(8.79f)
            quadToRelative(0.43f, -1.46f, 1.69f, -2.45f)
            reflectiveQuadToRelative(2.94f, -0.99f)
            quadToRelative(2.03f, 0f, 3.43f, 1.4f)
            reflectiveQuadToRelative(1.4f, 3.43f)
            reflectiveQuadToRelative(-1.4f, 3.43f)
            reflectiveQuadTo(31.83f, 35f)
            close()
            moveTo(9.63f, 11.3f)
            quadToRelative(0.59f, -0.59f, 0.59f, -1.47f)
            reflectiveQuadTo(9.63f, 8.37f)
            reflectiveQuadTo(8.17f, 7.78f)
            reflectiveQuadTo(6.7f, 8.37f)
            reflectiveQuadTo(6.11f, 9.83f)
            reflectiveQuadTo(6.7f, 11.3f)
            reflectiveQuadToRelative(1.47f, 0.59f)
            reflectiveQuadTo(9.63f, 11.3f)
            close()
          }
        }
        .build()
    return _Conversion_path!!
  }

private var _Conversion_path: ImageVector? = null

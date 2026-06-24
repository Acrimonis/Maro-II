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
public val Output_circle: ImageVector
  get() {
    if (_Output_circle != null) {
      return _Output_circle!!
    }
    _Output_circle =
      ImageVector.Builder(
          name = "Output_circle",
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
            moveTo(20f, 36.69f)
            lineTo(12.06f, 28.75f)
            lineTo(14f, 26.81f)
            lineToRelative(4.61f, 4.57f)
            verticalLineTo(13.61f)
            horizontalLineToRelative(2.78f)
            verticalLineTo(31.38f)
            lineTo(26f, 26.81f)
            lineToRelative(1.94f, 1.94f)
            lineTo(20f, 36.69f)
            close()
            moveTo(6.08f, 29.17f)
            quadTo(4.75f, 27.13f, 4.04f, 24.79f)
            reflectiveQuadTo(3.33f, 20f)
            quadToRelative(0f, -3.46f, 1.31f, -6.5f)
            reflectiveQuadTo(8.21f, 8.21f)
            reflectiveQuadTo(13.5f, 4.65f)
            reflectiveQuadTo(20f, 3.33f)
            reflectiveQuadToRelative(6.5f, 1.31f)
            reflectiveQuadToRelative(5.29f, 3.56f)
            reflectiveQuadToRelative(3.56f, 5.29f)
            reflectiveQuadTo(36.67f, 20f)
            quadToRelative(0f, 2.46f, -0.71f, 4.79f)
            reflectiveQuadToRelative(-2.04f, 4.38f)
            lineTo(31.89f, 27.19f)
            quadToRelative(1f, -1.65f, 1.5f, -3.46f)
            reflectiveQuadTo(33.89f, 20f)
            quadToRelative(0f, -5.81f, -4.04f, -9.85f)
            reflectiveQuadTo(20f, 6.11f)
            reflectiveQuadToRelative(-9.85f, 4.04f)
            reflectiveQuadTo(6.11f, 20f)
            quadToRelative(0f, 1.93f, 0.5f, 3.75f)
            reflectiveQuadToRelative(1.5f, 3.44f)
            lineTo(6.08f, 29.17f)
            close()
          }
        }
        .build()
    return _Output_circle!!
  }

private var _Output_circle: ImageVector? = null

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
public val route: ImageVector
  get() {
    if (_route != null) {
      return _route!!
    }
    _route =
      ImageVector.Builder(
          name = "route",
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
            moveTo(10.24f, 33.08f)
            quadTo(8.33f, 31.16f, 8.33f, 28.47f)
            verticalLineTo(14.24f)
            quadTo(6.88f, 13.69f, 5.94f, 12.49f)
            reflectiveQuadTo(5f, 9.73f)
            quadTo(5f, 7.75f, 6.39f, 6.37f)
            reflectiveQuadTo(9.74f, 5f)
            reflectiveQuadToRelative(3.33f, 1.38f)
            reflectiveQuadToRelative(1.37f, 3.35f)
            quadToRelative(0f, 1.54f, -0.94f, 2.75f)
            reflectiveQuadToRelative(-2.4f, 1.76f)
            verticalLineTo(28.47f)
            quadToRelative(0f, 1.55f, 1.09f, 2.65f)
            reflectiveQuadToRelative(2.67f, 1.1f)
            reflectiveQuadToRelative(2.66f, -1.1f)
            reflectiveQuadToRelative(1.08f, -2.65f)
            verticalLineTo(11.53f)
            quadToRelative(0f, -2.72f, 1.9f, -4.62f)
            reflectiveQuadTo(25.14f, 5f)
            reflectiveQuadToRelative(4.62f, 1.9f)
            reflectiveQuadToRelative(1.9f, 4.62f)
            verticalLineTo(25.76f)
            quadToRelative(1.46f, 0.54f, 2.4f, 1.75f)
            reflectiveQuadTo(35f, 30.28f)
            quadToRelative(0f, 1.94f, -1.38f, 3.33f)
            reflectiveQuadTo(30.28f, 35f)
            quadToRelative(-1.94f, 0f, -3.33f, -1.39f)
            reflectiveQuadTo(25.56f, 30.28f)
            quadToRelative(0f, -1.55f, 0.94f, -2.78f)
            reflectiveQuadToRelative(2.4f, -1.73f)
            verticalLineTo(11.53f)
            quadToRelative(0f, -1.57f, -1.09f, -2.66f)
            reflectiveQuadTo(25.14f, 7.78f)
            reflectiveQuadTo(22.48f, 8.87f)
            reflectiveQuadToRelative(-1.09f, 2.66f)
            verticalLineTo(28.47f)
            quadToRelative(0f, 2.69f, -1.9f, 4.61f)
            reflectiveQuadTo(14.86f, 35f)
            reflectiveQuadTo(10.24f, 33.08f)
            close()
            moveTo(9.74f, 11.67f)
            quadToRelative(0.81f, 0f, 1.37f, -0.58f)
            reflectiveQuadTo(11.67f, 9.71f)
            reflectiveQuadTo(11.11f, 8.34f)
            reflectiveQuadTo(9.72f, 7.78f)
            quadToRelative(-0.79f, 0f, -1.37f, 0.56f)
            reflectiveQuadTo(7.78f, 9.72f)
            quadToRelative(0f, 0.79f, 0.58f, 1.37f)
            reflectiveQuadToRelative(1.38f, 0.58f)
            close()
            moveTo(30.29f, 32.22f)
            quadToRelative(0.81f, 0f, 1.37f, -0.58f)
            reflectiveQuadToRelative(0.56f, -1.38f)
            reflectiveQuadTo(31.66f, 28.9f)
            reflectiveQuadTo(30.28f, 28.33f)
            quadToRelative(-0.79f, 0f, -1.37f, 0.56f)
            reflectiveQuadToRelative(-0.58f, 1.39f)
            quadToRelative(0f, 0.79f, 0.58f, 1.37f)
            reflectiveQuadToRelative(1.38f, 0.58f)
            close()
            moveTo(9.72f, 9.72f)
            close()
            moveTo(30.28f, 30.28f)
            close()
          }
        }
        .build()
    return _route!!
  }

private var _route: ImageVector? = null

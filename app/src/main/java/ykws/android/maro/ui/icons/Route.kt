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
            moveTo(6.18f, 19.83f)
            quadTo(5f, 18.65f, 5f, 17f)
            verticalLineTo(8.82f)
            quadTo(4.13f, 8.5f, 3.56f, 7.74f)
            quadTo(3f, 6.97f, 3f, 6f)
            quadTo(3f, 4.75f, 3.88f, 3.88f)
            reflectiveQuadTo(6f, 3f)
            reflectiveQuadTo(8.13f, 3.88f)
            reflectiveQuadTo(9f, 6f)
            quadTo(9f, 6.97f, 8.44f, 7.74f)
            reflectiveQuadTo(7f, 8.82f)
            verticalLineTo(17f)
            quadToRelative(0f, 0.82f, 0.59f, 1.41f)
            reflectiveQuadTo(9f, 19f)
            quadToRelative(0.83f, 0f, 1.41f, -0.59f)
            reflectiveQuadTo(11f, 17f)
            verticalLineTo(7f)
            quadTo(11f, 5.35f, 12.18f, 4.17f)
            reflectiveQuadTo(15f, 3f)
            reflectiveQuadToRelative(2.82f, 1.17f)
            reflectiveQuadTo(19f, 7f)
            verticalLineToRelative(8.17f)
            quadToRelative(0.88f, 0.33f, 1.44f, 1.09f)
            reflectiveQuadTo(21f, 18f)
            quadToRelative(0f, 1.25f, -0.88f, 2.13f)
            reflectiveQuadTo(18f, 21f)
            reflectiveQuadTo(15.88f, 20.13f)
            reflectiveQuadTo(15f, 18f)
            quadToRelative(0f, -0.98f, 0.56f, -1.75f)
            reflectiveQuadTo(17f, 15.18f)
            verticalLineTo(7f)
            quadTo(17f, 6.18f, 16.41f, 5.59f)
            reflectiveQuadTo(15f, 5f)
            reflectiveQuadTo(13.59f, 5.59f)
            quadTo(13f, 6.18f, 13f, 7f)
            verticalLineTo(17f)
            quadToRelative(0f, 1.65f, -1.17f, 2.82f)
            reflectiveQuadTo(9f, 21f)
            reflectiveQuadTo(6.18f, 19.83f)
            close()
            moveTo(6f, 7f)
            quadTo(6.43f, 7f, 6.71f, 6.71f)
            quadTo(7f, 6.43f, 7f, 6f)
            reflectiveQuadTo(6.71f, 5.29f)
            reflectiveQuadTo(6f, 5f)
            quadTo(5.58f, 5f, 5.29f, 5.29f)
            reflectiveQuadTo(5f, 6f)
            reflectiveQuadTo(5.29f, 6.71f)
            reflectiveQuadTo(6f, 7f)
            close()
            moveTo(18f, 19f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(19f, 18.43f, 19f, 18f)
            reflectiveQuadTo(18.71f, 17.29f)
            reflectiveQuadTo(18f, 17f)
            reflectiveQuadToRelative(-0.71f, 0.29f)
            reflectiveQuadTo(17f, 18f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(18f, 19f)
            close()
            moveTo(6f, 6f)
            close()
            moveTo(18f, 18f)
            close()
          }
        }
        .build()
    return _route!!
  }

private var _route: ImageVector? = null

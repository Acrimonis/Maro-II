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
public val Location_on: ImageVector
  get() {
    if (_Location_on != null) {
      return _Location_on!!
    }
    _Location_on =
      ImageVector.Builder(
          name = "location_on",
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
            moveTo(22.16f, 18.83f)
            quadToRelative(0.9f, -0.9f, 0.9f, -2.16f)
            reflectiveQuadToRelative(-0.9f, -2.16f)
            reflectiveQuadTo(20f, 13.61f)
            reflectiveQuadToRelative(-2.16f, 0.9f)
            reflectiveQuadToRelative(-0.9f, 2.16f)
            reflectiveQuadToRelative(0.9f, 2.16f)
            reflectiveQuadTo(20f, 19.72f)
            reflectiveQuadToRelative(2.16f, -0.9f)
            close()
            moveTo(20f, 33f)
            quadToRelative(5.39f, -4.92f, 7.97f, -8.92f)
            reflectiveQuadTo(30.56f, 17f)
            quadToRelative(0f, -4.79f, -3.06f, -7.84f)
            reflectiveQuadTo(20f, 6.11f)
            reflectiveQuadTo(12.51f, 9.16f)
            reflectiveQuadTo(9.44f, 17f)
            quadToRelative(0f, 3.07f, 2.63f, 7.08f)
            reflectiveQuadTo(20f, 33f)
            close()
            moveToRelative(0f, 3.67f)
            quadTo(13.29f, 30.96f, 9.98f, 26.06f)
            reflectiveQuadTo(6.67f, 17f)
            quadToRelative(0f, -6.25f, 4.02f, -9.96f)
            reflectiveQuadTo(20f, 3.33f)
            reflectiveQuadToRelative(9.31f, 3.71f)
            reflectiveQuadTo(33.33f, 17f)
            quadToRelative(0f, 4.17f, -3.31f, 9.06f)
            reflectiveQuadTo(20f, 36.67f)
            close()
            moveToRelative(0f, -20f)
            close()
          }
        }
        .build()
    return _Location_on!!
  }

private var _Location_on: ImageVector? = null

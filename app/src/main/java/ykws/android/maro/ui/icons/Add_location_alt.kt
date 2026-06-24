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
public val Add_location_alt: ImageVector
  get() {
    if (_Add_location_alt != null) {
      return _Add_location_alt!!
    }
    _Add_location_alt =
      ImageVector.Builder(
          name = "add_location_alt",
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
            moveTo(20f, 36.67f)
            quadTo(13.29f, 30.96f, 9.98f, 26.06f)
            reflectiveQuadTo(6.67f, 17f)
            quadToRelative(0f, -6.25f, 4.02f, -9.96f)
            reflectiveQuadTo(20f, 3.33f)
            quadToRelative(0.61f, 0f, 1.18f, 0.04f)
            reflectiveQuadToRelative(1.15f, 0.15f)
            verticalLineTo(6.37f)
            quadTo(21.78f, 6.24f, 21.2f, 6.17f)
            reflectiveQuadTo(20f, 6.11f)
            quadToRelative(-4.44f, 0f, -7.5f, 3.05f)
            reflectiveQuadTo(9.44f, 17f)
            quadToRelative(0f, 3.07f, 2.63f, 7.08f)
            reflectiveQuadTo(20f, 33f)
            quadToRelative(5.39f, -4.92f, 7.97f, -8.92f)
            reflectiveQuadTo(30.56f, 17f)
            quadToRelative(0f, -0.25f, -0.01f, -0.5f)
            reflectiveQuadTo(30.5f, 16f)
            horizontalLineToRelative(2.81f)
            quadToRelative(0.03f, 0.25f, 0.03f, 0.5f)
            reflectiveQuadToRelative(0f, 0.5f)
            quadToRelative(0f, 4.17f, -3.31f, 9.06f)
            reflectiveQuadTo(20f, 36.67f)
            close()
            moveTo(22.16f, 18.82f)
            quadToRelative(0.9f, -0.9f, 0.9f, -2.16f)
            reflectiveQuadToRelative(-0.9f, -2.16f)
            reflectiveQuadTo(20f, 13.61f)
            reflectiveQuadToRelative(-2.16f, 0.9f)
            reflectiveQuadToRelative(-0.9f, 2.16f)
            reflectiveQuadToRelative(0.9f, 2.16f)
            reflectiveQuadTo(20f, 19.72f)
            reflectiveQuadToRelative(2.16f, -0.9f)
            close()
            moveTo(20f, 16.67f)
            close()
            moveTo(30.33f, 13.22f)
            horizontalLineToRelative(2.78f)
            verticalLineTo(8f)
            horizontalLineToRelative(5.22f)
            verticalLineTo(5.22f)
            horizontalLineTo(33.11f)
            verticalLineTo(0f)
            horizontalLineTo(30.33f)
            verticalLineTo(5.22f)
            horizontalLineTo(25.11f)
            verticalLineTo(8f)
            horizontalLineToRelative(5.22f)
            verticalLineToRelative(5.22f)
            close()
          }
        }
        .build()
    return _Add_location_alt!!
  }

private var _Add_location_alt: ImageVector? = null

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
public val Publish: ImageVector
  get() {
    if (_Publish != null) {
      return _Publish!!
    }
    _Publish =
      ImageVector.Builder(
          name = "Publish",
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
            moveTo(11f, 20f)
            verticalLineTo(11.85f)
            lineToRelative(-2.6f, 2.6f)
            lineTo(7f, 13f)
            lineTo(12f, 8f)
            lineToRelative(5f, 5f)
            lineToRelative(-1.4f, 1.45f)
            lineTo(13f, 11.85f)
            verticalLineTo(20f)
            horizontalLineTo(11f)
            close()
            moveTo(4f, 9f)
            verticalLineTo(6f)
            quadTo(4f, 5.18f, 4.59f, 4.59f)
            reflectiveQuadTo(6f, 4f)
            horizontalLineTo(18f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            quadTo(20f, 5.18f, 20f, 6f)
            verticalLineTo(9f)
            horizontalLineTo(18f)
            verticalLineTo(6f)
            horizontalLineTo(6f)
            verticalLineTo(9f)
            horizontalLineTo(4f)
            close()
          }
        }
        .build()
    return _Publish!!
  }

private var _Publish: ImageVector? = null

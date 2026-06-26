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
public val where_to_vote: ImageVector
  get() {
    if (_where_to_vote != null) {
      return _where_to_vote!!
    }
    _where_to_vote =
      ImageVector.Builder(
          name = "where_to_vote",
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
            moveTo(10.95f, 13.35f)
            lineTo(15.9f, 8.4f)
            lineTo(14.48f, 6.97f)
            lineTo(10.95f, 10.5f)
            lineTo(9.55f, 9.1f)
            lineTo(8.13f, 10.52f)
            lineToRelative(2.83f, 2.83f)
            close()
            moveToRelative(1.05f, 6f)
            quadToRelative(3.05f, -2.8f, 4.53f, -5.09f)
            quadTo(18f, 11.98f, 18f, 10.2f)
            quadTo(18f, 7.47f, 16.26f, 5.74f)
            quadTo(14.53f, 4f, 12f, 4f)
            reflectiveQuadTo(7.74f, 5.74f)
            quadTo(6f, 7.47f, 6f, 10.2f)
            quadToRelative(0f, 1.78f, 1.48f, 4.06f)
            reflectiveQuadTo(12f, 19.35f)
            close()
            moveTo(12f, 22f)
            quadTo(7.98f, 18.58f, 5.99f, 15.64f)
            reflectiveQuadTo(4f, 10.2f)
            quadTo(4f, 6.45f, 6.41f, 4.22f)
            reflectiveQuadTo(12f, 2f)
            reflectiveQuadToRelative(5.59f, 2.22f)
            reflectiveQuadTo(20f, 10.2f)
            quadToRelative(0f, 2.5f, -1.99f, 5.44f)
            quadTo(16.03f, 18.58f, 12f, 22f)
            close()
            moveTo(12f, 10f)
            close()
          }
        }
        .build()
    return _where_to_vote!!
  }

private var _where_to_vote: ImageVector? = null

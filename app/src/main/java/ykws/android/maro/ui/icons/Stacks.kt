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
public val Stacks: ImageVector
  get() {
    if (_Stacks != null) {
      return _Stacks!!
    }
    _Stacks =
      ImageVector.Builder(
          name = "Stacks",
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
            moveTo(20f, 23.06f)
            lineTo(1.69f, 13.19f)
            lineTo(20f, 3.33f)
            lineToRelative(18.33f, 9.86f)
            lineTo(20f, 23.06f)
            close()
            moveToRelative(0f, 6.81f)
            lineTo(2.68f, 20.54f)
            lineTo(5.6f, 18.96f)
            lineTo(20f, 26.71f)
            lineTo(34.43f, 18.96f)
            lineToRelative(2.92f, 1.58f)
            lineTo(20f, 29.86f)
            close()
            moveToRelative(0f, 6.81f)
            lineTo(2.68f, 27.35f)
            lineTo(5.6f, 25.76f)
            lineTo(20f, 33.51f)
            lineTo(34.43f, 25.76f)
            lineToRelative(2.92f, 1.58f)
            lineTo(20f, 36.67f)
            close()
            moveTo(20f, 19.88f)
            lineTo(32.54f, 13.19f)
            lineTo(20f, 6.51f)
            lineTo(7.49f, 13.19f)
            lineTo(20f, 19.88f)
            close()
            moveToRelative(0.03f, -6.68f)
            close()
          }
        }
        .build()
    return _Stacks!!
  }

private var _Stacks: ImageVector? = null

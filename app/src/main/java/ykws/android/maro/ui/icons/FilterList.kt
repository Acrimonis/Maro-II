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
public val FilterList: ImageVector
  get() {
    if (_FilterList != null) {
      return _FilterList!!
    }
    _FilterList =
      ImageVector.Builder(
          name = "FilterList",
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
            moveTo(16.67f, 30f)
            verticalLineTo(27.22f)
            horizontalLineToRelative(6.67f)
            verticalLineTo(30f)
            horizontalLineTo(16.67f)
            close()
            moveTo(10f, 21.39f)
            verticalLineTo(18.61f)
            horizontalLineTo(30f)
            verticalLineToRelative(2.78f)
            horizontalLineTo(10f)
            close()
            moveTo(5f, 12.78f)
            verticalLineTo(10f)
            horizontalLineTo(35f)
            verticalLineToRelative(2.78f)
            horizontalLineTo(5f)
            close()
          }
        }
        .build()
    return _FilterList!!
  }

private var _FilterList: ImageVector? = null

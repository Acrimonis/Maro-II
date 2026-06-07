package ykws.android.maro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ykws.android.maro.ui.map.CoastlineViewModel
import ykws.android.maro.ui.map.DepthViewModel
import ykws.android.maro.ui.map.MapScreen
import ykws.android.maro.ui.map.ZoneConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load zone gradient tunables from zone.properties before the UI composes.
        ZoneConfig.init(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use a factory because CoastlineViewModel now extends AndroidViewModel
                    // with a multi-param constructor that AndroidViewModelFactory can't match.
                    val viewModel: CoastlineViewModel = viewModel(
                        factory = CoastlineViewModel.Factory
                    )
                    val depthViewModel: DepthViewModel = viewModel()

                    // Restore persisted coastline + depth caches on first composition
                    LaunchedEffect(Unit) {
                        viewModel.initCache(this@MainActivity)
                        depthViewModel.initCache(this@MainActivity)
                    }

                    MapScreen(
                        viewModel = viewModel,
                        depthViewModel = depthViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

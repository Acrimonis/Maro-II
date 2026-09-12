package ykws.android.maro.data.power

import android.content.Context
import android.os.PowerManager
import ykws.android.maro.data.settings.AppSettings

/**
 * Single home for the battery-optimization exemption question.
 *
 * This exists because the same decision was being made in four places with two different answers:
 * the Activity checked both the "already asked" flag **and** whether the app is exempt, while the two
 * MapScreen gates checked only the flag — so a user who had already granted the exemption could still
 * be prompted on that path. One predicate means the paths cannot drift apart again.
 *
 * Framework-light by necessity (a `PowerManager` query needs a `Context`), but it deliberately knows
 * nothing about UI, and `data/power/` gains no `ui/` dependency.
 */
object BatteryExemption {

    /**
     * Whether the app is already exempt from battery optimization.
     *
     * Fails **safe**: returns true when the service cannot be reached, so a failed query never turns
     * into a nagging prompt.
     */
    fun isExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Whether the exemption prompt should be shown: not answered before, and not already exempt.
     * Every trigger site asks this rather than re-deriving the rule.
     */
    fun shouldPrompt(context: Context, settings: AppSettings): Boolean =
        !settings.batteryOptimizationPrompted && !isExempt(context)
}

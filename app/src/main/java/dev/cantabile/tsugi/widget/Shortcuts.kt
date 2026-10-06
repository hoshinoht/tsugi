package dev.cantabile.tsugi.widget

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import dev.cantabile.tsugi.MainActivity
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.Favourite

/**
 * Launcher shortcuts for saved stops and places: the first few favourites appear when you
 * long-press the app icon (after the static Nearby and Search ones), and any stop can be pinned
 * to the home screen.
 */
object Shortcuts {
    /** Launchers show about four shortcuts; two are the static Nearby and Search. */
    private const val MAX_DYNAMIC = 2

    fun updateDynamic(context: Context, favourites: List<Favourite>, stopName: (String) -> String?) {
        val shortcuts = favourites.asSequence()
            .map { f -> if (f is Favourite.Service) Favourite.Stop(f.stopCode) else f }
            .distinct()
            .take(MAX_DYNAMIC)
            .mapIndexed { rank, f ->
                when (f) {
                    is Favourite.Place -> place(context, f.id, f.name)
                    else -> stop(context, f.stopCode, stopName(f.stopCode) ?: f.stopCode)
                }.setRank(rank).build()
            }
            .toList()
        runCatching { context.getSystemService(ShortcutManager::class.java).dynamicShortcuts = shortcuts }
    }

    /** Asks the launcher to pin a shortcut to [code]; false if it can't. */
    fun pinStop(context: Context, code: String, name: String): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (!manager.isRequestPinShortcutSupported) return false
        return runCatching { manager.requestPinShortcut(stop(context, code, name).build(), null) }.getOrDefault(false)
    }

    private fun stop(context: Context, code: String, name: String) = ShortcutInfo.Builder(context, "stop:$code")
        .setShortLabel(name.take(SHORT_LABEL))
        .setLongLabel(name)
        .setIcon(Icon.createWithResource(context, R.mipmap.ic_shortcut_stop))
        .setIntent(open(context).putExtra(MainActivity.EXTRA_OPEN_STOP, code))

    private fun place(context: Context, id: String, name: String) = ShortcutInfo.Builder(context, "place:$id")
        .setShortLabel(name.take(SHORT_LABEL))
        .setLongLabel(name)
        .setIcon(Icon.createWithResource(context, R.mipmap.ic_shortcut_place))
        .setIntent(open(context).putExtra(MainActivity.EXTRA_OPEN_PLACE, id))

    private fun open(context: Context) = Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)

    private const val SHORT_LABEL = 25
}

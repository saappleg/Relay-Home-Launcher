package com.relayhome.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * The only boundary allowed to hand a viewer from Relay into a provider.
 * Each provider owns its playback UI; Relay only hands the selected title or
 * known resume target across through that provider's public URI contract.
 */
internal object ProviderHandoff {
    private const val nuvioPackage = "com.nuvio.tv"
    private val smartTubePackages = listOf(
        "app.smarttube.stable",
        "org.smarttube.stable",
        "org.smarttube.beta"
    )

    fun isSmartTubeInstalled(context: Context): Boolean =
        smartTubePackages.any { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun isProviderPackage(packageName: String): Boolean =
        packageName == nuvioPackage || packageName in smartTubePackages

    fun isNuvioInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(nuvioPackage) != null

    fun openNuvio(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(nuvioPackage)
        if (intent == null) {
            notice(context, "Nuvio is not installed on this device.")
        } else {
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { notice(context, "Nuvio could not be opened.") }
        }
    }

    /** Opens Nuvio's title detail page so the viewer can choose a stream in Nuvio. */
    fun openNuvioEpisode(context: Context, item: MediaItem) {
        val contentId = item.providerContentId
        if (contentId.isNullOrBlank()) {
            openNuvio(context)
            return
        }
        val uri = Uri.parse("nuvio://meta").buildUpon()
            .appendQueryParameter("type", item.contentType)
            .appendQueryParameter("id", contentId)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(nuvioPackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            openNuvio(context)
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { openNuvio(context) }
    }

    fun openSmartTube(context: Context) {
        val intent = smartTubePackages.asSequence()
            .mapNotNull { context.packageManager.getLaunchIntentForPackage(it) }
            .firstOrNull()
        if (intent == null) {
            notice(context, "SmartTube is not installed. Install its Android TV stable or beta build, then Relay will add it automatically.")
        } else {
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { notice(context, "SmartTube could not be opened.") }
        }
    }

    /** Opens a specific YouTube video in the installed SmartTube variant. */
    fun openSmartTubeVideo(context: Context, videoId: String) {
        val packageName = smartTubePackages.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
        if (packageName == null) {
            notice(context, "SmartTube is not installed. Install its Android TV stable or beta build first.")
            return
        }
        val uri = Uri.parse("https://www.youtube.com/watch").buildUpon()
            .appendQueryParameter("v", videoId)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            notice(context, "This SmartTube build does not support direct video links.")
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { notice(context, "SmartTube could not open that video.") }
    }

    fun openStremioBoard(context: Context) = launch(
        context,
        Uri.parse("stremio:///board"),
        "Stremio is not installed or does not support this link yet."
    )

    fun searchStremio(context: Context, query: String) {
        val uri = Uri.parse("stremio:///search").buildUpon()
            .appendQueryParameter("search", query)
            .build()
        launch(context, uri, "Stremio is not installed or does not support search links yet.")
    }

    fun play(context: Context, item: MediaItem) {
        when (item.provider) {
            Provider.STREMIO -> {
                val id = item.providerContentId
                if (id == null) {
                    notice(context, "This is visual demo content. Connect a Stremio catalog before playback handoff.")
                    return
                }
                val videoId = if (item.contentType == "movie") id else id
                val uri = Uri.parse("stremio:///detail/${item.contentType}/$id/$videoId?autoPlay=false")
                launch(context, uri, "Stremio is not installed or does not support playback links yet.")
            }
            Provider.NUVIO -> openNuvioEpisode(context, item)
            Provider.SMARTTUBE -> item.providerContentId?.let { openSmartTubeVideo(context, it) } ?: openSmartTube(context)
        }
    }

    private fun launch(context: Context, uri: Uri, unavailableMessage: String) {
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            notice(context, unavailableMessage)
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { notice(context, unavailableMessage) }
    }

    private fun notice(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

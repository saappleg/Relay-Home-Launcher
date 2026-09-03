package com.relayhome.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

/**
 * The only boundary allowed to hand a viewer from Relay into a provider.
 * Each provider owns its playback UI; Relay only hands the selected title or
 * known resume target across through that provider's public URI contract.
 */
internal object ProviderHandoff {
    private const val nuvioPackage = "com.nuvio.tv"
    private const val stremioPackage = "com.stremio.one"
    private val smartTubePackages = listOf(
        "com.relaytube.stable",
        "com.relaytube.beta",
        "com.relaytube.fdroid",
        "app.smarttube.stable",
        "org.smarttube.stable",
        "org.smarttube.beta"
    )

    fun isSmartTubeInstalled(context: Context): Boolean =
        installedSmartTubePackage(context) != null

    internal fun installedSmartTubePackage(context: Context): String? = runCatching {
        smartTubePackages.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
    }.getOrNull()

    internal fun isSmartTubePackage(packageName: String): Boolean = packageName in smartTubePackages

    fun isProviderPackage(packageName: String): Boolean =
        packageName == nuvioPackage || packageName == stremioPackage || packageName in smartTubePackages

    fun isNuvioInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(nuvioPackage) != null

    fun isStremioInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(stremioPackage) != null

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
        val uri = buildNuvioMetaUri(item)
        if (uri == null) {
            if (item.providerContentId.isNullOrBlank()) openNuvio(context)
            else notice(context, "Nuvio cannot open this item type from Relay.")
            return
        }
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
            notice(context, "RelayTube is not installed. Install RelayTube, then Relay will add it automatically.")
        } else {
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { notice(context, "RelayTube could not be opened.") }
        }
    }

    /** Opens a specific YouTube video in the installed SmartTube / RelayTube variant. */
    fun openSmartTubeVideo(context: Context, videoId: String, resumePositionMs: Long = 0L) {
        val cleanVideoId = cleanProviderId(videoId)
        val packageName = installedSmartTubePackage(context)
        if (packageName == null) {
            notice(context, "RelayTube is not installed. Install RelayTube first.")
            return
        }
        if (cleanVideoId == null) {
            notice(context, "RelayTube cannot open a video without a valid YouTube id.")
            return
        }
        val uri = Uri.parse("https://www.youtube.com/watch").buildUpon()
            .appendQueryParameter("v", cleanVideoId)
            .apply {
                if (resumePositionMs > 0L) appendQueryParameter("t", "${resumePositionMs / 1_000}s")
            }
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            notice(context, "This RelayTube build does not support direct video links.")
            openSmartTube(context)
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { notice(context, "RelayTube could not open that video.") }
    }

    fun openStremio(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(stremioPackage)
        if (intent == null) {
            notice(context, "Stremio is not installed on this device.")
        } else {
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { notice(context, "Stremio could not be opened.") }
        }
    }

    fun openStremioBoard(context: Context) = launch(
        context,
        Uri.parse("stremio:///board"),
        "Stremio is not installed or does not support this link yet.",
        stremioPackage
    )

    fun searchStremio(context: Context, query: String) {
        val uri = Uri.parse("stremio:///search").buildUpon()
            .appendQueryParameter("search", query)
            .build()
        launch(context, uri, "Stremio is not installed or does not support search links yet.", stremioPackage)
    }

    fun search(context: Context, provider: Provider, query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return
        when (provider) {
            Provider.STREMIO -> searchStremio(context, cleanQuery)
            Provider.NUVIO -> {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("nuvio://search").buildUpon().appendQueryParameter("query", cleanQuery).build()
                ).setPackage(nuvioPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    runCatching { context.startActivity(intent) }.onFailure { openNuvio(context) }
                } else {
                    openNuvio(context)
                }
            }
            Provider.SMARTTUBE -> {
                val packageName = installedSmartTubePackage(context)
                if (packageName == null) {
                    notice(context, "RelayTube is not installed on this device.")
                    return
                }
                val uri = Uri.parse("https://www.youtube.com/results").buildUpon()
                    .appendQueryParameter("search_query", cleanQuery)
                    .build()
                val intent = Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    runCatching { context.startActivity(intent) }
                        .onFailure { openSmartTube(context) }
                } else {
                    openSmartTube(context)
                }
            }
        }
    }

    fun play(context: Context, item: MediaItem) {
        when (item.provider) {
            Provider.STREMIO -> {
                val uri = buildStremioDetailUri(item)
                if (uri == null) {
                    notice(context, "Stremio cannot open this item without a supported id and content type.")
                    return
                }
                launch(context, uri, "Stremio is not installed or does not support playback links yet.", stremioPackage)
            }
            Provider.NUVIO -> openNuvioEpisode(context, item)
            Provider.SMARTTUBE -> item.providerContentId?.let { openSmartTubeVideo(context, it, item.resumePositionMs) } ?: openSmartTube(context)
        }
    }

    fun openProvider(context: Context, provider: Provider) {
        when (provider) {
            Provider.STREMIO -> openStremio(context)
            Provider.NUVIO -> openNuvio(context)
            Provider.SMARTTUBE -> openSmartTube(context)
        }
    }

    /** Builds Nuvio's title handoff only for the types its public URI accepts. */
    internal fun buildNuvioMetaUri(item: MediaItem): Uri? {
        val contentId = cleanProviderId(item.providerContentId) ?: return null
        val type = when (item.contentType.trim().lowercase(Locale.ROOT)) {
            "movie" -> "movie"
            "tv", "show", "series", "episode" -> "tv"
            else -> return null
        }
        return Uri.parse("nuvio://meta").buildUpon()
            .appendQueryParameter("type", type)
            .appendQueryParameter("id", contentId)
            .build()
    }

    /** Builds Stremio's documented detail link without inventing an episode id. */
    internal fun buildStremioDetailUri(item: MediaItem): Uri? {
        val id = cleanProviderId(item.providerContentId) ?: return null
        val type = when (item.contentType.trim().lowercase(Locale.ROOT)) {
            "movie" -> "movie"
            "tv" -> "tv"
            "show", "series", "episode" -> "series"
            "channel" -> "channel"
            else -> return null
        }
        val episodeVideoId = if (type == "series") {
            val match = Regex("(?i)S\\s*(\\d+)\\D{0,8}E\\s*(\\d+)").find(item.episodeInfo.orEmpty())
            val season = match?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
            val episode = match?.groupValues?.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }
            if (season != null && episode != null) "$id:$season:$episode" else null
        } else {
            null
        }
        return Uri.parse("stremio:///detail").buildUpon()
            .appendPath(type)
            .appendPath(id)
            .apply {
                when {
                    type == "movie" || type == "tv" -> appendPath(id)
                    episodeVideoId != null -> appendPath(episodeVideoId)
                }
            }
            .appendQueryParameter("autoPlay", "false")
            .build()
    }

    private fun cleanProviderId(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?.takeIf { it.none(Char::isWhitespace) && '/' !in it && '?' !in it && '#' !in it }

    private fun launch(context: Context, uri: Uri, unavailableMessage: String, packageName: String? = null) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { packageName?.let { setPackage(it) } }
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

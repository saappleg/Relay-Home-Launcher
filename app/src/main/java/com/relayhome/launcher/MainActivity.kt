package com.relayhome.launcher

import android.os.Bundle
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RelayHomeApp() }
    }

    fun requestHomeRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 801)
            }
        } else {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    fun requestNotificationListenerAccess() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

private enum class Destination(val label: String) { HOME("Home"), DETAIL("Detail"), APPS("Apps"), SEARCH("Search"), CALENDAR("Calendar"), SETTINGS("Settings"), PROVIDER("Provider"), NUVIO_CONNECT("Nuvio connect") }
internal enum class Provider(val label: String, val accent: Color) {
    STREMIO("Stremio", Color(0xFF5B87FF)),
    NUVIO("Nuvio", Color(0xFFAF7AFF)),
    SMARTTUBE("SmartTube", Color(0xFFFF5F5F))
}

internal data class MediaItem(
    val title: String,
    val provider: Provider,
    val progress: Float,
    val colors: List<Color>,
    val artworkUrl: String,
    /** Provider-native identifier; demo artwork deliberately has none. */
    val providerContentId: String? = null,
    val contentType: String = "movie",
    /** Season/episode context when a provider has it. */
    val episodeInfo: String? = null,
    val showTitle: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val rating: Double? = null,
    val genres: String? = null
)

private data class Hero(
    val title: String,
    val subtitle: String,
    val palette: RelayPalette,
    val artworkUrl: String,
    val item: MediaItem? = null
)

private data class RelayPalette(val accent: Color, val glow: Color, val backdrop: Color)

private val midnight = Color(0xFF050608)
private val ivory = Color(0xFFF6F2EA)
private val muted = Color(0xFFB7B8C1)

private val orbitalPalette = RelayPalette(Color(0xFF6B9FFF), Color(0xFF192B61), Color(0xFF0D1932))
private val violetPalette = RelayPalette(Color(0xFFC187FF), Color(0xFF39205B), Color(0xFF1D112A))

@Composable
private fun RelayHomeApp() {
    val context = LocalContext.current
    var dateFormat by remember { mutableStateOf(DateFormatSettings.load(context)) }
    var nuvioSession by remember { mutableStateOf(NuvioSessionStore.load(context)) }
    var enabledProviders by remember {
        mutableStateOf(
            ProviderSettingsStore.load(
                context,
                fallback = if (nuvioSession != null) setOf(Provider.NUVIO) else emptySet()
            )
        )
    }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var activeProvider by remember { mutableStateOf(Provider.STREMIO) }
    var peekProvider by remember { mutableStateOf<Provider?>(null) }
    var nuvioProfiles by remember { mutableStateOf(emptyList<NuvioProfile>()) }
    var activeNuvioProfile by remember { mutableStateOf(NuvioSessionStore.loadProfile(context)) }
    var nuvioMedia by remember { mutableStateOf(emptyList<MediaItem>()) }
    var nuvioSyncing by remember { mutableStateOf(false) }
    var nuvioSyncError by remember { mutableStateOf<String?>(null) }
    var nuvioRefreshGeneration by remember { mutableStateOf(0) }
    var upcomingEpisodes by remember { mutableStateOf(emptyList<TmdbCalendarEntry>()) }
    var tmdbRecommendations by remember { mutableStateOf(emptyList<MediaItem>()) }
    val smartTubeNowPlaying = SmartTubePlaybackStore.nowPlaying
    LaunchedEffect(nuvioSession) {
        nuvioSession?.let { session ->
            NuvioApi.pullProfiles(session).onSuccess { profiles ->
                nuvioProfiles = profiles
                if (profiles.none { it.index == activeNuvioProfile }) activeNuvioProfile = profiles.firstOrNull()?.index ?: 1
            }
        }
    }
    LaunchedEffect(nuvioSession, activeNuvioProfile, nuvioRefreshGeneration) {
        nuvioSession?.let { session ->
            nuvioSyncing = true
            nuvioSyncError = null
            NuvioApi.pullRelayMedia(session, activeNuvioProfile)
                .onSuccess { nuvioMedia = it }
                .onFailure { nuvioSyncError = "Couldn’t sync Nuvio yet. Check the connection and try again." }
            nuvioSyncing = false
        }
    }
    LaunchedEffect(nuvioMedia) {
        upcomingEpisodes = TmdbApi.upcomingEpisodes(nuvioMedia)
    }
    LaunchedEffect(nuvioMedia) {
        tmdbRecommendations = TmdbApi.recommendations(nuvioMedia)
    }
    var selectedMedia by remember { mutableStateOf(sampleContinueWatching(setOf(Provider.STREMIO, Provider.NUVIO)).first()) }
    val detailScope = rememberCoroutineScope()
    fun openMediaDetails(item: MediaItem) {
        selectedMedia = item
        destination = Destination.DETAIL
        if (Regex("S\\d+\\s*•\\s*E\\d+").containsMatchIn(item.episodeInfo.orEmpty())) {
            detailScope.launch {
                val enriched = TmdbApi.enrichEpisodeDetails(item)
                if (destination == Destination.DETAIL && selectedMedia == item) selectedMedia = enriched
            }
        }
    }
    var activeHero by remember {
        mutableStateOf(Hero(
            "ORBITAL NIGHT",
            "A distant signal. A hidden truth. The orbit is never silent.",
            orbitalPalette,
            "https://images.unsplash.com/photo-1446776877081-d282a0f896e2?auto=format&fit=crop&w=1800&q=85"
        ))
    }
    val heroCandidates = remember(enabledProviders, nuvioMedia) {
        (nuvioMedia + sampleContinueWatching(enabledProviders).filter { it.provider != Provider.NUVIO } + sampleRecommended(enabledProviders))
            .filter { it.provider in enabledProviders }
            .distinctBy { "${it.provider}:${it.providerContentId ?: it.title}" }
    }
    LaunchedEffect(heroCandidates) {
        if (heroCandidates.isEmpty()) return@LaunchedEffect
        var index = 0
        while (true) {
            val item = heroCandidates[index % heroCandidates.size]
            activeHero = Hero(
                item.showTitle ?: item.title,
                item.episodeInfo ?: item.description ?: "Continue where you left off.",
                paletteFor(item),
                item.artworkUrl,
                item
            )
            delay(11_000)
            index++
        }
    }

    val palette = activeHero.palette
    val background by animateColorAsState(palette.backdrop, label = "media background")

    MaterialTheme(colorScheme = darkColorScheme(background = midnight, onBackground = ivory)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(background, midnight), endY = 1000f
                    )
                )
        ) {
            when (destination) {
                Destination.HOME -> HomeScreen(
                    hero = activeHero,
                    palette = palette,
                    providers = enabledProviders,
                    onDestination = { destination = it },
                    onProvider = { provider -> activeProvider = provider; destination = Destination.PROVIDER },
                    peekProvider = peekProvider,
                    onPeekProvider = { peekProvider = it },
                    onSettings = { destination = Destination.SETTINGS },
                    onHeroChanged = { hero -> if (hero != activeHero) activeHero = hero },
                    onItemSelected = ::openMediaDetails,
                    nuvioItems = nuvioMedia,
                    upcomingEpisodes = upcomingEpisodes,
                    recommendations = tmdbRecommendations,
                    dateFormat = dateFormat,
                    smartTubeNowPlaying = smartTubeNowPlaying,
                    nuvioProfiles = nuvioProfiles,
                    activeNuvioProfile = activeNuvioProfile,
                    onNuvioProfileSelected = {
                        activeNuvioProfile = it
                        NuvioSessionStore.saveProfile(context, it)
                    }
                )
                Destination.DETAIL -> DetailsScreen(selectedMedia, paletteFor(selectedMedia), dateFormat) { destination = Destination.HOME }
                Destination.APPS -> AppsScreen(
                    palette = palette,
                    onBackHome = { destination = Destination.HOME }
                )
                Destination.SETTINGS -> SettingsScreen(
                    palette = palette,
                    providers = enabledProviders,
                    onBackHome = { destination = Destination.HOME },
                    onProviderToggle = { provider ->
                        enabledProviders = if (provider in enabledProviders) enabledProviders - provider else enabledProviders + provider
                        ProviderSettingsStore.save(context, enabledProviders)
                    },
                    onRequestHome = { (context as? MainActivity)?.requestHomeRole() },
                    onRequestSmartTubeAccess = { (context as? MainActivity)?.requestNotificationListenerAccess() },
                    nuvioConnected = nuvioSession != null,
                    nuvioSyncing = nuvioSyncing,
                    nuvioItemCount = nuvioMedia.size,
                    nuvioSyncError = nuvioSyncError,
                    onRefreshNuvio = { nuvioRefreshGeneration++ },
                    dateFormat = dateFormat,
                    onDateFormatChanged = {
                        dateFormat = it
                        DateFormatSettings.save(context, it)
                    }
                )
                Destination.SEARCH -> SearchScreen(
                    palette = palette,
                    providers = enabledProviders,
                    onBackHome = { destination = Destination.HOME },
                    onItemSelected = ::openMediaDetails
                )
                Destination.CALENDAR -> CalendarScreen(
                    palette = palette,
                    providers = enabledProviders,
                    nuvioItems = nuvioMedia,
                    upcomingEpisodes = upcomingEpisodes,
                    dateFormat = dateFormat,
                    onBackHome = { destination = Destination.HOME },
                    onItemSelected = ::openMediaDetails
                )
                Destination.PROVIDER -> ProviderHubScreen(
                    activeProvider,
                    palette,
                    onBack = { destination = Destination.HOME },
                    onConnectNuvio = { destination = Destination.NUVIO_CONNECT },
                    nuvioConnected = nuvioSession != null,
                    nuvioSyncing = nuvioSyncing,
                    nuvioItemCount = nuvioMedia.size,
                    nuvioSyncError = nuvioSyncError,
                    nuvioProfiles = nuvioProfiles,
                    activeNuvioProfile = activeNuvioProfile,
                    onNuvioProfileSelected = {
                        activeNuvioProfile = it
                        NuvioSessionStore.saveProfile(context, it)
                    },
                    onRefreshNuvio = { nuvioRefreshGeneration++ },
                    onDisconnectNuvio = {
                        NuvioSessionStore.clear(context)
                        nuvioSession = null
                        nuvioProfiles = emptyList()
                        nuvioMedia = emptyList()
                        enabledProviders -= Provider.NUVIO
                        ProviderSettingsStore.save(context, enabledProviders)
                    }
                )
                Destination.NUVIO_CONNECT -> NuvioConnectScreen(
                    palette = violetPalette,
                    connected = nuvioSession != null,
                    onConnected = {
                        NuvioSessionStore.save(context, it)
                        nuvioSession = it
                        if (Provider.NUVIO !in enabledProviders) {
                            enabledProviders += Provider.NUVIO
                            ProviderSettingsStore.save(context, enabledProviders)
                        }
                        destination = Destination.PROVIDER
                    },
                    onBack = { destination = Destination.PROVIDER }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    hero: Hero,
    palette: RelayPalette,
    providers: Set<Provider>,
    onDestination: (Destination) -> Unit,
    onProvider: (Provider) -> Unit,
    peekProvider: Provider?,
    onPeekProvider: (Provider?) -> Unit,
    onSettings: () -> Unit,
    onHeroChanged: (Hero) -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    nuvioItems: List<MediaItem>,
    upcomingEpisodes: List<TmdbCalendarEntry>,
    recommendations: List<MediaItem>,
    dateFormat: RelayDateFormat,
    smartTubeNowPlaying: SmartTubeNowPlaying?,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    onNuvioProfileSelected: (Int) -> Unit
) {
    val homeFocusRequester = remember { FocusRequester() }
    val peekFocusRequester = remember { FocusRequester() }
    val heroFocusRequester = remember { FocusRequester() }
    val homeListState = rememberLazyListState()
    var profilePickerVisible by remember { mutableStateOf(false) }
    val smartTubeItem = smartTubeNowPlaying?.let { playback ->
        MediaItem(
            title = playback.title,
            provider = Provider.SMARTTUBE,
            progress = if (playback.durationMs > 0) (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 0f,
            colors = listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight),
            artworkUrl = playback.artworkUrl.orEmpty(),
            episodeInfo = listOfNotNull(playback.channel, if (playback.playing) "Playing now" else "Paused").joinToString(" • ").ifBlank { null }
        )
    }
    // The primary rail is deliberately provider-neutral: real Nuvio progress,
    // active SmartTube playback, and each enabled provider's available feed.
    val continueWatching = remember(providers, nuvioItems, smartTubeItem) {
        (listOfNotNull(smartTubeItem) + nuvioItems + sampleContinueWatching(providers).filter { it.provider != Provider.NUVIO })
            .filter { it.provider in providers }
            .distinctBy { "${it.provider}:${it.providerContentId ?: it.title}" }
    }
    val recommendationItems = remember(providers, recommendations) {
        recommendations.filter { it.provider in providers }.ifEmpty { sampleRecommended(providers) }
    }
    val homeScope = rememberCoroutineScope()
    // Do not restore a previous focus-scroll offset into the hero when returning to Home.
    LaunchedEffect(Unit) {
        homeListState.scrollToItem(0)
        homeFocusRequester.requestFocus()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = homeListState,
            contentPadding = PaddingValues(bottom = 52.dp)
        ) {
            item {
                if (peekProvider != null) {
                    AppPeekPanel(
                        provider = peekProvider,
                        items = if (peekProvider == Provider.NUVIO && nuvioItems.isNotEmpty()) nuvioItems else sampleContinueWatching(setOf(peekProvider)),
                        palette = palette,
                        focusRequester = peekFocusRequester,
                        onPreviewFocused = { homeScope.launch { homeListState.scrollToItem(0) } },
                        onItemSelected = onItemSelected,
                        onArtworkColor = { accent ->
                            if (accent != null) onHeroChanged(hero.copy(palette = paletteFor(MediaItem("", peekProvider, 0f, emptyList(), ""), accent)))
                        }
                    )
                } else HeroPanel(
                    hero, palette, homeFocusRequester, heroFocusRequester,
                    onHeroFocused = { homeScope.launch { homeListState.scrollToItem(0) } },
                    onItemSelected = onItemSelected
                ) { accent ->
                    if (accent != null) onHeroChanged(hero.copy(palette = hero.palette.copy(accent = accent, glow = accent.copy(alpha = .32f))))
                }
                Spacer(Modifier.height(18.dp))
            }
            if (providers.isEmpty()) {
                item {
                    EmptyHomeState(palette, onSettings)
                }
            } else {
                item {
                    MediaRail("Continue Watching", continueWatching, palette, dateFormat, onHeroChanged, onItemSelected, upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester)
                    Spacer(Modifier.height(18.dp))
                }
                item {
                    MediaRail("Recommended TV Shows", recommendationItems, palette, dateFormat, onHeroChanged, onItemSelected, posters = true, upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester)
                    Spacer(Modifier.height(18.dp))
                }
                if (upcomingEpisodes.isNotEmpty()) {
                    item {
                        MediaRail("Coming Up", upcomingEpisodes.map { it.item }, palette, dateFormat, onHeroChanged, onItemSelected, showPremiereDate = true, upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester)
                    }
                }
            }
        }
        // The navigation lives over the artwork, keeping the visual field continuous from the top edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(midnight.copy(alpha = .78f), midnight.copy(alpha = .30f), Color.Transparent)))
                .padding(top = 24.dp, bottom = 30.dp)
        ) {
            TopBar(providers, palette, homeFocusRequester, heroFocusRequester, peekFocusRequester, onDestination, onProvider, onSettings, onPeekProvider, nuvioProfiles, activeNuvioProfile) {
                profilePickerVisible = true
            }
        }
        if (profilePickerVisible) {
            ProfileSwitcher(
                palette = palette,
                profiles = nuvioProfiles,
                activeProfile = activeNuvioProfile,
                onSelect = {
                    onNuvioProfileSelected(it)
                    profilePickerVisible = false
                },
                onDismiss = { profilePickerVisible = false }
            )
        }
    }
}

@Composable
private fun EmptyHomeState(palette: RelayPalette, onSettings: () -> Unit) {
    Column(Modifier.padding(horizontal = 76.dp, vertical = 18.dp)) {
        Text("Add your media", color = ivory, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text("Choose Nuvio, Stremio, or SmartTube in Settings to build your personal Home view.", color = muted, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        ActionButton("Open Settings", palette, primary = true, onClick = onSettings)
    }
}

@Composable
private fun TopBar(
    providers: Set<Provider>,
    palette: RelayPalette,
    homeFocusRequester: FocusRequester,
    heroFocusRequester: FocusRequester,
    peekFocusRequester: FocusRequester,
    onDestination: (Destination) -> Unit,
    onProvider: (Provider) -> Unit,
    onSettings: () -> Unit,
    onPeekProvider: (Provider?) -> Unit,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("RELAY HOME", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp)
        Spacer(Modifier.weight(1f))
        TopDestination("Home", selected = true, palette = palette, focusRequester = homeFocusRequester, downFocusRequester = heroFocusRequester, onFocused = { if (it) onPeekProvider(null) }) { onDestination(Destination.HOME) }
        providers.sortedBy { it.label }.forEach { provider ->
            TopDestination(provider.label, selected = false, palette = palette, downFocusRequester = peekFocusRequester, onFocused = { if (it) onPeekProvider(provider) }) { onProvider(provider) }
        }
        TopDestination("Calendar", selected = false, palette = palette) { onDestination(Destination.CALENDAR) }
        TopDestination("Apps", selected = false, palette = palette) { onDestination(Destination.APPS) }
        TopDestination("Search", selected = false, palette = palette) { onDestination(Destination.SEARCH) }
        Spacer(Modifier.width(16.dp))
        if (nuvioProfiles.isNotEmpty()) {
            ProfileAvatarButton(nuvioProfiles.firstOrNull { it.index == activeNuvioProfile }, palette, onProfileClick)
            Spacer(Modifier.width(12.dp))
        }
        EmbossedSettingsButton(palette, onSettings)
    }
}

@Composable
private fun ProfileAvatarButton(profile: NuvioProfile?, palette: RelayPalette, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier = Modifier.size(45.dp).clip(CircleShape)
            .background(Provider.NUVIO.accent.copy(alpha = .78f))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .3f), CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source),
        contentAlignment = Alignment.Center
    ) {
        Text(profile?.name?.firstOrNull()?.uppercase() ?: "P", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileSwitcher(
    palette: RelayPalette,
    profiles: List<NuvioProfile>,
    activeProfile: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(midnight.copy(alpha = .82f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(430.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121C)).border(1.dp, palette.accent.copy(alpha = .6f), RoundedCornerShape(22.dp)).padding(28.dp)
        ) {
            Text("Who’s watching?", color = ivory, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            Text("Switching profiles refreshes Relay with that Nuvio profile’s Continue Watching.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            profiles.forEach { profile ->
                ActionButton(profile.name, palette.copy(accent = Provider.NUVIO.accent), primary = profile.index == activeProfile) { onSelect(profile.index) }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(8.dp))
            ActionButton("Cancel", palette, primary = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun TopDestination(
    label: String,
    selected: Boolean,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val active = selected || focused
    Text(
        text = label,
        color = if (active) ivory else muted,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 17.sp,
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .padding(horizontal = 7.dp)
            .then(if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier)
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) palette.accent.copy(alpha = if (selected) .24f else .16f) else Color.Transparent)
            .border(if (active) 1.dp else 0.dp, if (active) palette.accent.copy(alpha = .75f) else Color.Transparent, RoundedCornerShape(22.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source)
            .padding(horizontal = 17.dp, vertical = 9.dp)
    )
    LaunchedEffect(focused) { onFocused(focused) }
}

@Composable
private fun AppPeekPanel(
    provider: Provider,
    items: List<MediaItem>,
    palette: RelayPalette,
    focusRequester: FocusRequester,
    onPreviewFocused: () -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    onArtworkColor: (Color?) -> Unit
) {
    var selectedIndex by remember(provider, items) { mutableStateOf(0) }
    val lead = items.getOrNull(selectedIndex) ?: items.firstOrNull()
    Box(Modifier.fillMaxWidth().height(520.dp).background(midnight)) {
        lead?.let { item ->
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(.82f),
                onSuccess = { success ->
                    Palette.from(success.result.drawable.toBitmap().copy(Bitmap.Config.ARGB_8888, false)).generate { extracted ->
                        extracted?.vibrantSwatch?.rgb?.let { rgb -> onArtworkColor(Color(rgb or 0xFF000000.toInt())) }
                            ?: extracted?.lightVibrantSwatch?.rgb?.let { rgb -> onArtworkColor(Color(rgb or 0xFF000000.toInt())) }
                            ?: extracted?.dominantSwatch?.rgb?.let { rgb -> onArtworkColor(Color(rgb or 0xFF000000.toInt())) }
                    }
                }
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(midnight.copy(alpha = .96f), midnight.copy(alpha = .64f), provider.accent.copy(alpha = .18f), midnight.copy(alpha = .48f))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .66f)))
            )
        )
        Column(Modifier.padding(start = 78.dp, top = 164.dp, end = 78.dp, bottom = 42.dp).width(620.dp)) {
            Text("${provider.label.uppercase()} PEEK", color = provider.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                lead?.showTitle ?: lead?.title ?: provider.label,
                color = ivory,
                fontSize = 33.sp,
                lineHeight = 40.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Light,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                lead?.episodeInfo ?: lead?.let { item ->
                    if (item.progress > 0f) "Continue watching • ${(item.progress * 100).toInt()}% complete"
                    else "Ready to watch in ${provider.label}"
                } ?: "Recent picks from ${provider.label}",
                color = muted,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items.take(4).forEachIndexed { index, item ->
                    val source = remember { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    val selected = index == selectedIndex
                    LaunchedEffect(focused) {
                        if (focused) {
                            selectedIndex = index
                            onPreviewFocused()
                        }
                    }
                    Box(
                        modifier = (if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                            .size(126.dp, 82.dp)
                            .scale(if (focused) 1.08f else 1f)
                            .clip(RoundedCornerShape(9.dp))
                            .border(if (focused) 2.dp else if (selected) 1.dp else 0.dp, if (focused) palette.accent else provider.accent.copy(alpha = .55f), RoundedCornerShape(9.dp))
                            .clickable(interactionSource = source, indication = null) { onItemSelected(item) }
                            .focusable(interactionSource = source)
                    ) {
                        AsyncImage(model = item.artworkUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .84f)))))
                        Text(item.episodeInfo ?: item.title, color = ivory, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                    }
                }
            }
            lead?.let { item ->
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(360.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                    Box(Modifier.fillMaxWidth(item.progress).height(4.dp).background(provider.accent))
                }
            }
            Spacer(Modifier.height(16.dp))
            ActionButton(
                if (lead?.episodeInfo != null) "Episode details" else "Title details",
                palette.copy(accent = provider.accent),
                primary = false,
                onFocused = { if (it) onPreviewFocused() }
            ) { lead?.let(onItemSelected) }
        }
    }
}

@Composable
private fun EmbossedSettingsButton(palette: RelayPalette, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(42.dp)
            .scale(if (focused) 1.1f else 1f)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF31353D), Color(0xFF111318))))
            .border(1.dp, if (focused) palette.accent else Color(0xFF3A3D45), CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source),
        contentAlignment = Alignment.Center
    ) {
        Text("⚙", color = if (focused) palette.accent else ivory, fontSize = 23.sp)
    }
}

@Composable
private fun HeroPanel(
    hero: Hero,
    palette: RelayPalette,
    homeFocusRequester: FocusRequester,
    resumeFocusRequester: FocusRequester,
    onHeroFocused: () -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    onArtworkColor: (Color?) -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(midnight)
    ) {
        AsyncImage(
            model = hero.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(.86f),
            onSuccess = { success ->
                Palette.from(success.result.drawable.toBitmap().copy(Bitmap.Config.ARGB_8888, false)).generate { result ->
                    result?.vibrantSwatch?.rgb?.let { rgb -> onArtworkColor(Color(rgb or 0xFF000000.toInt())) }
                        ?: result?.dominantSwatch?.rgb?.let { rgb -> onArtworkColor(Color(rgb or 0xFF000000.toInt())) }
                }
            }
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(midnight.copy(alpha = .97f), palette.backdrop.copy(alpha = .68f), palette.accent.copy(alpha = .15f), midnight.copy(alpha = .42f))
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .72f)))))
        // Keep changing media titles well clear of the persistent navigation overlay.
        Column(modifier = Modifier.padding(start = 78.dp, top = 180.dp, end = 78.dp, bottom = 42.dp).width(620.dp)) {
            Text(
                hero.title,
                color = ivory,
                fontSize = 33.sp,
                lineHeight = 40.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Light,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(hero.subtitle, color = muted, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    if ((hero.item?.progress ?: 0f) > 0f) "▶  Resume" else "▶  Play",
                    palette,
                    primary = true,
                    focusRequester = resumeFocusRequester,
                    upFocusRequester = homeFocusRequester,
                    onFocused = { if (it) onHeroFocused() }
                ) { hero.item?.let { ProviderHandoff.play(context, it) } }
                ActionButton("ⓘ  Details", palette, primary = false, onFocused = { if (it) onHeroFocused() }) { hero.item?.let(onItemSelected) }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    palette: RelayPalette,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "button focus")
    LaunchedEffect(focused) { onFocused(focused) }
    Text(
        label,
        // Use an explicit opaque ink color for light primary surfaces. On some TV renderers
        // the themed backdrop color is composited away, leaving the action label invisible.
        color = if (primary) Color(0xFF111318) else ivory,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (upFocusRequester != null || downFocusRequester != null) Modifier.focusProperties {
                    if (upFocusRequester != null) up = upFocusRequester
                    if (downFocusRequester != null) down = downFocusRequester
                } else Modifier
            )
            .scale(scale).clip(RoundedCornerShape(24.dp))
            .background(if (primary) ivory else Color(0xFF171A20))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color(0xFF363A42), RoundedCornerShape(24.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source).padding(horizontal = 21.dp, vertical = 12.dp)
    )
}

@Composable
private fun MediaRail(
    title: String,
    items: List<MediaItem>,
    palette: RelayPalette,
    dateFormat: RelayDateFormat,
    onHeroChanged: (Hero) -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    posters: Boolean = false,
    showPremiereDate: Boolean = false,
    upFocusRequester: FocusRequester
) {
    if (items.isEmpty()) return
    Text(title, color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 76.dp, bottom = 10.dp))
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 76.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        items(items.size) { index ->
            MediaCard(
                item = items[index],
                palette = palette,
                poster = posters,
                dateFormat = dateFormat,
                showEpisodeInfo = title == "Continue Watching" || title == "Coming Up",
                showPremiereDate = showPremiereDate,
                upFocusRequester = upFocusRequester,
                onClick = { onItemSelected(items[index]) }
            ) { extractedAccent ->
                val item = items[index]
                onHeroChanged(Hero(
                    item.title,
                    "Continue the story, wherever you left off.",
                    paletteFor(item, extractedAccent),
                    item.artworkUrl,
                    item
                ))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MediaCard(
    item: MediaItem,
    palette: RelayPalette,
    poster: Boolean,
    dateFormat: RelayDateFormat = RelayDateFormat.LOCAL,
    showEpisodeInfo: Boolean = false,
    showPremiereDate: Boolean = false,
    upFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onFocused: (Color?) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var extractedAccent by remember(item.artworkUrl) { mutableStateOf<Color?>(null) }
    val scale by animateFloatAsState(if (focused) 1.055f else 1f, label = "card scale")
    val shape = RoundedCornerShape(11.dp)
    val width = if (poster) 140.dp else 310.dp
    Box(
        modifier = Modifier.requiredWidth(width).aspectRatio(if (poster) .69f else 1.78f)
            .scale(scale).clip(shape)
            .background(Brush.verticalGradient(item.colors))
            .bringIntoViewRequester(bringIntoViewRequester)
            .border(if (focused) 3.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .12f), shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier)
            .focusable(interactionSource = source)
            .padding(13.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.artworkUrl)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { success ->
                Palette.from(success.result.drawable.toBitmap().copy(Bitmap.Config.ARGB_8888, false)).generate { paletteResult ->
                    paletteResult?.dominantSwatch?.rgb?.let { rgb ->
                        extractedAccent = Color(rgb or 0xFF000000.toInt())
                    }
                }
            }
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .72f)))
            )
        )
        if (!poster) {
            Text(item.provider.label.uppercase(), color = ivory, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(8.dp)).background(item.provider.accent).padding(horizontal = 7.dp, vertical = 4.dp))
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f))) {
                Box(modifier = Modifier.fillMaxWidth(item.progress).height(4.dp).background(item.provider.accent))
            }
        }
        if (showEpisodeInfo && !poster) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp)
            ) {
                Text(
                    item.showTitle ?: item.title,
                    color = ivory,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                listOfNotNull(
                    item.episodeInfo,
                    if (showPremiereDate) formatRelayDate(item.releaseInfo, dateFormat)?.let { "Premieres $it" } else null
                ).joinToString("  •  ").takeIf { it.isNotBlank() }?.let { episode ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        episode,
                        color = ivory.copy(alpha = .82f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Text(item.title, color = ivory, fontSize = if (poster) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).alpha(if (poster) 1f else .96f))
        }
    }
    LaunchedEffect(focused, extractedAccent) {
        if (focused) {
            bringIntoViewRequester.bringIntoView()
            onFocused(extractedAccent)
        }
    }
}

@Composable
private fun AppsScreen(palette: RelayPalette, onBackHome: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { InstalledApps.discover(context).filterNot { ProviderHandoff.isProviderPackage(it.packageName) } }
    val firstAppFocusRequester = remember { FocusRequester() }
    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) firstAppFocusRequester.requestFocus()
    }
    Column(Modifier.fillMaxSize().padding(64.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", color = ivory, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            ActionButton("Back to Home", palette, primary = false, onClick = onBackHome)
        }
        Spacer(Modifier.height(34.dp))
        Text("All apps", color = ivory, fontSize = 20.sp)
        Spacer(Modifier.height(14.dp))
        Text("Launch every installed TV app from Relay. Connected media providers stay in the adaptive Home navigation, keeping this grid uncluttered.", color = muted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        if (apps.isEmpty()) {
            Text("No launchable apps were found yet.", color = muted, fontSize = 17.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(apps.size) { index ->
                    val app = apps[index]
                    AppTile(
                        label = app.label,
                        palette = palette,
                        focusRequester = if (index == 0) firstAppFocusRequester else null
                    ) { InstalledApps.launch(context, app) }
                }
            }
        }
    }
}

@Composable
private fun CalendarScreen(
    palette: RelayPalette,
    providers: Set<Provider>,
    nuvioItems: List<MediaItem>,
    upcomingEpisodes: List<TmdbCalendarEntry>,
    dateFormat: RelayDateFormat,
    onBackHome: () -> Unit,
    onItemSelected: (MediaItem) -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var weekView by remember { mutableStateOf(false) }
    var weekStart by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var tmdbEntries by remember(providers, nuvioItems) { mutableStateOf(emptyList<TmdbCalendarEntry>()) }
    var scheduleLoading by remember(providers, nuvioItems) { mutableStateOf(false) }
    val providerItems = remember(providers, nuvioItems) { nuvioItems.filter { it.provider in providers } }
    LaunchedEffect(providerItems) {
        scheduleLoading = providerItems.isNotEmpty()
        tmdbEntries = TmdbApi.calendarEntries(providerItems)
        scheduleLoading = false
    }
    val nativeEntries = remember(providerItems) {
        nuvioItems.filter { it.provider in providers }.mapNotNull { item ->
            item.releaseInfo?.take(10)?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }?.let { date -> TmdbCalendarEntry(date, item) }
        }
    }
    val entries = remember(nativeEntries, tmdbEntries, upcomingEpisodes) {
        (nativeEntries + tmdbEntries + upcomingEpisodes).distinctBy { "${it.date}:${it.item.providerContentId ?: it.item.title}:${it.item.episodeInfo}" }
    }
    val monthDays = remember(month) {
        val leading = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    }
    val visibleDays = if (weekView) (0..6).map { weekStart.plusDays(it.toLong()) } else monthDays
    val visibleEntries = if (weekView) entries.filter { it.date in weekStart..weekStart.plusDays(6) } else entries.filter { YearMonth.from(it.date) == month }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(horizontal = 76.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Calendar", color = ivory, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(16.dp))
            Text("Premieres & episodes from your connected libraries", color = muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            ActionButton("‹  Back", palette, primary = false, onClick = onBackHome)
        }
        Spacer(Modifier.height(25.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton("‹", palette, primary = false) {
                if (weekView) weekStart = weekStart.minusWeeks(1) else month = month.minusMonths(1)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                if (weekView) "${weekStart.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${weekStart.dayOfMonth} – ${weekStart.plusDays(6).month.name.lowercase().replaceFirstChar { it.uppercase() }} ${weekStart.plusDays(6).dayOfMonth}, ${weekStart.year}"
                else "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                color = ivory, fontSize = 25.sp, fontWeight = FontWeight.Light
            )
            Spacer(Modifier.width(14.dp))
            ActionButton("›", palette, primary = false) {
                if (weekView) weekStart = weekStart.plusWeeks(1) else month = month.plusMonths(1)
            }
            Spacer(Modifier.width(24.dp))
            Text(if (scheduleLoading) "Loading TMDB schedule…" else if (entries.isEmpty()) "No dated provider events yet" else "${visibleEntries.size} event${if (visibleEntries.size == 1) "" else "s"} in view", color = muted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            ActionButton("Month", palette, primary = !weekView) { weekView = false }
            Spacer(Modifier.width(9.dp))
            ActionButton("Week", palette, primary = weekView) {
                weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
                weekView = true
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(day, color = muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(if (weekView) 155.dp else 355.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleDays.size) { index ->
                val date = visibleDays[index]
                val dayEvents = date?.let { selected -> entries.filter { it.date == selected } }.orEmpty()
                val event = dayEvents.firstOrNull()
                Box(
                    modifier = Modifier.aspectRatio(1.15f).clip(RoundedCornerShape(10.dp))
                        .background(if (event != null) palette.accent.copy(alpha = .24f) else Color.White.copy(alpha = .045f))
                        .border(if (event != null) 1.dp else 0.dp, palette.accent.copy(alpha = .7f), RoundedCornerShape(10.dp))
                        .then(if (dayEvents.isNotEmpty()) Modifier.clickable { date?.let { selectedDay = it } }.focusable() else Modifier)
                        .padding(9.dp)
                ) {
                    date?.let { Text(if (weekView) "${it.dayOfWeek.name.take(3).lowercase().replaceFirstChar { char -> char.uppercase() }} ${it.dayOfMonth}" else it.dayOfMonth.toString(), color = if (event != null) ivory else muted, fontSize = 14.sp, fontWeight = if (event != null) FontWeight.Bold else FontWeight.Normal) }
                    event?.let {
                        Text(if (dayEvents.size > 1) "${dayEvents.size} new episodes" else it.item.episodeInfo ?: it.item.title, color = ivory, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(if (weekView) "This week" else "This month", color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        if (visibleEntries.isEmpty()) {
            Text(if (scheduleLoading) "Looking up exact premiere and episode dates…" else "No scheduled events for this month. Nuvio library titles are supplemented with exact TMDB dates; Stremio and SmartTube will join as their schedule data becomes available.", color = muted, fontSize = 15.sp, lineHeight = 22.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleEntries.size) { index ->
                    val event = visibleEntries[index]
                    ActionButton("${formatRelayDate(event.date, dateFormat)}  ${event.item.showTitle ?: event.item.title}", palette, primary = false) { onItemSelected(event.item) }
                }
            }
        }
    }
    selectedDay?.let { date ->
        CalendarDayOverlay(
            palette = palette,
            date = date,
            dateFormat = dateFormat,
            entries = entries.filter { it.date == date },
            onItemSelected = onItemSelected,
            onDismiss = { selectedDay = null }
        )
    }
}

@Composable
private fun CalendarDayOverlay(
    palette: RelayPalette,
    date: LocalDate,
    dateFormat: RelayDateFormat,
    entries: List<TmdbCalendarEntry>,
    onItemSelected: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(midnight.copy(alpha = .84f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(720.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121C)).border(1.dp, palette.accent.copy(alpha = .65f), RoundedCornerShape(22.dp)).padding(30.dp)) {
            Text(formatRelayDate(date, dateFormat), color = ivory, fontSize = 27.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            Text(if (entries.size == 1) "New episode or premiere" else "${entries.size} shows to watch", color = muted, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            entries.forEach { entry ->
                ActionButton("${entry.item.showTitle ?: entry.item.title}  ·  ${entry.item.episodeInfo ?: "Premiere"}", palette, primary = false) {
                    onItemSelected(entry.item)
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            ActionButton("Close", palette, primary = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun SearchScreen(
    palette: RelayPalette,
    providers: Set<Provider>,
    onBackHome: () -> Unit,
    onItemSelected: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<MediaItem>()) }
    var loading by remember { mutableStateOf(false) }
    val searchProvider = providers.firstOrNull { it == Provider.STREMIO } ?: providers.firstOrNull() ?: Provider.NUVIO
    LaunchedEffect(query, searchProvider) {
        if (query.trim().length < 2) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(350)
        results = TmdbApi.search(query.trim(), searchProvider)
        loading = false
    }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(horizontal = 76.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Search", color = ivory, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(18.dp))
            Text("Across your connected media", color = muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            ActionButton("‹  Back", palette, primary = false, onClick = onBackHome)
        }
        Spacer(Modifier.height(27.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search movies, series, and apps") },
            textStyle = androidx.compose.ui.text.TextStyle(color = ivory, fontSize = 20.sp),
            modifier = Modifier.fillMaxWidth().height(70.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = Color(0xFF3C4049),
                focusedLabelColor = palette.accent,
                unfocusedLabelColor = muted,
                cursorColor = palette.accent
            )
        )
        Spacer(Modifier.height(34.dp))
        Text(if (query.isBlank()) "Start typing to search TMDB" else "Results for “$query”", color = ivory, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(13.dp))
        if (loading) {
            Text("Searching TMDB…", color = muted, fontSize = 17.sp)
        } else if (query.trim().length < 2) {
            Text("Enter at least two characters to find movies and series with artwork and descriptions.", color = muted, fontSize = 17.sp)
        } else if (results.isEmpty()) {
            Text("No TMDB matches found. Try a more specific title.", color = muted, fontSize = 17.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                items(results.size) { index ->
                    MediaCard(results[index], palette, poster = true, onClick = { onItemSelected(results[index]) }) { }
                }
            }
        }
        if (Provider.STREMIO in providers) {
            Spacer(Modifier.height(22.dp))
            ActionButton(
                if (query.isBlank()) "Open Stremio search" else "Search “$query” in Stremio",
                palette,
                primary = false
            ) { ProviderHandoff.searchStremio(context, query) }
        }
        Spacer(Modifier.height(38.dp))
        Text("Apps", color = ivory, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            InstalledApps.discover(context)
                .filterNot { ProviderHandoff.isProviderPackage(it.packageName) }
                .take(6)
                .forEach { app -> AppTile(app.label, palette) { InstalledApps.launch(context, app) } }
        }
    }
}

@Composable
private fun AppTile(
    label: String,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit = {}
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(132.dp, 86.dp).clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF171A20))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color(0xFF333740), RoundedCornerShape(13.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source),
        contentAlignment = Alignment.Center
    ) { Text(label, color = ivory, fontSize = 15.sp) }
}

private enum class SettingsPage(val label: String) {
    DISPLAY("Display"), PROVIDERS("Providers"), INTEGRATIONS("Integrations"), LAUNCHER("Launcher")
}

@Composable
private fun SettingsScreen(
    palette: RelayPalette,
    providers: Set<Provider>,
    onBackHome: () -> Unit,
    onProviderToggle: (Provider) -> Unit,
    onRequestHome: () -> Unit,
    onRequestSmartTubeAccess: () -> Unit,
    nuvioConnected: Boolean,
    nuvioSyncing: Boolean,
    nuvioItemCount: Int,
    nuvioSyncError: String?,
    onRefreshNuvio: () -> Unit,
    dateFormat: RelayDateFormat,
    onDateFormatChanged: (RelayDateFormat) -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.DISPLAY) }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(64.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = ivory, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            ActionButton("Back to Home", palette, primary = false, onClick = onBackHome)
        }
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingsPage.entries.forEach { destination ->
                ActionButton(destination.label, palette, primary = page == destination) { page = destination }
            }
        }
        Spacer(Modifier.height(42.dp))
        when (page) {
            SettingsPage.DISPLAY -> {
                Text("Display", color = ivory, fontSize = 22.sp)
                Spacer(Modifier.height(12.dp))
                Text("Date format", color = ivory, fontSize = 18.sp)
                Spacer(Modifier.height(7.dp))
                Text("Used for Coming Up, media details, and Calendar.", color = muted, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RelayDateFormat.entries.forEach { format ->
                        ActionButton(format.label, palette, primary = dateFormat == format) { onDateFormatChanged(format) }
                    }
                }
            }
            SettingsPage.PROVIDERS -> {
                Text("Connected providers", color = ivory, fontSize = 22.sp)
                Spacer(Modifier.height(10.dp))
                Text("Choose which media services appear in Relay's Home navigation.", color = muted, fontSize = 16.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Provider.values().forEach { provider ->
                        val connected = provider in providers
                        ActionButton("${provider.label}  ${if (connected) "Shown" else "Add to Home"}", palette, primary = connected) { onProviderToggle(provider) }
                    }
                }
                Spacer(Modifier.height(30.dp))
                Text("Nuvio library sync", color = ivory, fontSize = 18.sp)
                Spacer(Modifier.height(7.dp))
                val status = when {
                    !nuvioConnected -> "Not connected"
                    nuvioSyncing -> "Syncing your active profile…"
                    nuvioSyncError != null -> nuvioSyncError
                    else -> "$nuvioItemCount Continue Watching item${if (nuvioItemCount == 1) "" else "s"} synced"
                }
                Text(status, color = if (nuvioSyncError != null) Provider.SMARTTUBE.accent else muted, fontSize = 16.sp)
                if (nuvioConnected) {
                    Spacer(Modifier.height(14.dp))
                    ActionButton(if (nuvioSyncing) "Refreshing Nuvio…" else "Refresh Nuvio", palette.copy(accent = Provider.NUVIO.accent), primary = false, onClick = onRefreshNuvio)
                }
            }
            SettingsPage.INTEGRATIONS -> {
                Text("SmartTube integration", color = ivory, fontSize = 22.sp)
                Spacer(Modifier.height(10.dp))
                Text("Allow Relay to read SmartTube's active media session for Now Playing and future Continue Watching. Existing private history remains private.", color = muted, fontSize = 16.sp, lineHeight = 23.sp)
                Spacer(Modifier.height(18.dp))
                ActionButton("Enable SmartTube Now Playing", palette.copy(accent = Provider.SMARTTUBE.accent), primary = false, onClick = onRequestSmartTubeAccess)
            }
            SettingsPage.LAUNCHER -> {
                Text("Launcher", color = ivory, fontSize = 22.sp)
                Spacer(Modifier.height(10.dp))
                Text("Set Relay as your Home app to open it when you press the remote Home button.", color = muted, fontSize = 16.sp)
                Spacer(Modifier.height(18.dp))
                ActionButton("Use Relay as Home", palette, primary = true, onClick = onRequestHome)
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String, palette: RelayPalette, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.Center) {
        Text(title, color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(description, color = muted, fontSize = 18.sp)
        Spacer(Modifier.height(30.dp))
        ActionButton("Back to Home", palette, primary = true, onClick = onBack)
    }
}

@Composable
private fun ProviderHubScreen(
    provider: Provider,
    palette: RelayPalette,
    onBack: () -> Unit,
    onConnectNuvio: () -> Unit,
    nuvioConnected: Boolean,
    nuvioSyncing: Boolean,
    nuvioItemCount: Int,
    nuvioSyncError: String?,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    onNuvioProfileSelected: (Int) -> Unit,
    onRefreshNuvio: () -> Unit,
    onDisconnectNuvio: () -> Unit
) {
    val context = LocalContext.current
    val primaryActionFocusRequester = remember { FocusRequester() }
    LaunchedEffect(provider) { primaryActionFocusRequester.requestFocus() }
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.Center) {
        Text(provider.label, color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(
            when (provider) {
                Provider.STREMIO -> "Stremio handoff is ready. Relay can open Stremio's board and search using its Android TV deep links. Catalog sync comes next."
                Provider.SMARTTUBE -> "SmartTube is ready as a focused video destination. Relay launches the installed stable or beta app directly, while SmartTube keeps its own subscriptions and playback experience."
                Provider.NUVIO -> if (nuvioConnected) {
                    when {
                        nuvioSyncing -> "Nuvio is connected. Syncing your profile and Continue Watching…"
                        nuvioSyncError != null -> nuvioSyncError
                        else -> "Nuvio is connected. $nuvioItemCount Continue Watching items are now available in Relay."
                    }
                } else {
                    "Nuvio is installed. Connect your Nuvio account to bring its profile, library, and Continue Watching into Relay."
                }
            },
            color = muted,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(30.dp))
        if (provider == Provider.STREMIO) {
            ActionButton("Open Stremio", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester) {
                ProviderHandoff.openStremioBoard(context)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (provider == Provider.SMARTTUBE) {
            ActionButton("Open SmartTube", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester) {
                ProviderHandoff.openSmartTube(context)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (provider == Provider.NUVIO) {
            if (nuvioProfiles.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Relay profile", color = ivory, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    nuvioProfiles.forEach { profile ->
                        ActionButton(profile.name, palette, primary = profile.index == activeNuvioProfile) { onNuvioProfileSelected(profile.index) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (!nuvioConnected) {
                ActionButton("Connect Nuvio data", palette.copy(accent = provider.accent), primary = true, onClick = onConnectNuvio)
                Spacer(Modifier.height(12.dp))
            } else {
                ActionButton(if (nuvioSyncing) "Refreshing Nuvio…" else "Refresh Nuvio", palette.copy(accent = provider.accent), primary = false, onClick = onRefreshNuvio)
                Spacer(Modifier.height(12.dp))
                ActionButton("Disconnect Nuvio", palette, primary = false, onClick = onDisconnectNuvio)
                Spacer(Modifier.height(12.dp))
            }
            ActionButton("Open Nuvio", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester) {
                ProviderHandoff.openNuvio(context)
            }
            Spacer(Modifier.height(12.dp))
        }
        ActionButton("Back to Home", palette.copy(accent = provider.accent), primary = true, onClick = onBack)
    }
}

@Composable
private fun NuvioConnectScreen(palette: RelayPalette, connected: Boolean, onConnected: (NuvioSession) -> Unit, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var connectRequested by remember { mutableStateOf(false) }
    LaunchedEffect(connectRequested) {
        if (connectRequested) {
            NuvioApi.signIn(email, password)
                .onSuccess { session ->
                    password = ""
                    onConnected(session)
                }
                .onFailure { error ->
                    status = error.message ?: "Nuvio sign-in failed."
                    working = false
                    connectRequested = false
                }
        }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(76.dp), verticalArrangement = Arrangement.Center) {
        Text("Connect Nuvio", color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(if (connected) "Nuvio is connected for this Relay session." else "Sign in directly with Nuvio to bring your library and Continue Watching into Relay.", color = muted, fontSize = 18.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Nuvio email") }, singleLine = true, modifier = Modifier.width(520.dp), textStyle = androidx.compose.ui.text.TextStyle(color = ivory))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Nuvio password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(520.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = ivory)
        )
        Spacer(Modifier.height(18.dp))
        ActionButton(if (working) "Connecting…" else "Connect securely", palette, primary = true) {
            if (!working && email.isNotBlank() && password.isNotBlank()) {
                working = true
                status = null
                connectRequested = true
            }
        }
        status?.let { Text(it, color = muted, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(12.dp))
        ActionButton("Back", palette, primary = false, onClick = onBack)
    }
}

@Composable
private fun DetailsScreen(item: MediaItem, palette: RelayPalette, dateFormat: RelayDateFormat, onBackHome: () -> Unit) {
    val context = LocalContext.current
    val backFocusRequester = remember { FocusRequester() }
    val resumeFocusRequester = remember { FocusRequester() }
    val seasonFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }
    val episodeMatch = remember(item.episodeInfo) { Regex("S(\\d+)\\s*•\\s*E(\\d+)").find(item.episodeInfo.orEmpty()) }
    val seasonEpisode = episodeMatch?.value
    val originalSeason = episodeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    val originalEpisode = episodeMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
    var expandedEpisodeSelector by remember(item) { mutableStateOf<String?>(null) }
    var selectedSeason by remember(item) { mutableStateOf(episodeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1) }
    var selectedEpisode by remember(item) { mutableStateOf(episodeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1) }
    var pickerData by remember(item, selectedSeason) { mutableStateOf<TvSeason?>(null) }
    LaunchedEffect(item, selectedSeason) {
        if (seasonEpisode != null) {
            pickerData = TmdbApi.seasonEpisodes(item, selectedSeason).getOrNull()
            pickerData?.episodes?.firstOrNull { it.number == selectedEpisode }
                ?: pickerData?.episodes?.firstOrNull()?.let { selectedEpisode = it.number }
        }
    }
    val selectedPlaybackItem = if (seasonEpisode != null && (selectedSeason != originalSeason || selectedEpisode != originalEpisode)) {
        item.copy(episodeInfo = "S${selectedSeason.toString().padStart(2, '0')} • E${selectedEpisode.toString().padStart(2, '0')}", progress = 0f)
    } else item
    BackHandler(onBack = onBackHome)
    LaunchedEffect(Unit) { resumeFocusRequester.requestFocus() }
    Box(
        modifier = Modifier.fillMaxSize().background(midnight)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(item.artworkUrl).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(.44f)
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(midnight, midnight.copy(alpha = .8f), Color.Transparent))
            )
        )
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 78.dp, top = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                "‹  Back", palette, primary = false,
                focusRequester = backFocusRequester,
                downFocusRequester = if (seasonEpisode != null) seasonFocusRequester else resumeFocusRequester,
                onClick = onBackHome
            )
            Spacer(Modifier.width(18.dp))
            Text("RELAY HOME", color = ivory.copy(alpha = .75f), fontSize = 15.sp, letterSpacing = 3.sp)
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(start = 78.dp, end = 78.dp, top = 145.dp, bottom = 105.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = Modifier.width(760.dp).padding(bottom = 80.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailPill(item.provider.label.uppercase(), item.provider.accent)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    item.title.uppercase(),
                    color = ivory,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    listOfNotNull(item.episodeInfo, formatRelayDate(item.releaseInfo, dateFormat), item.rating?.let { "★ ${"%.1f".format(it)}" }, item.genres, "${(item.progress * 100).toInt()}% complete").joinToString("  •  "),
                    color = ivory.copy(alpha = .82f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    item.description ?: "Details are available in ${item.provider.label}.",
                    color = muted,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
                seasonEpisode?.let {
                    Spacer(Modifier.height(18.dp))
                    Text("Season & episode", color = ivory.copy(alpha = .9f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton(
                            "Season ${selectedSeason.toString().padStart(2, '0')}  ▾", palette,
                            primary = true, focusRequester = seasonFocusRequester,
                            upFocusRequester = backFocusRequester, downFocusRequester = resumeFocusRequester
                        ) {
                            expandedEpisodeSelector = if (expandedEpisodeSelector == "season") null else "season"
                        }
                        ActionButton(
                            "Episode ${selectedEpisode.toString().padStart(2, '0')}  ▾", palette,
                            primary = true, focusRequester = episodeFocusRequester,
                            upFocusRequester = backFocusRequester, downFocusRequester = resumeFocusRequester
                        ) {
                            expandedEpisodeSelector = if (expandedEpisodeSelector == "episode") null else "episode"
                        }
                    }
                    if (expandedEpisodeSelector != null) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            Modifier.width(650.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE815121C)).border(1.dp, palette.accent.copy(alpha = .6f), RoundedCornerShape(14.dp)).padding(13.dp)
                        ) {
                            Text(if (expandedEpisodeSelector == "season") "Choose season" else "Choose episode", color = muted, fontSize = 13.sp)
                            Spacer(Modifier.height(9.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                if (expandedEpisodeSelector == "season") {
                                    // TMDB gives the exact season list when it can resolve the show.
                                    // Keep the picker useful for Nuvio titles that lack a confident match.
                                    items(pickerData?.seasons?.takeIf { it.isNotEmpty() } ?: (1..20).toList()) { value ->
                                        ActionButton("Season $value", palette, primary = value == selectedSeason, upFocusRequester = seasonFocusRequester) {
                                            selectedSeason = value
                                            expandedEpisodeSelector = null
                                        }
                                    }
                                } else {
                                    items(pickerData?.episodes?.takeIf { it.isNotEmpty() } ?: (1..50).map { TvEpisode(it, "Episode $it") }) { value ->
                                        ActionButton("E${value.number.toString().padStart(2, '0')}  ${value.title}", palette, primary = value.number == selectedEpisode, upFocusRequester = episodeFocusRequester) {
                                            selectedEpisode = value.number
                                            expandedEpisodeSelector = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(
                        "▶  ${if (selectedPlaybackItem.progress > 0f) "Resume" else "Play"}",
                        palette,
                        primary = true,
                        focusRequester = resumeFocusRequester,
                        upFocusRequester = if (seasonEpisode != null) seasonFocusRequester else backFocusRequester
                    ) { ProviderHandoff.play(context, selectedPlaybackItem) }
                }
                Spacer(Modifier.height(25.dp))
                if (item.progress > 0f) {
                    Text("Continue watching", color = ivory.copy(alpha = .9f), fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(400.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                        Box(Modifier.fillMaxWidth(item.progress).height(5.dp).background(item.provider.accent))
                    }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 78.dp, bottom = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Available in ${item.provider.label}", color = ivory, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Text("Playback opens in your connected provider", color = muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DetailPill(label: String, color: Color) {
    Text(
        label,
        color = ivory,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

private fun sampleContinueWatching(providers: Set<Provider>): List<MediaItem> = listOf(
    MediaItem("Silent Orbit", Provider.STREMIO, .58f, listOf(Color(0xFF152746), Color(0xFF050609)), "https://images.unsplash.com/photo-1446776653964-20c1d3a81b06?auto=format&fit=crop&w=960&q=85"),
    MediaItem("Afterlight", Provider.NUVIO, .39f, listOf(Color(0xFF3B1E51), Color(0xFF09070E)), "https://images.unsplash.com/photo-1519608487953-e999c86e7450?auto=format&fit=crop&w=960&q=85"),
    MediaItem("The Long Way Home", Provider.STREMIO, .24f, listOf(Color(0xFF4C302A), Color(0xFF0C0808)), "https://images.unsplash.com/photo-1533929736458-ca588d08c8be?auto=format&fit=crop&w=960&q=85"),
    MediaItem("Bright Hollow", Provider.NUVIO, .72f, listOf(Color(0xFF0F414E), Color(0xFF050B0D)), "https://images.unsplash.com/photo-1499346030926-9a72daac6c63?auto=format&fit=crop&w=960&q=85")
).filter { it.provider in providers }

private fun sampleRecommended(providers: Set<Provider>): List<MediaItem> {
    if (providers.isEmpty()) return emptyList()
    val source = providers
    return listOf(
        "Echoes of Mare", "Luminara", "Hollow Tide", "Velora", "Noctis Protocol", "Celestial Drift", "Last Beacon", "Silent Orbit"
    ).mapIndexed { index, title ->
        val provider = source.elementAt(index % source.size)
        MediaItem(
            title,
            provider,
            0f,
            listOf(Color(0xFF182743), Color(0xFF090A11)),
            "https://images.unsplash.com/photo-${listOf("1451187580459-43490279c0fa", "1462331940025-496dfbfc7564", "1470770841072-f978cf4d019e", "1500530855697-b586d89ba3ee")[index % 4]}?auto=format&fit=crop&w=460&q=85",
            contentType = "tv"
        )
    }
}

private fun paletteFor(item: MediaItem, extractedAccent: Color? = null): RelayPalette = when (item.provider) {
    Provider.STREMIO -> orbitalPalette.copy(accent = extractedAccent ?: item.provider.accent)
    Provider.NUVIO -> violetPalette.copy(accent = extractedAccent ?: item.provider.accent)
    Provider.SMARTTUBE -> orbitalPalette.copy(accent = extractedAccent ?: item.provider.accent)
}

package com.prismora.player.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prismora.player.PlayerViewModel
import com.prismora.player.R
import com.prismora.player.model.AudioOutput
import com.prismora.player.model.AudioOutputKind
import com.prismora.player.model.AudioRoutingMode
import com.prismora.player.model.DspSettings
import com.prismora.player.model.EqBand
import com.prismora.player.model.EqFilterType
import com.prismora.player.model.LibraryScanState
import com.prismora.player.model.LyricLine
import com.prismora.player.model.OutputStatus
import com.prismora.player.model.PlaybackPhase
import com.prismora.player.model.RepeatMode
import com.prismora.player.model.PlaylistOrganizationMode
import com.prismora.player.model.Track
import com.prismora.player.model.VisualizerStyle
import com.prismora.player.model.WrappedArtistStat
import com.prismora.player.model.WrappedTrackStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private data class GlassThemeColors(
    val ink: Color,
    val deep: Color,
    val glass: Color,
    val accent: Color,
    val secondary: Color,
    val tertiary: Color,
    val white: Color = Color(0xFFF4FBFF)
)

private enum class ThemeFamily(val label: String) {
    PRISMORA("PRISMORA"),
    VOCAL_SYNTH("VOCALOID"),
    PERSONA("PERSONA"),
    COVER_ART("COVER ART"),
    RETRO("RETRO")
}

private enum class BackdropStyle {
    GLOW, OLED, STRIPES, CLOCK, HALFTONE, CUTOUT, CLOUD, COVER_WAVE, COVER_BACKGROUND,
    NEON_GRID, CRT, Y2K, VAPORWAVE, CASSETTE, MINIDISC, WALKMAN,
    PAPER, SAKURA, BAUHAUS, BRUTALIST,
    DISCMAN, IOS_LIGHT, MEDIA_PLAYER, PRISMORA_OS, IPOD_LIGHT
}

private enum class ThemePreset(
    val id: String,
    val displayName: String,
    val shortMark: String,
    val family: ThemeFamily,
    val colors: GlassThemeColors,
    val backdrop: BackdropStyle
) {
    PRISMORA_GLASS(
        "prismora_glass", "Prismora Glass", "PRISMORA", ThemeFamily.PRISMORA,
        GlassThemeColors(Color(0xFF050713), Color(0xFF0B1630), Color(0xC0182948), Color(0xFF55F4FF), Color(0xFF7E92FF), Color(0xFFFF69CD)),
        BackdropStyle.GLOW
    ),
    PRISMORA_OLED(
        "prismora_oled", "Prismora OLED", "PRISMORA", ThemeFamily.PRISMORA,
        GlassThemeColors(Color.Black, Color(0xFF020308), Color(0xD9081019), Color(0xFF52F5FF), Color(0xFF6D78FF), Color(0xFFFF4EB8)),
        BackdropStyle.OLED
    ),
    MIKU(
        "miku", "Hatsune Miku", "MIKU", ThemeFamily.VOCAL_SYNTH,
        GlassThemeColors(Color(0xFF050712), Color(0xFF0B1022), Color(0xB8182038), Color(0xFF4AF6E8), Color(0xFF72A7FF), Color(0xFFFF60C8)),
        BackdropStyle.GLOW
    ),
    TETO(
        "teto", "Kasane Teto", "TETO", ThemeFamily.VOCAL_SYNTH,
        GlassThemeColors(Color(0xFF100609), Color(0xFF261018), Color(0xB8241018), Color(0xFFFF5B73), Color(0xFFFFA4B5), Color(0xFFFF2F80)),
        BackdropStyle.STRIPES
    ),
    NERU(
        "neru", "Akita Neru", "NERU", ThemeFamily.VOCAL_SYNTH,
        GlassThemeColors(Color(0xFF0C0A04), Color(0xFF252008), Color(0xB8201B08), Color(0xFFFFD94A), Color(0xFFFF9D3F), Color(0xFFFFF1A6)),
        BackdropStyle.STRIPES
    ),
    YI_XI(
        "yi_xi", "Yi Xi", "YI XI", ThemeFamily.VOCAL_SYNTH,
        GlassThemeColors(Color(0xFF0D0610), Color(0xFF25102B), Color(0xB823102C), Color(0xFFD96BFF), Color(0xFF73E6FF), Color(0xFFFF689E)),
        BackdropStyle.CUTOUT
    ),
    KAITO(
        "kaito", "KAITO", "KAITO", ThemeFamily.VOCAL_SYNTH,
        GlassThemeColors(Color(0xFF040814), Color(0xFF071B3A), Color(0xB80A1B35), Color(0xFF4AA8FF), Color(0xFF8AD8FF), Color(0xFF596EFF)),
        BackdropStyle.GLOW
    ),
    MEIKO(
        "meiko", "MEIKO", "MEIKO", ThemeFamily.VOCAL_SYNTH,
        GlassThemeColors(Color(0xFF110507), Color(0xFF2C0A10), Color(0xB82A0C12), Color(0xFFFF5654), Color(0xFFFFA080), Color(0xFFD8245A)),
        BackdropStyle.GLOW
    ),
    INABAKUMORI(
        "inabakumori", "inabakumori", "稲葉曇", ThemeFamily.VOCAL_SYNTH,
        GlassThemeColors(Color(0xFF090A0C), Color(0xFF181B20), Color(0xB81C2025), Color(0xFFE7ECF0), Color(0xFF90A8B9), Color(0xFF667482)),
        BackdropStyle.CLOUD
    ),
    INSOMNIA(
        "insomnia", "Insomnia", "INSOMNIA", ThemeFamily.VOCAL_SYNTH,
        GlassThemeColors(Color(0xFF04040B), Color(0xFF101027), Color(0xC0141430), Color(0xFF9C8CFF), Color(0xFF536BFF), Color(0xFFFF78C8)),
        BackdropStyle.CLOUD
    ),
    COVER_WAVE(
        "cover_wave", "OLED Wave", "WAVE", ThemeFamily.COVER_ART,
        GlassThemeColors(Color.Black, Color(0xFF030303), Color(0xD90A0A0D), Color(0xFF4ABEFF), Color(0xFF9EDCFF), Color(0xFF6B76FF)),
        BackdropStyle.COVER_WAVE
    ),
    COVER_BACKGROUND(
        "cover_background", "Cover Backdrop", "COVER", ThemeFamily.COVER_ART,
        GlassThemeColors(Color.Black, Color(0xFF050505), Color(0xD90A0A0D), Color(0xFF4ABEFF), Color(0xFF9EDCFF), Color(0xFF6B76FF)),
        BackdropStyle.COVER_BACKGROUND
    ),
    P3R(
        "p3r", "Persona 3 Reload", "P3R", ThemeFamily.PERSONA,
        GlassThemeColors(
            Color(0xFF01081D),
            Color(0xFF063FC6),
            Color(0xD90A3C9F),
            Color(0xFF28DCFF),
            Color(0xFFFFFFFF),
            Color(0xFFFF2D4F),
            Color(0xFFF8FCFF)
        ),
        BackdropStyle.CLOCK
    ),
    P4G(
        "p4g", "Persona 4 Golden", "P4G", ThemeFamily.PERSONA,
        GlassThemeColors(Color(0xFF0C0B02), Color(0xFF2A2400), Color(0xB8272205), Color(0xFFFFD600), Color(0xFFFFF18A), Color(0xFFFFFFFF)),
        BackdropStyle.HALFTONE
    ),
    P5R(
        "p5r", "Persona 5 Royal", "P5R", ThemeFamily.PERSONA,
        GlassThemeColors(Color(0xFF080808), Color(0xFF210306), Color(0xB8210508), Color(0xFFFF2747), Color(0xFFFFFFFF), Color(0xFF9E1025)),
        BackdropStyle.CUTOUT
    ),
    CYBERPUNK(
        "cyberpunk", "Cyberpunk", "CYBER", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFF050500), Color(0xFF171500), Color(0xD2262200), Color(0xFFFFE600), Color(0xFF00F5FF), Color(0xFFFF2F92)),
        BackdropStyle.NEON_GRID
    ),
    CRT(
        "crt", "CRT Terminal", "CRT", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFF010503), Color(0xFF031009), Color(0xD207160D), Color(0xFF58FF84), Color(0xFFB3FFC5), Color(0xFF23A84D)),
        BackdropStyle.CRT
    ),
    Y2K(
        "y2k", "Y2K", "Y2K", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFF080713), Color(0xFF17172B), Color(0xCC22223A), Color(0xFFB7F3FF), Color(0xFFC8B8FF), Color(0xFFFFA8E6)),
        BackdropStyle.Y2K
    ),
    VAPORWAVE(
        "vaporwave", "Vaporwave", "VAPOR", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFF09031A), Color(0xFF21094A), Color(0xCC29105A), Color(0xFFFF71CE), Color(0xFF01CDFE), Color(0xFFB967FF)),
        BackdropStyle.VAPORWAVE
    ),
    CASSETTE(
        "cassette", "Cassette", "TAPE", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFF07111B), Color(0xFF164B78), Color(0xE13278AC), Color(0xFFE8F1F7), Color(0xFF64B7EA), Color(0xFFFFD31A)),
        BackdropStyle.CASSETTE
    ),
    MINIDISC(
        "minidisc", "MiniDisc", "MD", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFF05090E), Color(0xFF14202B), Color(0xCC1C2B38), Color(0xFF67D8FF), Color(0xFFD7E8F2), Color(0xFFFF6A91)),
        BackdropStyle.MINIDISC
    ),
    WALKMAN(
        "walkman", "Portable Player", "WALKMAN", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFF080A0B), Color(0xFF1A1E20), Color(0xCC252B2E), Color(0xFFFF8B36), Color(0xFFC9D0D4), Color(0xFF56C8A4)),
        BackdropStyle.WALKMAN
    ),
    PAPER(
        "paper", "Paper White", "PAPER", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFF6F2E8), Color(0xFFE9E1D2), Color(0xFFFFFCF5), Color(0xFF2457F5), Color(0xFF76839A), Color(0xFFFF655D), Color(0xFF141619)),
        BackdropStyle.PAPER
    ),
    SAKURA(
        "sakura", "Sakura Pop", "SAKURA", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFFFF5FA), Color(0xFFFFE4EF), Color(0xFFFFFCFE), Color(0xFFFF4F93), Color(0xFF748CFF), Color(0xFFFFC933), Color(0xFF281A22)),
        BackdropStyle.SAKURA
    ),
    BAUHAUS(
        "bauhaus", "Bauhaus Player", "BAUHAUS", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFF4F0E5), Color(0xFFE5DDCC), Color(0xFFFFFBF2), Color(0xFFD94332), Color(0xFF2056A8), Color(0xFFE9B93F), Color(0xFF171717)),
        BackdropStyle.BAUHAUS
    ),
    BRUTALIST(
        "brutalist", "BRUTAL // RAW", "RAW", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFF3F3EC), Color(0xFFD8FF39), Color(0xFFFFFFFF), Color(0xFF111111), Color(0xFF2E5BFF), Color(0xFFFF3D3D), Color(0xFF111111)),
        BackdropStyle.BRUTALIST
    ),
    P3R_2(
        "p3r_2", "Persona 3R 2.0", "P3R2", ThemeFamily.PERSONA,
        GlassThemeColors(
            Color(0xFF02112A),
            Color(0xFF0548D8),
            Color(0xCC083B9D),
            Color(0xFF35E7FF),
            Color(0xFFFFFFFF),
            Color(0xFFFF355A),
            Color(0xFFF8FCFF)
        ),
        BackdropStyle.CLOCK
    ),
    WALKMAN_DECK(
        "walkman_deck", "Walkman Deck 2.0", "TPS-L2", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFE8EFF6), Color(0xFF4A91C6), Color(0xFFF5FAFF), Color(0xFF0B4F80), Color(0xFFB7C7D5), Color(0xFFFFA726), Color(0xFF111111)),
        BackdropStyle.CASSETTE
    ),
    WALKMAN_SPORTS(
        "walkman_sports", "Walkman Sports", "SPORTS", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFFBF7E8), Color(0xFFF3C84D), Color(0xFFFFF8DE), Color(0xFF003B6B), Color(0xFF7A8A95), Color(0xFFFF6A00), Color(0xFF111111)),
        BackdropStyle.WALKMAN
    ),
    MINIDISC_SILVER(
        "minidisc_silver", "MiniDisc Silver", "MD SILVER", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFF5F7FA), Color(0xFFD7E1EA), Color(0xFFFFFFFF), Color(0xFF2D7EFF), Color(0xFF6E7F92), Color(0xFFDF527D), Color(0xFF11151B)),
        BackdropStyle.MINIDISC
    ),
    DISCMAN(
        "discman", "Discman CD", "CD", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFF8F9FB), Color(0xFFE7EBF1), Color(0xFFFFFFFF), Color(0xFF348DFF), Color(0xFF7F8C9F), Color(0xFFFF5F6D), Color(0xFF141414)),
        BackdropStyle.DISCMAN
    ),
    IPOD_LIGHT(
        "ipod_light", "iPod-like Light", "CLICK", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFFAFAFA), Color(0xFFECECEC), Color(0xFFFFFFFF), Color(0xFF181818), Color(0xFFB9BDC4), Color(0xFF598FFF), Color(0xFF181818)),
        BackdropStyle.IPOD_LIGHT
    ),
    IOS_LIGHT(
        "ios_light", "Prismora iOS Light", "iOS", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFF5F7FB), Color(0xFFDDE5F3), Color(0xFFFFFFFF), Color(0xFF5A96FF), Color(0xFF8A99B4), Color(0xFFFF6DB2), Color(0xFF111827)),
        BackdropStyle.IOS_LIGHT
    ),
    MEDIA_PLAYER(
        "media_player", "Prismora Media Player", "WMP", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFEAF2FB), Color(0xFFC7DAEE), Color(0xFFF9FCFF), Color(0xFF1759B7), Color(0xFF52749A), Color(0xFFFF8E3C), Color(0xFF112033)),
        BackdropStyle.MEDIA_PLAYER
    ),
    PRISMORA_OS(
        "prismora_os", "Prismora OS", "OS", ThemeFamily.RETRO,
        GlassThemeColors(Color(0xFFF6F7F8), Color(0xFFE4E8EC), Color(0xFFFFFFFF), Color(0xFF202938), Color(0xFF5D7C92), Color(0xFF34B8FF), Color(0xFF111111)),
        BackdropStyle.PRISMORA_OS
    );

    companion object {
        fun fromId(id: String): ThemePreset = entries.firstOrNull { it.id == id } ?: MIKU
    }
}

private val ThemePreset.isP3rLike: Boolean get() = this == ThemePreset.P3R || this == ThemePreset.P3R_2
private val ThemePreset.isCassetteLike: Boolean get() = this == ThemePreset.CASSETTE || this == ThemePreset.WALKMAN_DECK || this == ThemePreset.WALKMAN_SPORTS

private val LocalGlassTheme = staticCompositionLocalOf { ThemePreset.MIKU.colors }
private val LocalThemePreset = staticCompositionLocalOf { ThemePreset.MIKU }
private val Ink: Color @Composable get() = LocalGlassTheme.current.ink
private val DeepBlue: Color @Composable get() = LocalGlassTheme.current.deep
private val Glass: Color @Composable get() = LocalGlassTheme.current.glass
private val Cyan: Color @Composable get() = LocalGlassTheme.current.accent
private val Blue: Color @Composable get() = LocalGlassTheme.current.secondary
private val Pink: Color @Composable get() = LocalGlassTheme.current.tertiary
private val White: Color @Composable get() = LocalGlassTheme.current.white

private enum class Page(val label: String, val symbol: String) {
    PLAYER("Player", "♪"),
    LIBRARY("Library", "≡"),
    EQUALIZER("Equalizer", "EQ"),
    LYRICS("Lyrics", "〽"),
    WRAPPED("Wrapped", "◎"),
    SETTINGS("Settings", "⚙")
}

private enum class LibraryView(val label: String) { TRACKS("Tracks"), FOLDERS("Folders"), ARTISTS("Artists"), ALBUMS("Albums") }
private enum class LibraryLayout { LIST, GRID }
private enum class SettingsSection { HOME, APPEARANCE, AUDIO, LIBRARY, PLAYBACK, VISUALIZER, PERFORMANCE, ADVANCED }
private data class LibraryDestination(val view: LibraryView, val value: String, val token: Int)
private val primaryPages = listOf(Page.PLAYER, Page.LIBRARY, Page.EQUALIZER, Page.WRAPPED)

@Composable
fun MikuGlassApp(vm: PlayerViewModel, onPickFolder: () -> Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val cover by vm.cover.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val lyricLines by vm.lyricLines.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val duration by vm.durationMs.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val outputs by vm.audioOutputs.collectAsStateWithLifecycle()
    val selectedOutput by vm.selectedOutputId.collectAsStateWithLifecycle()
    val audioRoutingMode by vm.audioRoutingMode.collectAsStateWithLifecycle()
    val audioFallbackEnabled by vm.audioFallbackEnabled.collectAsStateWithLifecycle()
    val liteMode by vm.liteMode.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val libraryScan by vm.libraryScan.collectAsStateWithLifecycle()
    val themeId by vm.themeId.collectAsStateWithLifecycle()
    val theme = remember(themeId) { ThemePreset.fromId(themeId) }
    var page by rememberSaveable { mutableStateOf(Page.PLAYER) }
    var returnPage by rememberSaveable { mutableStateOf(Page.PLAYER) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var libraryDestination by remember { mutableStateOf<LibraryDestination?>(null) }
    var destinationToken by remember { mutableIntStateOf(0) }
    var settingsSectionRequest by remember { mutableStateOf(SettingsSection.HOME) }

    fun navigatePrimary(target: Page) {
        if (target == page || target !in primaryPages) return
        page = target
    }
    val coverAccent by produceState(theme.colors.accent, cover, theme.id) {
        value = if (theme.family == ThemeFamily.COVER_ART) {
            withContext(Dispatchers.Default) { extractCoverAccent(cover) } ?: theme.colors.accent
        } else {
            theme.colors.accent
        }
    }
    val activeColors = remember(theme, coverAccent) {
        if (theme.family == ThemeFamily.COVER_ART) {
            theme.colors.copy(
                accent = coverAccent,
                secondary = lerp(coverAccent, Color.White, .38f),
                tertiary = lerp(coverAccent, Color(0xFF8B78FF), .28f)
            )
        } else {
            theme.colors
        }
    }

    CompositionLocalProvider(
        LocalGlassTheme provides activeColors,
        LocalThemePreset provides theme
    ) {
        MaterialTheme {
            Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    AnimatedBackdrop(
                        animate = phase == PlaybackPhase.PLAYING && page == Page.PLAYER && !liteMode,
                        theme = theme,
                        coverBytes = cover
                    )
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)
                    ) {
                        AppHeader(
                            status = status,
                            theme = theme,
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                            onBrandClick = {
                                if (page != Page.SETTINGS) returnPage = page
                                settingsSectionRequest = SettingsSection.HOME
                                page = Page.SETTINGS
                            },
                            onOutputClick = {
                                if (page != Page.SETTINGS) returnPage = page
                                settingsSectionRequest = SettingsSection.AUDIO
                                page = Page.SETTINGS
                            }
                        )
                        LibraryLoadingBar(libraryScan)
                        AnimatedContent(
                            targetState = page,
                            modifier = Modifier.weight(1f).pointerInput(page) {
                                if (page in primaryPages) {
                                    var horizontalDrag = 0f
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                                        onDragCancel = { horizontalDrag = 0f },
                                        onDragEnd = {
                                            val index = primaryPages.indexOf(page)
                                            when {
                                                horizontalDrag < -90f && index < primaryPages.lastIndex -> navigatePrimary(primaryPages[index + 1])
                                                horizontalDrag > 90f && index > 0 -> navigatePrimary(primaryPages[index - 1])
                                            }
                                            horizontalDrag = 0f
                                        }
                                    )
                                }
                            },
                            transitionSpec = {
                                if (theme.isP3rLike) {
                                    (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(180))) togetherWith
                                        (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(160)))
                                } else {
                                    (fadeIn(tween(260)) + scaleIn(initialScale = .985f)) togetherWith
                                        (fadeOut(tween(160)) + scaleOut(targetScale = 1.015f))
                                }
                            },
                            label = "page"
                        ) { selected ->
                            when (selected) {
                                Page.PLAYER -> PlayerPage(
                                    track = current,
                                    coverBytes = cover,
                                    phase = phase,
                                    duration = duration,
                                    message = message,
                                    theme = theme,
                                    status = status,
                                    onOpenLyrics = { page = Page.LYRICS },
                                    onOpenQueue = { showQueue = true },
                                    onBrowseTrack = { view, value ->
                                        vm.setSearch("")
                                        destinationToken++
                                        libraryDestination = LibraryDestination(view, value, destinationToken)
                                        navigatePrimary(Page.LIBRARY)
                                    },
                                    vm = vm
                                )
                                Page.LIBRARY -> LibraryPage(tracks, current, search, onPickFolder, vm, libraryDestination)
                                Page.EQUALIZER -> EqualizerPage(vm)
                                Page.LYRICS -> LyricsPage(lyrics?.plainLyrics, lyricLines, vm)
                                Page.WRAPPED -> WrappedPage(vm)
                                Page.SETTINGS -> SettingsPage(
                                    selectedTheme = theme,
                                    onThemeSelected = { vm.setTheme(it.id) },
                                    outputs = outputs,
                                    selectedOutput = selectedOutput,
                                    status = status,
                                    routingMode = audioRoutingMode,
                                    fallbackEnabled = audioFallbackEnabled,
                                    vm = vm,
                                    initialSection = settingsSectionRequest,
                                    onBack = { page = returnPage }
                                )
                            }
                        }
                        if (page != Page.SETTINGS) {
                            BottomGlassNavigation(selected = page, onSelected = ::navigatePrimary)
                        }
                    }
                    if (showQueue) {
                        QueueSheet(
                            queue = queue,
                            current = current,
                            vm = vm,
                            onDismiss = { showQueue = false },
                            onSelect = { vm.selectFromList(it, queue.ifEmpty { tracks }) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    status: OutputStatus,
    theme: ThemePreset,
    modifier: Modifier = Modifier,
    onBrandClick: () -> Unit,
    onOutputClick: () -> Unit
) {
    val p3r = theme.isP3rLike
    val brandShape = if (p3r) CutCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 0.dp, bottomStart = 18.dp) else RoundedCornerShape(14.dp)
    val outputShape = if (p3r) CutCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 0.dp, bottomStart = 16.dp) else RoundedCornerShape(18.dp)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.clip(brandShape)
                .background(if (p3r) White.copy(.94f) else Color.Transparent)
                .then(
                    if (p3r) Modifier.border(1.dp, Cyan.copy(.58f), brandShape)
                    else Modifier
                )
                .clickable(onClick = onBrandClick)
                .padding(start = if (p3r) 8.dp else 0.dp, top = 2.dp, end = 8.dp, bottom = 3.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val logoRes = themeLogoRes(theme)
            if (logoRes != null) {
                Image(
                    painter = painterResource(logoRes),
                    contentDescription = "Open settings",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(if (p3r) 118.dp else 144.dp).height(if (p3r) 46.dp else 42.dp)
                )
            } else {
                Column(Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(
                        theme.shortMark,
                        color = Cyan,
                        fontSize = if (theme.shortMark.length > 7) 15.sp else 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = if (theme.shortMark.length > 7) 1.0.sp else 1.8.sp,
                        maxLines = 1
                    )
                    Text("SETTINGS", color = White.copy(.32f), fontSize = 7.sp, letterSpacing = 1.5.sp)
                }
            }
            if (p3r) {
                Box(
                    Modifier.align(Alignment.TopEnd).width(28.dp).height(3.dp).background(Pink)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(outputShape)
                .background(
                    if (p3r) {
                        if (status.connected) Cyan.copy(.92f) else White.copy(.90f)
                    } else {
                        if (status.connected) Cyan.copy(.12f) else Pink.copy(.10f)
                    }
                )
                .border(
                    1.dp,
                    if (p3r) White.copy(.78f) else if (status.connected) Cyan.copy(.35f) else Pink.copy(.28f),
                    outputShape
                )
                .clickable(onClick = onOutputClick)
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(7.dp).clip(CircleShape).background(
                    if (p3r) DeepBlue else if (status.connected) Cyan else Pink
                )
            )
            Spacer(Modifier.width(7.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (status.connected) "OUTPUT LIVE" else "OUTPUT",
                    color = if (p3r) DeepBlue else White.copy(.82f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    status.deviceName.take(18),
                    color = if (p3r) DeepBlue.copy(.62f) else White.copy(.32f),
                    fontSize = 7.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LibraryLoadingBar(state: LibraryScanState) {
    if (!state.running) return
    Column(Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.stage.uppercase(), color = White.copy(.42f), fontSize = 9.sp, letterSpacing = 1.3.sp)
            Spacer(Modifier.weight(1f))
            Text("${state.loaded} TRACKS LOADED", color = Cyan.copy(.78f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
            color = Cyan,
            trackColor = White.copy(.08f)
        )
    }
}

@Composable
private fun PlayerPage(
    track: Track?,
    coverBytes: ByteArray?,
    phase: PlaybackPhase,
    duration: Long,
    message: String,
    theme: ThemePreset,
    status: OutputStatus,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onBrowseTrack: (LibraryView, String) -> Unit,
    vm: PlayerViewModel
) {
    val position by vm.positionMs.collectAsStateWithLifecycle()
    val spectrum by vm.spectrum.collectAsStateWithLifecycle()
    val repeatMode by vm.repeatMode.collectAsStateWithLifecycle()
    val visualizerReflection by vm.visualizerReflection.collectAsStateWithLifecycle()
    val visualizerStyle by vm.visualizerStyle.collectAsStateWithLifecycle()
    val dsp by vm.dspSettings.collectAsStateWithLifecycle()
    val playing = phase == PlaybackPhase.PLAYING || phase == PlaybackPhase.LOADING
    val transition = rememberInfiniteTransition(label = "art")
    val pulse by transition.animateFloat(
        initialValue = 0.992f,
        targetValue = if (playing) 1.018f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), AnimationRepeatMode.Reverse),
        label = "pulse"
    )
    var dragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var showMetadata by remember { mutableStateOf(false) }
    var showBrowseMenu by remember { mutableStateOf(false) }
    LaunchedEffect(position, duration, dragging) {
        if (!dragging) sliderPosition = if (duration > 0) position.toFloat() / duration else 0f
    }

    if (theme == ThemePreset.P3R_2) {
        P3R2PlayerLayout(
            track = track,
            coverBytes = coverBytes,
            phase = phase,
            duration = duration,
            position = position,
            onOpenLyrics = onOpenLyrics,
            onOpenQueue = onOpenQueue,
            vm = vm
        )
        return
    }
    if (theme.isCassetteLike) {
        CassettePlayerLayout(
            track = track, coverBytes = coverBytes, phase = phase,
            position = position, duration = duration, message = message,
            onOpenLyrics = onOpenLyrics, onOpenQueue = onOpenQueue, vm = vm
        )
        return
    }

    Column(
        Modifier.fillMaxSize().pointerInput(track?.id) {
            var totalDrag = 0f
            detectVerticalDragGestures(
                onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                onDragEnd = {
                    if (totalDrag < -80f) onOpenQueue()
                    totalDrag = 0f
                }
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(start = 14.dp, top = 24.dp, end = 14.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassArtwork(
                bytes = coverBytes,
                playing = playing,
                theme = theme,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).scale(pulse),
                onOpenMetadata = { showMetadata = true }
            )
        }
        Spacer(Modifier.height(8.dp))
        if (theme.isP3rLike) {
            val infoShape = CutCornerShape(topStart = 0.dp, topEnd = 24.dp, bottomEnd = 0.dp, bottomStart = 24.dp)
            Row(
                Modifier.fillMaxWidth().clip(infoShape).background(White.copy(.96f))
                    .border(1.dp, Cyan.copy(.74f), infoShape).padding(start = 14.dp, top = 7.dp, end = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(5.dp).height(38.dp).background(Pink))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title ?: "Choose your first track",
                        color = DeepBlue,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track?.artist ?: "Your music will appear here",
                        color = Color(0xFF0878C9),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    onClick = { if (track != null) showBrowseMenu = true },
                    shape = CutCornerShape(10.dp),
                    color = DeepBlue,
                    contentColor = White,
                    modifier = Modifier.size(40.dp)
                ) { Box(contentAlignment = Alignment.Center) { Text("⋮", fontSize = 22.sp, fontWeight = FontWeight.Black) } }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(40.dp))
                Text(
                    track?.title ?: "Choose your first track",
                    color = White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = { if (track != null) showBrowseMenu = true },
                    shape = CircleShape,
                    color = Glass.copy(.76f),
                    contentColor = White,
                    modifier = Modifier.size(40.dp).border(1.dp, White.copy(.08f), CircleShape)
                ) { Box(contentAlignment = Alignment.Center) { Text("⋮", fontSize = 22.sp, fontWeight = FontWeight.Bold) } }
            }
            Text(
                track?.artist ?: "Your music will appear here",
                color = Cyan.copy(.72f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        if (theme.isCassetteLike) {
            CassetteSeekDeck(
                track = track,
                coverBytes = coverBytes,
                position = position,
                duration = duration,
                playing = playing,
                onSeek = vm::seekTo,
                modifier = Modifier.fillMaxWidth().height(132.dp)
            )
        } else {
            Slider(
                value = sliderPosition.coerceIn(0f, 1f),
                onValueChange = { dragging = true; sliderPosition = it },
                onValueChangeFinished = {
                    vm.seekTo((duration * sliderPosition).toLong())
                    dragging = false
                },
                enabled = track != null && duration > 0,
                colors = SliderDefaults.colors(
                    thumbColor = if (theme.isP3rLike) White else Cyan,
                    activeTrackColor = Cyan,
                    inactiveTrackColor = if (theme.isP3rLike) Blue.copy(.30f) else White.copy(.12f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().offset(y = (-7).dp)) {
                Text(formatTime(if (dragging) (duration * sliderPosition).toLong() else position), color = White.copy(.42f), fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(duration), color = White.copy(.42f), fontSize = 10.sp)
            }
        }
        AudioSpectrum(spectrum, playing, visualizerReflection, visualizerStyle, Modifier.fillMaxWidth().height(70.dp))
        PlaybackControls(phase, vm, position, duration)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FeaturePill(
                label = "LYRICS",
                symbol = "〽",
                active = false,
                modifier = Modifier.weight(1f),
                action = onOpenLyrics
            )
            FeaturePill(
                label = when (repeatMode) {
                    RepeatMode.OFF -> "REPEAT"
                    RepeatMode.ALL -> "REPEAT ALL"
                    RepeatMode.ONE -> "REPEAT ONE"
                },
                symbol = if (repeatMode == RepeatMode.ONE) "↻1" else "↻",
                active = repeatMode != RepeatMode.OFF,
                modifier = Modifier.weight(1f),
                action = vm::cycleRepeatMode
            )
        }
        Text(
            text = when {
                message.isNotBlank() -> message
                dsp.enabled -> "DSP ACTIVE • ${track?.extension?.uppercase().orEmpty()} • SOURCE RATE"
                phase == PlaybackPhase.PLAYING -> "DIRECT PCM • ${track?.extension?.uppercase().orEmpty()}"
                phase == PlaybackPhase.PAUSED -> "PAUSED"
                else -> "LOCAL • LOSSLESS • MULTI-OUTPUT"
            },
            color = if (phase == PlaybackPhase.ERROR) Pink else White.copy(.42f),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        )
    }

    if (showMetadata && track != null) {
        TrackMetadataDialog(track = track, status = status, onDismiss = { showMetadata = false })
    }
    if (showBrowseMenu && track != null) {
        TrackBrowseDialog(
            track = track,
            onDismiss = { showBrowseMenu = false },
            onChoose = { view, value ->
                showBrowseMenu = false
                onBrowseTrack(view, value)
            }
        )
    }
}

@Composable
private fun P3R2PlayerLayout(
    track: Track?,
    coverBytes: ByteArray?,
    phase: PlaybackPhase,
    duration: Long,
    position: Long,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    vm: PlayerViewModel
) {
    val playing = phase == PlaybackPhase.PLAYING || phase == PlaybackPhase.LOADING
    val spectrum by vm.spectrum.collectAsStateWithLifecycle()
    val visualizerReflection by vm.visualizerReflection.collectAsStateWithLifecycle()
    val visualizerStyle by vm.visualizerStyle.collectAsStateWithLifecycle()
    val artwork = rememberArtworkBitmap(coverBytes, 420)
    var dragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(position, duration, dragging) {
        if (!dragging) sliderPosition = if (duration > 0L) position.toFloat() / duration else 0f
    }

    Column(
        Modifier.fillMaxSize().pointerInput(track?.id) {
            var totalDrag = 0f
            detectVerticalDragGestures(onVerticalDrag = { _, amount -> totalDrag += amount }, onDragEnd = {
                if (totalDrag < -80f) onOpenQueue()
                totalDrag = 0f
            })
        }
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(126.dp).fillMaxHeight().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LIBRARY", "PLAYLISTS", "ALBUMS", "ARTISTS").forEach { label ->
                    Box(
                        Modifier.fillMaxWidth().clip(CutCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 0.dp, bottomStart = 18.dp))
                            .background(DeepBlue.copy(.82f)).border(1.dp, Cyan.copy(.50f), CutCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 0.dp, bottomStart = 18.dp))
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Text(label, color = White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().clip(CutCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 0.dp, bottomStart = 18.dp))
                        .background(Brush.horizontalGradient(listOf(Pink, Color(0xFFFF5A5F), White)))
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(track?.title ?: "SELECTED TRACK", color = DeepBlue, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track?.artist ?: "Artist", color = Color(0xFF0A4DC4), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                listOf("QUEUE", "SETTINGS").forEach { label ->
                    Box(
                        Modifier.fillMaxWidth().clip(CutCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 0.dp, bottomStart = 18.dp))
                            .background(DeepBlue.copy(.82f)).border(1.dp, Cyan.copy(.50f), CutCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 0.dp, bottomStart = 18.dp))
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Text(label, color = White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("MORE\nMUSIC\nMORE\nYOU.", color = Cyan.copy(.95f), fontSize = 22.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(
                    Modifier.fillMaxWidth().clip(CutCornerShape(topStart = 0.dp, topEnd = 30.dp, bottomEnd = 0.dp, bottomStart = 24.dp))
                        .background(White.copy(.96f)).border(2.dp, Cyan.copy(.72f), CutCornerShape(topStart = 0.dp, topEnd = 30.dp, bottomEnd = 0.dp, bottomStart = 24.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column {
                        Text("P3R 2.0", color = DeepBlue, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text("A BRIGHTER PLAYLIST AWAITS.", color = Color(0xFF0B69CF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().weight(1f).clip(CutCornerShape(topStart = 0.dp, topEnd = 26.dp, bottomEnd = 0.dp, bottomStart = 26.dp))
                        .background(DeepBlue.copy(.82f)).border(1.dp, White.copy(.16f), CutCornerShape(topStart = 0.dp, topEnd = 26.dp, bottomEnd = 0.dp, bottomStart = 26.dp))
                        .padding(14.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f).clip(CutCornerShape(20.dp))
                                .background(White.copy(.05f)).border(2.dp, White.copy(.88f), CutCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (artwork != null) {
                                Image(artwork, contentDescription = "Album cover", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text("ALBUM ART", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(track?.title ?: "Choose your first track", color = White, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track?.album ?: "Album", color = Cyan, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track?.artist ?: "Artist", color = White.copy(.94f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = sliderPosition.coerceIn(0f, 1f),
                            onValueChange = { dragging = true; sliderPosition = it },
                            onValueChangeFinished = { vm.seekTo((duration * sliderPosition).toLong()); dragging = false },
                            enabled = track != null && duration > 0L,
                            colors = SliderDefaults.colors(thumbColor = White, activeTrackColor = Cyan, inactiveTrackColor = Blue.copy(.30f)),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth().offset(y = (-7).dp)) {
                            Text(formatTime(if (dragging) (duration * sliderPosition).toLong() else position), color = White.copy(.52f), fontSize = 10.sp)
                            Spacer(Modifier.weight(1f))
                            Text(formatTime(duration), color = White.copy(.52f), fontSize = 10.sp)
                        }
                        AudioSpectrum(spectrum, playing, visualizerReflection, visualizerStyle, Modifier.fillMaxWidth().height(72.dp))
                        PlaybackControls(phase, vm, position, duration)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            FeaturePill(label = "LYRICS", symbol = "〽", active = false, modifier = Modifier.weight(1f), action = onOpenLyrics)
                            FeaturePill(label = "QUEUE", symbol = "≡", active = false, modifier = Modifier.weight(1f), action = onOpenQueue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CassettePlayerLayout(
    track: Track?,
    coverBytes: ByteArray?,
    phase: PlaybackPhase,
    position: Long,
    duration: Long,
    message: String,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    vm: PlayerViewModel
) {
    val artwork = rememberArtworkBitmap(coverBytes, 320)
    val playing = phase == PlaybackPhase.PLAYING || phase == PlaybackPhase.LOADING
    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val blue = Color(0xFF4A91C6)
    val blueDark = Color(0xFF0B4F80)
    val shell = Color(0xFFD8D8D6)
    val black = Color(0xFF111111)
    val red = Color(0xFFC52D2D)

    Column(
        Modifier.fillMaxSize().background(shell).border(4.dp, blueDark, RoundedCornerShape(2.dp))
            .pointerInput(track?.id) {
                var totalDrag = 0f
                detectVerticalDragGestures(onVerticalDrag = { _, amount -> totalDrag += amount }, onDragEnd = {
                    if (totalDrag < -80f) onOpenQueue()
                    totalDrag = 0f
                })
            }
            .padding(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(76.dp).background(blue).border(3.dp, blueDark)) {
            Box(Modifier.align(Alignment.TopCenter).width(52.dp).height(8.dp).background(Color(0xFFFFD426)))
            Column(Modifier.padding(start = 18.dp, top = 8.dp)) {
                Text("SONY", color = Color(0xFFE7E3DF), fontSize = 34.sp, fontWeight = FontWeight.Normal)
                Text("PERSONAL STEREO CASSETTE PLAYER", color = Color(0xFFE7E3DF), fontSize = 10.sp, letterSpacing = .5.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.width(72.dp).fillMaxHeight().background(blue).border(3.dp, blueDark).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ALBUM", color = black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(black), contentAlignment = Alignment.Center) {
                    if (artwork != null) Image(artwork, contentDescription = "Album cover", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("ALBUM\nCOVER", color = Color.White, textAlign = TextAlign.Center, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.weight(1f))
                Text(track?.title ?: "NO TAPE", color = black, fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(track?.artist ?: "ARTIST", color = black, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Surface(onClick = onOpenLyrics, color = black, contentColor = Color.White, shape = CircleShape, modifier = Modifier.size(38.dp)) { Box(contentAlignment = Alignment.Center) { Text("〽", fontSize = 16.sp) } }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(78.dp).background(blue).border(3.dp, blueDark), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(track?.album ?: "ALBUM", color = black, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatTime(position)} / ${formatTime(duration)}", color = black.copy(.65f), fontSize = 9.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).background(blue).border(3.dp, blueDark).padding(8.dp)) {
                    Canvas(Modifier.fillMaxSize().background(Color(0xFFF3F1EC)).border(3.dp, black)) {
                        val lc = Offset(size.width * .5f, size.height * .25f)
                        val rc = Offset(size.width * .5f, size.height * .73f)
                        val base = min(size.width, size.height) * .16f
                        val lr = base * (1.24f - progress * .42f)
                        val rr = base * (.82f + progress * .42f)
                        val rot = (position % 2200L) / 2200f * 6.283185f
                        drawRect(red.copy(.26f), Offset(size.width * .08f, 0f), androidx.compose.ui.geometry.Size(size.width * .84f, size.height))
                        drawRect(black.copy(.92f), Offset(size.width * .15f, 0f), androidx.compose.ui.geometry.Size(size.width * .70f, size.height))
                        listOf(lc to lr, rc to rr).forEach { (c, r) ->
                            drawCircle(Color(0xFFBFC1C2), r, c)
                            drawCircle(Color(0xFFE5E5E3), r * .56f, c)
                            repeat(6) { i ->
                                val a = rot + i * 6.283185f / 6f
                                drawLine(Color(0xFFA7A7A5), Offset(c.x + cos(a) * r * .18f, c.y + sin(a) * r * .18f), Offset(c.x + cos(a) * r * .48f, c.y + sin(a) * r * .48f), 2.dp.toPx())
                            }
                        }
                    }
                }
            }
            Column(Modifier.width(104.dp).fillMaxHeight().background(blue).border(3.dp, blueDark).padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Canvas(Modifier.width(58.dp).height(150.dp)) {
                    val shaftW = size.width * .30f
                    val shaftL = size.height * .55f
                    drawRect(Color(0xFF8D8D8B), Offset((size.width-shaftW)/2f, size.height-shaftL), androidx.compose.ui.geometry.Size(shaftW, shaftL))
                    val arrow = Path().apply {
                        moveTo(size.width * .50f, 0f)
                        lineTo(size.width * .12f, size.height * .55f)
                        lineTo(size.width * .35f, size.height * .55f)
                        lineTo(size.width * .35f, size.height * .80f)
                        lineTo(size.width * .65f, size.height * .80f)
                        lineTo(size.width * .65f, size.height * .55f)
                        lineTo(size.width * .88f, size.height * .55f)
                        close()
                    }
                    drawPath(arrow, Color(0xFFD9D7D1))
                    drawPath(arrow, Color(0xFF9A9A98), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                }
                Spacer(Modifier.weight(1f))
                Text(if (playing) "PLAY" else "READY", color = black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                if (message.isNotBlank()) Text(message.take(20), color = black.copy(.55f), fontSize = 6.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(68.dp).background(blue).border(3.dp, blueDark).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CassettePrevButton(position, duration, vm, 44.dp)
            Button(onClick = vm::togglePlayPause, colors = ButtonDefaults.buttonColors(containerColor = black, contentColor = blue), shape = RoundedCornerShape(3.dp), modifier = Modifier.width(82.dp).height(48.dp)) { Text(if (playing) "Ⅱ" else "▶", fontSize = 24.sp, fontWeight = FontWeight.Black) }
            CassetteNextButton(position, duration, vm, 44.dp)
        }
        Text("HOLD PREV = REWIND  •  HOLD NEXT = FAST FORWARD", color = black.copy(.55f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .45.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 3.dp))
    }
}

@Composable
private fun CassetteSeekDeck(
    track: Track?,
    coverBytes: ByteArray?,
    position: Long,
    duration: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val artwork = rememberArtworkBitmap(coverBytes, 160)
    var previewPosition by remember { mutableStateOf<Long?>(null) }
    var holding by remember { mutableStateOf(false) }
    val latestPosition by rememberUpdatedState(position)
    val latestDuration by rememberUpdatedState(duration)
    val shownPosition = previewPosition ?: position
    val progress = if (duration > 0L) (shownPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val body = DeepBlue
    val face = Blue
    val ink = Ink
    val metal = White
    val fastForward = Pink

    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(body)
            .border(2.dp, face.copy(.82f), RoundedCornerShape(9.dp))
    ) {
        Box(Modifier.align(Alignment.TopCenter).width(52.dp).height(5.dp).background(fastForward))
        Column(Modifier.fillMaxSize().padding(start = 9.dp, top = 9.dp, end = 9.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(7.dp)).background(ink)
                        .border(1.dp, metal.copy(.30f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (artwork != null) {
                        Image(artwork, contentDescription = "Album cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text("ART", color = metal.copy(.62f), fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text("PRISMORA PERSONAL STEREO", color = metal.copy(.78f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.0.sp)
                    Text(track?.title ?: "NO TAPE", color = metal, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track?.artist ?: "Insert a track", color = metal.copy(.58f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatTime(shownPosition), color = fastForward, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(formatTime(duration), color = metal.copy(.48f), fontSize = 8.sp)
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Canvas(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                        .background(ink.copy(.92f)).border(1.dp, metal.copy(.18f), RoundedCornerShape(7.dp))
                ) {
                    val leftCenter = Offset(size.width * .31f, size.height * .52f)
                    val rightCenter = Offset(size.width * .69f, size.height * .52f)
                    val baseRadius = min(size.width, size.height) * .13f
                    val leftRadius = baseRadius * (1.25f - progress * .45f)
                    val rightRadius = baseRadius * (.80f + progress * .45f)
                    val rotation = (shownPosition % 2_400L) / 2_400f * 6.283185f

                    drawLine(fastForward.copy(.46f), Offset(leftCenter.x, leftCenter.y - leftRadius), Offset(rightCenter.x, rightCenter.y - rightRadius), 2.dp.toPx())
                    drawLine(fastForward.copy(.34f), Offset(leftCenter.x, leftCenter.y + leftRadius), Offset(rightCenter.x, rightCenter.y + rightRadius), 2.dp.toPx())
                    listOf(leftCenter to leftRadius, rightCenter to rightRadius).forEach { (center, radius) ->
                        drawCircle(metal.copy(.82f), radius, center)
                        drawCircle(ink.copy(.94f), radius * .55f, center)
                        repeat(6) { index ->
                            val angle = rotation + index * 6.283185f / 6f
                            drawLine(
                                metal.copy(.62f),
                                Offset(center.x + cos(angle) * radius * .18f, center.y + sin(angle) * radius * .18f),
                                Offset(center.x + cos(angle) * radius * .48f, center.y + sin(angle) * radius * .48f),
                                1.5.dp.toPx(),
                                StrokeCap.Round
                            )
                        }
                    }
                    val positionX = size.width * (.08f + progress * .84f)
                    drawLine(face.copy(.20f), Offset(size.width * .08f, size.height * .88f), Offset(size.width * .92f, size.height * .88f), 3.dp.toPx(), StrokeCap.Round)
                    drawCircle(fastForward, 3.5.dp.toPx(), Offset(positionX, size.height * .88f))
                }
                Box(
                    Modifier.width(82.dp).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                        .background(if (holding) fastForward else fastForward.copy(.88f))
                        .border(1.dp, metal.copy(.40f), RoundedCornerShape(7.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (latestDuration > 0L) {
                                        val start = latestPosition.coerceIn(0L, latestDuration)
                                        var target = start
                                        var released = false
                                        coroutineScope {
                                            val repeater = launch {
                                                delay(350L)
                                                holding = true
                                                while (target < latestDuration) {
                                                    target = (target + 1_000L).coerceAtMost(latestDuration)
                                                    previewPosition = target
                                                    delay(100L)
                                                }
                                            }
                                            released = tryAwaitRelease()
                                            repeater.cancel()
                                        }
                                        holding = false
                                        previewPosition = null
                                        if (released && target > start) onSeek(target)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("≫", color = ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(if (holding) "FAST FORWARD" else "HOLD", color = ink, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
                    }
                }
            }
        }
        if (!playing) {
            Text("TAPE READY", color = metal.copy(.28f), fontSize = 7.sp, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun GlassArtwork(
    bytes: ByteArray?,
    playing: Boolean,
    theme: ThemePreset,
    modifier: Modifier,
    onOpenMetadata: () -> Unit
) {
    val bitmap = rememberArtworkBitmap(bytes, 1024)
    val themeArt = themeArtworkRes(theme)
    val p3r = theme.isP3rLike
    // Capture composable theme colors before entering the non-composable Canvas draw scope.
    val p3rWhite = White
    val p3rPink = Pink
    val p3rCyan = Cyan
    val artShape = if (p3r) {
        CutCornerShape(topStart = 0.dp, topEnd = 38.dp, bottomEnd = 0.dp, bottomStart = 38.dp)
    } else {
        RoundedCornerShape(38.dp)
    }
    Box(
        modifier.clip(artShape)
            .background(
                if (p3r) Brush.linearGradient(listOf(White.copy(.94f), Cyan.copy(.42f), DeepBlue))
                else Brush.radialGradient(listOf(Cyan.copy(.22f), Blue.copy(.13f), Pink.copy(.10f), DeepBlue))
            )
            .border(
                if (p3r) 2.dp else 1.dp,
                if (p3r) Brush.linearGradient(listOf(White, Cyan, Blue))
                else Brush.linearGradient(listOf(Cyan.copy(.55f), White.copy(.08f), Pink.copy(.35f))),
                artShape
            )
            .combinedClickable(onClick = {}, onLongClick = onOpenMetadata)
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = "Album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val transition = rememberInfiniteTransition(label = "theme-art")
            val floatScale by transition.animateFloat(
                initialValue = .975f,
                targetValue = if (playing) 1.025f else 1f,
                animationSpec = infiniteRepeatable(tween(1_350, easing = FastOutSlowInEasing), AnimationRepeatMode.Reverse),
                label = "theme-art-float"
            )
            Box(Modifier.fillMaxSize().scale(floatScale), contentAlignment = Alignment.Center) {
                if (themeArt != null) {
                    Image(
                        painter = painterResource(themeArt),
                        contentDescription = "${theme.displayName} theme artwork",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp).alpha(.92f)
                    )
                } else {
                    Text(
                        theme.shortMark,
                        color = Cyan.copy(.26f),
                        fontSize = if (theme.shortMark.length > 7) 46.sp else 70.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 56.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
        if (p3r) {
            Canvas(Modifier.fillMaxSize()) {
                val slash = Path().apply {
                    moveTo(size.width * .72f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * .10f)
                    lineTo(size.width * .66f, size.height * .04f)
                    close()
                }
                drawPath(slash, p3rWhite.copy(.92f))
                drawLine(p3rPink.copy(.92f), Offset(size.width * .04f, size.height * .95f), Offset(size.width * .58f, size.height * .86f), 4.dp.toPx())
                drawLine(p3rCyan.copy(.92f), Offset(size.width * .58f, size.height * .86f), Offset(size.width * .93f, size.height * .78f), 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun AudioSpectrum(
    bars: List<Float>,
    playing: Boolean,
    reflectionAmount: Float,
    style: VisualizerStyle,
    modifier: Modifier = Modifier
) {
    val values = if (bars.isEmpty()) List(40) { 0f } else bars
    val spectrumPink = Pink
    val spectrumCyan = Cyan
    val spectrumWhite = White
    val p3r = LocalThemePreset.current.isP3rLike
    Canvas(modifier = modifier.alpha(if (playing) 1f else .62f)) {
        val count = values.size.coerceAtLeast(1)
        val baseline = if (p3r) size.height * .58f else size.height * .62f
        val maxHeight = if (p3r) size.height * .52f else size.height * .56f
        when (style) {
            VisualizerStyle.BARS -> {
                val gap = if (p3r) 2.dp.toPx() else 3.dp.toPx()
                val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(if (p3r) 1.6.dp.toPx() else 2.dp.toPx())
                val reflectionMax = size.height * reflectionAmount.coerceIn(0f, .45f) * if (p3r) .85f else 1f
                val topBrush = Brush.verticalGradient(
                    colors = if (p3r) listOf(spectrumWhite, spectrumCyan, Color(0xFF1AA8FF)) else listOf(spectrumPink, spectrumCyan),
                    startY = baseline - maxHeight,
                    endY = baseline
                )
                values.forEachIndexed { index, raw ->
                    val level = raw.coerceIn(0f, 1f)
                    val x = barWidth / 2f + index * (barWidth + gap)
                    val height = if (playing) 3.dp.toPx() + level * maxHeight else 2.dp.toPx()
                    drawLine(topBrush, Offset(x, baseline), Offset(x, baseline - height), barWidth, if (p3r) StrokeCap.Square else StrokeCap.Round)
                    val reflection = if (playing) level * reflectionMax else 0f
                    if (reflection > 1f) drawLine(
                        spectrumCyan.copy(.20f), Offset(x, baseline + 4.dp.toPx()),
                        Offset(x, baseline + 4.dp.toPx() + reflection), barWidth,
                        if (p3r) StrokeCap.Square else StrokeCap.Round
                    )
                }
            }
            VisualizerStyle.LINE -> {
                val path = Path()
                val reflection = Path()
                values.forEachIndexed { index, raw ->
                    val x = if (count == 1) 0f else size.width * index / (count - 1f)
                    val y = baseline - raw.coerceIn(0f, 1f) * maxHeight
                    val reflectedY = baseline + raw.coerceIn(0f, 1f) * size.height * reflectionAmount
                    if (index == 0) { path.moveTo(x, y); reflection.moveTo(x, reflectedY) }
                    else { path.lineTo(x, y); reflection.lineTo(x, reflectedY) }
                }
                drawPath(path, spectrumCyan, style = androidx.compose.ui.graphics.drawscope.Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
                drawPath(reflection, spectrumPink.copy(.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
            }
            VisualizerStyle.RADIAL -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                val inner = min(size.width, size.height) * .20f
                val range = min(size.width, size.height) * .30f
                values.forEachIndexed { index, raw ->
                    val angle = index * 6.283185f / count - 1.5708f
                    val level = if (playing) raw.coerceIn(0f, 1f) else 0f
                    val start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner)
                    val end = Offset(center.x + cos(angle) * (inner + 2.dp.toPx() + range * level), center.y + sin(angle) * (inner + 2.dp.toPx() + range * level))
                    drawLine(if (index % 3 == 0) spectrumPink else spectrumCyan, start, end, 2.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(spectrumWhite.copy(.14f), inner, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            VisualizerStyle.MINIMAL -> {
                val average = values.average().toFloat().coerceIn(0f, 1f)
                val peak = (values.maxOrNull() ?: 0f).coerceIn(0f, 1f)
                val y1 = size.height * .38f
                val y2 = size.height * .67f
                drawLine(spectrumWhite.copy(.10f), Offset(0f, y1), Offset(size.width, y1), 5.dp.toPx(), StrokeCap.Round)
                drawLine(spectrumCyan, Offset(0f, y1), Offset(size.width * average, y1), 5.dp.toPx(), StrokeCap.Round)
                drawLine(spectrumWhite.copy(.08f), Offset(0f, y2), Offset(size.width, y2), 2.dp.toPx(), StrokeCap.Round)
                drawLine(spectrumPink, Offset(0f, y2), Offset(size.width * peak, y2), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        drawLine(if (p3r) spectrumWhite.copy(.22f) else spectrumWhite.copy(.08f), Offset(0f, baseline + 1.dp.toPx()), Offset(size.width, baseline + 1.dp.toPx()), if (p3r) 1.4.dp.toPx() else 1.dp.toPx())
        if (p3r) {
            drawLine(
                color = spectrumPink.copy(.88f),
                start = Offset(size.width * .18f, size.height * .93f),
                end = Offset(size.width * .42f, size.height * .84f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun PlaybackControls(phase: PlaybackPhase, vm: PlayerViewModel, position: Long = 0L, duration: Long = 0L) {
    val active = phase == PlaybackPhase.PLAYING || phase == PlaybackPhase.LOADING
    val theme = LocalThemePreset.current
    val p3r = theme.isP3rLike
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleTextButton("‹|", "Previous", 54.dp) { vm.previous() }
        val playShape = if (p3r) CutCornerShape(20.dp) else CircleShape
        Button(
            onClick = vm::togglePlayPause,
            shape = playShape,
            modifier = Modifier.size(72.dp).then(if (p3r) Modifier.border(2.dp, Cyan, playShape) else Modifier),
            colors = ButtonDefaults.buttonColors(containerColor = if (p3r) White else Cyan, contentColor = if (p3r) DeepBlue else Ink)
        ) { Text(if (active) "Ⅱ" else "▶", fontSize = 25.sp, fontWeight = FontWeight.Black) }
        if (theme.isCassetteLike) CassetteNextButton(position, duration, vm)
        else CircleTextButton("|›", "Next", 54.dp) { vm.next() }
    }
}

@Composable
private fun CassettePrevButton(position: Long, duration: Long, vm: PlayerViewModel, size: androidx.compose.ui.unit.Dp = 54.dp) {
    var rewinding by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Long?>(null) }
    val latestPosition by rememberUpdatedState(position)
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (rewinding) Cyan else Glass)
            .border(2.dp, if (rewinding) Ink else White.copy(.20f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val start = latestPosition.coerceAtLeast(0L)
                    var target = start
                    var didRewind = false
                    coroutineScope {
                        val job = launch {
                            delay(320L)
                            rewinding = true
                            didRewind = true
                            while (target > 0L) {
                                target = (target - 1_250L).coerceAtLeast(0L)
                                preview = target
                                delay(90L)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                    rewinding = false
                    preview = null
                    if (didRewind && target < start) vm.seekTo(target) else vm.previous()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (rewinding) "≪" else "‹|", color = if (rewinding) Ink else White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            if (rewinding) Text(formatTime(preview ?: position), color = Ink, fontSize = 6.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CassetteNextButton(position: Long, duration: Long, vm: PlayerViewModel, size: androidx.compose.ui.unit.Dp = 54.dp) {
    var fastForwarding by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Long?>(null) }
    val latestPosition by rememberUpdatedState(position)
    val latestDuration by rememberUpdatedState(duration)
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (fastForwarding) Pink else Glass)
            .border(2.dp, if (fastForwarding) Ink else White.copy(.20f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val start = latestPosition.coerceAtLeast(0L)
                    var target = start
                    var didFastForward = false
                    coroutineScope {
                        val job = launch {
                            delay(320L)
                            if (latestDuration > 0L) {
                                fastForwarding = true
                                didFastForward = true
                                while (target < latestDuration) {
                                    target = (target + 1_250L).coerceAtMost(latestDuration)
                                    preview = target
                                    delay(90L)
                                }
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                    fastForwarding = false
                    preview = null
                    if (didFastForward && target > start) vm.seekTo(target) else vm.next()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (fastForwarding) "≫" else "|›", color = if (fastForwarding) Ink else White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            if (fastForwarding) Text(formatTime(preview ?: position), color = Ink, fontSize = 6.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CircleTextButton(symbol: String, description: String, size: androidx.compose.ui.unit.Dp, action: () -> Unit) {
    val p3r = LocalThemePreset.current.isP3rLike
    val shape = if (p3r) CutCornerShape(14.dp) else CircleShape
    Surface(
        onClick = action,
        shape = shape,
        color = if (p3r) DeepBlue.copy(.92f) else Glass,
        contentColor = if (p3r) White else White,
        modifier = Modifier.size(size).border(1.dp, if (p3r) Cyan.copy(.72f) else White.copy(.08f), shape)
    ) { Box(contentAlignment = Alignment.Center) { Text(symbol, fontSize = 18.sp, fontWeight = FontWeight.Black) } }
}

@Composable
private fun FeaturePill(label: String, symbol: String, active: Boolean, modifier: Modifier = Modifier, action: () -> Unit) {
    val p3r = LocalThemePreset.current.isP3rLike
    val shape = if (p3r) CutCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 0.dp, bottomStart = 14.dp) else RoundedCornerShape(18.dp)
    Surface(
        onClick = action,
        shape = shape,
        color = if (p3r) {
            if (active) White else DeepBlue.copy(.82f)
        } else {
            if (active) Cyan.copy(.16f) else Glass
        },
        contentColor = if (p3r) {
            if (active) DeepBlue else White
        } else {
            if (active) Cyan else White.copy(.62f)
        },
        modifier = modifier.border(
            1.dp,
            if (p3r) { if (active) Pink else Cyan.copy(.56f) } else { if (active) Cyan.copy(.34f) else White.copy(.08f) },
            shape
        )
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(symbol, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(7.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun PlaybackOptionsSheet(
    repeatMode: RepeatMode,
    shuffle: Boolean,
    sleepRemaining: Long,
    queuePersistence: Boolean,
    vm: PlayerViewModel,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(.48f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Brush.verticalGradient(listOf(White.copy(.08f), Glass, Ink)))
                .border(1.dp, White.copy(.10f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(enabled = false) {}
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(54.dp).height(5.dp)
                    .clip(CircleShape).background(White.copy(.18f))
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PLAYBACK", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Player controls", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                }
                SmallGlassButton("CLOSE", onDismiss)
            }

            Text("REPEAT", color = White.copy(.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RepeatMode.entries.forEach { mode ->
                    FeaturePill(
                        label = when (mode) {
                            RepeatMode.OFF -> "OFF"
                            RepeatMode.ALL -> "ALL"
                            RepeatMode.ONE -> "ONE"
                        },
                        symbol = if (mode == RepeatMode.ONE) "↻1" else "↻",
                        active = repeatMode == mode,
                        modifier = Modifier.weight(1f),
                        action = { vm.setRepeatMode(mode) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            ToggleSettingsRow("Shuffle", "Randomize the next track in the active queue", shuffle) { vm.toggleShuffle() }

            Text("SLEEP TIMER", color = White.copy(.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 15, 30, 60, 90).forEach { minutes ->
                    val active = if (minutes == 0) sleepRemaining == 0L else {
                        val remainingMinutes = ((sleepRemaining + 59_999L) / 60_000L).toInt()
                        remainingMinutes in (minutes - 1)..minutes
                    }
                    Surface(
                        onClick = { vm.setSleepTimerMinutes(minutes) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) Cyan.copy(.16f) else White.copy(.04f),
                        contentColor = if (active) Cyan else White.copy(.55f),
                        modifier = Modifier.weight(1f).border(1.dp, if (active) Cyan.copy(.30f) else White.copy(.05f), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            if (minutes == 0) "OFF" else "$minutes",
                            textAlign = TextAlign.Center,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            ToggleSettingsRow(
                "Queue persistence",
                "Remember the active queue and restore its order after reopening Prismora.",
                queuePersistence
            ) { vm.setQueuePersistenceEnabled(!queuePersistence) }

            Surface(
                onClick = onOpenQueue,
                shape = RoundedCornerShape(18.dp),
                color = Cyan.copy(.11f),
                contentColor = White,
                modifier = Modifier.fillMaxWidth().border(1.dp, Cyan.copy(.28f), RoundedCornerShape(18.dp))
            ) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Queue + reorder", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Open the queue. Use ↑ / ↓ to move tracks.", color = White.copy(.45f), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Text("OPEN", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(8.dp))
            InfoSettingsRow(
                "Gapless",
                "Visible here with the other playback controls. True sample-continuous gapless will activate with the native decoder/audio-engine rewrite."
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QueueSheet(queue: List<Track>, current: Track?, vm: PlayerViewModel, onDismiss: () -> Unit, onSelect: (Track) -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(.46f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Brush.verticalGradient(listOf(White.copy(.08f), Glass, Ink)))
                .border(1.dp, White.copy(.1f), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount -> total += dragAmount },
                        onDragEnd = {
                            if (total > 80f) onDismiss()
                            total = 0f
                        }
                    )
                }
                .clickable(enabled = false) {}
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(54.dp).height(5.dp)
                    .clip(CircleShape).background(White.copy(.18f))
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("QUEUE", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Upcoming tracks", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                }
                SmallGlassButton("CLOSE", onDismiss)
            }
            if (queue.isEmpty()) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("Queue is empty", color = White, fontWeight = FontWeight.Bold)
                    Text("Play something from your library to build the queue.", color = White.copy(.52f), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 7.dp))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(queue, key = { _, track -> track.id }) { index, track ->
                        Column {
                            TrackRow(track, selected = track.id == current?.id, vm = vm, onClick = { onSelect(track) })
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                QueueMoveButton("↑", enabled = index > 0) { vm.moveQueueItem(track.id, -1) }
                                Spacer(Modifier.width(6.dp))
                                QueueMoveButton("↓", enabled = index < queue.lastIndex) { vm.moveQueueItem(track.id, 1) }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
            }
        }
    }
}

@Composable
private fun QueueMoveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) Cyan.copy(.10f) else White.copy(.025f),
        contentColor = if (enabled) Cyan else White.copy(.20f),
        modifier = Modifier.size(width = 38.dp, height = 28.dp)
            .border(1.dp, if (enabled) Cyan.copy(.22f) else White.copy(.04f), RoundedCornerShape(10.dp))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TrackMetadataDialog(track: Track, status: OutputStatus, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Surface(onClick = onDismiss, shape = RoundedCornerShape(14.dp), color = Cyan, contentColor = Ink) {
                Text("Close", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
            }
        },
        title = { Text("Track info", color = White, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Title", track.title)
                InfoRow("Artist", track.artist)
                InfoRow("Album", track.album.ifBlank { "Unknown Album" })
                InfoRow("Format", track.extension.uppercase().ifBlank { track.mime ?: "AUDIO" })
                InfoRow("Duration", formatTime(track.durationMs))
                InfoRow("Track no.", track.trackNumber.takeIf { it > 0 }?.toString() ?: "—")
                InfoRow("Folder", track.folderPath.ifBlank { "Unavailable" })
                InfoRow("Output", status.deviceName)
                InfoRow("Playback path", if (status.connected) "${status.sampleRate} Hz • ${status.bits}-bit • ${status.format.ifBlank { "PCM" }}" else "Not playing")
            }
        },
        containerColor = DeepBlue,
        textContentColor = White,
        titleContentColor = White
    )
}

@Composable
private fun TrackBrowseDialog(
    track: Track,
    onDismiss: () -> Unit,
    onChoose: (LibraryView, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            Surface(onClick = onDismiss, shape = RoundedCornerShape(14.dp), color = White.copy(.08f), contentColor = White) {
                Text("Cancel", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
            }
        },
        title = { Text("Open in library", color = White, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                BrowseChoice("▣", "Go to folder", track.folderPath.ifBlank { "Unknown folder" }) {
                    onChoose(LibraryView.FOLDERS, track.folderPath.cleanFolder())
                }
                BrowseChoice("♬", "Go to artist", track.artist.ifBlank { "Unknown Artist" }) {
                    onChoose(LibraryView.ARTISTS, track.artist.ifBlank { "Unknown Artist" })
                }
                BrowseChoice("◫", "Go to album", track.album.ifBlank { "Unknown Album" }) {
                    onChoose(LibraryView.ALBUMS, track.album.ifBlank { "Unknown Album" })
                }
            }
        },
        containerColor = DeepBlue,
        textContentColor = White,
        titleContentColor = White
    )
}

@Composable
private fun BrowseChoice(symbol: String, title: String, subtitle: String, onClick: () -> Unit) {
    val p3r = LocalThemePreset.current.isP3rLike
    val shape = if (p3r) CutCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 0.dp, bottomStart = 16.dp) else RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (p3r) DeepBlue.copy(.84f) else Glass)
            .border(1.dp, if (p3r) Cyan.copy(.56f) else White.copy(.07f), shape).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = Cyan, fontSize = 19.sp, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = White.copy(.42f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = Cyan.copy(.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(value, color = White.copy(.82f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun LibraryPage(
    tracks: List<Track>,
    current: Track?,
    search: String,
    onPickFolder: () -> Unit,
    vm: PlayerViewModel,
    destination: LibraryDestination?
) {
    var view by rememberSaveable { mutableStateOf(LibraryView.TRACKS) }
    var layout by rememberSaveable { mutableStateOf(LibraryLayout.LIST) }
    var currentFolder by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = remember(tracks, search) {
        if (search.isBlank()) tracks else tracks.filter {
            it.title.contains(search, true) || it.artist.contains(search, true) ||
                it.album.contains(search, true) || it.folderPath.contains(search, true)
        }
    }
    LaunchedEffect(destination?.token) {
        destination?.let {
            view = it.view
            currentFolder = if (it.view == LibraryView.FOLDERS) it.value else ""
            selectedGroup = if (it.view == LibraryView.ARTISTS || it.view == LibraryView.ALBUMS) it.value else null
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR LIBRARY", color = White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallGlassButton(if (layout == LibraryLayout.LIST) "GRID" else "LIST") {
                    layout = if (layout == LibraryLayout.LIST) LibraryLayout.GRID else LibraryLayout.LIST
                }
                SmallGlassButton("RESCAN", vm::scanMediaStore)
                SmallGlassButton("+ FOLDER", onPickFolder)
            }
        }
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = search,
            onValueChange = vm::setSearch,
            singleLine = true,
            textStyle = TextStyle(color = White, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Glass)
                .border(1.dp, White.copy(.08f), RoundedCornerShape(20.dp)).padding(horizontal = 15.dp, vertical = 13.dp),
            decorationBox = { field ->
                Box { if (search.isBlank()) Text("Search track, artist, album or folder…", color = White.copy(.32f), fontSize = 14.sp); field() }
            }
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LibraryView.entries.forEach { option ->
                LibraryModeButton(option.label, option == view, Modifier.weight(1f)) {
                    view = option
                    currentFolder = ""
                    selectedGroup = null
                }
            }
        }
        Text("${filtered.size} TRACKS", color = Cyan.copy(.62f), fontSize = 10.sp, letterSpacing = 1.8.sp, modifier = Modifier.padding(bottom = 9.dp))
        if (filtered.isEmpty()) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Text(if (tracks.isEmpty()) "No music yet" else "Nothing matches your search", color = White, fontWeight = FontWeight.Bold)
                Text("Tap + FOLDER and choose the folder where your FLAC, WAV, MP3, M4A or DSF files are stored.", color = White.copy(.52f), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            when (view) {
                LibraryView.TRACKS -> TrackList(filtered, current, layout, vm) { vm.selectFromList(it, filtered) }
                LibraryView.FOLDERS -> FolderBrowser(filtered, current, layout, currentFolder, { currentFolder = it }, vm) { vm.selectFromList(it, filtered.filter { tr -> tr.folderPath.cleanFolder() == currentFolder.cleanFolder() }) }
                LibraryView.ARTISTS -> GroupBrowser(
                    groups = filtered.groupBy { it.artist.ifBlank { "Unknown Artist" } }.toList().sortedBy { it.first.lowercase() },
                    selected = selectedGroup,
                    onSelected = { selectedGroup = it },
                    layout = layout,
                    current = current,
                    vm = vm,
                    onSelect = { group, track -> vm.selectFromList(track, group) }
                )
                LibraryView.ALBUMS -> GroupBrowser(
                    groups = filtered.groupBy { it.album.ifBlank { "Unknown Album" } }.toList().sortedBy { it.first.lowercase() },
                    selected = selectedGroup,
                    onSelected = { selectedGroup = it },
                    layout = layout,
                    current = current,
                    vm = vm,
                    onSelect = { group, track -> vm.selectFromList(track, group) }
                )
            }
        }
    }
}

@Composable
private fun LibraryModeButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = if (active) Cyan.copy(.15f) else Glass.copy(.68f),
        contentColor = if (active) Cyan else White.copy(.42f),
        modifier = modifier.border(1.dp, if (active) Cyan.copy(.28f) else White.copy(.05f), RoundedCornerShape(13.dp))
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun TrackList(tracks: List<Track>, current: Track?, layout: LibraryLayout, vm: PlayerViewModel, onSelect: (Track) -> Unit) {
    val sorted = remember(tracks) { tracks.sortedWith(compareBy({ it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.title.lowercase() })) }
    if (layout == LibraryLayout.LIST) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(sorted, key = { it.id }) { track ->
                TrackRow(track, selected = track.id == current?.id, vm = vm, onClick = { onSelect(track) })
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sorted, key = { it.id }) { track ->
                TrackGridItem(track, selected = track.id == current?.id, vm = vm, onClick = { onSelect(track) })
            }
        }
    }
}

@Composable
private fun FolderBrowser(
    tracks: List<Track>,
    current: Track?,
    layout: LibraryLayout,
    folder: String,
    onFolder: (String) -> Unit,
    vm: PlayerViewModel,
    onSelect: (Track) -> Unit
) {
    val childFolders = remember(tracks, folder) {
        val prefix = if (folder.isBlank()) "" else "$folder/"
        tracks.mapNotNull { track ->
            val path = track.folderPath.cleanFolder()
            if (!path.startsWith(prefix)) return@mapNotNull null
            val remainder = path.removePrefix(prefix)
            remainder.substringBefore('/').takeIf { it.isNotBlank() }
        }.distinct().sortedBy { it.lowercase() }
    }
    val directTracks = remember(tracks, folder) {
        tracks.filter { it.folderPath.cleanFolder() == folder.cleanFolder() }
            .sortedWith(compareBy({ it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.title.lowercase() }))
    }
    if (layout == LibraryLayout.LIST) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (folder.isNotBlank()) {
                item { BrowserRow("‹", "Back", folder.substringBeforeLast('/', "Library")) { onFolder(folder.substringBeforeLast('/', "")) } }
            }
            items(childFolders, key = { "folder-$folder-$it" }) { child ->
                val path = if (folder.isBlank()) child else "$folder/$child"
                val count = tracks.count {
                    val trackFolder = it.folderPath.cleanFolder()
                    trackFolder == path || trackFolder.startsWith("$path/")
                }
                val preview = tracks.firstOrNull {
                    val trackFolder = it.folderPath.cleanFolder()
                    trackFolder == path || trackFolder.startsWith("$path/")
                }
                BrowserRow("▸", child, "$count tracks", preview, vm) { onFolder(path) }
            }
            items(directTracks, key = { it.id }) { track ->
                TrackRow(track, selected = track.id == current?.id, vm = vm, onClick = { onSelect(track) })
            }
        }
    } else {
        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(directTracks, key = { it.id }) { track ->
                TrackGridItem(track, selected = track.id == current?.id, vm = vm, onClick = { onSelect(track) })
            }
        }
    }
}

@Composable
private fun GroupBrowser(
    groups: List<Pair<String, List<Track>>>,
    selected: String?,
    onSelected: (String?) -> Unit,
    layout: LibraryLayout,
    current: Track?,
    vm: PlayerViewModel,
    onSelect: (List<Track>, Track) -> Unit
) {
    val selectedTracks = remember(groups, selected) { groups.firstOrNull { it.first == selected }?.second.orEmpty() }
    if (selected == null) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(groups, key = { it.first }) { (name, tracks) ->
                BrowserRow("▸", name, "${tracks.size} tracks", tracks.firstOrNull(), vm) { onSelected(name) }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            BrowserRow("‹", "Back", selected) { onSelected(null) }
            Spacer(Modifier.height(8.dp))
            TrackList(selectedTracks, current, layout, vm) { onSelect(selectedTracks, it) }
        }
    }
}

@Composable
private fun BrowserRow(
    symbol: String,
    title: String,
    subtitle: String,
    artworkTrack: Track? = null,
    vm: PlayerViewModel? = null,
    onClick: () -> Unit
) {
    val bitmap = if (artworkTrack != null && vm != null) rememberTrackArtwork(artworkTrack, vm, 128) else null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Glass.copy(alpha = .58f))
            .border(1.dp, White.copy(.05f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Cyan.copy(.24f), Pink.copy(.16f)))),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(symbol, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = White.copy(.42f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrackRow(track: Track, selected: Boolean, vm: PlayerViewModel, onClick: () -> Unit) {
    val bitmap = rememberTrackArtwork(track, vm, 128)
    val p3r = LocalThemePreset.current.isP3rLike
    val rowShape = if (p3r) CutCornerShape(topStart = 0.dp, topEnd = 20.dp, bottomEnd = 0.dp, bottomStart = 20.dp) else RoundedCornerShape(20.dp)
    val selectedContainer = if (p3r) White.copy(.96f) else Cyan.copy(.11f)
    val normalContainer = if (p3r) DeepBlue.copy(.86f) else Glass.copy(alpha = .58f)
    val selectedBorder = if (p3r) Cyan.copy(.78f) else Cyan.copy(.32f)
    val normalBorder = if (p3r) Cyan.copy(.28f) else White.copy(.05f)
    Row(
        Modifier.fillMaxWidth().clip(rowShape)
            .background(if (selected) selectedContainer else normalContainer)
            .border(1.dp, if (selected) selectedBorder else normalBorder, rowShape)
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (p3r) {
            Box(
                Modifier.width(if (selected) 6.dp else 3.dp).height(44.dp)
                    .background(if (selected) Pink else White.copy(.10f))
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier.size(43.dp).clip(if (p3r) CutCornerShape(12.dp) else RoundedCornerShape(14.dp))
                .background(
                    if (p3r) Brush.linearGradient(listOf(White.copy(.28f), Cyan.copy(.28f), Blue.copy(.52f)))
                    else Brush.linearGradient(listOf(Cyan.copy(.26f), Pink.copy(.18f)))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    if (track.trackNumber > 0) track.trackNumber.toString() else "♪",
                    color = if (p3r && selected) DeepBlue else Cyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (p3r && selected) DeepBlue else White,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                color = if (p3r && selected) Color(0xFF0B7ED2) else if (p3r) Cyan.copy(.72f) else White.copy(.46f),
                fontSize = 11.sp,
                fontWeight = if (p3r && selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (selected) {
                Surface(
                    shape = if (p3r) CutCornerShape(8.dp) else RoundedCornerShape(9.dp),
                    color = if (p3r) DeepBlue else Cyan.copy(.18f),
                    contentColor = if (p3r) White else Cyan,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        if (p3r) "PLAYING" else "NOW",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = .8.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                track.extension.uppercase().ifBlank { "AUDIO" },
                color = if (p3r && selected) DeepBlue else if (p3r) White.copy(.76f) else Cyan.copy(.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                formatTime(track.durationMs),
                color = if (p3r && selected) DeepBlue.copy(.62f) else if (p3r) White.copy(.42f) else White.copy(.3f),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun TrackGridItem(track: Track, selected: Boolean, vm: PlayerViewModel, onClick: () -> Unit) {
    val bitmap = rememberTrackArtwork(track, vm, 384)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(if (selected) Cyan.copy(.11f) else Glass.copy(alpha = .62f))
            .border(1.dp, if (selected) Cyan.copy(.32f) else White.copy(.05f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(10.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Cyan.copy(.22f), Pink.copy(.14f)))),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(if (track.trackNumber > 0) track.trackNumber.toString() else "♪", color = Cyan, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(track.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, color = White.copy(.55f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LyricsPage(plain: String?, lines: List<LyricLine>, vm: PlayerViewModel) {
    val position by vm.positionMs.collectAsStateWithLifecycle()
    val currentIndex = lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex, lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem((currentIndex - 3).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize()) {
        Text("LYRICS SYNC", color = Pink, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
        Text("Words in motion", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
        GlassPanel(Modifier.fillMaxWidth().weight(1f)) {
            when {
                lines.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(lines.size) { index ->
                        Text(
                            lines[index].text,
                            color = if (index == currentIndex) Cyan else White.copy(if (index < currentIndex) .28f else .55f),
                            fontSize = if (index == currentIndex) 22.sp else 17.sp,
                            lineHeight = 27.sp,
                            fontWeight = if (index == currentIndex) FontWeight.ExtraBold else FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { vm.seekTo(lines[index].timeMs) }.padding(vertical = 3.dp)
                        )
                    }
                }
                !plain.isNullOrBlank() -> LazyColumn(Modifier.fillMaxSize()) { item { Text(plain, color = White.copy(.72f), fontSize = 16.sp, lineHeight = 25.sp) } }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Lyrics will appear automatically\nwhen a match is found.", color = White.copy(.42f), fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Text("Powered by LRCLIB • cached for offline use", color = White.copy(.25f), fontSize = 9.sp, modifier = Modifier.padding(12.dp).align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun EqualizerPage(vm: PlayerViewModel) {
    val settings by vm.dspSettings.collectAsStateWithLifecycle()
    val profiles by vm.deviceDspProfiles.collectAsStateWithLifecycle()
    val autoProfile by vm.autoDeviceDspProfile.collectAsStateWithLifecycle()
    val selectedOutput by vm.selectedOutputId.collectAsStateWithLifecycle()
    val outputs by vm.audioOutputs.collectAsStateWithLifecycle()
    var importText by rememberSaveable { mutableStateOf("") }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    val deviceName = outputs.firstOrNull { it.id == selectedOutput }?.name

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PRISMORA PCM DSP", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
                Text("Equalizer", color = White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Surface(
                onClick = { vm.setDspEnabled(!settings.enabled) },
                shape = RoundedCornerShape(16.dp),
                color = if (settings.enabled) Cyan else White.copy(.06f),
                contentColor = if (settings.enabled) Ink else White,
                modifier = Modifier.border(1.dp, if (settings.enabled) Cyan else White.copy(.10f), RoundedCornerShape(16.dp))
            ) {
                Text(if (settings.enabled) "DSP ON" else "BYPASS", fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (settings.enabled) "DSP ACTIVE" else "BIT-PERFECT CAPABLE", color = if (settings.enabled) Pink else Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                            Text(
                                if (settings.enabled) "Processing at source sample rate" else "Enable DSP to apply EQ and dynamics",
                                color = White.copy(.52f), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Text("${settings.bands.count { it.enabled }} BANDS", color = White.copy(.46f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    EqualizerResponseGraph(settings, Modifier.fillMaxWidth().height(138.dp).padding(top = 10.dp))
                }
            }
            item {
                Text("PRESETS", color = White.copy(.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 3.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    vm.dspPresets.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { preset -> PresetChip(preset.name, Modifier.weight(1f)) { vm.applyDspPreset(preset) } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item {
                SliderSetting("Preamp", settings.preampDb, -24f..12f, "${dspNumber(settings.preampDb)} dB • lower it when boosting several bands", vm::setDspPreamp)
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("PARAMETRIC BANDS", color = White.copy(.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                    SmallGlassButton("+ BAND", vm::addEqBand)
                }
            }
            items(settings.bands, key = { it.id }) { band ->
                EqBandEditor(band, vm)
            }
            item {
                Text("DYNAMICS", color = White.copy(.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 6.dp))
                ToggleSettingsRow("Compressor", "Controls peaks above the threshold", settings.compressorEnabled) { vm.setCompressorEnabled(!settings.compressorEnabled) }
                if (settings.compressorEnabled) {
                    SliderSetting("Threshold", settings.compressorThresholdDb, -60f..0f, "${dspNumber(settings.compressorThresholdDb)} dB", vm::setCompressorThreshold)
                    SliderSetting("Ratio", settings.compressorRatio, 1f..20f, "${dspNumber(settings.compressorRatio)}:1", vm::setCompressorRatio)
                    SliderSetting("Attack", settings.compressorAttackMs, .1f..250f, "${dspNumber(settings.compressorAttackMs)} ms", vm::setCompressorAttack)
                    SliderSetting("Release", settings.compressorReleaseMs, 10f..2_000f, "${dspNumber(settings.compressorReleaseMs)} ms", vm::setCompressorRelease)
                }
                ToggleSettingsRow("Limiter", "Prevents PCM clipping after EQ and preamp", settings.limiterEnabled) { vm.setLimiterEnabled(!settings.limiterEnabled) }
                if (settings.limiterEnabled) {
                    SliderSetting("Limiter ceiling", settings.limiterCeilingDb, -12f..0f, "${dspNumber(settings.limiterCeilingDb)} dBFS", vm::setLimiterCeiling)
                }
                SliderSetting("Crossfeed", settings.crossfeed, 0f..0.65f, "Natural speaker-like channel blend", vm::setCrossfeed)
            }
            item {
                Text("AUTOEQ / EQUALIZER APO", color = White.copy(.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetChip("IMPORT", Modifier.weight(1f)) { showImport = true }
                    PresetChip("EXPORT", Modifier.weight(1f)) { showExport = true }
                    PresetChip("RESET", Modifier.weight(1f), vm::resetDsp)
                }
            }
            item {
                Text("DEVICE PROFILE", color = White.copy(.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 6.dp))
                ToggleSettingsRow("Automatic device profile", "Load a saved DSP setup when its output is selected", autoProfile) { vm.setAutoDeviceDspProfile(!autoProfile) }
                InfoSettingsRow("Current output", deviceName ?: "No output selected")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetChip("SAVE PROFILE", Modifier.weight(1f), vm::saveDspProfileForCurrentDevice)
                    if (deviceName != null && profiles.containsKey(deviceName)) {
                        PresetChip("REMOVE", Modifier.weight(1f), vm::deleteDspProfileForCurrentDevice)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import AutoEQ / APO", color = White, fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("Paste Equalizer APO text containing Preamp and Filter lines.", color = White.copy(.56f), fontSize = 11.sp, lineHeight = 17.sp)
                    BasicTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        textStyle = TextStyle(color = White, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth().height(210.dp).padding(top = 10.dp)
                            .clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(.28f)).padding(12.dp)
                    )
                }
            },
            confirmButton = {
                SmallGlassButton("IMPORT") {
                    if (vm.importEqualizerApo(importText)) showImport = false
                }
            },
            dismissButton = { SmallGlassButton("CANCEL") { showImport = false } },
            containerColor = DeepBlue
        )
    }
    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("Equalizer APO export", color = White, fontWeight = FontWeight.Black) },
            text = {
                BasicTextField(
                    value = vm.exportEqualizerApo(),
                    onValueChange = {},
                    readOnly = true,
                    textStyle = TextStyle(color = White.copy(.80f), fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(.28f)).padding(12.dp)
                )
            },
            confirmButton = { SmallGlassButton("CLOSE") { showExport = false } },
            containerColor = DeepBlue
        )
    }
}

@Composable
private fun PresetChip(label: String, modifier: Modifier = Modifier, action: () -> Unit) {
    Surface(
        onClick = action,
        shape = RoundedCornerShape(13.dp),
        color = Cyan.copy(.10f),
        contentColor = Cyan,
        modifier = modifier.border(1.dp, Cyan.copy(.24f), RoundedCornerShape(13.dp))
    ) {
        Text(label, textAlign = TextAlign.Center, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp))
    }
}

@Composable
private fun EqBandEditor(band: EqBand, vm: PlayerViewModel) {
    var expanded by rememberSaveable(band.id) { mutableStateOf(false) }
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = { vm.updateEqBand(band.copy(enabled = !band.enabled)) },
                shape = RoundedCornerShape(10.dp),
                color = if (band.enabled) Cyan.copy(.18f) else White.copy(.05f),
                contentColor = if (band.enabled) Cyan else White.copy(.40f)
            ) { Text(if (band.enabled) "ON" else "OFF", fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Band ${band.id} • ${band.type.label}", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${formatFrequency(band.frequencyHz)}  ${signedDb(band.gainDb)}  Q ${dspNumber(band.q)}", color = Cyan.copy(.70f), fontSize = 10.sp)
            }
            SmallGlassButton(if (expanded) "DONE" else "EDIT") { expanded = !expanded }
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                EqFilterType.entries.forEach { type ->
                    val selected = type == band.type
                    Surface(
                        onClick = { vm.updateEqBand(band.copy(type = type)) },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selected) Cyan.copy(.16f) else White.copy(.04f),
                        contentColor = if (selected) Cyan else White.copy(.44f),
                        modifier = Modifier.weight(1f)
                    ) { Text(type.apoCode, textAlign = TextAlign.Center, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 7.dp)) }
                }
            }
            val frequencyPosition = ((log10(band.frequencyHz.coerceIn(20f, 20_000f)) - log10(20f)) / (log10(20_000f) - log10(20f))).coerceIn(0f, 1f)
            Text("Frequency • ${formatFrequency(band.frequencyHz)}", color = White.copy(.62f), fontSize = 10.sp, modifier = Modifier.padding(top = 11.dp))
            Slider(
                value = frequencyPosition,
                onValueChange = { position -> vm.updateEqBand(band.copy(frequencyHz = 20f * 1000f.pow(position))) },
                colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = White.copy(.10f))
            )
            if (band.type != EqFilterType.LOW_PASS && band.type != EqFilterType.HIGH_PASS) {
                Text("Gain • ${signedDb(band.gainDb)}", color = White.copy(.62f), fontSize = 10.sp)
                Slider(
                    value = band.gainDb.coerceIn(-18f, 18f), valueRange = -18f..18f,
                    onValueChange = { vm.updateEqBand(band.copy(gainDb = it)) },
                    colors = SliderDefaults.colors(thumbColor = Pink, activeTrackColor = Pink, inactiveTrackColor = White.copy(.10f))
                )
            }
            Text("Q • ${dspNumber(band.q)}", color = White.copy(.62f), fontSize = 10.sp)
            Slider(
                value = band.q.coerceIn(.1f, 10f), valueRange = .1f..10f,
                onValueChange = { vm.updateEqBand(band.copy(q = it)) },
                colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = White.copy(.10f))
            )
            if (band.id == vm.dspSettings.value.bands.lastOrNull()?.id) {
                PresetChip("REMOVE BAND", Modifier.fillMaxWidth()) { vm.removeEqBand(band.id) }
            }
        }
    }
}

@Composable
private fun EqualizerResponseGraph(settings: DspSettings, modifier: Modifier = Modifier) {
    val accent = Cyan
    val secondary = Pink
    val grid = White
    Canvas(modifier) {
        repeat(7) { index ->
            val y = size.height * index / 6f
            drawLine(grid.copy(if (index == 3) .18f else .07f), Offset(0f, y), Offset(size.width, y), if (index == 3) 1.5.dp.toPx() else 1.dp.toPx())
        }
        repeat(10) { index ->
            val x = size.width * index / 9f
            drawLine(grid.copy(.05f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
        val path = Path()
        for (point in 0..160) {
            val position = point / 160f
            val frequency = 20f * 1000f.pow(position)
            var db = settings.preampDb
            settings.bands.filter { it.enabled }.forEach { band -> db += approximateBandDb(band, frequency) }
            val y = size.height * (1f - ((db.coerceIn(-18f, 18f) + 18f) / 36f))
            val x = size.width * position
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Brush.horizontalGradient(listOf(secondary, accent)), style = androidx.compose.ui.graphics.drawscope.Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun approximateBandDb(band: EqBand, frequency: Float): Float {
    val distance = kotlin.math.abs(log10(frequency / band.frequencyHz.coerceAtLeast(1f)))
    return when (band.type) {
        EqFilterType.PEAK -> band.gainDb * kotlin.math.exp(-distance * distance * band.q * band.q * 5f)
        EqFilterType.LOW_SHELF -> band.gainDb / (1f + (frequency / band.frequencyHz).pow(2f * band.q))
        EqFilterType.HIGH_SHELF -> band.gainDb / (1f + (band.frequencyHz / frequency).pow(2f * band.q))
        EqFilterType.LOW_PASS -> if (frequency <= band.frequencyHz) 0f else (-12f * log10(frequency / band.frequencyHz)).coerceAtLeast(-24f)
        EqFilterType.HIGH_PASS -> if (frequency >= band.frequencyHz) 0f else (-12f * log10(band.frequencyHz / frequency)).coerceAtLeast(-24f)
    }
}

private fun formatFrequency(value: Float): String = if (value >= 1_000f) "${dspNumber(value / 1_000f)} kHz" else "${value.toInt()} Hz"
private fun signedDb(value: Float): String = "${if (value >= 0f) "+" else ""}${dspNumber(value)} dB"
private fun dspNumber(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)

@Composable
private fun SettingsPage(
    selectedTheme: ThemePreset,
    onThemeSelected: (ThemePreset) -> Unit,
    outputs: List<AudioOutput>,
    selectedOutput: Int?,
    status: OutputStatus,
    routingMode: AudioRoutingMode,
    fallbackEnabled: Boolean,
    vm: PlayerViewModel,
    initialSection: SettingsSection,
    onBack: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(initialSection) }
    LaunchedEffect(initialSection) { section = initialSection }

    when (section) {
        SettingsSection.HOME -> SettingsHome(
            onBack = onBack,
            open = { section = it }
        )
        SettingsSection.APPEARANCE -> AppearanceSettingsPage(
            selectedTheme = selectedTheme,
            onThemeSelected = onThemeSelected,
            onBack = { section = SettingsSection.HOME }
        )
        SettingsSection.AUDIO -> AudioSettingsPage(
            outputs = outputs,
            selectedId = selectedOutput,
            status = status,
            routingMode = routingMode,
            fallbackEnabled = fallbackEnabled,
            vm = vm,
            onBack = { section = SettingsSection.HOME }
        )
        SettingsSection.LIBRARY -> LibrarySettingsPage(vm) { section = SettingsSection.HOME }
        SettingsSection.PLAYBACK -> PlaybackSettingsPage(vm) { section = SettingsSection.HOME }
        SettingsSection.VISUALIZER -> VisualizerSettingsPage(vm) { section = SettingsSection.HOME }
        SettingsSection.PERFORMANCE -> PerformanceSettingsPage(vm) { section = SettingsSection.HOME }
        SettingsSection.ADVANCED -> AdvancedSettingsPage { section = SettingsSection.HOME }
    }
}

@Composable
private fun SettingsHome(onBack: () -> Unit, open: (SettingsSection) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SettingsTitle("Settings", onBack)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { SettingsGroupLabel("CUSTOMIZATION") }
            item { BrowseChoice("✦", "Appearance", "Themes, colors and Now Playing look") { open(SettingsSection.APPEARANCE) } }
            item { BrowseChoice("♫", "Audio", "Routing, bit-perfect USB, DAC and DSP status") { open(SettingsSection.AUDIO) } }
            item { SettingsGroupLabel("MUSIC") }
            item { BrowseChoice("≡", "Library", "Scanning, cache, playlists and organization") { open(SettingsSection.LIBRARY) } }
            item { BrowseChoice("▶", "Playback", "Repeat, shuffle, queue and playback behavior") { open(SettingsSection.PLAYBACK) } }
            item { BrowseChoice("▥", "Visualizer", "Bars, Line, Radial, Minimal and tuning") { open(SettingsSection.VISUALIZER) } }
            item { SettingsGroupLabel("SYSTEM") }
            item { BrowseChoice("⚡", "Performance", "Lite Mode and rendering performance") { open(SettingsSection.PERFORMANCE) } }
            item { BrowseChoice("◇", "Advanced", "Planned DSP, customization, widgets and smart playlists") { open(SettingsSection.ADVANCED) } }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun SettingsGroupLabel(text: String) {
    Text(text, color = White.copy(.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, modifier = Modifier.padding(start = 4.dp, top = 7.dp))
}

@Composable
private fun SettingsTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = Glass,
            contentColor = White,
            modifier = Modifier.size(42.dp).border(1.dp, White.copy(.08f), CircleShape)
        ) { Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 27.sp, fontWeight = FontWeight.Bold) } }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("PRISMORA", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
            Text(title, color = White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AppearanceSettingsPage(
    selectedTheme: ThemePreset,
    onThemeSelected: (ThemePreset) -> Unit,
    onBack: () -> Unit
) {
    var family by rememberSaveable(selectedTheme.id) { mutableStateOf(selectedTheme.family) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Glass,
                contentColor = White,
                modifier = Modifier.size(42.dp).border(1.dp, White.copy(.08f), CircleShape)
            ) { Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 27.sp, fontWeight = FontWeight.Bold) } }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("SETTINGS", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
                Text("Appearance", color = White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("THEME FAMILY", color = White.copy(.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ThemeFamily.entries.forEach { item ->
                    val active = family == item
                    Surface(
                        onClick = { family = item },
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) Cyan.copy(.16f) else White.copy(.035f),
                        contentColor = if (active) Cyan else White.copy(.52f),
                        modifier = Modifier.weight(1f).border(1.dp, if (active) Cyan.copy(.34f) else White.copy(.06f), RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            item.label,
                            textAlign = TextAlign.Center,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when (family) {
                ThemeFamily.PRISMORA -> "PRISMORA CORE THEMES"
                ThemeFamily.VOCAL_SYNTH -> "VOCALOID / SYNTH / PRODUCER THEMES"
                ThemeFamily.PERSONA -> "GAME MENU THEMES"
                ThemeFamily.COVER_ART -> "DYNAMIC THEMES FROM THE CURRENT COVER"
                ThemeFamily.RETRO -> "RETRO / FUTURE PLAYER THEMES"
            },
            color = White.copy(.38f),
            fontSize = 9.sp,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(ThemePreset.entries.filter { it.family == family }, key = { it.id }) { preset ->
                ThemeChoiceCard(
                    preset = preset,
                    selected = selectedTheme == preset,
                    onClick = { onThemeSelected(preset) }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        Text(
            "Theme changes are saved automatically.",
            color = White.copy(.25f),
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun ThemeChoiceCard(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val c = preset.colors
    val logoRes = themeLogoRes(preset)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(if (preset == ThemePreset.P5R) 8.dp else 22.dp))
            .background(Brush.linearGradient(listOf(c.deep, c.ink, c.accent.copy(.11f))))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) c.accent.copy(.8f) else c.white.copy(.08f),
                RoundedCornerShape(if (preset == ThemePreset.P5R) 8.dp else 22.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(if (preset.family == ThemeFamily.PERSONA) 10.dp else 18.dp))
                .background(Brush.radialGradient(listOf(c.accent.copy(.42f), c.secondary.copy(.14f), c.ink)))
                .border(1.dp, c.accent.copy(.3f), RoundedCornerShape(if (preset.family == ThemeFamily.PERSONA) 10.dp else 18.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (logoRes != null) {
                Image(
                    painter = painterResource(logoRes),
                    contentDescription = preset.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                )
            } else {
                Text(
                    preset.shortMark,
                    color = c.white,
                    fontSize = if (preset.shortMark.length > 6) 8.sp else 13.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 11.sp,
                    modifier = Modifier.padding(5.dp)
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(preset.displayName, color = c.white, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (preset) {
                    ThemePreset.PRISMORA_GLASS -> "Original cyan/pink liquid-glass interface"
                    ThemePreset.PRISMORA_OLED -> "True-black OLED version of Prismora Glass"
                    ThemePreset.MIKU -> "Cyan / pink liquid glass"
                    ThemePreset.TETO -> "Red-pink synthwave stripes"
                    ThemePreset.NERU -> "Yellow-black energetic menu"
                    ThemePreset.YI_XI -> "Neon cutout theme"
                    ThemePreset.KAITO -> "Cold blue glass"
                    ThemePreset.MEIKO -> "Deep red stage glass"
                    ThemePreset.INABAKUMORI -> "Muted monochrome cloud / rail aesthetic"
                    ThemePreset.INSOMNIA -> "Midnight violet dreamscape"
                    ThemePreset.COVER_WAVE -> "OLED black with a cover-colored PS-style wave"
                    ThemePreset.COVER_BACKGROUND -> "Current cover behind the interface with dynamic colors"
                    ThemePreset.P3R -> "Reload menu: sharp white/blue panels, red accents, clock + glass shards"
                    ThemePreset.P4G -> "Golden halftone TV-menu energy"
                    ThemePreset.P5R -> "Red-black sharp Royal-style panels"
                    ThemePreset.CYBERPUNK -> "Neon grid, cyan and hazard yellow"
                    ThemePreset.CRT -> "Green phosphor terminal and scanlines"
                    ThemePreset.Y2K -> "Chrome-like pastel digital player"
                    ThemePreset.VAPORWAVE -> "Pink/cyan sunset grid aesthetic"
                    ThemePreset.CASSETTE -> "Sony-inspired blue personal-stereo hardware UI; hold Prev/Next for rewind / fast-forward"
                    ThemePreset.PAPER -> "Bright paper UI: flat cards, ink-like typography, no glass"
                    ThemePreset.SAKURA -> "Light pink pop-player with solid candy panels"
                    ThemePreset.BAUHAUS -> "Cream/red/blue geometric hi-fi interface"
                    ThemePreset.BRUTALIST -> "Extreme neo-brutalist blocks, oversized type and raw controls"
                    ThemePreset.MINIDISC -> "Compact silver-blue MiniDisc styling"
                    ThemePreset.WALKMAN -> "Portable player with orange hardware accents"
                    ThemePreset.P3R_2 -> "Deeper Persona 3-style menu flow with track list selector and sharp diagonal panels"
                    ThemePreset.WALKMAN_DECK -> "TPS-L2-inspired cassette deck with tape-progress feel"
                    ThemePreset.WALKMAN_SPORTS -> "Bright Walkman Sports look with retro hardware energy"
                    ThemePreset.MINIDISC_SILVER -> "Clean light-silver MiniDisc edition"
                    ThemePreset.DISCMAN -> "Portable CD player styling with disc-inspired backdrop"
                    ThemePreset.IPOD_LIGHT -> "Bright click-wheel-inspired minimalist player"
                    ThemePreset.IOS_LIGHT -> "Modern light mobile-player cards and soft UI"
                    ThemePreset.MEDIA_PLAYER -> "Desktop media-player vibe with blue-gray panels"
                    ThemePreset.PRISMORA_OS -> "Fake Prismora operating system with windowed UI feel"
                },
                color = c.white.copy(.48f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (selected) {
            Text("✓", color = c.accent, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun AudioSettingsPage(
    outputs: List<AudioOutput>,
    selectedId: Int?,
    status: OutputStatus,
    routingMode: AudioRoutingMode,
    fallbackEnabled: Boolean,
    vm: PlayerViewModel,
    onBack: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Glass,
                    contentColor = White,
                    modifier = Modifier.size(42.dp).border(1.dp, White.copy(.08f), CircleShape)
                ) { Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 27.sp, fontWeight = FontWeight.Bold) } }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("SETTINGS / AUDIO", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                Text("Audio routing", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            }
            SmallGlassButton("REFRESH", vm::refreshOutputs)
        }

        Spacer(Modifier.height(16.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Text(
                if (status.connected) "CURRENT ROUTE" else "ROUTE STATUS",
                color = if (status.connected) Cyan else Pink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
            Text(status.deviceName, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            if (status.connected) {
                Text(
                    "${status.sampleRate} Hz  •  ${status.bits}-bit  •  ${status.format.ifBlank { "PCM" }}",
                    color = White.copy(.64f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text("Nothing is playing right now", color = White.copy(.52f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (status.backend.contains("DSP")) Pink.copy(.18f) else if (status.bitPerfect) Cyan.copy(.18f) else White.copy(.05f),
                    contentColor = if (status.backend.contains("DSP")) Pink else if (status.bitPerfect) Cyan else White.copy(.62f)
                ) {
                    Text(
                        when {
                            status.backend.contains("DSP") -> "DSP ACTIVE"
                            status.bitPerfect -> "BIT-PERFECT MIXER"
                            else -> "COMPATIBILITY"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
                if (status.backend.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = White.copy(.05f),
                        contentColor = White.copy(.68f)
                    ) {
                        Text(status.backend, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                    }
                }
            }
            Text(status.note, color = White.copy(.4f), fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Text("ROUTING MODE", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))

        AudioRoutingChoice(
            title = "Auto",
            subtitle = "USB: exact Android bit-perfect mixer when available. Everything else uses the compatibility path.",
            active = routingMode == AudioRoutingMode.AUTO
        ) { vm.setAudioRoutingMode(AudioRoutingMode.AUTO) }

        Spacer(Modifier.height(8.dp))
        AudioRoutingChoice(
            title = "Native bit-perfect",
            subtitle = "USB only. Android 14+ must expose the exact sample rate, bit depth and channel format.",
            active = routingMode == AudioRoutingMode.NATIVE_BIT_PERFECT
        ) { vm.setAudioRoutingMode(AudioRoutingMode.NATIVE_BIT_PERFECT) }

        Spacer(Modifier.height(8.dp))
        AudioRoutingChoice(
            title = "Compatibility",
            subtitle = "AAudio route for maximum device compatibility. Used for Bluetooth, AUX, speaker and older Android versions.",
            active = routingMode == AudioRoutingMode.COMPATIBILITY
        ) { vm.setAudioRoutingMode(AudioRoutingMode.COMPATIBILITY) }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Glass.copy(.58f))
                .border(1.dp, White.copy(.06f), RoundedCornerShape(18.dp))
                .clickable { vm.setAudioFallbackEnabled(!fallbackEnabled) }
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Compatibility fallback", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "If an exact USB bit-perfect format is unavailable, continue with AAudio instead of stopping playback.",
                    color = White.copy(.46f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (fallbackEnabled) Cyan.copy(.18f) else White.copy(.05f),
                contentColor = if (fallbackEnabled) Cyan else White.copy(.45f)
            ) {
                Text(
                    if (fallbackEnabled) "ON" else "OFF",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Text("OUTPUT DEVICE", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        if (outputs.isEmpty()) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Text("No audio output detected", color = White, fontWeight = FontWeight.Bold)
                Text("Connect Bluetooth, AUX or a USB DAC, then tap Refresh.", color = White.copy(.5f), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 7.dp))
            }
        } else {
            outputs.forEach { output ->
                DeviceRow(output, selectedId == output.id) { vm.selectOutput(output.id) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("PCM DSP", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        InfoSettingsRow("Equalizer", "Open Equalizer from the bottom navigation for PEQ, AutoEQ/APO import, dynamics and device profiles.")
        InfoSettingsRow("Bit-perfect safety", "When DSP is enabled Prismora switches to the compatibility PCM path and reports DSP ACTIVE.")
        InfoSettingsRow("Planned USB controls", "Manual rate/bit-depth, buffer, latency and diagnostics remain part of the direct-UAC2 milestone.")

        Spacer(Modifier.height(12.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("HOW AUTO ROUTING WORKS", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                "USB DAC + Android 14+: Prismora first asks Android for an exact BIT_PERFECT mixer format that matches the decoded PCM. If Android exposes it, the stream is sent without mixer volume or effects.",
                color = White.copy(.52f),
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            HorizontalDivider(color = White.copy(.07f), modifier = Modifier.padding(vertical = 11.dp))
            Text(
                "Bluetooth always uses Android's codec path. AUX and the phone speaker stay on the compatibility route. AAudio is no longer labelled bit-perfect just because Exclusive mode opened successfully.",
                color = White.copy(.48f),
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
            HorizontalDivider(color = White.copy(.07f), modifier = Modifier.padding(vertical = 11.dp))
            Text(
                "Current decoder note: WAV keeps 16/24/32-bit PCM. MP3/AAC and the current MediaCodec FLAC path are decoded to 16-bit PCM. DSF is currently converted to 44.1 kHz float PCM.",
                color = Pink.copy(.76f),
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AudioRoutingChoice(
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) Cyan.copy(.10f) else Glass.copy(.55f))
            .border(1.dp, if (active) Cyan.copy(.34f) else White.copy(.05f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = active,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Cyan, unselectedColor = White.copy(.35f))
        )
        Column(Modifier.padding(start = 4.dp).weight(1f)) {
            Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = White.copy(.46f), fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun DeviceRow(output: AudioOutput, selected: Boolean, select: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Cyan.copy(.10f) else Glass.copy(alpha = .55f))
            .border(1.dp, if (selected) Cyan.copy(.3f) else White.copy(.05f), RoundedCornerShape(20.dp))
            .clickable(onClick = select).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = select, colors = RadioButtonDefaults.colors(selectedColor = Cyan, unselectedColor = White.copy(.35f)))
        Column(Modifier.padding(start = 4.dp).weight(1f)) {
            Text(output.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            val rates = output.sampleRates.take(4).joinToString(" / ").ifBlank { "Rates negotiated on play" }
            Text("${output.kind.label}  •  $rates", color = if (output.kind == AudioOutputKind.USB) Cyan.copy(.58f) else White.copy(.38f), fontSize = 10.sp)
            if (output.kind == AudioOutputKind.USB) {
                Text(
                    if (output.supportsNativeBitPerfect)
                        "Native bit-perfect: ${output.nativeBitPerfectFormats.take(2).joinToString("  |  ")}"
                    else
                        "Native bit-perfect mixer not exposed by Android for this device",
                    color = if (output.supportsNativeBitPerfect) Cyan.copy(.72f) else Pink.copy(.62f),
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LibrarySettingsPage(
    vm: PlayerViewModel,
    onBack: () -> Unit
) {
    val scan by vm.libraryScan.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val organization by vm.playlistOrganizationMode.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTitle("Library", onBack)
        Spacer(Modifier.height(16.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("LIBRARY STATUS", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
            Text("${tracks.size} tracks", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
            Text(if (scan.running) "${scan.stage} • ${scan.loaded} loaded" else "Cache ready • ${scan.stage}", color = White.copy(.48f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            SmallGlassButton("RESCAN") { vm.scanMediaStore() }
        }
        Text("PLAYLIST FILE ORGANIZATION", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        PlaylistOrganizationMode.entries.forEach { mode ->
            val desc = when (mode) {
                PlaylistOrganizationMode.REFERENCE -> "Keep one source file; playlists only reference it."
                PlaylistOrganizationMode.COPY -> "Copy existing tracks into the playlist folder."
                PlaylistOrganizationMode.ORGANIZED -> "Album tracks copy; standalone imports move; full albums stay together."
            }
            AudioRoutingChoice(mode.label, desc, organization == mode) { vm.setPlaylistOrganizationMode(mode) }
            Spacer(Modifier.height(8.dp))
        }
        Text("PLANNED IMPORTER", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
        InfoSettingsRow("Playlist Importer", "Metadata/order import, local matching, M3U8 and watched import folder")
        InfoSettingsRow("Folders / Artists / Albums / Genres", "Full hierarchy and richer sorting continue in the library roadmap")
        Text("PLANNED", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        InfoSettingsRow("Smart playlists", "Planned: High-Res, Most Played, Forgotten Favorites and custom rules")
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlaybackSettingsPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val repeat by vm.repeatMode.collectAsStateWithLifecycle()
    val shuffle by vm.shuffleEnabled.collectAsStateWithLifecycle()
    val sleep by vm.sleepRemainingMs.collectAsStateWithLifecycle()
    val queuePersistence by vm.queuePersistenceEnabled.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTitle("Playback", onBack)
        Spacer(Modifier.height(16.dp))
        Text("REPEAT", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 8.dp))
        RepeatMode.entries.forEach { mode ->
            AudioRoutingChoice(mode.label, when (mode) {
                RepeatMode.OFF -> "Stop automatically at the end of the queue."
                RepeatMode.ALL -> "Continue from the beginning after the final track."
                RepeatMode.ONE -> "Replay the current track automatically."
            }, repeat == mode) { vm.setRepeatMode(mode) }
            Spacer(Modifier.height(8.dp))
        }
        ToggleSettingsRow("Shuffle", "Randomize the next track in the active queue", shuffle) { vm.toggleShuffle() }
        ToggleSettingsRow("Sleep timer", if (sleep > 0L) "Active: ${formatRemaining(sleep)}" else "Tap to cycle 15 / 30 / 60 / 90 minutes", sleep > 0L) { vm.cycleSleepTimer() }
        ToggleSettingsRow("Queue persistence", "Restore the active queue and its order after reopening Prismora", queuePersistence) { vm.setQueuePersistenceEnabled(!queuePersistence) }
        InfoSettingsRow("Queue reorder", "Now available directly from the Player -> Playback panel -> Queue using ↑ / ↓.")
        InfoSettingsRow("Gapless", "Native decoder/audio-engine rewrite is the next step; the current engine is not yet true sample-continuous gapless.")
        Text("These playback controls are also available directly on the Player screen.", color = Cyan.copy(.55f), fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun VisualizerSettingsPage(
    vm: PlayerViewModel,
    onBack: () -> Unit
) {
    val smoothing by vm.visualizerSmoothing.collectAsStateWithLifecycle()
    val sensitivity by vm.visualizerSensitivity.collectAsStateWithLifecycle()
    val reflection by vm.visualizerReflection.collectAsStateWithLifecycle()
    val style by vm.visualizerStyle.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTitle("Visualizer", onBack)
        Spacer(Modifier.height(16.dp))
        Text("STYLE", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            VisualizerStyle.entries.forEach { item ->
                val selected = item == style
                Surface(
                    onClick = { vm.setVisualizerStyle(item) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) Cyan.copy(.16f) else White.copy(.04f),
                    contentColor = if (selected) Cyan else White.copy(.45f),
                    modifier = Modifier.weight(1f).border(1.dp, if (selected) Cyan.copy(.30f) else White.copy(.05f), RoundedCornerShape(12.dp))
                ) { Text(item.label.uppercase(), textAlign = TextAlign.Center, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 10.dp)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        SliderSetting("Smoothing", smoothing, 0f..1f, "Lower = quicker movement • Higher = calmer bars", vm::setVisualizerSmoothing)
        SliderSetting("Sensitivity", sensitivity, .55f..1.8f, "FFT level multiplier", vm::setVisualizerSensitivity)
        SliderSetting("Reflection", reflection, 0f..0.45f, "Amount of the water-style reflection below the bars", vm::setVisualizerReflection)
        InfoSettingsRow("Optimized FFT", "Reusable 1024-point FFT buffers and a lightweight rendering path are active in every mode.")
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PerformanceSettingsPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val lite by vm.liteMode.collectAsStateWithLifecycle()
    val autoLite by vm.autoLiteMode.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTitle("Performance", onBack)
        Spacer(Modifier.height(16.dp))
        ToggleSettingsRow("Lite Mode", "Reduces animated backdrops while keeping audio quality and the optimized visualizer unchanged.", lite) { vm.setLiteMode(!lite) }
        ToggleSettingsRow("Auto Lite recommendation", "Remember that this device may benefit from Lite Mode. Automatic enabling still requires a later device-score implementation.", autoLite) { vm.setAutoLiteMode(!autoLite) }
        InfoSettingsRow("120 Hz UI", "Compose rendering is kept frame-rate friendly; heavy themes will be optimized after core audio stabilizes.")
        InfoSettingsRow("Artwork/cache optimization", "Lazy artwork decode and cache tuning remain in the performance roadmap.")
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AdvancedSettingsPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTitle("Advanced", onBack)
        Spacer(Modifier.height(16.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("ADVANCED STATUS", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
            Text("All implemented features are available", color = White, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
            Text(
                "Implemented tools are available directly. Remaining roadmap entries are shown as information until their underlying engines exist.",
                color = White.copy(.54f), fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }
        Text("AUDIO", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        InfoSettingsRow("Parametric EQ", "Available in the bottom Equalizer tab with real-time PCM processing, presets and a response graph.")
        InfoSettingsRow("Dynamics", "Compressor, limiter, preamp and crossfeed are active in the PCM DSP engine.")
        InfoSettingsRow("Device profiles", "Save and automatically load an EQ/DSP setup for each selected audio output.")
        Text("CUSTOMIZATION", color = White.copy(.42f), fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        InfoSettingsRow("Planned Now Playing editor", "Custom layout, gradients and backgrounds")
        InfoSettingsRow("Planned advanced widgets", "More layouts and theme-matched widgets")
        InfoSettingsRow("Planned smart playlists", "Rule-based automatically generated playlists")
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ToggleSettingsRow(title: String, subtitle: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp))
            .background(if (active) Cyan.copy(.10f) else Glass.copy(.55f))
            .border(1.dp, if (active) Cyan.copy(.30f) else White.copy(.05f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = White.copy(.45f), fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Surface(shape = RoundedCornerShape(11.dp), color = if (active) Cyan.copy(.18f) else White.copy(.05f), contentColor = if (active) Cyan else White.copy(.42f)) {
            Text(if (active) "ON" else "OFF", fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun InfoSettingsRow(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp))
            .background(Glass.copy(.50f)).border(1.dp, White.copy(.05f), RoundedCornerShape(18.dp)).padding(13.dp)
    ) {
        Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = White.copy(.43f), fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun SliderSetting(title: String, value: Float, range: ClosedFloatingPointRange<Float>, subtitle: String, onValueChange: (Float) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(String.format("%.2f", value), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Text(subtitle, color = White.copy(.43f), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = White.copy(.10f)),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun WrappedPage(vm: PlayerViewModel) {
    val stats by vm.wrappedStats.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("PRISMORA WRAPPED", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
        Text("Your local listening recap", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("LISTENED", prettyDuration(stats.totalListeningMs), Modifier.weight(1f))
            StatPill("PLAYS", stats.totalPlays.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("TRACKS", stats.uniqueTracks.toString(), Modifier.weight(1f))
            StatPill("ARTISTS", stats.uniqueArtists.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("TOP TRACKS", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(10.dp))
            if (stats.topTracks.isEmpty()) {
                Text("Start playing music and your wrapped will build itself automatically.", color = White.copy(.54f), fontSize = 12.sp, lineHeight = 18.sp)
            } else {
                stats.topTracks.take(5).forEachIndexed { index, item ->
                    TopPlayRow(index + 1, item)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("TOP ARTISTS", color = Pink, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(10.dp))
            if (stats.topArtists.isEmpty()) {
                Text("Artists will appear here after a few listens.", color = White.copy(.54f), fontSize = 12.sp)
            } else {
                stats.topArtists.take(5).forEachIndexed { index, item ->
                    ArtistRow(index + 1, item)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Wrapped uses only your local playback history. No Last.fm required.", color = White.copy(.25f), fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 8.dp))
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier) {
    GlassPanel(modifier) {
        Text(label, color = White.copy(.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(value, color = White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TopPlayRow(rank: Int, item: WrappedTrackStat) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank.toString().padStart(2, '0'), color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.artist, color = White.copy(.48f), fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${item.playCount} plays", color = Cyan.copy(.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(prettyDuration(item.listenedMs), color = White.copy(.35f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun ArtistRow(rank: Int, item: WrappedArtistStat) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank.toString().padStart(2, '0'), color = Pink, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(item.artist, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.playCount} plays", color = White.copy(.48f), fontSize = 11.sp)
        }
        Text(prettyDuration(item.listenedMs), color = White.copy(.35f), fontSize = 10.sp)
    }
}

@Composable
private fun BottomGlassNavigation(selected: Page, onSelected: (Page) -> Unit) {
    val p3r = LocalThemePreset.current.isP3rLike
    val outerShape = if (p3r) CutCornerShape(topStart = 0.dp, topEnd = 24.dp, bottomEnd = 0.dp, bottomStart = 24.dp) else RoundedCornerShape(27.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp).clip(outerShape)
            .background(if (p3r) DeepBlue.copy(.94f) else Glass)
            .border(1.dp, if (p3r) Cyan.copy(.62f) else White.copy(.08f), outerShape).padding(5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        primaryPages.forEach { page ->
            val active = page == selected
            val itemShape = if (p3r) CutCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 0.dp, bottomStart = 14.dp) else RoundedCornerShape(22.dp)
            Column(
                Modifier.weight(1f).clip(itemShape)
                    .background(
                        if (p3r && active) White
                        else if (active) Cyan.copy(.13f)
                        else Color.Transparent
                    )
                    .clickable { onSelected(page) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    page.symbol,
                    color = if (p3r) { if (active) DeepBlue else White.copy(.58f) } else { if (active) Cyan else White.copy(.42f) },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    page.label.uppercase(),
                    color = if (p3r) { if (active) Color(0xFF0878C9) else White.copy(.48f) } else { if (active) White else White.copy(.38f) },
                    fontSize = 8.sp,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Normal,
                    maxLines = 1
                )
                if (p3r && active) {
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.width(24.dp).height(2.dp).background(Pink))
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p3r = LocalThemePreset.current.isP3rLike
    val shape = if (p3r) CutCornerShape(topStart = 0.dp, topEnd = 24.dp, bottomEnd = 0.dp, bottomStart = 24.dp) else RoundedCornerShape(28.dp)
    Column(
        modifier.clip(shape).background(
            if (p3r) Brush.linearGradient(listOf(DeepBlue.copy(.94f), Color(0xFF075BD5).copy(.72f), DeepBlue.copy(.90f)))
            else Brush.verticalGradient(listOf(White.copy(.075f), Glass))
        ).border(1.dp, if (p3r) Cyan.copy(.48f) else White.copy(.08f), shape).padding(17.dp),
        content = content
    )
}

@Composable
private fun SmallGlassButton(label: String, action: () -> Unit) {
    val p3r = LocalThemePreset.current.isP3rLike
    val shape = if (p3r) CutCornerShape(12.dp) else RoundedCornerShape(18.dp)
    Surface(
        onClick = action,
        shape = shape,
        color = if (p3r) White.copy(.94f) else Cyan.copy(.10f),
        contentColor = if (p3r) DeepBlue else Cyan,
        modifier = Modifier.border(1.dp, if (p3r) Cyan.copy(.72f) else Cyan.copy(.24f), shape)
    ) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) }
}

@Composable
private fun AnimatedBackdrop(animate: Boolean, theme: ThemePreset, coverBytes: ByteArray?) {
    val backdropInk = Ink
    val backdropDeepBlue = DeepBlue
    val backdropCyan = Cyan
    val backdropPink = Pink
    val backdropBlue = Blue
    val backdropWhite = White
    val motion = if (animate) {
        val transition = rememberInfiniteTransition(label = "background")
        val animatedMotion by transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(7_000, easing = FastOutSlowInEasing), AnimationRepeatMode.Reverse),
            label = "motion"
        )
        animatedMotion
    } else {
        0f
    }
    val backgroundArt = rememberArtworkBitmap(
        if (theme.backdrop == BackdropStyle.COVER_BACKGROUND) coverBytes else null,
        320
    )
    val oled = theme.family == ThemeFamily.COVER_ART || theme == ThemePreset.PRISMORA_OLED || theme == ThemePreset.CRT
    val flatLight = theme == ThemePreset.PAPER || theme == ThemePreset.SAKURA || theme == ThemePreset.BAUHAUS || theme == ThemePreset.BRUTALIST || theme.isCassetteLike || theme == ThemePreset.MINIDISC_SILVER || theme == ThemePreset.DISCMAN || theme == ThemePreset.IPOD_LIGHT || theme == ThemePreset.IOS_LIGHT || theme == ThemePreset.MEDIA_PLAYER || theme == ThemePreset.PRISMORA_OS
    Box(
        Modifier.fillMaxSize().background(
            when {
                oled -> Brush.verticalGradient(listOf(Color.Black, Color(0xFF020202), Color.Black))
                flatLight -> Brush.verticalGradient(listOf(backdropInk, backdropInk, backdropDeepBlue))
                else -> Brush.verticalGradient(listOf(backdropInk, backdropDeepBlue, backdropInk))
            }
        )
    ) {
        if (theme.backdrop == BackdropStyle.COVER_BACKGROUND && backgroundArt != null) {
            Image(
                bitmap = backgroundArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().scale(1.08f).alpha(.34f)
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(.24f), Color.Black.copy(.58f), Color.Black.copy(.94f))
                    )
                )
            )
        }
        if (theme.isP3rLike) {
            Image(
                painter = painterResource(R.drawable.theme_p3r_makoto),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(start = 96.dp, top = 82.dp, bottom = 10.dp).alpha(.17f)
            )
        }
        if (!oled && !flatLight) Canvas(Modifier.fillMaxSize().alpha(.9f)) {
            val x = size.width * (.15f + .16f * motion)
            val y = size.height * (.18f + .08f * sin(motion * 6.28f))
            drawCircle(
                brush = Brush.radialGradient(listOf(backdropCyan.copy(.18f), Color.Transparent), center = Offset(x, y), radius = size.width * .34f),
                radius = size.width * .34f,
                center = Offset(x, y)
            )
            val pinkCenter = Offset(size.width * (.86f - .12f * motion), size.height * .64f)
            drawCircle(
                brush = Brush.radialGradient(listOf(backdropPink.copy(.12f), Color.Transparent), center = pinkCenter, radius = size.width * .30f),
                radius = size.width * .30f,
                center = pinkCenter
            )
            val blueCenter = Offset(size.width * .55f, size.height * (.32f + .14f * motion))
            drawCircle(
                brush = Brush.radialGradient(listOf(backdropBlue.copy(.10f), Color.Transparent), center = blueCenter, radius = size.width * .28f),
                radius = size.width * .28f,
                center = blueCenter
            )
        }
        Canvas(Modifier.fillMaxSize().alpha(if (oled) 1f else if (theme.isP3rLike) .78f else .16f)) {
            when (theme.backdrop) {
                BackdropStyle.GLOW -> {
                    val gap = 34.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(backdropCyan.copy(.16f), Offset(0f, y), Offset(size.width, y), 1f)
                        y += gap
                    }
                }
                BackdropStyle.OLED -> {
                    drawCircle(
                        Brush.radialGradient(listOf(backdropCyan.copy(.18f), Color.Transparent), center = Offset(size.width * .25f, size.height * .22f), radius = size.width * .42f),
                        size.width * .42f,
                        Offset(size.width * .25f, size.height * .22f)
                    )
                    drawLine(backdropPink.copy(.35f), Offset(0f, size.height * .82f), Offset(size.width, size.height * .70f), 2.dp.toPx())
                }
                BackdropStyle.STRIPES -> {
                    val gap = 42.dp.toPx()
                    var x0 = -size.height
                    while (x0 < size.width) {
                        drawLine(backdropCyan.copy(.24f), Offset(x0 + motion * 30.dp.toPx(), size.height), Offset(x0 + size.height + motion * 30.dp.toPx(), 0f), 4.dp.toPx())
                        x0 += gap
                    }
                }
                BackdropStyle.CLOCK -> {
                    // Reload-style diagonal menu planes
                    val topPlane = Path().apply {
                        moveTo(size.width * .42f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height * .17f)
                        lineTo(size.width * .27f, size.height * .08f)
                        close()
                    }
                    drawPath(topPlane, backdropWhite.copy(.16f))
                    drawLine(
                        backdropWhite.copy(.36f),
                        Offset(-size.width * .08f, size.height * .18f),
                        Offset(size.width * 1.08f, size.height * .05f),
                        10.dp.toPx()
                    )
                    drawLine(
                        backdropPink.copy(.78f),
                        Offset(size.width * .34f, size.height * .19f),
                        Offset(size.width * 1.03f, size.height * .11f),
                        2.5.dp.toPx()
                    )
                    drawLine(
                        backdropCyan.copy(.46f),
                        Offset(-size.width * .20f, size.height * .74f),
                        Offset(size.width * .88f, size.height * .57f),
                        15.dp.toPx()
                    )

                    // Glass shards drifting through the background
                    for (i in 0 until 8) {
                        val phase = i * .73f + motion * 2.1f
                        val px = size.width * (.08f + ((i * .137f + motion * .08f) % .88f))
                        val py = size.height * (.12f + ((i * .109f + motion * .05f) % .76f))
                        val r = (5f + (i % 3) * 3f).dp.toPx()
                        val shard = Path().apply {
                            moveTo(px, py - r)
                            lineTo(px + r * .85f, py + r * .55f)
                            lineTo(px - r * .45f, py + r)
                            close()
                        }
                        drawPath(shard, if (i % 3 == 0) backdropPink.copy(.50f) else backdropCyan.copy(.54f))
                    }

                    // Clock motif from P3R menus
                    val center = Offset(size.width * .78f, size.height * .32f)
                    val radius = size.width * .35f
                    drawCircle(backdropCyan.copy(.34f), radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    drawCircle(backdropWhite.copy(.28f), radius * .72f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    for (i in 0 until 12) {
                        val angle = (i / 12f) * 6.28318f + motion * .18f
                        val a = Offset(center.x + cos(angle) * radius * .82f, center.y + sin(angle) * radius * .82f)
                        val b = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                        drawLine(backdropWhite.copy(.42f), a, b, 2.dp.toPx())
                    }
                    drawLine(
                        backdropWhite.copy(.62f),
                        center,
                        Offset(center.x + cos(motion * 3.2f) * radius * .62f, center.y + sin(motion * 3.2f) * radius * .62f),
                        3.dp.toPx()
                    )
                    drawCircle(backdropPink.copy(.78f), 4.dp.toPx(), center)
                }
                BackdropStyle.HALFTONE -> {
                    val gap = 30.dp.toPx()
                    var y0 = 0f
                    var row = 0
                    while (y0 < size.height) {
                        var x0 = if (row % 2 == 0) 0f else gap / 2f
                        while (x0 < size.width) {
                            val r = (2.2f + 1.8f * sin((x0 + y0) * .01f + motion * 6f)).dp.toPx()
                            drawCircle(backdropCyan.copy(.32f), r, Offset(x0, y0))
                            x0 += gap
                        }
                        y0 += gap
                        row++
                    }
                }
                BackdropStyle.CUTOUT -> {
                    val shift = motion * 40.dp.toPx()
                    drawLine(backdropCyan.copy(.26f), Offset(-80.dp.toPx() + shift, size.height * .24f), Offset(size.width * .85f + shift, size.height * .08f), 18.dp.toPx())
                    drawLine(backdropPink.copy(.24f), Offset(size.width * .12f - shift, size.height * .70f), Offset(size.width * 1.12f - shift, size.height * .52f), 28.dp.toPx())
                    drawLine(backdropWhite.copy(.12f), Offset(-40.dp.toPx(), size.height * .88f), Offset(size.width * .78f, size.height * .76f), 7.dp.toPx())
                }
                BackdropStyle.CLOUD -> {
                    val baseY = size.height * (.23f + .04f * motion)
                    val cloud = backdropWhite.copy(.07f)
                    drawCircle(cloud, size.width * .22f, Offset(size.width * .18f, baseY))
                    drawCircle(cloud, size.width * .28f, Offset(size.width * .43f, baseY - 20.dp.toPx()))
                    drawCircle(cloud, size.width * .20f, Offset(size.width * .70f, baseY + 10.dp.toPx()))
                    val gap = 44.dp.toPx()
                    var y0 = size.height * .60f
                    while (y0 < size.height) {
                        drawLine(backdropBlue.copy(.18f), Offset(0f, y0), Offset(size.width, y0), 1.dp.toPx())
                        y0 += gap
                    }
                }
                BackdropStyle.COVER_WAVE -> {
                    for (layer in 0 until 7) {
                        val path = Path()
                        val centerY = size.height * (.30f + layer * .055f)
                        val amplitude = size.height * (.035f + layer * .004f)
                        for (point in 0..56) {
                            val px = size.width * point / 56f
                            val py = centerY +
                                sin(point * .17f + motion * 6.283f + layer * .48f) * amplitude +
                                sin(point * .055f - motion * 3.8f + layer) * amplitude * .55f
                            if (point == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(
                            path = path,
                            color = backdropCyan.copy(alpha = .34f - layer * .035f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = (5.5f - layer * .45f).dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                    val horizon = size.height * .51f
                    drawLine(backdropCyan.copy(.24f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
                }
                BackdropStyle.COVER_BACKGROUND -> {
                    val horizon = size.height * (.54f + .012f * sin(motion * 6.283f))
                    drawLine(backdropCyan.copy(.30f), Offset(0f, horizon), Offset(size.width, horizon), 1.5.dp.toPx())
                }
                BackdropStyle.NEON_GRID -> {
                    val horizon = size.height * .48f
                    for (i in 0..12) {
                        val x = size.width * i / 12f
                        drawLine(backdropCyan.copy(.48f), Offset(size.width / 2f, horizon), Offset(x, size.height), 1.dp.toPx())
                    }
                    for (i in 0..10) {
                        val t = i / 10f
                        val y = horizon + (size.height - horizon) * t * t
                        drawLine(backdropCyan.copy(.38f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    drawLine(backdropPink.copy(.72f), Offset(0f, horizon), Offset(size.width, horizon), 3.dp.toPx())
                }
                BackdropStyle.CRT -> {
                    var y = 0f
                    val gap = 4.dp.toPx()
                    while (y < size.height) {
                        drawLine(backdropCyan.copy(.28f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        y += gap
                    }
                    val sweep = size.height * motion
                    drawLine(backdropWhite.copy(.28f), Offset(0f, sweep), Offset(size.width, sweep), 2.dp.toPx())
                }
                BackdropStyle.Y2K -> {
                    for (i in 0..7) {
                        val radius = size.width * (.10f + i * .06f)
                        drawCircle(
                            if (i % 2 == 0) backdropCyan.copy(.26f) else backdropPink.copy(.22f),
                            radius,
                            Offset(size.width * (.72f - motion * .08f), size.height * .28f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke((1f + i * .15f).dp.toPx())
                        )
                    }
                    drawLine(backdropWhite.copy(.30f), Offset(0f, size.height * .72f), Offset(size.width, size.height * .62f), 8.dp.toPx())
                }
                BackdropStyle.VAPORWAVE -> {
                    val horizon = size.height * .47f
                    drawCircle(backdropPink.copy(.50f), size.width * .17f, Offset(size.width * .72f, horizon - size.width * .08f))
                    for (i in 0..11) {
                        val x = size.width * i / 11f
                        drawLine(backdropCyan.copy(.36f), Offset(size.width / 2f, horizon), Offset(x, size.height), 1.dp.toPx())
                    }
                    for (i in 0..9) {
                        val t = i / 9f
                        val y = horizon + (size.height - horizon) * t * t
                        drawLine(backdropPink.copy(.32f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                }
                BackdropStyle.CASSETTE -> {
                    val y = size.height * .33f
                    val left = Offset(size.width * .34f, y)
                    val right = Offset(size.width * .66f, y)
                    drawCircle(backdropWhite.copy(.22f), size.width * .12f, left, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                    drawCircle(backdropWhite.copy(.22f), size.width * .12f, right, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                    drawLine(backdropCyan.copy(.35f), left, right, 3.dp.toPx())
                    drawLine(backdropPink.copy(.30f), Offset(size.width * .18f, size.height * .63f), Offset(size.width * .82f, size.height * .63f), 12.dp.toPx())
                }
                BackdropStyle.MINIDISC -> {
                    val card = Path().apply {
                        moveTo(size.width * .18f, size.height * .18f)
                        lineTo(size.width * .82f, size.height * .15f)
                        lineTo(size.width * .86f, size.height * .60f)
                        lineTo(size.width * .22f, size.height * .64f)
                        close()
                    }
                    drawPath(card, backdropWhite.copy(.12f))
                    drawCircle(backdropCyan.copy(.30f), size.width * .15f, Offset(size.width * .54f, size.height * .39f), style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx()))
                }
                BackdropStyle.WALKMAN -> {
                    val gap = 26.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(backdropWhite.copy(.15f), Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx())
                        x += gap
                    }
                    drawLine(backdropCyan.copy(.58f), Offset(size.width * .08f, size.height * .72f), Offset(size.width * .92f, size.height * .72f), 5.dp.toPx())
                }
                BackdropStyle.PAPER -> {
                    val margin = 20.dp.toPx()
                    for (i in 1..14) {
                        val y = size.height * i / 15f
                        drawLine(backdropBlue.copy(.12f), Offset(margin, y), Offset(size.width - margin, y), 1.dp.toPx())
                    }
                    drawLine(backdropCyan.copy(.80f), Offset(margin, size.height * .16f), Offset(size.width * .56f, size.height * .16f), 4.dp.toPx())
                }
                BackdropStyle.SAKURA -> {
                    repeat(22) { i ->
                        val x = size.width * ((i * 37 % 101) / 100f)
                        val y = size.height * ((i * 61 % 97) / 100f)
                        val r = (3 + i % 4).dp.toPx()
                        drawCircle(if (i % 3 == 0) backdropBlue.copy(.18f) else backdropCyan.copy(.20f), r, Offset(x, y))
                    }
                    drawCircle(backdropPink.copy(.26f), size.width * .18f, Offset(size.width * .86f, size.height * .12f))
                    drawCircle(backdropCyan.copy(.16f), size.width * .24f, Offset(size.width * .12f, size.height * .78f))
                }
                BackdropStyle.BAUHAUS -> {
                    drawCircle(backdropCyan.copy(.88f), size.width * .18f, Offset(size.width * .18f, size.height * .18f))
                    drawRect(backdropBlue.copy(.82f), Offset(size.width * .70f, size.height * .08f), androidx.compose.ui.geometry.Size(size.width * .22f, size.height * .22f))
                    val tri = Path().apply {
                        moveTo(size.width * .60f, size.height * .78f)
                        lineTo(size.width * .93f, size.height * .68f)
                        lineTo(size.width * .82f, size.height * .94f)
                        close()
                    }
                    drawPath(tri, backdropPink.copy(.86f))
                }
                BackdropStyle.BRUTALIST -> {
                    drawRect(backdropCyan, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, 14.dp.toPx()))
                    drawRect(backdropBlue.copy(.96f), Offset(size.width * .72f, size.height * .10f), androidx.compose.ui.geometry.Size(size.width * .28f, size.height * .18f))
                    drawRect(backdropPink.copy(.96f), Offset(0f, size.height * .72f), androidx.compose.ui.geometry.Size(size.width * .44f, size.height * .12f))
                    drawLine(backdropWhite.copy(.85f), Offset(0f, size.height * .38f), Offset(size.width, size.height * .28f), 7.dp.toPx())
                    drawLine(backdropCyan.copy(.92f), Offset(size.width * .12f, 0f), Offset(size.width * .92f, size.height), 3.dp.toPx())
                }
                BackdropStyle.DISCMAN -> {
                    val center = Offset(size.width * .70f, size.height * .24f)
                    val radius = size.width * .26f
                    drawCircle(backdropWhite.copy(.46f), radius, center)
                    drawCircle(backdropDeepBlue.copy(.18f), radius * .22f, center)
                    for (i in 0..10) {
                        drawCircle(if (i % 2 == 0) backdropCyan.copy(.18f) else backdropPink.copy(.14f), radius * (1f - i * .07f), center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    }
                    drawLine(backdropBlue.copy(.38f), Offset(size.width * .10f, size.height * .72f), Offset(size.width * .90f, size.height * .72f), 4.dp.toPx())
                }
                BackdropStyle.IPOD_LIGHT -> {
                    drawCircle(backdropWhite.copy(.88f), size.width * .18f, Offset(size.width * .80f, size.height * .22f), style = androidx.compose.ui.graphics.drawscope.Stroke(9.dp.toPx()))
                    drawCircle(backdropBlue.copy(.20f), size.width * .10f, Offset(size.width * .80f, size.height * .22f))
                    drawLine(backdropBlue.copy(.20f), Offset(size.width * .12f, size.height * .74f), Offset(size.width * .88f, size.height * .74f), 2.dp.toPx())
                }
                BackdropStyle.IOS_LIGHT -> {
                    repeat(5) { i ->
                        val top = size.height * (.08f + i * .12f)
                        drawRoundRect(backdropWhite.copy(.56f), Offset(size.width * .10f, top), androidx.compose.ui.geometry.Size(size.width * .80f, size.height * .08f), androidx.compose.ui.geometry.CornerRadius(28.dp.toPx(), 28.dp.toPx()))
                    }
                }
                BackdropStyle.MEDIA_PLAYER -> {
                    drawRect(backdropWhite.copy(.48f), Offset(size.width * .06f, size.height * .10f), androidx.compose.ui.geometry.Size(size.width * .24f, size.height * .68f))
                    drawRect(backdropCyan.copy(.18f), Offset(size.width * .34f, size.height * .10f), androidx.compose.ui.geometry.Size(size.width * .58f, size.height * .14f))
                    var y = size.height * .30f
                    repeat(7) {
                        drawLine(backdropBlue.copy(.28f), Offset(size.width * .36f, y), Offset(size.width * .88f, y), 2.dp.toPx())
                        y += size.height * .07f
                    }
                }
                BackdropStyle.PRISMORA_OS -> {
                    repeat(3) { i ->
                        val x = size.width * (.08f + i * .12f)
                        val y = size.height * (.12f + i * .09f)
                        drawRoundRect(backdropWhite.copy(.54f), Offset(x, y), androidx.compose.ui.geometry.Size(size.width * .58f, size.height * .24f), androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()))
                        drawRect(backdropCyan.copy(.42f), Offset(x, y), androidx.compose.ui.geometry.Size(size.width * .58f, 20.dp.toPx()))
                    }
                }
            }
        }
    }
}

private fun themeLogoRes(theme: ThemePreset): Int? = when (theme) {
    ThemePreset.MIKU -> R.drawable.hatsune_miku_logo
    ThemePreset.TETO -> R.drawable.logo_teto
    ThemePreset.NERU -> R.drawable.logo_neru
    ThemePreset.YI_XI -> R.drawable.logo_yixi
    ThemePreset.KAITO -> R.drawable.logo_kaito
    ThemePreset.MEIKO -> R.drawable.logo_meiko
    ThemePreset.P3R, ThemePreset.P3R_2 -> R.drawable.logo_p3r
    ThemePreset.P4G -> R.drawable.logo_p4g
    ThemePreset.P5R -> R.drawable.logo_p5r
    ThemePreset.PRISMORA_GLASS, ThemePreset.PRISMORA_OLED,
    ThemePreset.INABAKUMORI, ThemePreset.INSOMNIA,
    ThemePreset.COVER_WAVE, ThemePreset.COVER_BACKGROUND,
    ThemePreset.CYBERPUNK, ThemePreset.CRT, ThemePreset.Y2K, ThemePreset.VAPORWAVE,
    ThemePreset.CASSETTE, ThemePreset.MINIDISC, ThemePreset.WALKMAN,
    ThemePreset.PAPER, ThemePreset.SAKURA, ThemePreset.BAUHAUS, ThemePreset.BRUTALIST,
    ThemePreset.WALKMAN_DECK, ThemePreset.WALKMAN_SPORTS, ThemePreset.MINIDISC_SILVER,
    ThemePreset.DISCMAN, ThemePreset.IPOD_LIGHT, ThemePreset.IOS_LIGHT,
    ThemePreset.MEDIA_PLAYER, ThemePreset.PRISMORA_OS -> null
}

private fun themeArtworkRes(theme: ThemePreset): Int? = when (theme) {
    ThemePreset.MIKU -> R.drawable.miku_v6
    ThemePreset.TETO -> R.drawable.theme_teto
    ThemePreset.NERU -> R.drawable.theme_neru
    ThemePreset.YI_XI -> R.drawable.theme_yixi
    ThemePreset.KAITO -> R.drawable.theme_kaito
    ThemePreset.MEIKO -> R.drawable.theme_meiko
    ThemePreset.INABAKUMORI -> R.drawable.theme_inabakumori
    ThemePreset.PRISMORA_GLASS, ThemePreset.PRISMORA_OLED, ThemePreset.INSOMNIA,
    ThemePreset.COVER_WAVE, ThemePreset.COVER_BACKGROUND,
    ThemePreset.CYBERPUNK, ThemePreset.CRT, ThemePreset.Y2K, ThemePreset.VAPORWAVE,
    ThemePreset.CASSETTE, ThemePreset.MINIDISC, ThemePreset.WALKMAN,
    ThemePreset.PAPER, ThemePreset.SAKURA, ThemePreset.BAUHAUS, ThemePreset.BRUTALIST,
    ThemePreset.WALKMAN_DECK, ThemePreset.WALKMAN_SPORTS, ThemePreset.MINIDISC_SILVER,
    ThemePreset.DISCMAN, ThemePreset.IPOD_LIGHT, ThemePreset.IOS_LIGHT,
    ThemePreset.MEDIA_PLAYER, ThemePreset.PRISMORA_OS -> null
    ThemePreset.P3R, ThemePreset.P3R_2 -> R.drawable.theme_p3r_makoto
    ThemePreset.P4G -> R.drawable.theme_p4g_yu
    ThemePreset.P5R -> R.drawable.theme_p5r_ren
}

@Composable
private fun rememberTrackArtwork(track: Track, vm: PlayerViewModel, maxEdgePx: Int): ImageBitmap? {
    val artwork by produceState<ByteArray?>(track.localArt, track.id, track.modifiedMs) {
        if (value == null) value = withContext(Dispatchers.IO) { vm.getLibraryArt(track) }
    }
    return rememberArtworkBitmap(artwork, maxEdgePx)
}

@Composable
private fun rememberArtworkBitmap(bytes: ByteArray?, maxEdgePx: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, bytes, maxEdgePx) {
        value = if (bytes == null) null else withContext(Dispatchers.Default) {
            decodeSampledBitmap(bytes, maxEdgePx)?.asImageBitmap()
        }
    }
    return bitmap
}

private fun decodeSampledBitmap(bytes: ByteArray, maxEdgePx: Int): android.graphics.Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxEdgePx) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun extractCoverAccent(bytes: ByteArray?): Color? {
    if (bytes == null) return null
    val bitmap = decodeSampledBitmap(bytes, 64) ?: return null
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val weights = FloatArray(24 * 4 * 4)
    val red = FloatArray(weights.size)
    val green = FloatArray(weights.size)
    val blue = FloatArray(weights.size)
    val hsv = FloatArray(3)
    for (pixel in pixels) {
        if (android.graphics.Color.alpha(pixel) < 180) continue
        android.graphics.Color.colorToHSV(pixel, hsv)
        val saturation = hsv[1]
        val value = hsv[2]
        if (value < .08f || (value > .94f && saturation < .10f)) continue
        val hueBin = min(23, (hsv[0] / 15f).toInt())
        val saturationBin = min(3, (saturation * 4f).toInt())
        val valueBin = min(3, (value * 4f).toInt())
        val index = hueBin * 16 + saturationBin * 4 + valueBin
        val weight = (.25f + saturation * 1.25f) * (.35f + value)
        weights[index] += weight
        red[index] += android.graphics.Color.red(pixel) * weight
        green[index] += android.graphics.Color.green(pixel) * weight
        blue[index] += android.graphics.Color.blue(pixel) * weight
    }
    val best = weights.indices.maxByOrNull { weights[it] } ?: return null
    val total = weights[best].takeIf { it > 0f } ?: return null
    val selected = android.graphics.Color.rgb(
        (red[best] / total).toInt().coerceIn(0, 255),
        (green[best] / total).toInt().coerceIn(0, 255),
        (blue[best] / total).toInt().coerceIn(0, 255)
    )
    android.graphics.Color.colorToHSV(selected, hsv)
    hsv[1] = hsv[1].coerceAtLeast(.48f)
    hsv[2] = hsv[2].coerceIn(.64f, .96f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val totalSeconds = milliseconds / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

private fun formatRemaining(milliseconds: Long): String {
    val totalMinutes = ((milliseconds + 59_999L) / 60_000L).coerceAtLeast(1L)
    return "${totalMinutes}m"
}

private fun prettyDuration(milliseconds: Long): String {
    val totalMinutes = (milliseconds / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun String.cleanFolder(): String = replace('\\', '/').trim('/')

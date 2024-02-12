package com.toasterofbread.spmp.ui.layout.nowplaying.queue

import LocalPlayerState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.toasterofbread.composekit.platform.composable.ScrollBarLazyColumn
import com.toasterofbread.composekit.platform.composable.composeScope
import com.toasterofbread.composekit.utils.common.*
import com.toasterofbread.composekit.utils.composable.getTop
import com.toasterofbread.composekit.utils.modifier.background
import com.toasterofbread.spmp.model.mediaitem.song.Song
import com.toasterofbread.spmp.model.settings.category.NowPlayingQueueRadioInfoPosition
import com.toasterofbread.spmp.model.settings.category.NowPlayingQueueWaveBorderMode
import com.toasterofbread.spmp.model.settings.category.PlayerSettings
import com.toasterofbread.spmp.model.settings.rememberMutableEnumState
import com.toasterofbread.spmp.platform.PlayerListener
import com.toasterofbread.spmp.platform.playerservice.PlatformPlayerService
import com.toasterofbread.spmp.service.playercontroller.LocalPlayerClickOverrides
import com.toasterofbread.spmp.ui.component.WaveBorder
import com.toasterofbread.spmp.ui.component.multiselect.MediaItemMultiSelectContext
import com.toasterofbread.spmp.ui.component.multiselect.MultiSelectItem
import com.toasterofbread.spmp.ui.layout.apppage.mainpage.MINIMISED_NOW_PLAYING_HEIGHT_DP
import com.toasterofbread.spmp.service.playercontroller.PlayerState
import com.toasterofbread.spmp.ui.layout.nowplaying.NowPlayingTopBar
import com.toasterofbread.spmp.ui.layout.nowplaying.getNPAltOnBackground
import com.toasterofbread.spmp.ui.layout.nowplaying.getNPBackground
import com.toasterofbread.spmp.ui.layout.nowplaying.rememberTopBarShouldShowInQueue
import com.toasterofbread.spmp.youtubeapi.radio.StatusDisplay
import kotlinx.coroutines.delay
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

const val QUEUE_CORNER_RADIUS_DP: Float = 25f

@Composable
internal fun QueueTab(
    page_height: Dp?,
    modifier: Modifier = Modifier,
    top_bar: NowPlayingTopBar? = null,
    padding_modifier: Modifier = Modifier,
    inline: Boolean = false,
    shape: Shape = RoundedCornerShape(QUEUE_CORNER_RADIUS_DP.dp),
    content_padding: PaddingValues = PaddingValues(),
    border_thickness: Dp = 1.5.dp,
    wave_border_mode_override: NowPlayingQueueWaveBorderMode? = null,
    button_row_arrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
    getBackgroundColour: PlayerState.() -> Color = { getNPAltOnBackground() },
    getOnBackgroundColour: PlayerState.() -> Color = { getNPBackground() },
    getWaveBorderColour: PlayerState.() -> Color = getOnBackgroundColour
) {
    val player: PlayerState = LocalPlayerState.current
    val density: Density = LocalDensity.current
    val scroll_coroutine_scope = rememberCoroutineScope()

    var key_inc by remember { mutableStateOf(0) }
    val radio_info_position: NowPlayingQueueRadioInfoPosition by PlayerSettings.Key.QUEUE_RADIO_INFO_POSITION.rememberMutableEnumState()
    val multiselect_context: MediaItemMultiSelectContext = remember { MediaItemMultiSelectContext() }

    val song_items: SnapshotStateList<QueueTabItem> = remember { mutableStateListOf<QueueTabItem>().also { list ->
        player.controller?.service_player?.iterateSongs { _, song: Song ->
            list.add(QueueTabItem(song, key_inc++))
        }
    } }

    val queue_listener = remember {
        object : PlayerListener() {
            override fun onSongAdded(index: Int, song: Song) {
                song_items.add(index, QueueTabItem(song, key_inc++))
            }
            override fun onSongRemoved(index: Int) {
                try {
                    song_items.removeAt(index)
                }
                catch (e: Throwable) {
                    throw RuntimeException("$index ${song_items.toList()}", e)
                }
            }
            override fun onSongMoved(from: Int, to: Int) {
                val coerced_to: Int = to.coerceIn(0, song_items.size - 1)
                if (from == coerced_to) {
                    return
                }

                song_items.add(coerced_to, song_items.removeAt(from))

                for (item in multiselect_context.getSelectedItems().map { it.second!! }.withIndex()) {
                    if (item.value == from) {
                        multiselect_context.updateKey(item.index, coerced_to)
                    }
                    else if (from > coerced_to) {
                        if (item.value in coerced_to until from) {
                            multiselect_context.updateKey(item.index, item.value + 1)
                        }
                    }
                    else if (item.value in (from + 1) .. coerced_to) {
                        multiselect_context.updateKey(item.index, item.value - 1)
                    }
                }
            }
        }
    }

    var playing_key: Int? by remember { mutableStateOf(null) }
    LaunchedEffect(player.status.m_index, song_items.size) {
        if (player.status.m_index in song_items.indices) {
            playing_key = song_items[player.status.m_index].key
        }
        else {
            playing_key = null
        }
    }

    DisposableEffect(Unit) {
        PlatformPlayerService.addListener(queue_listener)
        onDispose {
            PlatformPlayerService.removeListener(queue_listener)
        }
    }

    val items_above_queue: Int = if (radio_info_position == NowPlayingQueueRadioInfoPosition.ABOVE_ITEMS) 1 else 0
    val queue_list_state: ReorderableLazyListState = rememberReorderableLazyListState(
        onMove = { base_from, base_to ->
            val from = base_from.index - items_above_queue
            val to = base_to.index - items_above_queue
            if (to !in song_items.indices || from !in song_items.indices) {
                return@rememberReorderableLazyListState
            }

            song_items.add(to, song_items.removeAt(from))
        },
        onDragEnd = { base_from, base_to ->
            val from = base_from - items_above_queue
            val to = base_to - items_above_queue
            if (from == to || to !in song_items.indices || from !in song_items.indices) {
                return@rememberReorderableLazyListState
            }

            song_items.add(from, song_items.removeAt(to))
            player.controller?.service_player?.undoableAction {
                moveSong(from, to)
            }
            playing_key = null
        }
    )

    val show_top_bar: Boolean = rememberTopBarShouldShowInQueue(player.expansion.top_bar_mode.value)
    val top_bar_height: Dp by animateDpAsState(
        if (show_top_bar && top_bar != null) top_bar.height else 0.dp
    )

    composeScope {
        val expanded: Boolean by remember { derivedStateOf { player.expansion.get() > 1f } }
        LaunchedEffect(expanded) {
            val index = player.status.m_index
            if (expanded && index >= 0) {
                queue_list_state.listState.scrollToItem(index)
            }
        }
    }

    val content_colour: Color by remember { derivedStateOf { getBackgroundColour(player).getContrasted() } }

    CompositionLocalProvider(LocalContentColor provides content_colour) {
        Box(
            modifier
                .thenIf(!inline) {
                    // Add extra height for overscroll
                    thenWith(page_height) {
                        requiredHeight(it + 200.dp)
                    }
                    .padding(
                        top =
                            WindowInsets.getTop()
                            + top_bar_height
                            + MINIMISED_NOW_PLAYING_HEIGHT_DP.dp
                    )
                }
                .background(shape) { getBackgroundColour(player) }
                .clip(shape)
        ) {
            val list_padding: Dp = 10.dp

            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                QueueButtonsRow(
                    { getOnBackgroundColour(player) },
                    multiselect_context,
                    arrangement = button_row_arrangement
                ) { scroll_index ->
                    scroll_coroutine_scope.launchSingle {
                        queue_list_state.listState.scrollToItem(scroll_index)
                    }
                }

                if (radio_info_position == NowPlayingQueueRadioInfoPosition.TOP_BAR) {
                    CurrentRadioIndicator({ getOnBackgroundColour(player) }, multiselect_context, Modifier.padding(bottom = 10.dp)) {
                        song_items.map { MultiSelectItem(it.song, it.key) }
                    }
                }

                val wave_border_mode_state: NowPlayingQueueWaveBorderMode by PlayerSettings.Key.QUEUE_WAVE_BORDER_MODE.rememberMutableEnumState()
                val wave_border_mode: NowPlayingQueueWaveBorderMode = wave_border_mode_override ?: wave_border_mode_state
                QueueBorder(
                    wave_border_mode,
                    list_padding,
                    queue_list_state,
                    border_thickness,
                    getBackgroundColour = getBackgroundColour,
                    getBorderColour = getWaveBorderColour
                )

                CompositionLocalProvider(LocalPlayerClickOverrides provides LocalPlayerClickOverrides.current.copy(
                    onClickOverride = { song, index: Int? ->
                        if (index != player.status.m_index) {
                            player.controller?.seekToSong(index!!)
                        }
                    }
                )) {
                    var list_position by remember { mutableStateOf(0.dp) }
                    val top_padding = (
                        list_padding
                        // Extra space to prevent initial wave border overlap
                        + if (wave_border_mode != NowPlayingQueueWaveBorderMode.LINE) 15.dp else 0.dp
                    )

                    BoxWithConstraints(
                        Modifier.onPlaced { coords ->
                            list_position = with(density) { coords.positionInParent().y.toDp() }
                        }
                    ) {
                        val side_padding: Dp = maxWidth * PlayerSettings.Key.QUEUE_EXTRA_SIDE_PADDING.get<Float>() * 0.25f

                        ScrollBarLazyColumn(
                            state = queue_list_state.listState,
                            contentPadding = PaddingValues(
                                top = top_padding + content_padding.calculateTopPadding(),
                                bottom = content_padding.calculateBottomPadding(),
                                start = side_padding + content_padding.calculateStartPadding(LocalLayoutDirection.current),
                                end = side_padding + content_padding.calculateStartPadding(LocalLayoutDirection.current)
                            ),
                            modifier = Modifier.reorderable(queue_list_state),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (radio_info_position == NowPlayingQueueRadioInfoPosition.ABOVE_ITEMS) {
                                item {
                                    CurrentRadioIndicator({ getBackgroundColour(player) }, multiselect_context, Modifier.padding(bottom = 15.dp)) {
                                        song_items.map { MultiSelectItem(it.song, it.key) }
                                    }
                                }
                            }

                            QueueItems(
                                song_items,
                                queue_list_state,
                                multiselect_context,
                                player,
                                { playing_key },
                                { playing_key = it },
                                Modifier.padding(horizontal = list_padding),
                                getItemColour = getBackgroundColour,
                                getCurrentItemColour = getOnBackgroundColour
                            )

                            item {
                                player.controller?.radio_state?.StatusDisplay(
                                    Modifier
                                        .heightIn(min = 50.dp)
                                        .padding(top = list_padding, start = list_padding, end = list_padding)
                                        .fillMaxWidth(),
                                    expanded_modifier = Modifier.thenWith(page_height) {
                                        height(it / 2)
                                    }
                                )
                            }

                            if (!inline) {
                                item {
                                    var bottom_padding: Dp = (
                                        MINIMISED_NOW_PLAYING_HEIGHT_DP.dp * 2
                                        + list_position
                                    )

                                    if (player.controller?.radio_state?.loading == true && page_height != null) {
                                        bottom_padding = page_height - bottom_padding
                                    }

                                    if (player.controller?.radio_state?.load_error != null) {
                                        bottom_padding += 60.dp
                                    }

                                    Spacer(Modifier.height(bottom_padding))
                                }
                            }
                        }

                        if (side_padding > 0.dp) {
                            val padding_box_modifier = Modifier.fillMaxHeight().width(side_padding).then(padding_modifier)
                            Box(padding_box_modifier.align(Alignment.CenterStart))
                            Box(padding_box_modifier.align(Alignment.CenterEnd))
                        }
                    }
                }
            }
        }
    }
}

const val WAVE_BORDER_TIME_SPEED: Float = 0.15f

@Composable
private fun QueueBorder(
    wave_border_mode: NowPlayingQueueWaveBorderMode,
    list_padding: Dp,
    queue_list_state: ReorderableLazyListState,
    border_thickness: Dp,
    getBackgroundColour: PlayerState.() -> Color,
    getBorderColour: PlayerState.() -> Color
) {
    val player: PlayerState = LocalPlayerState.current

    if (wave_border_mode == NowPlayingQueueWaveBorderMode.LINE) {
        Divider(Modifier.padding(horizontal = list_padding), border_thickness, getBorderColour(player))
    }
    else {
        var wave_border_offset: Float by remember { mutableStateOf(0f) }

        LaunchedEffect(wave_border_mode) {
            val update_interval: Long = 1000 / 30
            when (wave_border_mode) {
                NowPlayingQueueWaveBorderMode.TIME -> {
                    while (true) {
                        wave_border_offset += update_interval * WAVE_BORDER_TIME_SPEED
                        delay(update_interval)
                    }
                }
                NowPlayingQueueWaveBorderMode.TIME_SYNC -> {
                    while (true) {
                        wave_border_offset = player.status.getPositionMillis() * WAVE_BORDER_TIME_SPEED
                        delay(update_interval)
                    }
                }
                else -> wave_border_offset = 0f
            }
        }

        WaveBorder(
            Modifier.fillMaxWidth().zIndex(1f),
            getColour = { getBackgroundColour(player) },
            getWaveOffset = {
                when (wave_border_mode) {
                    NowPlayingQueueWaveBorderMode.SCROLL -> {
                        ((50.dp.toPx() * queue_list_state.listState.firstVisibleItemIndex) + queue_list_state.listState.firstVisibleItemScrollOffset)
                    }
                    else -> wave_border_offset
                }
            },
            border_thickness = border_thickness,
            border_colour = getBorderColour(player),
            waves = 4,
            width_multiplier = 2f
        )
    }
}

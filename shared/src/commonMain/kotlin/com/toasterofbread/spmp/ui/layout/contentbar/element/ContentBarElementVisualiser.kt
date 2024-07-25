package com.toasterofbread.spmp.ui.layout.contentbar.element

import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Waves
import androidx.compose.foundation.layout.padding
import LocalAppState
import com.toasterofbread.spmp.ui.layout.contentbar.layoutslot.LayoutSlot
import LocalPlayerState
import LocalSessionState
import dev.toastbits.composekit.utils.common.thenIf

@Serializable
data class ContentBarElementVisualiser(
    override val config: ContentBarElementConfig = ContentBarElementConfig()
): ContentBarElement() {
    override fun getType(): ContentBarElement.Type = ContentBarElement.Type.VISUALISER

    override fun copyWithConfig(config: ContentBarElementConfig): ContentBarElement =
        copy(config = config)

    @Composable
    override fun isDisplaying(): Boolean =
        LocalSessionState.current.status.m_song != null

    @Composable
    override fun ElementContent(vertical: Boolean, slot: LayoutSlot?, bar_size: DpSize, onPreviewClick: (() -> Unit)?, modifier: Modifier) {
        val state: SpMp.State = LocalAppState.current

        if (onPreviewClick != null) {
            IconButton(onPreviewClick) {
                Icon(Icons.Default.Waves, null)
            }
        }
        else {
            state.session.controller?.Visualiser(
                LocalContentColor.current,
                modifier.thenIf(!vertical) {
                    padding(horizontal = 10.dp)
                },
                0.5f
            )
        }
    }
}

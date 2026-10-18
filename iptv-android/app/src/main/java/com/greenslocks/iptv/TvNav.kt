package com.greenslocks.iptv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState

/** Width the page leaves free for the collapsed rail. */
private val RAIL_CLOSED = 72.dp

/**
 * App chrome. A top bar when "Top navigation" is on, otherwise Compose for TV's navigation drawer: a slim icon
 * rail that slides open over the page (with labels) as soon as it takes focus.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavShell(
    items: List<RailItem>,
    topNav: Boolean,
    onItem: (RailIconType) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    if (topNav) {
        Box(Modifier.fillMaxSize()) {
            TopNav(items, Modifier.align(Alignment.TopStart), onItem)
            content(Modifier.fillMaxSize().padding(top = 60.dp))
        }
        return
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    ModalNavigationDrawer(
        drawerContent = { value ->
            val open = value == DrawerValue.Open
            Column(
                Modifier.fillMaxHeight().background(Palette.surface.copy(alpha = if (open) 0.98f else 1f))
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.Start,
            ) {
                Box(
                    Modifier.padding(start = 6.dp, bottom = 6.dp).size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Palette.accent, Palette.accent2))),
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.Text("V", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
                items.forEach { item ->
                    NavigationDrawerItem(
                        selected = item.selected,
                        onClick = { onItem(item.icon) },
                        leadingContent = { RailIcon(item.icon, if (item.selected) Palette.accent else Palette.muted) },
                        shape = NavigationDrawerItemDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = NavigationDrawerItemDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = Palette.muted,
                            selectedContainerColor = Palette.accent.copy(alpha = 0.18f),
                            selectedContentColor = Palette.text,
                            focusedContainerColor = Palette.accent.copy(alpha = 0.3f),
                            focusedContentColor = Palette.text,
                            focusedSelectedContainerColor = Palette.accent.copy(alpha = 0.34f),
                            focusedSelectedContentColor = Palette.text,
                            pressedContainerColor = Palette.accent.copy(alpha = 0.4f),
                            pressedContentColor = Palette.text,
                            pressedSelectedContainerColor = Palette.accent.copy(alpha = 0.4f),
                            pressedSelectedContentColor = Palette.text,
                        ),
                        border = NavigationDrawerItemDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Palette.accent), shape = RoundedCornerShape(14.dp)),
                            focusedSelectedBorder = Border(BorderStroke(2.dp, Palette.accent), shape = RoundedCornerShape(14.dp)),
                        ),
                    ) { Text(item.label, maxLines = 1) }
                }
            }
        },
        drawerState = drawerState,
        scrimBrush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)),
    ) {
        content(Modifier.fillMaxSize().padding(start = RAIL_CLOSED))
    }
}

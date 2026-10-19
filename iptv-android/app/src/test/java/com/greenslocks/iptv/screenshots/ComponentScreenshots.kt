package com.greenslocks.iptv.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.greenslocks.iptv.ActionButton
import com.greenslocks.iptv.Chip
import com.greenslocks.iptv.IptvTheme
import com.greenslocks.iptv.ListRow
import com.greenslocks.iptv.Palette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of the shared components in the states that have gone wrong before (unfocused pills with a stray outline,
 * a button that stretched, a focus ring on the wrong thing). `gradle recordRoborazziDebug` writes the reference
 * images into src/test/screenshots; CI runs `verifyRoborazziDebug` and fails when a picture changes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Television1080p)
class ComponentScreenshots {
    @get:Rule val compose = createComposeRule()

    @Test fun pills() {
        compose.setContent {
            IptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.bg), contentAlignment = Alignment.TopStart) {
                Column(Modifier.background(Palette.bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("All", true) {}
                        Chip("Movies", false) {}
                        Chip("Series", false) {}
                        Chip("Live", false, Modifier.testTag("focused")) {}
                    }
                }
                }
            }
        }
        compose.onNodeWithTag("focused").requestFocus()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/pills.png")
    }

    @Test fun buttons() {
        compose.setContent {
            IptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.bg), contentAlignment = Alignment.TopStart) {
                Column(Modifier.background(Palette.bg).padding(24.dp).width(420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Play", Modifier.testTag("focused"), icon = Icons.Rounded.PlayArrow) {}
                    ActionButton("More options", primary = false) {}
                    ActionButton("Find on my channels", Modifier.width(380.dp)) {}
                }
                }
            }
        }
        compose.onNodeWithTag("focused").requestFocus()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/buttons.png")
    }

    @Test fun rows() {
        compose.setContent {
            IptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.bg), contentAlignment = Alignment.TopStart) {
                Column(Modifier.background(Palette.bg).padding(24.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListRow("ESPN", "Live now: SportsCenter", false, Modifier.testTag("focused")) {}
                    ListRow("CBS", "NFL Today", true, Modifier) {}
                    ListRow("Fox", "", false, Modifier) {}
                }
                }
            }
        }
        compose.onNodeWithTag("focused").requestFocus()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/rows.png")
    }

    @Test fun buttonRows() {
        compose.setContent {
            IptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.bg), contentAlignment = Alignment.TopStart) {
                    Column(Modifier.padding(24.dp).width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Two buttons side by side with no widths set: each should be as wide as its text.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionButton("Cancel", primary = false) {}
                            ActionButton("Save") {}
                        }
                        // The same, equal widths.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionButton("Follow CHI", Modifier.weight(1f), primary = false) {}
                            ActionButton("Follow ATL", Modifier.weight(1f), primary = false) {}
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/button_rows.png")
    }
}

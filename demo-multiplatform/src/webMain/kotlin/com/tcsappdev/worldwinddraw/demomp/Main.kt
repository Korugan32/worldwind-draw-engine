@file:OptIn(ExperimentalComposeUiApi::class)

package com.tcsappdev.worldwinddraw.demomp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

fun main() {
    ComposeViewport(document.getElementById("ui")!!) { App() }
}

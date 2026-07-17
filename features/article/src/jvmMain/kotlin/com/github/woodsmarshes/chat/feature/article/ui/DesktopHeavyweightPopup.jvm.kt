package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import java.awt.Component
import java.awt.Window

@Composable
actual fun DesktopHeavyweightPopup(
    alignment: Alignment,
    offset: IntOffset,
    content: @Composable (() -> Unit),
) {
    var parentWindow by remember { mutableStateOf<Window?>(null) }
    
    LaunchedEffect(Unit) {
        // Find the main application window
        val windows = Window.getWindows()
        parentWindow = windows.firstOrNull { it.isShowing && it.type != Window.Type.POPUP }
    }
    
    Window(
        onCloseRequest = { },
        undecorated = true,
        transparent = true,
        focusable = false,
        resizable = false,
        alwaysOnTop = true,
        state = rememberWindowState(
            width = 300.dp,
            height = 100.dp,
        ),
    ) {
        // Position the window at bottom-right of parent
        LaunchedEffect(parentWindow, offset) {
            val currentWindow = (this@Window.window as? Window) ?: return@LaunchedEffect
            val parent = parentWindow ?: return@LaunchedEffect
            
            val parentBounds = parent.bounds
            val windowBounds = currentWindow.bounds
            
            val x = parentBounds.x + parentBounds.width - windowBounds.width + offset.x - 16
            val y = parentBounds.y + parentBounds.height - windowBounds.height + offset.y - 16
            
            currentWindow.setLocation(x, y)
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            content()
        }
    }
}
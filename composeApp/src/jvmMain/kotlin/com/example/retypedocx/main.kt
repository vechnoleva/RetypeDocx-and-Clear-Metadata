package com.example.retypedocx

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title          = "RetypeDocx — Перенабор документа",
        state          = rememberWindowState(width = 680.dp, height = 360.dp),
        resizable      = true,
    ) {
        App()
    }
}

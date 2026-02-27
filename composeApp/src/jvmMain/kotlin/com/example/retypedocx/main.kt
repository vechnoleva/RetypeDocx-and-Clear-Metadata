package com.example.retypedocx

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title          = "RetypeDocx — Перенабор документа",
        state          = rememberWindowState(width = 700.dp, height = 580.dp),
        resizable      = true,
    ) {
        App()
    }
}

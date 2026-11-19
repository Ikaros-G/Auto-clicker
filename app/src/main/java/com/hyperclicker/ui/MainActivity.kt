package com.hyperclicker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyperclicker.ui.theme.HyperClickerTheme

sealed class Screen {
    object List : Screen()
    data class Edit(val id: Long?) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HyperClickerTheme {
                val vm: MainViewModel = viewModel()
                var screen by remember { mutableStateOf<Screen>(Screen.List) }

                when (val current = screen) {
                    is Screen.List -> SchemeListScreen(
                        vm = vm,
                        onNew = { screen = Screen.Edit(null) },
                        onEdit = { screen = Screen.Edit(it) }
                    )
                    is Screen.Edit -> {
                        BackHandler { screen = Screen.List }
                        SchemeEditScreen(
                            vm = vm,
                            schemeId = current.id,
                            onBack = { screen = Screen.List }
                        )
                    }
                }
            }
        }
    }
}

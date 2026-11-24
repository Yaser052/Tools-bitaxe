package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.BitaxeAppShell
import com.example.ui.BitaxeViewModel
import com.example.ui.i18n.Localization
import com.example.ui.theme.BitaxeAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: BitaxeViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val language = state.settings.language
            val strings = Localization.forLanguage(language)
            val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                BitaxeAiTheme(isArabic = language.isRtl) {
                    BitaxeAppShell(
                        viewModel = viewModel,
                        state = state,
                        strings = strings
                    )
                }
            }
        }
    }
}

package com.relayhome.launcher

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.data.MetadataKeyRemoteValidationHook
import com.relayhome.launcher.data.MetadataKeyValidationResult
import com.relayhome.launcher.ui.settings.DataSourcesSettings
import com.relayhome.launcher.ui.shared.orbitalPalette
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataSourcesSettingsAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun clearSettings() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
    }

    @After
    fun restoreSettings() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
    }

    @Test
    fun invalidKey_showsInlineError_andDoesNotPersist() {
        setContent()

        composeRule.onNodeWithTag("data-source-key-TMDB").performTextInput("invalid")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("data-source-save-TMDB").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("data-source-error-TMDB").assertExists()
        composeRule.runOnIdle {
            assertNull(RelaySettingsRepository.loadTmdbApiKey(context))
        }
    }

    @Test
    fun validKey_isSavedButNeverRenderedInPlaintext() {
        val key = "0123456789abcdef0123456789abcdef"
        setContent(
            remoteValidationHook = MetadataKeyRemoteValidationHook { _, _ ->
                MetadataKeyValidationResult.valid()
            }
        )

        composeRule.onNodeWithTag("data-source-key-TMDB").performTextInput(key)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("data-source-save-TMDB").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(key).assertDoesNotExist()
        runBlocking { RelaySettingsRepository.awaitIdleForTesting(context) }
        composeRule.runOnIdle {
            assertTrue(RelaySettingsRepository.loadTmdbApiKey(context) == key)
        }
    }

    private fun setContent(
        remoteValidationHook: MetadataKeyRemoteValidationHook? = null
    ) {
        composeRule.setContent {
            val revision by RelaySettingsRepository.revision(context).collectAsState()
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    DataSourcesSettings(
                        palette = orbitalPalette,
                        context = context,
                        settingsRevision = revision,
                        firstFocusRequester = FocusRequester(),
                        backFocusRequester = FocusRequester(),
                        remoteValidationHook = remoteValidationHook
                            ?: com.relayhome.launcher.data.RelayMetadataApiKeyRemoteValidation
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}

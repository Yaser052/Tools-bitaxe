package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BitaxeDatabase
import com.example.data.local.ExperimentEntity
import com.example.ui.i18n.Localization
import com.example.domain.model.AppLanguage
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.BitaxeAiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Bitaxe AI", appName)
    }

    @Test
    fun `room database persists experiments permanently`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, BitaxeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val dao = db.experimentDao()
        val id = dao.insertExperiment(
            ExperimentEntity(
                timestampMillis = 1_700_000_000_000L,
                endTimestampMillis = 1_700_000_120_000L,
                deviceIp = "192.168.1.100",
                deviceIdentifier = "bitaxe-601",
                deviceModel = "Bitaxe Gamma",
                boardVersion = "601",
                firmwareVersion = "v2.6.0",
                frequencyMhz = 625,
                voltageMv = 1150,
                avgHashrateThs = 1.20,
                minHashrateThs = 1.18,
                maxHashrateThs = 1.22,
                avgPowerWatts = 18.7,
                efficiencyJth = 15.58,
                avgAsicTempC = 60.0,
                avgVrTempC = 68.0,
                avgAsicErrorPct = 0.25,
                acceptedShares = 35,
                rejectedShares = 0,
                staleShares = 0,
                sampleCount = 30,
                disconnectCount = 0,
                isStable = true,
                stabilityScore = 94.0,
                stabilityNotes = "Verified",
                durationSeconds = 120,
                isLastKnownStable = true
            )
        )

        val saved = dao.observeAllExperiments().first()
        assertEquals(1, saved.size)
        assertEquals(id, saved.first().id)
        assertEquals(625, saved.first().frequencyMhz)
        assertEquals(1150, saved.first().voltageMv)
        db.close()
    }

    @Test
    fun `splash screen renders enter control panel button`() {
        composeTestRule.setContent {
            BitaxeAiTheme {
                SplashScreen(
                    strings = Localization.forLanguage(AppLanguage.EN),
                    onContinue = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("splash_enter_button").assertIsDisplayed()
    }
}

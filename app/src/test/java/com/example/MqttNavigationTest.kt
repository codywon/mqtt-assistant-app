package com.example

import com.example.model.AppScreen
import com.example.model.MqttLogPacket
import com.example.viewmodel.MqttAssistantViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MqttNavigationTest {

    private lateinit var viewModel: MqttAssistantViewModel

    @Before
    fun setup() {
        viewModel = MqttAssistantViewModel()
    }

    @Test
    fun `initial screen is publish screen`() {
        assertEquals(AppScreen.Publish, viewModel.currentScreen.value)
    }

    @Test
    fun `navigation between screens works as specified`() {
        // Navigate to Subscribe
        viewModel.navigateTo(AppScreen.Subscribe)
        assertEquals(AppScreen.Subscribe, viewModel.currentScreen.value)

        // Navigate to LiveLogs
        viewModel.navigateTo(AppScreen.LiveLogs)
        assertEquals(AppScreen.LiveLogs, viewModel.currentScreen.value)

        // Navigate to Settings
        viewModel.navigateTo(AppScreen.Settings)
        assertEquals(AppScreen.Settings, viewModel.currentScreen.value)

        // Navigate back to Publish
        viewModel.navigateTo(AppScreen.Publish)
        assertEquals(AppScreen.Publish, viewModel.currentScreen.value)
    }

    @Test
    fun `resending log packet preloads payload and navigates to publish screen`() {
        // Given we are on LiveLogs
        viewModel.navigateTo(AppScreen.LiveLogs)
        assertEquals(AppScreen.LiveLogs, viewModel.currentScreen.value)

        val packet = MqttLogPacket(
            id = "test-packet-1",
            topic = "grid/substation/breaker/CB-04",
            qos = 2,
            packetSeq = "#9999",
            timestamp = "12:00:00.000",
            payload = "{\"test\": 123}",
            devInfo = "DEV: TEST",
            sizeText = "12B",
            category = "Breaker",
            dotColorHex = 0xFFF59E0B
        )

        // When resend is clicked
        viewModel.resendLogPacket(packet)

        // Then navigation transitions to Publish and topic/payload are set
        assertEquals(AppScreen.Publish, viewModel.currentScreen.value)
        assertEquals("grid/substation/breaker/CB-04", viewModel.publishTopic.value)
        assertEquals(2, viewModel.publishQos.value)
        assertEquals("{\"test\": 123}", viewModel.publishPayload.value)
    }

    @Test
    fun `subscription toggle works correctly`() {
        val firstSub = viewModel.subscriptions.value.first()
        val originalState = firstSub.isEnabled

        viewModel.toggleSubscription(firstSub.id)
        val updatedSub = viewModel.subscriptions.value.first { it.id == firstSub.id }
        assertEquals(!originalState, updatedSub.isEnabled)
    }
}

package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches AI Studio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("AI Studio", context.getString(R.string.app_name))
    }

    @Test
    fun `navigate across all six main destinations`() {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // Advance past splash screen (~1.2s)
        composeRule.mainClock.advanceTimeBy(1500)
        composeRule.waitForIdle()

        // Chat screen visible
        composeRule.onNodeWithTag("chat_input_field").assertIsDisplayed()

        // Projects
        composeRule.onNodeWithTag("nav_projects").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_project_btn").assertIsDisplayed()

        // Skills
        composeRule.onNodeWithTag("nav_skills").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("skills_search_field").assertIsDisplayed()

        // Coding Agent
        composeRule.onNodeWithTag("nav_coding_agent").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("coding_task_input").assertIsDisplayed()

        // Models
        composeRule.onNodeWithTag("nav_models").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("models_search_field").assertIsDisplayed()

        // Settings
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_global_prompt_btn").assertIsDisplayed()
    }
}

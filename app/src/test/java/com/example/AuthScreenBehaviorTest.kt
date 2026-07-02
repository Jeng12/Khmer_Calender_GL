package com.example

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.ui.auth.RegisterScreenContent
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthScreenBehaviorTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun registerAgreementRowTogglesCheckbox() {
        composeTestRule.setContent {
            MyApplicationTheme {
                RegisterScreenContent(onBack = {}, onRegister = {})
            }
        }

        composeTestRule.onNodeWithTag("register_terms_row").assertIsOff()
        composeTestRule.onNodeWithTag("register_terms_row").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("register_terms_row").assertIsOn()
    }

    @Test
    fun registerRequiresAgreementBeforeSubmit() {
        var submitCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                RegisterScreenContent(
                    onBack = {},
                    onRegister = {},
                    onSubmit = { _, _, _, _ -> submitCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("register_last_name_input").performTextInput("Doe")
        composeTestRule.onNodeWithTag("register_first_name_input").performTextInput("Jane")
        composeTestRule.onNodeWithTag("register_email_input").performTextInput("jane@example.com")
        composeTestRule.onNodeWithTag("register_password_input").performTextInput("secret1")

        composeTestRule.onNodeWithTag("register_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals(0, submitCount)
        }
        composeTestRule.onNodeWithTag("register_agreement_error").fetchSemanticsNode()

        composeTestRule.onNodeWithTag("register_terms_row").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("register_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals(1, submitCount)
        }
    }
}

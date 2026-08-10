package com.example.mviflowdemo.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun incrementButton_sendsIntentAndRendersNewState() {
    composeTestRule.setContent {
      var state by remember { mutableStateOf(MainUiState(count = 2)) }
      MviDemoScreen(
        state = state,
        liveDataCount = 0,
        onIntent = {
          if (it == MainIntent.Increment) state = state.copy(count = state.count + 1)
        },
        snackbarHostState = remember { SnackbarHostState() },
      )
    }

    composeTestRule.onNodeWithText("+1").performClick()
    composeTestRule.onNodeWithText("3").assertTextEquals("3")
  }
}

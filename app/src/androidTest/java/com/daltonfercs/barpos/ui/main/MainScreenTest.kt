package com.daltonfercs.barpos.ui.roles

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [RoleSelectionScreen]. */
class RoleSelectionTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      RoleSelectionScreen(
        onNavigateToTables = {},
        onNavigateToKitchen = {},
        onNavigateToReports = {}
      )
    }
  }

  @Test
  fun roleSelectionScreen_showsTitleAndButtons() {
    composeTestRule.onNodeWithText("BarPOS").assertExists()
    composeTestRule.onNodeWithText("Gestión de Mesas (Salón)").assertExists()
    composeTestRule.onNodeWithText("Cocina (KDS)").assertExists()
    composeTestRule.onNodeWithText("Administrador (Reportes)").assertExists()
  }
}

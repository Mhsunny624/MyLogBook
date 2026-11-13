/*
 * Bottom navigation (Logbook, Records, Calculator, Settings), the Add button, back-button
 * handling and the slide-up entry form overlay.
 */
package com.example.mylogbook.ui.navigation

import android.app.Activity
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mylogbook.ui.components.StartupDialogs
import com.example.mylogbook.ui.screens.calculator.TimeCalculatorScreen
import com.example.mylogbook.ui.screens.entry.FlightEntryScreen
import com.example.mylogbook.ui.screens.logbook.LogbookScreen
import com.example.mylogbook.ui.screens.records.RecordsScreen
import com.example.mylogbook.ui.screens.settings.SettingsScreen
import com.example.mylogbook.viewmodel.FlightLogViewModel
import kotlinx.coroutines.launch

@Composable
fun MainNavigationApp(
    airlineName: String,
    prefs: SharedPreferences,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    vm: FlightLogViewModel = viewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    var isAddOverlayVisible by remember { mutableStateOf(false) }
    var activeSubTab by remember { mutableStateOf("Entries") }

    val context = LocalContext.current
    var backPressTime by remember { mutableStateOf(0L) }

    // NEW MASTER ROUTER: Handles all back button presses for the entire app!
    BackHandler {
        if (isAddOverlayVisible) {
            // If the form is open, close it!
            isAddOverlayVisible = false
            vm.logsBeingEdited = null
        } else if (pagerState.currentPage != 0) {
            // If on Settings/Calculator, go back to Logbook
            coroutineScope.launch { pagerState.animateScrollToPage(0) }
        } else {
            // If on the Logbook, do the double-tap exit!
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressTime < 2000) {
                (context as? Activity)?.finish()
            } else {
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                backPressTime = currentTime
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                if ((pagerState.currentPage == 0 || pagerState.currentPage == 1) && !isAddOverlayVisible) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            vm.logsBeingEdited = null
                            isAddOverlayVisible = true
                        },
                        containerColor = Color(0xFF0077C2),
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.Edit, contentDescription = "Add") },
                        text = { Text("Add", fontWeight = FontWeight.Bold) }
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )

                    NavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        label = { Text("Logbook") },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Logbook") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        label = { Text("Records") },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Records") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 2,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        label = { Text("Calculator") },
                        icon = { Icon(Icons.Default.Calculate, contentDescription = "Calculator") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 3,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                        label = { Text("Settings") },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        colors = navColors
                    )
                }
            }
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> LogbookScreen(
                        vm = vm,
                        activeSubTab = activeSubTab,
                        onSubTabChange = { activeSubTab = it },
                        onEditClicked = { isAddOverlayVisible = true }
                    )
                    1 -> RecordsScreen(
                        vm = vm,
                        title = "Training & Health Records",
                        allowedTypes = listOf("Training", "Health Card"),
                        onEditClicked = { isAddOverlayVisible = true }
                    )
                    2 -> TimeCalculatorScreen()
                    3 -> SettingsScreen(
                        prefs = prefs,
                        airlineName = airlineName,
                        currentTheme = currentTheme,
                        onThemeChange = onThemeChange,
                        vm = vm
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = isAddOverlayVisible,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}) {
                    FlightEntryScreen(
                        onSaveSuccess = { savedRecordType ->
                            isAddOverlayVisible = false
                            val targetPage = if (savedRecordType == "Training" || savedRecordType == "Health Card") 1 else 0
                            coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                        },
                        vm = vm
                    )
                }
            }
        }

        StartupDialogs(prefs)
    }
}

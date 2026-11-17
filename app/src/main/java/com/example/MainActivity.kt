package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MaxMotionViewModel
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ProjectHubScreen
import com.example.ui.theme.MaxMotionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaxMotionTheme {
                val navController = rememberNavController()
                val viewModel: MaxMotionViewModel = viewModel()
                val projects by viewModel.savedProjects.collectAsStateWithLifecycle()

                NavHost(navController = navController, startDestination = "hub") {
                    composable("hub") {
                        ProjectHubScreen(
                            projects = projects,
                            onOpenProject = { id ->
                                viewModel.openProject(id) {
                                    navController.navigate("editor") { launchSingleTop = true }
                                }
                            },
                            onOpenDemo = {
                                viewModel.openDemoProject {
                                    navController.navigate("editor") { launchSingleTop = true }
                                }
                            },
                            onCreateProject = { name, aspect, fps, durSec ->
                                viewModel.createNewProject(name, aspect, fps, durSec) {
                                    navController.navigate("editor") { launchSingleTop = true }
                                }
                            },
                            onDuplicateProject = { viewModel.duplicateSavedProject(it) },
                            onDeleteProject = { viewModel.deleteSavedProject(it) },
                            onImportJson = { raw ->
                                viewModel.importProjectJson(raw) {
                                    navController.navigate("editor") { launchSingleTop = true }
                                }
                            }
                        )
                    }
                    composable("editor") {
                        EditorScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

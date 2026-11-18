package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecipeDetailScreen
import com.example.ui.theme.ChefNeveraTheme
import com.example.viewmodel.FridgeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ChefNeveraTheme {
                val viewModel: FridgeViewModel = viewModel()
                val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val selectedRecipe by viewModel.selectedRecipe.collectAsStateWithLifecycle()
                val isRefreshingRecipes by viewModel.isRefreshingRecipes.collectAsStateWithLifecycle()
                val recipeRefreshError by viewModel.recipeRefreshError.collectAsStateWithLifecycle()
                val completedSteps by viewModel.completedSteps.collectAsStateWithLifecycle()

                AnimatedContent(
                    targetState = selectedRecipe,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screenTransition"
                ) { activeRecipe ->
                    if (activeRecipe == null) {
                        HomeScreen(
                            photoUri = photoUri,
                            uiState = uiState,
                            isRefreshingRecipes = isRefreshingRecipes,
                            recipeRefreshError = recipeRefreshError,
                            onPhotoSelected = { uri ->
                                viewModel.onPhotoSelected(this@MainActivity, uri)
                            },
                            onRetryAnalysis = {
                                viewModel.retryAnalysis(this@MainActivity)
                            },
                            onToggleIngredient = viewModel::toggleIngredientSelection,
                            onRemoveIngredient = viewModel::removeIngredient,
                            onAddCustomIngredient = viewModel::addCustomIngredient,
                            onRegenerateRecipes = {
                                viewModel.regenerateRecipes(this@MainActivity)
                            },
                            onSelectRecipe = viewModel::openRecipe,
                            onResetScan = viewModel::resetScan
                        )
                    } else {
                        RecipeDetailScreen(
                            recipe = activeRecipe,
                            completedSteps = completedSteps[activeRecipe.id].orEmpty(),
                            onToggleStep = { index ->
                                viewModel.toggleStepCompleted(activeRecipe.id, index)
                            },
                            onBack = viewModel::closeRecipe
                        )
                    }
                }
            }
        }
    }
}

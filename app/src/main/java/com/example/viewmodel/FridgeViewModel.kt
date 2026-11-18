package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.FridgeAiRepository
import com.example.model.DetectedIngredient
import com.example.model.FridgeUiState
import com.example.model.RecipeSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "FridgeViewModel"

class FridgeViewModel(
    private val repository: FridgeAiRepository = FridgeAiRepository()
) : ViewModel() {

    private val _photoUri = MutableStateFlow<Uri?>(null)
    val photoUri: StateFlow<Uri?> = _photoUri.asStateFlow()

    private val _uiState = MutableStateFlow<FridgeUiState>(FridgeUiState.Idle)
    val uiState: StateFlow<FridgeUiState> = _uiState.asStateFlow()

    private val _selectedRecipe = MutableStateFlow<RecipeSuggestion?>(null)
    val selectedRecipe: StateFlow<RecipeSuggestion?> = _selectedRecipe.asStateFlow()

    private val _isRefreshingRecipes = MutableStateFlow(false)
    val isRefreshingRecipes: StateFlow<Boolean> = _isRefreshingRecipes.asStateFlow()

    private val _recipeRefreshError = MutableStateFlow<String?>(null)
    val recipeRefreshError: StateFlow<String?> = _recipeRefreshError.asStateFlow()

    private val _completedSteps = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
    val completedSteps: StateFlow<Map<String, Set<Int>>> = _completedSteps.asStateFlow()

    fun onPhotoSelected(context: Context, uri: Uri) {
        _photoUri.value = uri
        _selectedRecipe.value = null
        _recipeRefreshError.value = null
        analyzePhoto(context.applicationContext, uri)
    }

    fun retryAnalysis(context: Context) {
        val currentUri = _photoUri.value ?: return
        analyzePhoto(context.applicationContext, currentUri)
    }

    private fun analyzePhoto(appContext: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = FridgeUiState.Analyzing(
                appContext.getString(R.string.analyzing_subtitle)
            )
            try {
                val bitmap = repository.decodeScaledBitmap(appContext, uri)
                if (bitmap == null) {
                    _uiState.value = FridgeUiState.Error(
                        appContext.getString(R.string.error_image_decode)
                    )
                    return@launch
                }

                val result = repository.analyzeFridgePhoto(bitmap)
                _uiState.value = FridgeUiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Fridge analysis timed out", e)
                _uiState.value = FridgeUiState.Error(
                    appContext.getString(R.string.error_timeout)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fridge analysis failed", e)
                _uiState.value = FridgeUiState.Error(
                    e.localizedMessage ?: appContext.getString(R.string.error_generic)
                )
            }
        }
    }

    fun toggleIngredientSelection(ingredientId: String) {
        val currentState = _uiState.value as? FridgeUiState.Success ?: return
        val updatedIngredients = currentState.result.ingredients.map { item ->
            if (item.id == ingredientId) item.copy(isSelected = !item.isSelected) else item
        }
        _uiState.value = FridgeUiState.Success(
            currentState.result.copy(ingredients = updatedIngredients)
        )
    }

    fun removeIngredient(ingredientId: String) {
        val currentState = _uiState.value as? FridgeUiState.Success ?: return
        val updatedIngredients = currentState.result.ingredients.filterNot { it.id == ingredientId }
        _uiState.value = FridgeUiState.Success(
            currentState.result.copy(ingredients = updatedIngredients)
        )
    }

    fun addCustomIngredient(rawName: String) {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return
        val currentState = _uiState.value as? FridgeUiState.Success ?: return
        val newIngredient = DetectedIngredient(
            name = trimmed,
            quantity = "Agregado manualmente",
            category = "Otros",
            isSelected = true
        )
        _uiState.value = FridgeUiState.Success(
            currentState.result.copy(
                ingredients = currentState.result.ingredients + newIngredient
            )
        )
    }

    fun regenerateRecipes(context: Context) {
        val currentState = _uiState.value as? FridgeUiState.Success ?: return
        val selectedNames = currentState.result.ingredients
            .filter { it.isSelected }
            .map { "${it.name} (${it.quantity})" }

        if (selectedNames.isEmpty()) {
            _recipeRefreshError.value = context.getString(R.string.empty_recipes_message)
            return
        }

        val appContext = context.applicationContext
        viewModelScope.launch {
            _isRefreshingRecipes.value = true
            _recipeRefreshError.value = null
            try {
                val updatedRecipes = repository.suggestRecipesForIngredients(selectedNames)
                val latestState = _uiState.value as? FridgeUiState.Success
                if (latestState != null) {
                    _uiState.value = FridgeUiState.Success(
                        latestState.result.copy(recipes = updatedRecipes)
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Recipe refresh timed out", e)
                _recipeRefreshError.value = appContext.getString(R.string.error_timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recipe refresh failed", e)
                _recipeRefreshError.value =
                    e.localizedMessage ?: appContext.getString(R.string.error_generic)
            } finally {
                _isRefreshingRecipes.value = false
            }
        }
    }

    fun openRecipe(recipe: RecipeSuggestion) {
        _selectedRecipe.value = recipe
    }

    fun closeRecipe() {
        _selectedRecipe.value = null
    }

    fun toggleStepCompleted(recipeId: String, stepIndex: Int) {
        _completedSteps.update { currentMap ->
            val currentSet = currentMap[recipeId].orEmpty()
            val updatedSet = if (stepIndex in currentSet) {
                currentSet - stepIndex
            } else {
                currentSet + stepIndex
            }
            currentMap + (recipeId to updatedSet)
        }
    }

    fun resetScan() {
        _photoUri.value = null
        _selectedRecipe.value = null
        _recipeRefreshError.value = null
        _completedSteps.value = emptyMap()
        _uiState.value = FridgeUiState.Idle
    }
}

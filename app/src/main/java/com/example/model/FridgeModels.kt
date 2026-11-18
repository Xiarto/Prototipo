package com.example.model

import java.util.UUID

data class DetectedIngredient(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: String,
    val category: String,
    val isSelected: Boolean = true
)

data class RecipeSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val prepTimeMinutes: Int,
    val difficulty: String,
    val servings: Int,
    val detectedIngredientsUsed: List<String>,
    val extraPantryBasics: List<String>,
    val steps: List<String>
)

data class FridgeAnalysisResult(
    val summary: String,
    val ingredients: List<DetectedIngredient>,
    val recipes: List<RecipeSuggestion>
)

sealed interface FridgeUiState {
    data object Idle : FridgeUiState
    data class Analyzing(val message: String) : FridgeUiState
    data class Success(val result: FridgeAnalysisResult) : FridgeUiState
    data class Error(val message: String) : FridgeUiState
}

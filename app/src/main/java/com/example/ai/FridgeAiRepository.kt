package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.example.model.DetectedIngredient
import com.example.model.FridgeAnalysisResult
import com.example.model.RecipeSuggestion
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

class FridgeAiRepository {

    private val ingredientSchema = Schema.obj(
        properties = mapOf(
            "name" to Schema.string(description = "Nombre claro del ingrediente en español"),
            "quantity" to Schema.string(description = "Cantidad aproximada visible, ej. '3 piezas', '1 envase', 'aprox. 200 g'"),
            "category" to Schema.enumeration(
                values = listOf(
                    "Verduras",
                    "Frutas",
                    "Lácteos",
                    "Proteínas",
                    "Condimentos",
                    "Bebidas",
                    "Granos",
                    "Otros"
                ),
                description = "Categoría alimentaria del ingrediente"
            )
        )
    )

    private val recipeSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Título atractivo de la receta en español"),
            "description" to Schema.string(description = "Breve descripción apetitosa de 1 o 2 líneas"),
            "prepTimeMinutes" to Schema.integer(description = "Tiempo total estimado de preparación y cocción en minutos"),
            "difficulty" to Schema.enumeration(
                values = listOf("Fácil", "Media", "Elaborada"),
                description = "Nivel de dificultad de la receta"
            ),
            "servings" to Schema.integer(description = "Número de porciones"),
            "detectedIngredientsUsed" to Schema.array(
                items = Schema.string(),
                description = "Lista con cantidades de los ingredientes detectados en el refrigerador que usa esta receta"
            ),
            "extraPantryBasics" to Schema.array(
                items = Schema.string(),
                description = "Básicos comunes de despensa opcionales o necesarios (ej. sal, pimienta, aceite de oliva)"
            ),
            "steps" to Schema.array(
                items = Schema.string(),
                description = "Pasos secuenciales, claros y numerables para preparar la receta"
            )
        )
    )

    private val fridgeAnalysisSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Resumen breve y amigable de lo observado en el refrigerador"),
            "ingredients" to Schema.array(
                items = ingredientSchema,
                description = "Lista completa de alimentos e ingredientes visibles en la imagen"
            ),
            "recipes" to Schema.array(
                items = recipeSchema,
                description = "Entre 3 y 5 recetas prácticas y variadas priorizando los ingredientes detectados"
            )
        )
    )

    private val recipesOnlySchema = Schema.obj(
        properties = mapOf(
            "recipes" to Schema.array(
                items = recipeSchema,
                description = "Entre 3 y 5 recetas creativas y prácticas basadas en la lista de ingredientes proporcionada"
            )
        )
    )

    private val analysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = fridgeAnalysisSchema
                temperature = 0.4f
            }
        )
    }

    private val recipesModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = recipesOnlySchema
                temperature = 0.6f
            }
        )
    }

    suspend fun analyzeFridgePhoto(bitmap: Bitmap): FridgeAnalysisResult {
        val promptText = """
            Eres un chef experto y asistente de cocina inteligente.
            Analiza cuidadosamente esta fotografía del interior de un refrigerador o alacena.
            1. Identifica todos los alimentos e ingredientes reconocibles con su cantidad aproximada y categoría.
            2. Si la imagen no muestra alimentos ni un refrigerador, devuelve una lista de ingredientes vacía, una lista de recetas vacía y explícalo amablemente en 'summary'.
            3. Si hay ingredientes detectados, sugiere entre 3 y 5 recetas prácticas, variadas y deliciosas que aprovechen al máximo esos insumos (asumiendo básicos de despensa comunes como sal, pimienta, agua o aceite).
            Responde únicamente en español respetando el esquema JSON solicitado.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            analysisModel.generateContent(
                content {
                    image(bitmap)
                    text(promptText)
                }
            )
        }

        val jsonText = response.text
            ?: throw IllegalStateException("El modelo devolvió una respuesta vacía.")
        return parseAnalysisJson(jsonText)
    }

    suspend fun suggestRecipesForIngredients(ingredientNames: List<String>): List<RecipeSuggestion> {
        val formattedList = ingredientNames.joinToString(separator = ", ")
        val promptText = """
            Eres un chef experto en aprovechar ingredientes disponibles en casa.
            Con los siguientes ingredientes disponibles en el refrigerador:
            $formattedList

            Sugiere entre 3 y 5 recetas prácticas, variadas y apetitosas en español.
            Prioriza usar principalmente estos ingredientes, indicando aparte los básicos de despensa mínimos (sal, pimienta, aceite, etc.).
            Incluye pasos claros y realistas.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            recipesModel.generateContent(promptText)
        }

        val jsonText = response.text
            ?: throw IllegalStateException("El modelo devolvió una respuesta vacía.")
        return parseRecipesJson(jsonText)
    }

    private fun parseAnalysisJson(jsonString: String): FridgeAnalysisResult {
        val root = JSONObject(jsonString)
        val summary = root.optString("summary", "")

        val ingredientsArray = root.optJSONArray("ingredients")
        val ingredients = buildList {
            if (ingredientsArray != null) {
                for (i in 0 until ingredientsArray.length()) {
                    val obj = ingredientsArray.getJSONObject(i)
                    val name = obj.optString("name").trim()
                    if (name.isNotEmpty()) {
                        add(
                            DetectedIngredient(
                                name = name,
                                quantity = obj.optString("quantity", "Disponible").trim(),
                                category = obj.optString("category", "Otros").trim()
                            )
                        )
                    }
                }
            }
        }

        val recipes = parseRecipesArray(root.optJSONArray("recipes"))
        return FridgeAnalysisResult(
            summary = summary,
            ingredients = ingredients,
            recipes = recipes
        )
    }

    private fun parseRecipesJson(jsonString: String): List<RecipeSuggestion> {
        val root = JSONObject(jsonString)
        return parseRecipesArray(root.optJSONArray("recipes"))
    }

    private fun parseRecipesArray(recipesArray: org.json.JSONArray?): List<RecipeSuggestion> {
        if (recipesArray == null) return emptyList()
        return buildList {
            for (i in 0 until recipesArray.length()) {
                val obj = recipesArray.getJSONObject(i)
                val title = obj.optString("title").trim()
                if (title.isEmpty()) continue

                val usedArray = obj.optJSONArray("detectedIngredientsUsed")
                val usedIngredients = buildList {
                    if (usedArray != null) {
                        for (j in 0 until usedArray.length()) {
                            val item = usedArray.optString(j).trim()
                            if (item.isNotEmpty()) add(item)
                        }
                    }
                }

                val basicsArray = obj.optJSONArray("extraPantryBasics")
                val basics = buildList {
                    if (basicsArray != null) {
                        for (j in 0 until basicsArray.length()) {
                            val item = basicsArray.optString(j).trim()
                            if (item.isNotEmpty()) add(item)
                        }
                    }
                }

                val stepsArray = obj.optJSONArray("steps")
                val steps = buildList {
                    if (stepsArray != null) {
                        for (j in 0 until stepsArray.length()) {
                            val step = stepsArray.optString(j).trim()
                            if (step.isNotEmpty()) add(step)
                        }
                    }
                }

                add(
                    RecipeSuggestion(
                        title = title,
                        description = obj.optString("description", "").trim(),
                        prepTimeMinutes = obj.optInt("prepTimeMinutes", 20).coerceAtLeast(1),
                        difficulty = obj.optString("difficulty", "Fácil").trim(),
                        servings = obj.optInt("servings", 2).coerceAtLeast(1),
                        detectedIngredientsUsed = usedIngredients,
                        extraPantryBasics = basics,
                        steps = steps
                    )
                )
            }
        }
    }

    suspend fun decodeScaledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val largest = max(width, height)
                if (largest > maxDimension) {
                    val ratio = maxDimension.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        (width * ratio).roundToInt().coerceAtLeast(1),
                        (height * ratio).roundToInt().coerceAtLeast(1)
                    )
                }
            }
            decoded
        } else {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val largest = max(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (largest / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val currentLargest = max(bitmap.width, bitmap.height)
            if (currentLargest > maxDimension) {
                val scale = maxDimension.toFloat() / currentLargest.toFloat()
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
                if (scaled != bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }
        }
    }
}

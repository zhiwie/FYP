package com.example.fypdraft.view

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fypdraft.model.PetAnimation
import com.example.fypdraft.model.PetState

/**
 * Lottie-powered pet renderer with Canvas PixelPet fallback.
 *
 * Looks for Lottie JSON files in assets/pet/ directory:
 *   - assets/pet/{petType}_{animation}.json
 *   - e.g. assets/pet/cat_idle.json, assets/pet/cat_dancing.json
 *
 * If the Lottie file exists → renders smooth 60fps Lottie animation.
 * If not → falls back to the existing Canvas-based PixelPet renderer.
 *
 * === HOW TO ADD LOTTIE ANIMATIONS ===
 *
 * 1. Add dependency to build.gradle (app):
 *    implementation "com.airbnb.android:lottie-compose:6.3.0"
 *
 * 2. Create folder: app/src/main/assets/pet/
 *
 * 3. Add JSON files named as: {petType}_{animation}.json
 *    Pet types: cat, dog, bunny (from PetType enum displayName, lowercased)
 *    Animations: idle, dancing, happy_bounce, love_eyes, sleeping,
 *                eating, celebrating, sad, listening
 *
 *    Examples:
 *      assets/pet/cat_idle.json
 *      assets/pet/cat_dancing.json
 *      assets/pet/bunny_happy_bounce.json
 *
 * 4. Until you add the JSON files, the existing PixelPet Canvas
 *    renderer will be used automatically as fallback.
 *
 * === GETTING LOTTIE FILES ===
 *
 * Free sources:
 *   - LottieFiles.com — search "cute pet", "pixel cat", "dancing animal"
 *   - Create your own at lottiefiles.com/editor
 *   - Use Rive (rive.app) and export as Lottie JSON
 */
@Composable
fun LottiePetView(
    petState: PetState,
    animation: PetAnimation,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val petTypeName = petState.type.displayName.lowercase()
    val animName = animation.name.lowercase()
    val assetPath = "pet/${petTypeName}_${animName}.json"

    // Check if Lottie file exists in assets
    val hasLottieFile = remember(assetPath) {
        hasAssetFile(context, assetPath)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (hasLottieFile) {
            // ═══ LOTTIE RENDERER ═══
            // Uncomment the block below after adding lottie-compose dependency
            // and placing JSON files in assets/pet/

            /*
            val composition by rememberLottieComposition(
                LottieCompositionSpec.Asset(assetPath)
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                isPlaying = true,
                speed = when (animation) {
                    PetAnimation.DANCING -> if (isPlaying) 1.5f else 0.8f
                    PetAnimation.SLEEPING -> 0.5f
                    PetAnimation.CELEBRATING -> 1.2f
                    PetAnimation.EATING -> 1.0f
                    else -> 1.0f
                }
            )
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
            */

            // Placeholder until Lottie is set up — uses PixelPet
            PixelPet(
                petState = petState,
                animation = animation,
                modifier = Modifier.size(100.dp),
                isPlaying = isPlaying
            )
        } else {
            // ═══ CANVAS FALLBACK ═══
            PixelPet(
                petState = petState,
                animation = animation,
                modifier = Modifier.size(100.dp),
                isPlaying = isPlaying
            )
        }
    }
}

/**
 * Check if an asset file exists without throwing.
 */
private fun hasAssetFile(context: Context, path: String): Boolean {
    return try {
        context.assets.open(path).use { true }
    } catch (e: Exception) {
        false
    }
}
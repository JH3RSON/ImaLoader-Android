package com.ima.imaloader.ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

enum class ButtonState { IDLE, LOADING, SUCCESS }

fun Modifier.pressScalePrimary(interactionSource: MutableInteractionSource): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = if (isPressed) {
            tween(durationMillis = 90, easing = LinearOutSlowInEasing)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.001f
            )
        },
        label = "PrimaryButtonScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.pressScaleSecondary(interactionSource: MutableInteractionSource): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "SecondaryButtonScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun AnimatedPrimaryButtonContent(state: ButtonState, buttonText: String) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.8f, animationSpec = tween(220)))
                .togetherWith(fadeOut(animationSpec = tween(180)))
        },
        label = "ButtonStateTransition"
    ) { targetState ->
        when (targetState) {
            ButtonState.IDLE -> Text(text = buttonText)
            ButtonState.LOADING -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            ButtonState.SUCCESS -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Éxito"
            )
        }
    }
}

@Composable
fun AnimatedTextFieldFocus(
    isFocused: Boolean,
    isValid: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            !isValid -> Color.Red
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(180),
        label = "BorderColor"
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(15.dp))
                .padding(4.dp)
        ) {
            content()
        }

        AnimatedVisibility(
            visible = isValid,
            enter = fadeIn(tween(200)) + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(200)
            )
        ) {
            Text("Enlace válido", color = Color.Green, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun AnimatedBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(280, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { it / 4 },
            animationSpec = tween(200, easing = FastOutLinearInEasing)
        ) + fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        ) {
            content()
        }
    }
}

@Composable
fun AnimatedPlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
        },
        label = "PlayPauseTransition"
    ) { playing ->
        IconButton(onClick = onClick) {
            if (playing) {
                Icon(Icons.Default.Pause, contentDescription = "Pausar")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir")
            }
        }
    }
}

@Composable
fun AnimatedBottomNavIcon(isSelected: Boolean, icon: @Composable () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1.0f,
        animationSpec = tween(180),
        label = "NavIconScale"
    )

    Box(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        icon()
    }
}

@Composable
fun animatedThemeColors(isDarkTheme: Boolean): Color {
    val backgroundColor by animateColorAsState(
        targetValue = if (isDarkTheme) Color(0xFF0A0E17) else Color(0xFFF8FAFC),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "ThemeBackground"
    )
    return backgroundColor
}

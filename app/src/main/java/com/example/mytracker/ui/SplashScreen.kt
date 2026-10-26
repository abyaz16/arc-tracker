package com.example.mytracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytracker.R
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Radius
import com.example.mytracker.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var exiting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Aligning trajectory...") }
    val progressAnimatable = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )

    LaunchedEffect(Unit) {
        progressAnimatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(1500, easing = FastOutSlowInEasing)
        ) {
            statusText = when {
                value < 0.35f -> "Aligning trajectory..."
                value < 0.75f -> "Preparing workspace..."
                value < 1.0f -> "Calibrating focus..."
                else -> "Ready"
            }
        }
        delay(180)
        exiting = true
        delay(450)
        onComplete()
    }

    AnimatedVisibility(
        visible = !exiting,
        exit = fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 1.04f),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(C.canvas)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    exiting = true
                    onComplete()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(Space.s6)
            ) {
                // App Logo with subtle float animation
                Image(
                    painter = painterResource(R.drawable.ic_app_icon),
                    contentDescription = "Arc Logo",
                    modifier = Modifier
                        .size(84.dp)
                        .graphicsLayer { translationY = floatOffset }
                        .shadow(16.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                )

                Spacer(Modifier.height(Space.s6))

                // Brand Name & Tagline
                Icon(
                    painter = painterResource(R.drawable.arc_wordmark),
                    contentDescription = "Arc",
                    tint = C.textPrimary,
                    modifier = Modifier.height(34.dp)
                )

                Spacer(Modifier.height(Space.s3))

                Text(
                    text = "SHAPE YOUR TRAJECTORY",
                    style = CadenceType.caption.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    ),
                    color = C.textSecondary,
                )

                Spacer(Modifier.height(Space.s8))

                // Trajectory Loading Track
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(Radius.full))
                        .background(C.surfaceSunken)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressAnimatable.value.coerceIn(0f, 1f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(Radius.full))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(C.sky.deep, C.butter.deep, C.coral.deep)
                                )
                            )
                    )
                }

                Spacer(Modifier.height(Space.s4))

                Text(
                    text = statusText,
                    style = CadenceType.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = C.textSecondary,
                )
            }

            Text(
                text = "Tap anywhere to skip",
                style = CadenceType.caption.copy(fontSize = 11.sp),
                color = C.textMuted.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Space.s8)
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CormorantGaramondFamily
import com.example.ui.theme.DeepLavender
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.PrimaryLavender
import com.example.ui.theme.SoftPeach

@Composable
fun FeeloraBrandMark(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    withSoftFrame: Boolean = false
) {
    val tokens = LifeOsThemeTokensProvider.current
    val emblemModifier = if (withSoftFrame) {
        val frameShape = RoundedCornerShape(size * 0.30f)
        modifier
            .size(size)
            .clip(frameShape)
            .background(
                Brush.linearGradient(
                    colors = if (tokens.isDark) {
                        listOf(tokens.surfaceElevated, tokens.surfaceCard)
                    } else {
                        listOf(Color(0xFFFAF7F0), tokens.surfaceHighlight)
                    }
                )
            )
            .border(1.dp, tokens.borderAccent.copy(alpha = 0.55f), frameShape)
            .padding(size * 0.12f)
    } else {
        modifier.size(size)
    }
    Box(
        modifier = emblemModifier.semantics { contentDescription = "FEELORA botanical emblem" },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_feelora_emblem),
            contentDescription = null,
            modifier = Modifier.size(size * if (withSoftFrame) 0.78f else 1f)
        )
    }
}

@Composable
fun FeeloraBrandLockup(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 40.dp,
    showTagline: Boolean = false,
    centered: Boolean = false
) {
    val tokens = LifeOsThemeTokensProvider.current
    if (centered) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeeloraBrandMark(size = emblemSize, withSoftFrame = true)
            Text(
                text = "FEELORA",
                fontFamily = CormorantGaramondFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = 3.2.sp,
                color = tokens.inkPrimary
            )
            if (showTagline) {
                Text(
                    text = "Your space to grow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeeloraBrandMark(size = emblemSize, withSoftFrame = true)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "FEELORA",
                    fontFamily = CormorantGaramondFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 2.2.sp,
                    color = tokens.inkPrimary
                )
                if (showTagline) {
                    Text(
                        text = "Calm clarity · Personal growth",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.inkSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun FeeloraEditorialAccentBar(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(DeepLavender, PrimaryLavender, SoftPeach)
                )
            )
    )
}

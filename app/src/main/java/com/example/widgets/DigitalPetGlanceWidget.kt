package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.R
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DigitalPetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DigitalPetGlanceWidget()
}

class DigitalPetGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = withContext(Dispatchers.IO) {
            UserPreferencesRepository(context).preferencesFlow.first()
        }
        val petBitmap = decodeKuttaWidgetBitmap(
            context = context,
            happiness = prefs.petHappiness,
            energy = prefs.petEnergy
        )

        provideContent {
            GlanceTheme {
                DigitalPetWidgetContent(prefs = prefs, petBitmap = petBitmap)
            }
        }
    }

    @Composable
    private fun DigitalPetWidgetContent(prefs: UserPreferencesState, petBitmap: Bitmap) {
        val size = LocalSize.current
        val isTall = size.height >= 145.dp
        val isWide = size.width >= 200.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(LifeOsGlanceColors.BgObsidian)
                .cornerRadius(20.dp)
                .padding(12.dp)
        ) {
            GlanceWidgetHeader(
                title = "KUTTA",
                badgeText = "Lv.${prefs.level} · ${prefs.petStage.title}",
                headerAction = openLifeOsRouteAction("growth")
            )

            Spacer(GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(LifeOsGlanceColors.SurfaceCharcoal)
                    .cornerRadius(14.dp)
                    .clickable(openLifeOsRouteAction("growth"))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(petBitmap),
                    contentDescription = "KUTTA companion",
                    modifier = GlanceModifier.size(if (isWide) 68.dp else 54.dp)
                )
                Spacer(GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Mood ${prefs.petHappiness}%",
                        style = TextStyle(color = LifeOsGlanceColors.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = (prefs.petHappiness / 100f).coerceIn(0f, 1f),
                        modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                        color = LifeOsGlanceColors.Cyan,
                        backgroundColor = LifeOsGlanceColors.ProgressTrack
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = "Energy ${prefs.petEnergy}%",
                        style = TextStyle(color = LifeOsGlanceColors.Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = (prefs.petEnergy / 100f).coerceIn(0f, 1f),
                        modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                        color = LifeOsGlanceColors.Violet,
                        backgroundColor = LifeOsGlanceColors.ProgressTrack
                    )
                    if (isTall) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = "Water: ${prefs.waterIntakeMlToday}/${prefs.dailyWaterGoalMl} ml",
                            style = TextStyle(color = LifeOsGlanceColors.InkSecondary, fontSize = 10.sp),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                PetActionButton(
                    label = "Snack (+12E)",
                    actionType = "FEED",
                    accentColor = LifeOsGlanceColors.Violet,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                PetActionButton(
                    label = "Cheer (+10M)",
                    actionType = "PLAY",
                    accentColor = LifeOsGlanceColors.Cyan,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }

    @Composable
    private fun PetActionButton(
        label: String,
        actionType: String,
        accentColor: androidx.glance.unit.ColorProvider,
        modifier: GlanceModifier
    ) {
        Box(
            modifier = modifier
                .background(LifeOsGlanceColors.SurfaceElevated)
                .cornerRadius(10.dp)
                .clickable(
                    actionRunCallback<PetInteractWidgetAction>(
                        actionParametersOf(PetInteractWidgetAction.KEY_PET_ACTION to actionType)
                    )
                )
                .padding(vertical = 7.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        val fallback = RemoteViews(context.packageName, R.layout.widget_loading)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
    }
}

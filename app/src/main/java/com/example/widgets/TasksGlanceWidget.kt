package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.example.R
import com.example.data.local.LifeOsDatabase
import com.example.data.local.LifeOsSeeder
import com.example.data.local.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksGlanceWidget()
}

class TasksGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = withContext(Dispatchers.IO) {
            val db = LifeOsDatabase.getInstance(context)
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            db.taskDao().getAllTasksOnce()
                .sortedWith(compareBy<TaskEntity> { it.isCompleted }.thenBy { it.deadlineEpochDay })
                .take(8)
        }

        provideContent {
            GlanceTheme {
                TasksWidgetContent(tasks = tasks)
            }
        }
    }

    @Composable
    private fun TasksWidgetContent(tasks: List<TaskEntity>) {
        val size = LocalSize.current
        val isCompactHeight = size.height < 150.dp
        val isWide = size.width >= 220.dp
        val pendingCount = tasks.count { !it.isCompleted }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(LifeOsGlanceColors.BgObsidian)
                .cornerRadius(20.dp)
                .padding(12.dp)
        ) {
            GlanceWidgetHeader(
                title = "Tasks",
                badgeText = "$pendingCount left",
                iconRes = R.drawable.ic_widget_check,
                headerAction = openLifeOsRouteAction("tasks"),
                trailingIconRes = R.drawable.ic_widget_add,
                trailingAction = openLifeOsRouteAction("create_task")
            )

            Spacer(GlanceModifier.height(8.dp))

            if (tasks.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(LifeOsGlanceColors.SurfaceCharcoal)
                        .cornerRadius(14.dp)
                        .clickable(openLifeOsRouteAction("create_task"))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All clear! Tap to add a task",
                        style = TextStyle(color = LifeOsGlanceColors.InkSecondary, fontSize = 12.sp)
                    )
                }
            } else if (isCompactHeight) {
                tasks.take(2).forEach { task ->
                    TaskWidgetRow(task = task, showMeta = isWide)
                    Spacer(GlanceModifier.height(4.dp))
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items = tasks, itemId = { it.id }) { task ->
                        Column {
                            TaskWidgetRow(task = task, showMeta = isWide)
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TaskWidgetRow(task: TaskEntity, showMeta: Boolean) {
        val toggleAction = actionRunCallback<ToggleTaskWidgetAction>(
            actionParametersOf(ToggleTaskWidgetAction.KEY_TASK_ID to task.id)
        )
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(LifeOsGlanceColors.SurfaceCharcoal)
                .cornerRadius(12.dp)
                .clickable(toggleAction)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(
                    if (task.isCompleted) R.drawable.ic_widget_check else R.drawable.ic_widget_uncheck
                ),
                contentDescription = if (task.isCompleted) "Completed" else "Mark complete",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = task.title,
                    style = TextStyle(
                        color = if (task.isCompleted) LifeOsGlanceColors.InkMuted else LifeOsGlanceColors.InkPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 1
                )
                if (showMeta) {
                    Text(
                        text = "${task.category.label} · +${task.xpReward} XP",
                        style = TextStyle(
                            color = LifeOsGlanceColors.InkSecondary,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
            }
            if (showMeta) {
                Spacer(GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(priorityColorProvider(task.priority))
                ) {}
            }
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

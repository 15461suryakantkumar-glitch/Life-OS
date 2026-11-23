package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ChallengeEntity
import com.example.data.local.HabitEntity
import com.example.data.local.PetAccessory
import com.example.data.local.PetGrowthStage
import com.example.data.local.TaskCategory
import com.example.ui.components.AnimatedStreakPill
import com.example.ui.components.DigitalPetCanvas
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianBase
import com.example.ui.viewmodel.BadgeCatalog
import com.example.ui.viewmodel.LifeOsUiState

@Composable
fun GrowthScreen(
    state: LifeOsUiState,
    onInteractPet: (String) -> Unit,
    onEquipAccessory: (PetAccessory) -> Unit,
    onClaimChallenge: (ChallengeEntity) -> Unit,
    onToggleHabit: (HabitEntity) -> Unit,
    onAddHabit: (String, TaskCategory, Int) -> Unit
) {
    val prefs = state.prefs
    var showAddHabitDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("growth_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Growth & Companion", style = MaterialTheme.typography.headlineLarge, color = InkPrimary)
            Text("XP progression, streaks, challenges, badges & your evolving digital companion", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
        }

        // Digital Pet Sanctuary Card
        item {
            GradientGlowCard(modifier = Modifier.testTag("pet_sanctuary_card")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(prefs.petName, style = MaterialTheme.typography.headlineMedium, color = InkPrimary)
                        Text("Stage: ${prefs.petStage.title}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                    StatusPill(text = "Lv.${prefs.level}", color = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    DigitalPetCanvas(
                        petName = prefs.petName,
                        stage = prefs.petStage,
                        happiness = prefs.petHappiness,
                        energy = prefs.petEnergy,
                        accessory = prefs.equippedAccessory,
                        size = 164.dp,
                        onTap = { onInteractPet("TAP") }
                    )
                }

                Text(
                    text = prefs.petStage.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap ${prefs.petName} for a status pulse. XP & evolution advance from completed tasks, habits, and focus sessions.",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )

                AnimatedVisibility(
                    visible = state.petReactionBanner != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        StatusPill(
                            text = state.petReactionBanner.orEmpty(),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val animatedHappiness by animateIntAsState(
                    targetValue = prefs.petHappiness,
                    animationSpec = tween(350),
                    label = "growth_pet_happiness"
                )
                val animatedEnergy by animateIntAsState(
                    targetValue = prefs.petEnergy,
                    animationSpec = tween(350),
                    label = "growth_pet_energy"
                )

                // Happiness & Energy bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Happiness · ${animatedHappiness}%", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                        Spacer(Modifier.height(4.dp))
                        NeonProgressBar(progress = prefs.petHappiness / 100f, heightDp = 6)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Energy · ${animatedEnergy}%", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                        Spacer(Modifier.height(4.dp))
                        NeonProgressBar(progress = prefs.petEnergy / 100f, heightDp = 6)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onInteractPet("FEED") },
                        modifier = Modifier.weight(1f).testTag("pet_feed_btn")
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Charge", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onInteractPet("PLAY") },
                        modifier = Modifier.weight(1f).testTag("pet_play_btn")
                    ) {
                        Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onInteractPet("REST") },
                        modifier = Modifier.weight(1f).testTag("pet_rest_btn")
                    ) {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rest", maxLines = 1)
                    }
                }
            }
        }

        // Accessories & Growth Stages
        item {
            ObsidianCard {
                SectionHeader(title = "Companion Accessories", subtitle = "Unlock gear as you level up")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PetAccessory.entries.forEach { acc ->
                        val unlocked = prefs.level >= acc.requiredLevel
                        val equipped = prefs.equippedAccessory == acc
                        Column(
                            modifier = Modifier
                                .width(136.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (equipped) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else CharcoalElevated)
                                .border(
                                    1.dp,
                                    if (equipped) MaterialTheme.colorScheme.primary else BorderHairline,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onEquipAccessory(acc) }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(acc.label, style = MaterialTheme.typography.labelLarge, color = InkPrimary, maxLines = 1)
                                if (!unlocked) {
                                    Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = InkMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (unlocked) acc.bonusLabel else "Unlock Lv.${acc.requiredLevel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (equipped) MaterialTheme.colorScheme.secondary else InkSecondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Evolution Roadmap", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PetGrowthStage.entries.forEach { stage ->
                        val reached = prefs.level >= stage.minLevel
                        StatusPill(
                            text = "Lv.${stage.minLevel} ${stage.title}",
                            color = if (reached) MaterialTheme.colorScheme.secondary else InkMuted
                        )
                    }
                }
            }
        }

        // XP, Level & Streaks Summary
        item {
            val animatedXp by animateIntAsState(
                targetValue = prefs.totalXp,
                animationSpec = tween(420),
                label = "growth_xp_counter"
            )
            ObsidianCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("XP Telemetry", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                        Text("$animatedXp Total XP · ${prefs.xpIntoCurrentLevel}/${prefs.xpPerLevel} to Lv.${prefs.level + 1}", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AnimatedStreakPill(streakDays = prefs.dailyStreak, label = "${prefs.dailyStreak}d")
                        StatusPill(text = "${prefs.weeklyStreak}w", color = EmeraldSuccess)
                    }
                }
                Spacer(Modifier.height(10.dp))
                NeonProgressBar(progress = prefs.xpIntoCurrentLevel.toFloat() / prefs.xpPerLevel)
            }
        }

        // Habits Manager
        item {
            SectionHeader(
                title = "Daily Habits",
                subtitle = "Earn XP once per day per habit",
                trailing = {
                    TextButton(onClick = { showAddHabitDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Habit")
                    }
                }
            )
        }

        if (state.habits.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "No habits added yet",
                    subtitle = "Tap + Add Habit or ask LIFE AI to create one."
                )
            }
        } else {
            items(state.habits, key = { "hab_${it.id}" }) { habit ->
                val doneToday = habit.isCompletedOn(state.todayEpochDay)
                ObsidianCard(onClick = { onToggleHabit(habit) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (doneToday) EmeraldSuccess else InkMuted
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(habit.title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Text("Streak: ${habit.currentStreak}d (Best ${habit.bestStreak}d) · +${habit.xpReward} XP", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                        StatusPill(
                            text = if (doneToday) "DONE" else "LOG",
                            color = if (doneToday) EmeraldSuccess else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Daily & Weekly Challenges
        item {
            SectionHeader(title = "Challenges", subtitle = "Complete goals to earn bonus XP")
        }

        items(state.challenges, key = { "chal_${it.id}" }) { challenge ->
            val readyToClaim = !challenge.isClaimed && challenge.currentProgress >= challenge.targetCount
            ObsidianCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                text = if (challenge.isWeekly) "WEEKLY" else "DAILY",
                                color = if (challenge.isWeekly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            Text(challenge.title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(challenge.description, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onClaimChallenge(challenge) },
                        enabled = readyToClaim,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (readyToClaim) EmeraldSuccess else CharcoalElevated
                        )
                    ) {
                        Text(
                            when {
                                challenge.isClaimed -> "Claimed"
                                readyToClaim -> "+${challenge.xpReward} XP"
                                else -> "${challenge.currentProgress}/${challenge.targetCount}"
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                NeonProgressBar(
                    progress = challenge.currentProgress.toFloat() / challenge.targetCount.coerceAtLeast(1),
                    heightDp = 6
                )
            }
        }

        // Achievement Badges
        item {
            SectionHeader(title = "Achievement Badges", subtitle = "${prefs.unlockedBadges.size}/${BadgeCatalog.all.size} unlocked")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeCatalog.all.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { badge ->
                            val unlocked = prefs.unlockedBadges.contains(badge.id)
                            ObsidianCard(
                                modifier = Modifier.weight(1f),
                                borderColor = if (unlocked) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f) else BorderHairline
                            ) {
                                Icon(
                                    imageVector = if (unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (unlocked) AmberWarning else InkMuted
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(badge.title, style = MaterialTheme.typography.titleSmall, color = if (unlocked) InkPrimary else InkMuted)
                                Text(badge.description, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddHabitDialog) {
        var title by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf(TaskCategory.HEALTH) }
        AlertDialog(
            onDismissRequest = { showAddHabitDialog = false },
            containerColor = ObsidianBase,
            title = { Text("New Daily Habit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Habit name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TaskCategory.entries.forEach { c ->
                            FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c.label) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddHabit(title, cat, 7)
                        showAddHabitDialog = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddHabitDialog = false }) { Text("Cancel") }
            }
        )
    }
}

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CompanionMood
import com.example.data.local.CompanionSpecies
import com.example.data.local.PersonalityQuizEvaluator
import com.example.data.local.PetAccessory
import com.example.data.local.PetGrowthStage
import com.example.ui.theme.LifeOsThemeTokensProvider

data class PersonalityQuizQuestion(
    val prompt: String,
    val options: List<String>
)

val LIFE_OS_PERSONALITY_QUESTIONS = listOf(
    PersonalityQuizQuestion(
        prompt = "1. How do you like to tackle your daily tasks?",
        options = listOf(
            "One steady calm step at a time",
            "Fast sprints with bursts of curiosity",
            "Quiet deep dives late into the evening",
            "Checking wins off with friends & streaks"
        )
    ),
    PersonalityQuizQuestion(
        prompt = "2. Which study or work space feels most like you?",
        options = listOf(
            "A warm desk, tea mug, and clean checklist",
            "Minimalist quiet desk after sunset",
            "Sticky notes, sketches, and fresh timers",
            "Cozy lounge with uplifting background beats"
        )
    ),
    PersonalityQuizQuestion(
        prompt = "3. What motivates you when a day gets busy?",
        options = listOf(
            "Gentle warmth and zero pressure",
            "Seeing momentum build quickly",
            "Protecting my streaks and daily rituals",
            "Clear focus and thoughtful reflection"
        )
    )
)

@Composable
fun PersonalityQuizQuestionCard(
    questionIndex: Int,
    selectedAnswer: Int,
    onSelectAnswer: (Int) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val question = LIFE_OS_PERSONALITY_QUESTIONS[questionIndex]

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = question.prompt,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.inkPrimary
        )
        question.options.forEachIndexed { idx, option ->
            val selected = selectedAnswer == idx
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.smallRadius))
                    .clickable { onSelectAnswer(idx) }
                    .testTag("quiz_q${questionIndex}_opt$idx"),
                shape = RoundedCornerShape(tokens.smallRadius),
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else tokens.surfaceElevated,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else tokens.borderSubtle
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.inkPrimary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun AppointedCompanionRevealCard(
    species: CompanionSpecies,
    petName: String,
    onPetNameChange: (String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    GradientGlowCard(contentPadding = PaddingValues(14.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusPill(
                text = "YOUR MATCH · ${species.archetypeTag.uppercase()}",
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            DigitalPetCanvas(
                petName = petName.ifBlank { species.defaultName },
                stage = PetGrowthStage.SPARK,
                happiness = 75,
                energy = 80,
                accessory = PetAccessory.NONE,
                species = species,
                mood = CompanionMood.JOYFUL,
                size = 124.dp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = species.displayTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.inkPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = species.personalityBio,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSecondary
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = petName,
                onValueChange = onPetNameChange,
                label = { Text("Companion Nickname") },
                placeholder = { Text(species.defaultName) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("appointed_pet_name_input")
            )
        }
    }
}

/**
 * Reusable modal to retake the personality match quiz from Profile, Growth, or Settings.
 * Shows ONLY the single appointed companion result — never an open catalog of all companions.
 */
@Composable
fun RetakePersonalityQuizDialog(
    onDismiss: () -> Unit,
    onConfirmCompanion: (CompanionSpecies, String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var q1 by remember { mutableIntStateOf(0) }
    var q2 by remember { mutableIntStateOf(0) }
    var q3 by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(0) } // 0 = questions, 1 = reveal
    val appointed = remember(q1, q2, q3) { PersonalityQuizEvaluator.appointCompanion(q1, q2, q3) }
    var customName by remember(appointed) { mutableStateOf(appointed.defaultName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceBase,
        title = {
            Text(
                text = if (step == 0) "Personality Match" else "Meet ${appointed.displayTitle}",
                color = tokens.inkPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step == 0) {
                    Text(
                        text = "Answer 3 quick questions to match with a tailored companion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                    PersonalityQuizQuestionCard(0, q1) { q1 = it }
                    PersonalityQuizQuestionCard(1, q2) { q2 = it }
                    PersonalityQuizQuestionCard(2, q3) { q3 = it }
                } else {
                    AppointedCompanionRevealCard(
                        species = appointed,
                        petName = customName,
                        onPetNameChange = { customName = it }
                    )
                }
            }
        },
        confirmButton = {
            if (step == 0) {
                Button(onClick = {
                    customName = appointed.defaultName
                    step = 1
                }) {
                    Text("Reveal Match", maxLines = 1, softWrap = false)
                }
            } else {
                Button(onClick = { onConfirmCompanion(appointed, customName) }) {
                    Text("Appoint Companion", maxLines = 1, softWrap = false)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (step == 1) step = 0 else onDismiss()
            }) {
                Text(if (step == 1) "Back" else "Cancel", maxLines = 1, softWrap = false)
            }
        }
    )
}

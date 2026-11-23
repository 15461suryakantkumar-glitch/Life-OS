package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PetGrowthStage
import com.example.data.local.UserPreferencesState
import com.example.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Life OS`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Life OS", appName)
    }

    @Test
    fun `xp level and pet stage progress properly`() {
        val level1 = UserPreferencesState(totalXp = 100)
        assertEquals(1, level1.level)
        assertEquals(PetGrowthStage.SPARK, level1.petStage)

        val level5 = UserPreferencesState(totalXp = 1050)
        assertEquals(5, level5.level)
        assertEquals(PetGrowthStage.SENTINEL, level5.petStage)
    }

    @Test
    fun `countdown days calculation works`() {
        val today = DateTimeUtils.todayEpochDay()
        assertEquals(5L, DateTimeUtils.daysBetween(today, today + 5))
    }
}

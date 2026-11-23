package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, deadlineEpochDay ASC, createdAtMillis DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY dateEpochDay ASC, startMinuteOfDay ASC")
    fun observeAllEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY dateEpochDay ASC, startMinuteOfDay ASC")
    suspend fun getAllEventsOnce(): List<CalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity): Long

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    @Delete
    suspend fun deleteEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events")
    suspend fun clearAll()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id ASC")
    fun observeAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY id ASC")
    suspend fun getAllHabitsOnce(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("DELETE FROM habits")
    suspend fun clearAll()
}

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_configs ORDER BY sortOrder ASC")
    fun observeWidgets(): Flow<List<WidgetConfigEntity>>

    @Query("SELECT * FROM widget_configs ORDER BY sortOrder ASC")
    suspend fun getWidgetsOnce(): List<WidgetConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(widgets: List<WidgetConfigEntity>)

    @Update
    suspend fun updateWidget(widget: WidgetConfigEntity)
}

@Dao
interface QuickNoteDao {
    @Query("SELECT * FROM quick_notes WHERE id = 1")
    fun observeNote(): Flow<QuickNoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: QuickNoteEntity)
}

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage_logs ORDER BY dateEpochDay DESC, minutesUsed DESC")
    fun observeAllLogs(): Flow<List<AppUsageLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AppUsageLogEntity): Long

    @Delete
    suspend fun deleteLog(log: AppUsageLogEntity)

    @Query("DELETE FROM app_usage_logs")
    suspend fun clearAll()
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges ORDER BY isWeekly ASC, id ASC")
    fun observeAllChallenges(): Flow<List<ChallengeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenge(challenge: ChallengeEntity)

    @Update
    suspend fun updateChallenge(challenge: ChallengeEntity)

    @Query("DELETE FROM challenges")
    suspend fun clearAll()
}

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY xp DESC")
    fun observeAllFriends(): Flow<List<FriendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendEntity)

    @Delete
    suspend fun deleteFriend(friend: FriendEntity)

    @Query("DELETE FROM friends")
    suspend fun clearAll()
}

@Dao
interface AiMessageDao {
    @Query("SELECT * FROM ai_messages ORDER BY timestampMillis ASC, id ASC")
    fun observeMessages(): Flow<List<AiMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: AiMessageEntity)

    @Query("DELETE FROM ai_messages")
    suspend fun clearAll()
}

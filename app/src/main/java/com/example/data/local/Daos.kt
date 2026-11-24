package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskSectionDao {
    @Query("SELECT * FROM task_sections ORDER BY sortOrder ASC, createdAtMillis ASC")
    fun observeAllSections(): Flow<List<TaskSectionEntity>>

    @Query("SELECT * FROM task_sections ORDER BY sortOrder ASC, createdAtMillis ASC")
    suspend fun getAllSectionsOnce(): List<TaskSectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: TaskSectionEntity): Long

    @Update
    suspend fun updateSection(section: TaskSectionEntity)

    @Delete
    suspend fun deleteSection(section: TaskSectionEntity)

    @Query("DELETE FROM task_sections")
    suspend fun clearAll()
}

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

    @Query("DELETE FROM tasks WHERE sectionId = :sectionId")
    suspend fun deleteTasksBySection(sectionId: Long)

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

    @Query("SELECT * FROM app_usage_logs WHERE dateEpochDay = :epochDay")
    suspend fun getLogsForDay(epochDay: Long): List<AppUsageLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AppUsageLogEntity): Long

    @Delete
    suspend fun deleteLog(log: AppUsageLogEntity)

    @Query("DELETE FROM app_usage_logs")
    suspend fun clearAll()
}

@Dao
interface DailyProgressDao {
    @Query("SELECT * FROM daily_progress_logs ORDER BY epochDay ASC")
    fun observeAllLogs(): Flow<List<DailyProgressLogEntity>>

    @Query("SELECT * FROM daily_progress_logs WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getForDay(epochDay: Long): DailyProgressLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DailyProgressLogEntity)

    @Query("DELETE FROM daily_progress_logs")
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

    @Query("SELECT * FROM friends WHERE (friendCode != '' AND friendCode = :code) OR LOWER(handle) = LOWER(:handle) LIMIT 1")
    suspend fun findByCodeOrHandle(code: String, handle: String): FriendEntity?

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

@Dao
interface CycleLogDao {
    @Query("SELECT * FROM cycle_logs ORDER BY startEpochDay DESC, id DESC")
    fun observeAllLogs(): Flow<List<CycleLogEntity>>

    @Query("SELECT * FROM cycle_logs ORDER BY startEpochDay DESC, id DESC")
    suspend fun getAllLogsOnce(): List<CycleLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CycleLogEntity): Long

    @Update
    suspend fun updateLog(log: CycleLogEntity)

    @Delete
    suspend fun deleteLog(log: CycleLogEntity)

    @Query("DELETE FROM cycle_logs")
    suspend fun clearAll()
}

@Dao
interface DailyCheckInDao {
    @Query("SELECT * FROM daily_check_ins ORDER BY epochDay DESC")
    fun observeAllCheckIns(): Flow<List<DailyCheckInEntity>>

    @Query("SELECT * FROM daily_check_ins WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getForDay(epochDay: Long): DailyCheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DailyCheckInEntity)

    @Query("DELETE FROM daily_check_ins")
    suspend fun clearAll()
}

@Dao
interface SuruMemoryDao {
    @Query("SELECT * FROM suru_memories ORDER BY updatedAtMillis DESC, id DESC")
    fun observeAllMemories(): Flow<List<SuruMemoryEntity>>

    @Query("SELECT * FROM suru_memories ORDER BY updatedAtMillis DESC, id DESC")
    suspend fun getAllMemoriesOnce(): List<SuruMemoryEntity>

    @Query("SELECT * FROM suru_memories WHERE LOWER(keyLabel) = LOWER(:keyLabel) LIMIT 1")
    suspend fun findByKey(keyLabel: String): SuruMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: SuruMemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: SuruMemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: SuruMemoryEntity)

    @Query("DELETE FROM suru_memories")
    suspend fun clearAll()
}

@Dao
interface SuruStudyPlanDao {
    @Query("SELECT * FROM suru_study_plans ORDER BY createdAtMillis DESC")
    fun observeAllPlans(): Flow<List<SuruStudyPlanEntity>>

    @Query("SELECT * FROM suru_study_plans ORDER BY createdAtMillis DESC")
    suspend fun getAllPlansOnce(): List<SuruStudyPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: SuruStudyPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: SuruStudyPlanEntity)

    @Delete
    suspend fun deletePlan(plan: SuruStudyPlanEntity)

    @Query("DELETE FROM suru_study_plans")
    suspend fun clearAll()
}

@Dao
interface NotificationHistoryDao {
    @Query("SELECT * FROM notification_history ORDER BY createdAtMillis DESC, id DESC LIMIT 120")
    fun observeAll(): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT * FROM notification_history ORDER BY createdAtMillis DESC, id DESC LIMIT 120")
    suspend fun getAllOnce(): List<NotificationHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: NotificationHistoryEntity): Long

    @Query("UPDATE notification_history SET isRead = 1")
    suspend fun markAllRead()

    @Query("UPDATE notification_history SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM notification_history")
    suspend fun clearAll()
}

@Dao
interface AiProviderUsageDao {
    @Query("SELECT * FROM ai_provider_usage ORDER BY createdAtMillis DESC LIMIT 200")
    fun observeAll(): Flow<List<AiProviderUsageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AiProviderUsageEntity): Long

    @Query("DELETE FROM ai_provider_usage")
    suspend fun clearAll()
}

@Dao
interface TogetherOutboxDao {
    @Query("SELECT * FROM together_outbox ORDER BY createdAtMillis ASC")
    fun observeAll(): Flow<List<TogetherOutboxEntity>>

    @Query("SELECT * FROM together_outbox ORDER BY createdAtMillis ASC")
    suspend fun getAllOnce(): List<TogetherOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TogetherOutboxEntity): Long

    @Query("DELETE FROM together_outbox WHERE clientMessageId = :clientMessageId")
    suspend fun deleteByClientId(clientMessageId: String)

    @Query("DELETE FROM together_outbox")
    suspend fun clearAll()
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM study_subjects ORDER BY createdAtMillis ASC, id ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM study_subjects ORDER BY createdAtMillis ASC, id ASC")
    suspend fun getAllOnce(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subject: SubjectEntity): Long

    @Delete
    suspend fun delete(subject: SubjectEntity)

    @Query("DELETE FROM study_subjects")
    suspend fun clearAll()
}

@Dao
interface StudyChapterDao {
    @Query("SELECT * FROM study_chapters ORDER BY sortOrder ASC, createdAtMillis ASC, id ASC")
    fun observeAll(): Flow<List<StudyChapterEntity>>

    @Query("SELECT * FROM study_chapters ORDER BY sortOrder ASC, createdAtMillis ASC, id ASC")
    suspend fun getAllOnce(): List<StudyChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: StudyChapterEntity): Long

    @Update
    suspend fun update(chapter: StudyChapterEntity)

    @Delete
    suspend fun delete(chapter: StudyChapterEntity)

    @Query("DELETE FROM study_chapters WHERE subjectId = :subjectId")
    suspend fun deleteBySubjectId(subjectId: Long)

    @Query("DELETE FROM study_chapters")
    suspend fun clearAll()
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY endedAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY endedAtMillis DESC, id DESC")
    suspend fun getAllOnce(): List<StudySessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: StudySessionEntity): Long

    @Delete
    suspend fun delete(session: StudySessionEntity)

    @Query("DELETE FROM study_sessions")
    suspend fun clearAll()
}

@Dao
interface StudyResourceDao {
    @Query("SELECT * FROM study_resources ORDER BY updatedAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<StudyResourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(resource: StudyResourceEntity): Long

    @Delete
    suspend fun delete(resource: StudyResourceEntity)

    @Query("DELETE FROM study_resources WHERE subjectId = :subjectId")
    suspend fun deleteBySubjectId(subjectId: Long)

    @Query("DELETE FROM study_resources")
    suspend fun clearAll()
}

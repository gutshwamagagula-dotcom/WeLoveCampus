package com.welovecampus.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * DAOs (Data Access Objects). Room checks every SQL query at compile time.
 * Abstract classes (not interfaces) so the @Transaction helpers can have a body.
 * Functions returning Flow re-emit automatically whenever the table changes,
 * which is how the screens update themselves after a sync.
 */

@Dao
abstract class ModuleDao {
    @Query("SELECT * FROM modules ORDER BY code")
    abstract fun observeAll(): Flow<List<ModuleEntity>>

    @Query("SELECT * FROM modules ORDER BY code")
    abstract suspend fun getAll(): List<ModuleEntity>

    @Upsert
    abstract suspend fun upsertAll(items: List<ModuleEntity>)

    @Query("DELETE FROM modules")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(items: List<ModuleEntity>) { clear(); upsertAll(items) }
}

@Dao
abstract class TimetableDao {
    @Query("SELECT * FROM timetable_slots ORDER BY dayOfWeek, startTime")
    abstract fun observeAll(): Flow<List<TimetableSlotEntity>>

    @Query("SELECT * FROM timetable_slots ORDER BY dayOfWeek, startTime")
    abstract suspend fun getAll(): List<TimetableSlotEntity>

    @Upsert
    abstract suspend fun upsertAll(items: List<TimetableSlotEntity>)

    @Query("DELETE FROM timetable_slots")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(items: List<TimetableSlotEntity>) { clear(); upsertAll(items) }
}

@Dao
abstract class AssignmentDao {
    @Query("SELECT * FROM assignments ORDER BY dueDate")
    abstract fun observeAll(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE assignmentId = :id")
    abstract fun observeById(id: String): Flow<AssignmentEntity?>

    @Query("SELECT * FROM assignments")
    abstract suspend fun getAll(): List<AssignmentEntity>

    @Query("SELECT * FROM assignments WHERE statusSynced = 0")
    abstract suspend fun getUnsynced(): List<AssignmentEntity>

    @Query("UPDATE assignments SET submitted = 1, submittedAt = :at, statusSynced = 0 WHERE assignmentId = :id")
    abstract suspend fun markSubmitted(id: String, at: Long)

    @Query("UPDATE assignments SET statusSynced = 1 WHERE assignmentId = :id")
    abstract suspend fun markStatusSynced(id: String)

    @Upsert
    abstract suspend fun upsertAll(items: List<AssignmentEntity>)

    @Query("DELETE FROM assignments")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(items: List<AssignmentEntity>) { clear(); upsertAll(items) }
}

@Dao
abstract class NoteDao {
    /** Notes waiting to be deleted are hidden from the user straight away. */
    @Query("SELECT * FROM notes WHERE syncStatus != 'PENDING_DELETE' ORDER BY lastModified DESC")
    abstract fun observeVisible(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE noteId = :id")
    abstract suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE syncStatus != 'SYNCED'")
    abstract suspend fun getPending(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE syncStatus != 'SYNCED'")
    abstract fun observePendingCount(): Flow<Int>

    @Upsert
    abstract suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE noteId = :id")
    abstract suspend fun deleteById(id: String)
}

@Dao
abstract class AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY timestamp DESC")
    abstract fun observeAll(): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcements ORDER BY timestamp DESC")
    abstract suspend fun getAll(): List<AnnouncementEntity>

    @Upsert
    abstract suspend fun upsertAll(items: List<AnnouncementEntity>)

    @Query("DELETE FROM announcements")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(items: List<AnnouncementEntity>) { clear(); upsertAll(items) }
}

@Dao
abstract class EventDao {
    @Query("SELECT * FROM events ORDER BY eventDate")
    abstract fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY eventDate")
    abstract suspend fun getAll(): List<EventEntity>

    @Upsert
    abstract suspend fun upsertAll(items: List<EventEntity>)

    @Query("DELETE FROM events")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(items: List<EventEntity>) { clear(); upsertAll(items) }
}

@Dao
abstract class ProfileDao {
    @Query("SELECT * FROM profile WHERE uid = :uid")
    abstract fun observe(uid: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE uid = :uid")
    abstract suspend fun get(uid: String): ProfileEntity?

    @Upsert
    abstract suspend fun upsert(profile: ProfileEntity)
}

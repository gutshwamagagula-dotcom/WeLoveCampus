package com.welovecampus.app.data.repo

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.welovecampus.app.data.remote.Fs
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * There is no separate admin website, so the app can fill an empty Firestore project
 * with realistic sample data (modules, timetable, assignments, announcements, events,
 * discussion threads). Fixed document IDs mean re-seeding overwrites instead of duplicating.
 * Dates are relative to "now" so the demo always has something due soon.
 *
 * Replace or edit this data in the Firebase console for a real institution.
 */
class DemoDataSeeder {

    data class DemoModule(val code: String, val name: String, val lecturer: String)

    val modules = listOf(
        DemoModule("OPSC6312", "Open Source Coding (Intermediate)", "Mr T. Nkosi"),
        DemoModule("PROG6212", "Programming 2B", "Ms L. van Wyk"),
        DemoModule("WEDE6021", "Web Development (Intermediate)", "Mr S. Dlamini"),
        DemoModule("INSY6112", "Information Systems 2", "Dr P. Mokoena"),
        DemoModule("PROG6221", "Programming 2A", "Ms R. Pillay")
    )

    /** Seeds only if the "modules" collection is empty. Returns true if it seeded. */
    suspend fun seedIfEmpty(): Boolean {
        val existing = Fs.db.collection(Fs.MODULES).limit(1).get(Source.SERVER).await()
        if (!existing.isEmpty) return false
        seed()
        return true
    }

    suspend fun seed() {
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val hour = TimeUnit.HOURS.toMillis(1)
        val batch: WriteBatch = Fs.db.batch()

        modules.forEach { m ->
            batch.set(Fs.db.collection(Fs.MODULES).document(m.code), mapOf("name" to m.name, "lecturer" to m.lecturer))
        }

        // ---- Timetable (Mon=1 ... Fri=5) ----
        data class Slot(val id: String, val code: String, val day: Int, val start: String, val end: String, val room: String)
        listOf(
            Slot("slot01", "OPSC6312", 1, "08:00", "10:00", "Lab 3"),
            Slot("slot02", "PROG6212", 1, "11:00", "13:00", "Room 2.14"),
            Slot("slot03", "WEDE6021", 2, "09:00", "11:00", "Room 1.05"),
            Slot("slot04", "INSY6112", 2, "13:00", "15:00", "https://teams.microsoft.com/wlc-insy"),
            Slot("slot05", "OPSC6312", 3, "10:00", "12:00", "Lab 3"),
            Slot("slot06", "PROG6221", 3, "13:00", "15:00", "Room 2.10"),
            Slot("slot07", "PROG6212", 4, "08:00", "10:00", "Room 2.14"),
            Slot("slot08", "WEDE6021", 4, "11:00", "13:00", "Lab 1"),
            Slot("slot09", "INSY6112", 5, "09:00", "11:00", "Room 3.01"),
            Slot("slot10", "PROG6221", 5, "11:30", "13:00", "Room 2.10")
        ).forEach { s ->
            val m = modules.first { it.code == s.code }
            batch.set(
                Fs.db.collection(Fs.TIMETABLE).document(s.id), mapOf(
                    "moduleCode" to s.code, "moduleName" to m.name, "dayOfWeek" to s.day,
                    "startTime" to s.start, "endTime" to s.end, "room" to s.room, "lecturer" to m.lecturer
                )
            )
        }

        // ---- Assignments ----
        data class Asg(val id: String, val code: String, val title: String, val desc: String, val due: Long)
        listOf(
            Asg("asg01", "OPSC6312", "POE Part 2 - Prototype", "Build the working prototype of your app in Android Studio (Kotlin, XML). Submit the GitHub link and a demo video.", now + 30 * hour),
            Asg("asg02", "PROG6212", "Claims System - Part 2", "Implement the lecturer claim submission and approval workflow in ASP.NET Core MVC.", now + 4 * day),
            Asg("asg03", "WEDE6021", "Pastimes e-store Project", "Complete the responsive storefront with cart and checkout pages.", now + 9 * day),
            Asg("asg04", "INSY6112", "PL/SQL Assignment", "Write stored procedures and triggers for the library database case study.", now - 2 * day),
            Asg("asg05", "PROG6221", "Chatbot Part 3", "Add the activity log and NLP features to your cybersecurity awareness chatbot.", now + 20 * day),
            Asg("asg06", "OPSC6312", "Research Report Reflection", "Write a one-page reflection on the research phase.", now + 6 * day)
        ).forEach { a ->
            batch.set(
                Fs.db.collection(Fs.ASSIGNMENTS).document(a.id), mapOf(
                    "moduleCode" to a.code, "title" to a.title, "description" to a.desc, "dueDate" to Timestamp(Date(a.due))
                )
            )
        }

        // ---- Announcements ----
        data class Ann(val id: String, val sender: String, val title: String, val msg: String, val cat: String, val ago: Long)
        listOf(
            Ann("ann01", "Library", "Extended library hours", "Library hours are extended until 22:00 during the exam period.", "ADMINISTRATIVE", 3 * hour),
            Ann("ann02", "Registrar", "Exam timetable published", "The final exam timetable is now available on the student portal.", "ACADEMIC", 1 * day),
            Ann("ann03", "Campus Security", "Evacuation drill in Block B", "There will be an evacuation drill in Block B at 10:00 today. Please follow the marshals.", "EMERGENCY", 5 * hour),
            Ann("ann04", "IT Services", "Wi-Fi maintenance", "Campus Wi-Fi will be unavailable on Saturday between 06:00 and 09:00.", "ADMINISTRATIVE", 2 * day),
            Ann("ann05", "Faculty of IT", "Guest lecture: Cloud careers", "Join our guest speaker from a leading cloud provider in the auditorium.", "ACADEMIC", 3 * day)
        ).forEach { a ->
            batch.set(
                Fs.db.collection(Fs.ANNOUNCEMENTS).document(a.id), mapOf(
                    "sender" to a.sender, "title" to a.title, "message" to a.msg, "category" to a.cat,
                    "timestamp" to Timestamp(Date(now - a.ago))
                )
            )
        }

        // ---- Events (set at fixed times of day) ----
        fun at(daysFromNow: Int, hourOfDay: Int): Timestamp {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, daysFromNow)
            c.set(Calendar.HOUR_OF_DAY, hourOfDay); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
            return Timestamp(c.time)
        }
        data class Evt(val id: String, val title: String, val desc: String, val loc: String, val ts: Timestamp)
        listOf(
            Evt("evt01", "Career Fair", "Meet over 30 employers hiring graduates and interns.", "Hall B", at(2, 9)),
            Evt("evt02", "Hackathon 2026", "24-hour coding challenge. Teams of up to 4.", "Innovation Lab", at(8, 8)),
            Evt("evt03", "Orientation: Final-year projects", "Information session on WIL and final-year projects.", "Auditorium", at(4, 14)),
            Evt("evt04", "Study Skills Workshop", "Tips for exam preparation and time management.", "Room 1.01", at(12, 11)),
            Evt("evt05", "Sports Day", "Inter-campus soccer and netball.", "Sports field", at(18, 10)),
            Evt("evt06", "Old event (hidden)", "Shows that past events are hidden from Upcoming.", "Hall A", at(-3, 10))
        ).forEach { e ->
            batch.set(
                Fs.db.collection(Fs.EVENTS).document(e.id), mapOf(
                    "title" to e.title, "description" to e.desc, "location" to e.loc, "eventDate" to e.ts
                )
            )
        }

        // ---- A couple of discussion threads ----
        listOf(
            Triple("thr01", "OPSC6312", "Firestore rules for the POE?"),
            Triple("thr02", "PROG6212", "Study group for Part 2")
        ).forEach { (id, code, title) ->
            batch.set(
                Fs.db.collection(Fs.DISCUSSIONS).document(id), mapOf(
                    "moduleCode" to code, "title" to title,
                    "body" to "Starting this thread so we can help each other out.",
                    "authorUid" to "demo", "authorName" to "Class Rep",
                    "createdAt" to Timestamp(Date(now - day)), "lastActivity" to Timestamp(Date(now - day)),
                    "replyCount" to 0
                )
            )
        }

        batch.commit().await()
    }
}

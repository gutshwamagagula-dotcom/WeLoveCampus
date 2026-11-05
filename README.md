# We Love Campus – Android app (Kotlin + XML)

Student hub app built from the *We Love Campus Planning & Design* document:
dashboard, timetable, assignment tracker, offline notes with automatic sync,
module discussion boards, announcements, event calendar, notifications,
Google sign-in, English / isiZulu / Afrikaans and gamification.

**Stack:** Kotlin · XML layouts + ViewBinding (no Jetpack Compose) · Firebase Authentication ·
Cloud Firestore · Room (local storage) · WorkManager · Firebase Cloud Messaging · Credential Manager.

> The design document planned a Kotlin REST API on Azure. This build uses **Firebase/Firestore as the
> backend instead**, so no server has to be hosted. The Firestore layout is documented in
> `app/src/main/java/com/welovecampus/app/data/remote/Fs.kt`.

## Quick start

1. Open the `WeLoveCampus` folder in **Android Studio** (Ladybug or newer). Let Gradle sync.
2. Create a Firebase project → add an Android app with package name **`com.welovecampus.app`**.
3. Download **`google-services.json`** and put it in the **`app/`** folder.
4. In Firebase: enable **Authentication → Email/Password** and **Google**; create a **Firestore database**;
   paste `firestore.rules` into **Firestore → Rules** and publish.
5. For Google sign-in, add your debug **SHA-1** (`./gradlew signingReport`) to the Firebase app settings,
   then download `google-services.json` again.
6. Run the app. On first sign-in it fills the empty database with demo data automatically.

Full step-by-step instructions and a guide to every screen are in the user manual.

## Project layout

```
app/src/main/java/com/welovecampus/app/
├── WeLoveCampusApp.kt        Application: channels, 30-min sync, sync on open
├── AppContainer.kt           Creates the repositories (manual DI) + login bootstrap
├── data/local/               Room: entities, DAOs, database
├── data/remote/Fs.kt         Firestore collection names + helpers
├── data/repo/                Auth, Profile, Campus, Notes (sync), Discussions, Gamification, DemoDataSeeder
├── sync/SyncWorker.kt        WorkManager background sync + local reminders
├── notifications/            Notification channels + FCM service
├── util/                     Priority / streak / timetable rules, prefs, network check
└── ui/                       auth, main, home, timetable, assignments, notes, more,
                              discussions, announcements, events, profile
app/src/main/res/             XML layouts, strings (values, values-zu, values-af), icons
app/src/test/                 Unit tests for priority, streak and timetable rules
firestore.rules               Security rules
functions/                    Optional Cloud Functions for real push notifications
```

## Tests

`./gradlew test` runs `CampusLogicTest` (48-hour HIGH priority, streak reset, next class, clashes).

/**
 * OPTIONAL - Cloud Functions that send real push notifications through FCM.
 * The app already shows local notifications from its 30-minute background sync,
 * so it works without this. Deploying functions needs the Firebase Blaze (pay-as-you-go) plan.
 *
 * Deploy:  npm install -g firebase-tools
 *          firebase login
 *          firebase init functions   (choose JavaScript, keep this index.js)
 *          firebase deploy --only functions
 */
const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

// New announcement -> every student (topic "announcements").
// Emergency announcements use category "emergency", which the app always shows.
exports.notifyAnnouncement = onDocumentCreated("announcements/{id}", async (event) => {
  const a = event.data.data();
  const emergency = (a.category || "").toUpperCase() === "EMERGENCY";
  await getMessaging().send({
    topic: "announcements",
    data: {
      category: emergency ? "emergency" : "announcements",
      title: `${a.sender || "Campus"}: ${a.title || "New announcement"}`,
      body: a.message || "",
      id: event.params.id,
    },
    android: { priority: emergency ? "high" : "normal" },
  });
});

// New reply -> the thread's author (if it is not their own reply)
exports.notifyReply = onDocumentCreated("discussions/{threadId}/replies/{replyId}", async (event) => {
  const reply = event.data.data();
  const db = getFirestore();
  const thread = (await db.doc(`discussions/${event.params.threadId}`).get()).data();
  if (!thread || thread.authorUid === reply.authorUid) return;
  const user = (await db.doc(`users/${thread.authorUid}`).get()).data();
  const tokens = (user && user.fcmTokens) || [];
  if (!tokens.length) return;
  await getMessaging().sendEachForMulticast({
    tokens,
    data: {
      category: "discussions",
      title: `${reply.authorName} replied: ${thread.title}`,
      body: reply.body || "",
      id: event.params.replyId,
    },
  });
});

// Timetable slot changed -> every student (simple version: topic broadcast)
exports.notifyTimetable = onDocumentWritten("timetable/{slotId}", async (event) => {
  if (!event.data.before.exists) return; // ignore brand-new slots from seeding
  await getMessaging().send({
    topic: "announcements",
    data: {
      category: "timetable",
      title: "Timetable updated",
      body: "Your institution changed the timetable. Open the app to see the new times.",
      id: `${event.params.slotId}-${Date.now()}`,
    },
  });
});

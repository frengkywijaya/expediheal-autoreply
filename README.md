# Expediheal AutoReply

Personal-use Android app that auto-replies to incoming WhatsApp and WhatsApp Business messages with random text from a list you control.

## Features

- ✅ Replies to **all** incoming WhatsApp & WhatsApp Business messages
- ✅ **Editable** template list (add / edit / delete)
- ✅ **Random** template picked for each reply
- ✅ **Random delay** between 6–10 seconds (configurable)
- ✅ **Per-user cooldown** (default 30 min) — won't spam-reply the same person
- ✅ Master **on/off toggle** in the app
- ✅ Works with both regular WhatsApp and WhatsApp Business simultaneously

## Limitations (be aware)

- ❌ Won't reply in **group chats** (only direct messages)
- ❌ Won't reply if your phone is **off** or WA is **force-stopped**
- ❌ Won't reply to messages where the **sender's name is hidden** in notifications (rare)
- ❌ Group/summary notifications (`5 new messages from 3 chats`) are skipped

## How it works

The app uses Android's NotificationListenerService to read incoming WhatsApp notifications, then sends a reply using the inline-reply RemoteInput action that's built into the notification (the same mechanism Android Auto and smartwatches use).

This means:
- No accessibility services needed
- No root needed
- No connection to WhatsApp's servers (everything is local)
- Sender sees your reply as a normal WA message from you

## Build the APK

You don't need Android Studio. The project includes a GitHub Actions workflow that builds the APK in the cloud.

### Steps

1. Create a GitHub account if you don't have one
2. Create a new GitHub repo (private is fine — it's your code)
3. Upload all files from this folder to the repo (including `.github/`)
4. Push to the `main` branch
5. Go to the **Actions** tab on your repo
6. Wait for the build to finish (~3-5 minutes)
7. Click the completed run → scroll down → download `ExpedihealAutoReply-debug.zip`
8. Unzip → you have `app-debug.apk`
9. Transfer to your phone and install

### Alternative: Android Studio (if you prefer local build)

1. Open Android Studio → File → Open → select this folder
2. Wait for Gradle sync
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK appears in `app/build/outputs/apk/debug/app-debug.apk`

## Install on your phone

1. Transfer the APK to your phone (Drive, USB, Telegram-to-self, etc.)
2. Tap the APK file
3. Allow "Install from this source" if prompted
4. Tap Install

### Install on each profile

Because Android's NotificationListener is isolated per user profile, the APK must be installed INSIDE each profile that has WhatsApp.

For Island / Shelter work profiles:
1. Install the APK normally (main profile)
2. Open Island (or Shelter)
3. Find "Expediheal AutoReply" in the app list
4. Tap "Clone to work profile" (Island) or "Add to work profile" (Shelter)
5. Launch the cloned app (will show briefcase 💼 icon)
6. Grant notification access for that profile via ADB from your computer:

   ```
   # Find the work profile user ID:
   adb -s <serial> shell pm list users

   # Grant notification access to that user (replace 10 with actual Island user ID):
   adb -s <serial> shell settings put --user 10 secure enabled_notification_listeners com.expediheal.autoreply/com.expediheal.autoreply.AutoReplyNotificationListener
   ```

7. Open the cloned Expediheal AutoReply (briefcase icon) → toggle ON

Repeat per phone that has an Island/Shelter work profile.

## Setup after install

1. Open **Expediheal AutoReply**
2. Tap **Grant access** → enable Expediheal AutoReply in the notification access list
3. Edit/add your reply templates (5 Indonesian defaults are included)
4. Adjust delay range and cooldown if you want
5. Flip the main toggle to **on**

That's it. The app will now auto-reply to incoming WhatsApp messages.

## Tip: don't trigger your own auto-replies

If your friend's auto-reply also runs, you might end up in an infinite reply loop. The 30-minute per-user cooldown prevents this within your app, but if THEIR app doesn't have a cooldown, you could see weird behavior. The cooldown is your safeguard.

## Files

```
wa-autoreply/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/expediheal/autoreply/
│       │   ├── MainActivity.kt
│       │   ├── AutoReplyNotificationListener.kt
│       │   ├── Settings.kt
│       │   └── TemplateAdapter.kt
│       └── res/  (layouts, strings, colors, icons)
├── .github/workflows/build.yml   (auto-builds APK on push)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## Disclaimer

This is for personal use only. Auto-replying to people who didn't ask for an automated response can be annoying or rude — use thoughtfully. Don't use this to spam, scam, or otherwise misrepresent yourself.

WhatsApp's terms of service may also restrict automation; using this on a business account at scale could risk your account. For personal use replying with "I'll reply later" type messages, this is well within normal phone behavior.

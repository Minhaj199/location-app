You are a senior Android engineer.

I am building a V1 Android location-based alarm application.

Tech stack:
- Kotlin
- Jetpack Compose
- Android SDK
- Room Database
- Google Maps SDK
- Google Fused Location Provider
- Android Geofencing API
- BroadcastReceiver
- Android Notification API

Architecture:
- MVVM
- Repository pattern
- Room for local persistence
- No backend
- No authentication
- No Firebase unless absolutely required

V1 concept:

A user creates an alarm associated with a geographic location.
When the user's device enters the configured geographic boundary,
the application should trigger an alarm/notification.

For this phase ONLY:

1. Create/verify the Android project structure.
2. Configure Kotlin and Compose correctly.
3. Add only the dependencies required for the current foundation.
4. Set up a clean package structure suitable for MVVM.
5. Create a basic MainActivity.
6. Create a basic Compose theme.
7. Create a simple placeholder HomeScreen.
8. Make sure the project builds successfully.

Do NOT implement:
- Room entities
- Google Maps
- Geofencing
- Location tracking
- Notifications
- Alarm triggering
- Authentication
- Backend

Before changing anything:
- Inspect the existing project.
- Understand its current structure.
- Do not unnecessarily rewrite existing files.
- Reuse existing code where appropriate.

After implementation:
1. List the files you created/modified.
2. Explain why each major dependency/configuration exists.
3. Run/build the project if possible.
4. Report any errors instead of hiding them.

Keep the implementation minimal and production-oriented.
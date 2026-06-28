# 🛡️ PhantomTrace

**PhantomTrace** is a premium, stealth-oriented Android anti-theft and device tracking application. Designed with a sleek, glassmorphic dark user interface, it runs silently in the background to ensure your device's security. It allows users to remotely trigger location tracking, activate alarms, and receive real-time updates via secure, stealthy triggers.

---

## 🌟 Key Features

- **🌐 Live Location Tracing:** Tracks and monitors device location via background services with high precision.
- **🤫 SMS Stealth Triggers:** Trigger device tracking or sound the alarm remotely using a secret custom keyword and PIN.
- **🔊 Remote Loud Alarm:** Play a high-volume alarm even if the phone is on silent mode, with customizable durations.
- **🔋 Battery Optimization Bypass:** Ensures the background tracing services are never killed by Android's aggressive battery-saving algorithms.
- **🎨 Glassmorphic Dark UI:** Modern, immersive, and premium UI built using Material 3, custom shaders, and smooth spring animations.
- **📊 Real-Time Logs:** Detailed logs of all tracing activities, triggers, and location updates for audit and debugging.

---

## 📸 User Interface

PhantomTrace features a state-of-the-art UI inspired by modern design paradigms:
- **ClassMate-Style Navigation:** A sleek bottom navigation bar with a smooth sliding dot indicator and spring-bouncing tab icons.
- **Ambient Glow Orbs:** Dynamic, floating colored orbs in the background that add depth to the dark theme.
- **Premium Glass Sheets:** Bottom sheets and cards utilize a glass-blur effect (using `RenderEffect` on Android 12+) with subtle white borders.

---

## 🛠️ Technical Stack

- **Language:** 100% Kotlin
- **Architecture:** MVVM / Clean Architecture principles
- **Navigation:** Jetpack Navigation Component
- **Database:** Firebase Realtime Database & Local Preferences
- **Background Work:** Foreground Services & Broadcast Receivers
- **Animations:** AndroidX Dynamic Animation (Spring Force) & ViewPropertyAnimator
- **UI Components:** Material Design 3, View Binding, ConstraintLayout

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Koala (or newer)
- Android SDK 31 (Android 12) or higher
- A Firebase project (configure `google-services.json` in the `app/` directory)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/phantomtrace.git
   cd phantomtrace
   ```
2. Open the project in Android Studio.
3. Place your `google-services.json` in the `app/` folder.
4. Build and run the app on an Android device or emulator.

---

## 🔒 Permissions Required

To function as a reliable anti-theft tracker, PhantomTrace requires:
- **Location (Always Allow):** For background location updates.
- **SMS Permissions:** To listen for the secret remote keyword.
- **Disable Battery Optimizations:** To keep the service alive in the background.

---

## 👨‍💻 Developer

Developed by **Shuaib Dowla**.
- 🌐 [Facebook](https://www.facebook.com/shuaibdowla)
- 💬 [Telegram](https://t.me/shuaibdowla)

https://play.google.com/store/apps/details?id=com.rst.wisdawn
>
👆🏻👆🏻👆🏻
# Wisdawn Android

> [!NOTE]
> This repository houses the codebase for the **Android application** of Wisdawn.
> If you are looking to explore or contribute to the web version of our platform, please visit the [Wisdawn Web Repository](https://github.com/rstdax/wisdawn-website).
> For regular users, we recommend downloading the official release directly from the [Google Play Store](https://play.google.com/store/apps/details?id=com.rst.wisdawn).

<img width="1280" height="720" alt="Untitled design" src="https://github.com/user-attachments/assets/742860f8-9483-496a-b50b-6543089a5f32" />

<img width="1280" height="720" alt="ss2" src="https://github.com/user-attachments/assets/e6f6eae0-394b-4305-8b62-72405e50e3a4" />

Wisdawn is a collaborative peer-led learning ecosystem developed at **Nagaon University**. Shifting away from traditional teacher-centric models, Wisdawn empowers students to host mini online workshops, share knowledge, and solve academic doubts collaboratively through a gamified engagement system.

## 🛠 Tech Stack & Architecture

The Android client is built using **Modern Android Development (MAD)** practices, ensuring a reactive, performant, and scalable educational tool.

### External Libraries

* **Jetpack Compose (Material 3):** Declarative UI for a fluid, modern user experience.
* **Firebase Suite:** * `Auth`: Secure Google Sign-In and session management.
* `Firestore`: Real-time NoSQL database for syncing doubts and workshop data.
* `Storage`: Hosting for user-uploaded learning resources (Images, PDFs, Videos).


* **Google AI (Gemini SDK):** Powers the **Quest Screen** for AI-generated MCQ challenges.
* **Media3 ExoPlayer:** Native video streaming for educational workshop chapters.
* **Coil:** Optimized image and GIF loading for profiles and attachments.
* **Google Play Services Location:** Enables the "Locality" feature to find peers nearby.

### Dev/Build Tooling

* **Kotlin & Coroutines:** Core language and asynchronous state management.
* **Vite-like Speed with AGP:** Optimized build configurations using the Android Gradle Plugin.
* **Version Catalogs:** Type-safe dependency management via `libs.versions.toml`.
* **Jetpack Navigation:** Type-safe Compose-based navigation.

## ✨ Key Features

* **Mini Online Workshops:** Create topic-based sessions and upload learning resources like PDFs and videos.
* **Peer Doubt Solving:** A dedicated system allowing students to post questions and receive collaborative responses.
* **AI-Powered Quests:** Generate dynamic MCQ questions on any topic using Google Gemini to test your knowledge.
* **Focus Mode:** Integrated Pomodoro timer with ambient sounds (Rain, Cafe, Wind) to boost productivity.
* **Gamified Rewards:** Earn XP (Reputation) for helping peers and completing daily objectives.

## 🚀 Getting Started

### Prerequisites

* Android Studio Ladybug | 2024.2.1 or newer.
* JDK 11+.
* A `google-services.json` file from your Firebase console.

### Installation

1. Clone the repository:
```bash
git clone https://github.com/rstdax/wisdawn-android.git

```


2. Open the project in Android Studio.
3. Sync the Gradle files.
4. Run the app on a physical device or emulator (API 24+).

## 👥 Team

Developed with ❤️ by:

* **Rohan Ranjan Das** – Android Developer
* **Aryan Dutta** – Web Developer
* **Rahul Chanda** – UI/UX Designer

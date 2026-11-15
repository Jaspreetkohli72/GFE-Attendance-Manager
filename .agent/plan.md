# Project Plan

Android Workshop Attendance & Advance Kiosk app targeting tablet (Samsung Galaxy Tab 3 8.0, Android 6.0+ API 23 to API 36, fixed landscape). Features: 100% dynamic home screen staff grid from GET /api/kiosk/staff, 2-step worker action popup for CLOCK IN / CLOCK OUT and ₹ ADD ADVANCE with preset and custom amounts, Retrofit backend sync (POST /api/kiosk/punch, POST /api/kiosk/advance), SharedPreferences offline fallback caching, 30s background polling, ToneGenerator chime feedback, confirmation overlay banner, Kiosk lockdown (HOME launcher, FLAG_KEEP_SCREEN_ON, sticky immersive mode, disabled back button), PIN-protected (1234) Admin Settings dialog for configurable server URL (default https://galaxyerp.vercel.app), and network security config supporting user certs and cleartext traffic.

## Project Brief

# Project Brief: Workshop Attendance & Advance Kiosk

## Overview
A lightweight, dedicated Android Kiosk application designed for tablet deployment (optimized for low-spec tablets such as Samsung Galaxy Tab 3 8.0 / SM-T311 running Android 6.0+ API 23 to API 36 in fixed landscape mode). The app enables workshop staff to clock in/out and request cash advances with offline caching resilience, audio-visual feedback, and secure hardware/software kiosk lockdown.

## Features
1. **Dynamic Staff Grid & Offline Sync**: Displays staff members in a grid fetched dynamically via `GET /api/kiosk/staff` with periodic 30-second background polling and `SharedPreferences` offline caching fallback.
2. **2-Step Worker Action Modal (Punch & Advance)**: Interactive popup allowing workers to perform **CLOCK IN / CLOCK OUT** punches or submit **₹ ADD ADVANCE** requests (supporting preset and custom amounts) synced via Retrofit (`POST /api/kiosk/punch` and `POST /api/kiosk/advance`). Includes audio chime feedback (`ToneGenerator`) and a visual confirmation overlay banner.
3. **Kiosk Mode Lockdown**: Enforces complete device lockdown including HOME launcher registration, sticky immersive full-screen mode, `FLAG_KEEP_SCREEN_ON`, fixed landscape orientation, and back-button disabling.
4. **PIN-Protected Admin Settings**: Secure administrative dialog protected by a PIN (default `1234`) for runtime configuration of the target server URL (default `https://galaxyerp.vercel.app`) and network security settings.

## High-Level Technical Stack
- **Language**: Kotlin
- **UI Architecture & Layouts**: Android XML Layouts, ViewBinding, Material Components
- **Grid Layout**: `RecyclerView` with `GridLayoutManager`
- **Modal Dialogs**: `DialogFragment`
- **Networking**: Retrofit2 & OkHttp
- **Concurrency & Background Tasks**: Kotlin Coroutines & `LiveData` / `StateFlow` (for 30s polling timer)
- **Offline Storage & Caching**: `SharedPreferences`
- **System & Audio Integrations**: `ToneGenerator` (audio chime feedback), Android System UI / Immersive Mode APIs, Custom Network Security Config (user certificates & cleartext traffic support)

## Implementation Steps

### Task_1_DataAndNetworking: Implement Network Security Config, Retrofit API service (/api/kiosk/staff, /api/kiosk/punch, /api/kiosk/advance), data models, and SharedPreferences offline caching repository for staff and settings.
- **Status:** COMPLETED
- **Updates:** Implemented network security config, data models, Retrofit service, ApiClient, PreferenceManager caching, and updated build.gradle.kts with targetSdk 36. Verified debug build successfully.
- **Acceptance Criteria:**
  - Network security config created and referenced in AndroidManifest.xml
  - Retrofit API client and data models for staff, punch, and advance endpoints implemented
  - SharedPreferences repository for caching staff list and base URL implemented
  - build pass

### Task_2_UIAndStaffGrid: Build main Activity layout with 3-column RecyclerView grid, ViewModel with 30-second polling timer, ToneGenerator chime feedback helper, and confirmation overlay banner.
- **Status:** COMPLETED
- **Updates:** Implemented item_staff_button.xml layout, StaffButtonAdapter with DiffUtil, SoundManager with ToneGenerator, KioskViewModel with 30s background polling and offline caching state, activity_main.xml top bar + 3-column GridLayoutManager + confirmation overlay banner, and MainActivity integration. Verified debug build successfully.
- **Acceptance Criteria:**
  - Main activity layout uses 3-column GridLayoutManager for staff card display
  - ViewModel manages staff list with 30s background polling and offline fallback
  - ToneGenerator chime feedback helper implemented
  - Confirmation overlay banner displays after successful actions
  - build pass

### Task_3_WorkerModalsAndAdminSettings: Implement StaffActionDialog for Punch In/Out & Advance selection, AdvanceDialog for cash advance input, and PIN-protected Admin Settings dialog (PIN 1234) for server URL configuration.
- **Status:** COMPLETED
- **Updates:** Implemented StaffActionDialog with 80dp CLOCK IN/OUT primary button + 10s auto-dismiss + polling control, AdvanceDialog with preset amounts (₹100, ₹200, ₹500, ₹1000) + custom input, AdminSettingsDialog with PIN 1234 authentication gate + dynamic server URL configuration + system settings intent, and integrated all dialogs with KioskViewModel and MainActivity. Verified debug build successfully.
- **Acceptance Criteria:**
  - StaffActionDialog opens on staff card click for CLOCK IN / CLOCK OUT or ADD ADVANCE
  - AdvanceDialog supports preset and custom ₹ amounts and submits advance request
  - Admin Settings dialog protected by PIN 1234 allows modifying base server URL
  - build pass

### Task_4_KioskLockdownAndRunVerify: Configure Kiosk Mode lockdown (HOME launcher intent filter, FLAG_KEEP_SCREEN_ON, sticky immersive full-screen, back-button override, fixed landscape) and instruct critic_agent to verify application stability, confirm alignment with user requirements, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Configured full Kiosk Mode lockdown (HOME launcher intent filters, FLAG_KEEP_SCREEN_ON, version-compatible sticky immersive fullscreen mode for API 23-36, back-button override, fixed landscape). Verified unit tests and debug assemble build. Executed critic_agent quality evaluation.
- **Acceptance Criteria:**
  - Kiosk mode lockdown configured with HOME launcher filter, FLAG_KEEP_SCREEN_ON, sticky immersive mode, back button override, and landscape orientation
  - make sure all existing tests pass
  - build pass
  - app does not crash
- **Duration:** N/A


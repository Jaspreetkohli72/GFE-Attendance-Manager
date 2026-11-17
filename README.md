# Galaxy Fabrication Experts — Workshop Attendance Kiosk & Staff Manager

An industrial-grade Android kiosk application engineered for workshop tablets at Galaxy Fabrication Experts. The application manages real-time worker clock-in/clock-out attendance, cash advance logging, administrative staff management, dynamic device battery monitoring, and fast server data synchronization.

---

## 📌 System Overview

The Attendance Kiosk is designed to run in dedicated fullscreen lockdown mode on factory floor Android tablets. It provides low-friction, high-contrast touch interfaces for workshop staff while giving management complete control over employee rosters, daily wages, roles, and attendance records.

---

## 🏗️ System Architecture & Frameworks

- **Presentation Architecture**: Model-View-ViewModel (MVVM) design pattern separating UI rendering from business logic.
- **Asynchronous Execution**: Kotlin Coroutines managing background threads, network requests, and timer loops.
- **Reactive Data Pipelines**: `StateFlow` for state observation and `SharedFlow` for zero-latency event notifications and live terminal log streaming.
- **Networking Engine**: Retrofit 2 REST client with OkHttp 4 connection pooling, custom logging interceptors, automatic connection retries, and low-latency socket handling.
- **Data Serialization**: Gson converter engine for automated DTO parsing.
- **Offline Persistence & Cache Isolation**: SharedPreferences storage with separate cache keys for active workshop workers and administrative staff rosters.
- **Hardware Integration**: Intent broadcast receivers for real-time battery monitoring and window inset controllers for total kiosk system bar suppression.

---

## ⚡ Core Features & Functional Specification

### 1. Workshop Floor Attendance Kiosk
- **Adaptive Worker Grid**: High-contrast, dynamic multi-column grid displaying active workshop staff. Column counts scale automatically (3, 4, or 5 columns) based on tablet screen width and orientation.
- **Worker Action Sheet**: Tapping any worker tile opens an action sheet allowing the employee to Clock IN, Clock OUT, or request a Cash Advance.
- **Automated Attendance Calculation**: Backend computes total daily working hours upon clock-out and assigns attendance status according to workshop policy:
  - **Present**: 6 to 10 hours worked.
  - **Half-Day**: 4 to 6 hours worked.
  - **Absent**: Less than 4 hours worked.
  - **Overtime**: Greater than 10 hours worked.
- **Server Confirmation Guard**: Action sheets display an active loading spinner while waiting for server confirmation. Confirmation banners and audio feedback tones trigger only after the server validates the punch.

### 2. Cash Advance Tracking & Manager Attribution
- **Preset & Custom Input**: Preset buttons (₹100, ₹200, ₹500, ₹1000) or custom numeric entry with Rupee currency formatting.
- **Manager Attribution**: Radio button selection attributing cash distribution to authorized managers (Sandeep, Harjeet, Jaspreet, or Jaskaran).
- **Backend Audit Trail**: Records advance transaction details including staff ID, amount, timestamp, and distributing manager.

### 3. Administrative Staff Roster & Management (`AdminActivity`)
- **Admin Security Gate**: Secured access requiring PIN entry (`1234`) via the header Admin Shield icon.
- **Complete Employee Roster**: Displays ALL employee records (Available, On Leave, Terminated) in the same high-contrast grid card format as the main kiosk view.
- **Multi-Tier Roster Sorting**: Roster automatically sorts staff records into three distinct tiers:
  1. **Tier 1 (Available)**: Active staff sorted alphabetically A–Z by name.
  2. **Tier 2 (On Leave)**: Workers on leave sorted alphabetically A–Z by name.
  3. **Tier 3 (Terminated)**: Terminated staff sorted alphabetically A–Z by name.
- **Add New Staff Modal**: Form for inputting employee Name, Role (exposed dropdown with default choices: Welder, Helper, Fitter, Fabricator, Supervisor, Operator, Painter, Electrician, Driver, Manager), Daily Wage (₹/day), Phone Number, and Status.
- **Edit Staff Details Modal**: Pre-filled modal to update Name, Role, Salary, Phone, or Status (`Available`, `On Leave`, `Terminated`). Updating status to Terminated automatically appends today's date tag.
- **Server Configuration Dialog**: Interface for updating the backend Base URL.

### 4. Material 3 Design System & Dynamic Battery Indicator
- **Material 3 Dark Theme**: Dark slate palette (`Theme.Material3.Dark.NoActionBar`) featuring 16dp and 24dp rounded card containers, pill badges, and high-contrast typography.
- **Re-Aligned Kiosk Header Bar**:
  - **Left**: Real-time date (e.g. `Mon, 16 Nov 2026`) and digital time clock (e.g. `10:45:12 AM`).
  - **Center**: Mathematically centered workshop header (`GALAXY FABRICATION EXPERTS`).
  - **Right**: Sync Status Pill, Live Battery Indicator, Refresh Button, and Admin Shield Icon.
- **Live Battery System**: Continuous 1-second system polling and intent broadcast receiver monitoring device percentage and charging status. Dynamic vector icons switch automatically between charging mode and power level tiers (Full, Medium, Low, Critical).

### 5. Kiosk Diagnostics & Sync Log Terminal (`SyncLogDialog`)
- **Structured Log Channels**: Logcat channels (`KioskUser`, `KioskApi`, `KioskSys`, `KioskWarn`, `KioskErr`) capturing user actions, API transactions, and system events.
- **Interactive Terminal Modal**: Tapping the Sync status pill opens a sleek dark console streaming real-time log entries with color-coded severity levels and auto-scrolling terminal output.

---

## 🌐 API Endpoints & Data Specifications

### Base URL Environments
- **Production Server**: `https://galaxyerp.vercel.app/`
- **Local Network / Dev**: `http://<LOCAL_IP>:3000/`

### Caching & Connection Pool Rules
- **Vercel Edge Cache Bypass**: Requests include strict HTTP headers (`Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`, `Expires: 0`), ensuring live database queries on Supabase.
- **Single-Flight Request Coalescing**: Concurrent requests within a 1,000 ms window coalesce into a single live query to protect connection pools.
- **Fault-Tolerant Disk Backups**: System falls back to local disk JSON backups if backend database response times exceed 3,500 ms.

### Endpoint Matrix

| Method | Endpoint Path | Description / Scope | Query & Body Parameters | Expected Response Data |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/kiosk/staff` | List active workers for main kiosk grid | `all=false` | JSON array of active staff members with real-time clock status |
| `GET` | `/api/kiosk/staff?all=true` | List all staff members for Admin roster | `all=true` | JSON array of all staff members (Available, On Leave, Terminated) |
| `GET` | `/api/kiosk/staff/{id}` | Get single staff profile & status | Path `id` | Single staff member object |
| `POST` | `/api/kiosk/staff` | Create a new employee | `name`, `role`, `salary`, `phone`, `status` | Success indicator, confirmation message, created staff object |
| `PUT` | `/api/kiosk/staff/{id}` | Update employee details | Path `id`, `name`, `role`, `salary`, `phone`, `status` | Success indicator, confirmation message, updated staff object |
| `POST` | `/api/kiosk/punch` | Record Clock IN / Clock OUT | `staffId`, `action` (`IN` or `OUT`) | Success indicator, status message, refreshed staff array |
| `POST` | `/api/kiosk/advance` | Record cash advance | `staffId`, `amount`, `notes` (Manager Name) | Success indicator, confirmation message |

---

## 🛡️ Kiosk Hardware Lockdown & Security

- **Kiosk Lockdown**: Intercepts and disables hardware back button navigation.
- **Screen Awake Lock**: `FLAG_KEEP_SCREEN_ON` prevents display sleeping during factory shifts.
- **Immersive Fullscreen Mode**: Suppresses status bar and navigation bar using transient swipe gestures across all activities and dialogs.
- **Memory & Hardware Optimization**: Enabled `largeHeap` allocation and `hardwareAccelerated` graphics rendering.
- **Launcher Compatibility**: Raster PNG icons included across mdpi, hdpi, xhdpi, xxhdpi, and xxxhdpi densities to prevent third-party launcher crashes on custom Android tablet ROMs.

---

## 🛠️ Build & Development Commands

### SDK Requirements
- **JDK**: Version 17
- **Compile SDK**: 37
- **Target SDK**: 36
- **Min SDK**: 24 (Android 7.0 Nougat)

### Build Commands
- **Compile Debug Build**: `./gradlew :app:assembleDebug`
- **Compile Release Build**: `./gradlew :app:assembleRelease`
- **Run Unit Test Suite**: `./gradlew :app:testDebugUnitTest`

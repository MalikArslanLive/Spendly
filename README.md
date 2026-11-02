# Spendly

**Daily expenses, income and financial summaries — built for Android and PKR.**

Spendly is a native Android personal finance app for recording money coming in and going out, reviewing daily and monthly activity, and synchronizing transactions to a connected Google Spreadsheet.

Developed by [Malik Arslan](https://github.com/MalikArslanLive) with assistance from [Google AI Studio](https://aistudio.google.com/). Android APK builds are configured through [Codemagic](https://codemagic.io/start/).

[Features](#features) · [Design](#design-and-navigation) · [Google Sheets](#google-sheets-sync) · [Build](#build-and-run) · [Architecture](#project-structure)

## At a glance

| Item | Current implementation |
| --- | --- |
| Platform | Native Android |
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| Currency | Pakistani rupees (PKR / Rs.) |
| Minimum Android version | Android 7.0 / API 24 |
| Target SDK | 36 |
| App version | 1.0 (version code 1) |
| Application ID | `com.aistudio.myexpenses.kfqrz` |
| Storage | On-device Room database |
| Optional online integration | Google Sign-In, Google Sheets and Google Drive |
| Default branch | `Spendly` |
| CI output | Debug APK through Codemagic |

## Features

### 1. Expense and income tracking

- Add and edit **Expense (Out)** and **Credit (In)** transactions.
- Record amount, category, optional note, date and time.
- Enter an amount directly or use quick-amount choices.
- Use date and time pickers for past or current transactions.
- Identify records with sequential numeric transaction IDs.
- Preserve the transaction ID when editing an existing record.
- Store transactions locally so basic entry and reporting work without a Google connection.

**Expense categories:** Food, Tea/Coffee, Travel, Shopping, Bills, Family, Work, Health and Other.

**Credit categories:** Salary, Freelance, Business, Investment, Returned Money, Gift and Other Income.

### 2. Home dashboard

- Today's income/credits, expenses and net balance.
- Current month's credit, expense and balance summary.
- Today's transaction list with category styling and transaction details.
- A prominent **Add Transaction** action and floating add button.
- Google Sheets connection and synchronization status.
- Menu access to sync settings, Trash and Clear All Data.

Balance is calculated as **credits minus expenses** within the displayed period; it is not a live bank account balance.

### 3. Calendar and date-range reports

- Browse months in a calendar.
- See indicators for dates containing transactions.
- Select one day to review its transactions and totals.
- Switch to a date range for combined credits, expenses and net balance.
- Return quickly to today.
- Add a transaction for the selected calendar date.

### 4. Monthly reports

- Move between reporting months.
- Review monthly net balance, total credits, total expenses and transaction count.
- View expense categories ranked by total, with percentage breakdowns.
- Inspect day-wise totals and tap a day to open its details.

The current daily-average calculation divides monthly expenses by the number of dates containing transactions, including dates with credit-only records. It does not divide by every calendar day in the month.

### 5. Trash and recovery

- Deleting a transaction normally moves it to Trash.
- Restore a trashed transaction while retaining its transaction data and ID.
- Permanently delete individual trashed items or empty Trash after confirmation.
- **Clear All Data** moves active local transactions to Trash.
- For a connected transaction marked as synced, individual deletion first attempts to remove its matching Google Sheets row. If that request fails, the local record is retained and a retry is offered.

Permanent deletion cannot be undone through the app.

## Design and navigation

Spendly uses a card-based Material 3 interface with an emerald-green identity, mint accents and slate text/background colors.

| Element | Design and purpose |
| --- | --- |
| Bottom navigation | Home, Calendar and Monthly |
| Main dashboard | Green summary card, financial totals and transaction list |
| Transaction entry | Dialog with type selection, amount, category chips, note and date/time controls |
| Credits and expenses | Green credit styling and red expense styling, with category-specific icons and colors |
| Calendar | Month navigation, selected-day/range highlights and transaction indicators |
| Monthly breakdown | Summary cards, category percentages and day-wise rows |
| Sync feedback | Distinct not-connected, pending, syncing, synced and failed states |
| Theme | Light and dark color schemes selected from the system theme |
| Keyboard handling | Resizing window and IME padding in the transaction dialog |

Android Back closes Trash first; from Calendar or Monthly it returns to Home.

This description is based on the Compose source. No device screenshots or runtime visual verification are included in this repository documentation.

## Google Sheets sync

Google Sheets is optional. Local tracking does not require sign-in.

### Connect a spreadsheet

1. Open the Home menu and choose **Google Sheets Sync**.
2. Connect your Google account.
3. Choose **Create New** to create a dedicated spreadsheet, or **Use Existing** and paste a spreadsheet URL.
4. Review the connected spreadsheet and use **Sync Now**.
5. Open the spreadsheet from the sync settings when needed.

The settings also provide **Change Sheet**, **Disconnect**, pending-count information, last-sync time and error feedback.

### Spreadsheet layout

Transactions are organized into month-named tabs, such as `October 2026`.

| Column | Value |
| --- | --- |
| A | Transaction ID |
| B | Date |
| C | Time |
| D | Type |
| E | Category |
| F | Amount |
| G | Note |

### What synchronization actually does

The **local active transaction database is authoritative**. The sync manager creates required monthly tabs, normalizes headers, adds missing local transactions, updates differing spreadsheet rows, and removes duplicate or obsolete spreadsheet rows.

**Use a dedicated spreadsheet.** Connecting an existing spreadsheet can reorganize headers and remove rows that do not correspond to active local transaction IDs. Spreadsheet-only records and manual spreadsheet edits are not imported into the local database and can be removed or overwritten during reconciliation.

Sync is requested after transaction changes, during ViewModel initialization when connected and online, through the manual button, and by the network-availability callback. The implementation uses coroutines and an in-process network callback; it does not provide a scheduled WorkManager background-sync service.

### Accounts and local data

Google authentication and saved spreadsheet settings are associated with the selected account. However, the Room transaction database is shared on the device rather than partitioned by Google account. Changing accounts changes the sync destination; it does not create a separate local ledger. This is not a multi-user accounting system or a verified cross-device merge/restore mechanism.

Disconnecting keeps local transactions and does not automatically delete existing spreadsheet data.

## Data and permissions

- Transactions are stored in the Room database `my_expenses.db`.
- Spreadsheet settings and selected-account information use app SharedPreferences.
- Google Sheets requests are authenticated with Google access tokens.
- The requested OAuth scopes are `drive.file` and `spreadsheets`.
- Android permissions declared in the manifest are `INTERNET` and `ACCESS_NETWORK_STATE`.
- Android backup is enabled in the current manifest; the repository contains backup/data-extraction configuration.
- No dedicated app-level database encryption, biometric lock or PIN gate is implemented in the reviewed source.

Google AI Studio describes the development workflow; the active implementation is not a Gemini-powered financial assistant. Firebase, camera and other entries in the dependency catalog do not imply those features are enabled.

## Build and run

### Local development

Use Android Studio or a compatible Android SDK/JDK setup. The project currently declares Android Gradle Plugin **9.1.1**, Gradle wrapper **9.3.1**, Kotlin **2.2.10**, and Android compile SDK **36.1**. Install the required SDK components and use a JDK compatible with these build tools.

```bash
git clone https://github.com/MalikArslanLive/Spendly.git
cd Spendly
git checkout Spendly
chmod +x gradlew
./gradlew assembleDebug
```

Default debug APK location:

```text
app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat assembleDebug`.

### Google sign-in configuration

For a separately configured build, enable the Google Sheets and Google Drive APIs in the appropriate Google Cloud project, configure OAuth consent/test-user access as applicable, and register an Android OAuth client matching the application ID and the signing certificate SHA-1.

```bash
./gradlew :app:signingReport
```

The package name and certificate must match the build installed on the device. A Google sign-in error with status code 10 usually points to an OAuth/package/signing configuration mismatch. Merely adding an unused OAuth client ID to an environment file does not replace the Android OAuth configuration.

### Codemagic APK workflow

The repository includes `codemagic.yaml` with workflow ID `android-build` and display name **Spendly APK**.

1. Connect this repository in Codemagic.
2. Select the `Spendly` branch and the YAML workflow.
3. Configure signing material consistently with the Android OAuth client.
4. Run the workflow and download the generated debug APK from its artifacts.

The current workflow uses a `mac_mini_m2` instance, has a 120-minute maximum duration, runs a signing report, then executes `./gradlew assembleDebug`. Its artifact pattern is `**/build/outputs/apk/debug/*.apk`.

The debug signing configuration supports Codemagic Secure Files, `DEBUG_KEYSTORE_BASE64`, a local Base64 keystore file and an embedded fallback debug keystore. These are existing development-build mechanisms, not a production signing recommendation. Keep production signing material private.

The Gradle release configuration expects `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` and the `upload` alias. The checked-in Codemagic workflow builds a **debug APK**, not a Play Store release or app bundle.

## Project structure

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/com/example/MainActivity.kt` | App entry point, navigation scaffold and dialog wiring |
| `.../data/Expense.kt` | Transaction entity, types, timestamps, sync/trash fields and IDs |
| `.../data/ExpenseDao.kt` | Room queries and transaction persistence operations |
| `.../data/ExpenseRepository.kt` | Data operations, ID migration and sync-state updates |
| `.../data/AppDatabase.kt` | Room database version 4 and migrations |
| `.../ui/ExpenseViewModel.kt` | Reactive screen state, totals, reports and user actions |
| `.../ui/screens/` | Home, Calendar, Monthly, Trash and sync settings |
| `.../ui/components/` | Transaction editor, transaction cards and confirmation dialogs |
| `.../ui/theme/` | Colors, typography and Material themes |
| `.../sync/GoogleSheetsSyncManager.kt` | Google authentication, connection status and spreadsheet reconciliation |
| `.../util/Formatters.kt` | PKR, date, month and calendar helpers |
| `app/src/test/` | Existing unit/Robolectric test sources |
| `app/src/androidTest/` | Existing instrumentation test source |
| `gradle/libs.versions.toml` | Dependency and plugin version catalog |
| `codemagic.yaml` | Debug APK CI workflow |

The data flow is Compose screens → ViewModel → repository → Room. The Google Sheets sync manager reads local records and communicates with Google APIs using OkHttp and coroutines.

## Existing tests

The repository contains tests for PKR formatting, month/calendar helpers, date-range totals, credit/expense balance calculations, transaction IDs, edit/restore preservation, spreadsheet column order, URL parsing and helper behavior.

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

The second command requires a connected device or emulator. Test-source presence is not a claim that all tests or live Google authentication/synchronization have passed. This documentation update did not build, run or modify the app.

## Current scope

Spendly currently focuses on manual PKR transaction tracking and local reporting with optional Sheets synchronization. The reviewed app does not implement bank feeds, payment processing, multi-currency conversion, budget limits, recurring-transaction automation or a user-facing CSV export.

No installable APK is linked here because no GitHub release asset was verified during this documentation review. Use Codemagic artifacts or build from source.

## Feedback and contributions

For reproducible problems or feature suggestions, [open an issue](https://github.com/MalikArslanLive/Spendly/issues). Include the Android version, app/build version, steps to reproduce and expected versus actual behavior. Remove private transaction data, account details and tokens from any logs or screenshots.

Discuss substantial changes before submitting a pull request, and include relevant validation.

## Author and development workflow

Created by **Malik Arslan**.

- **Google AI Studio:** assisted code creation and iteration, as described by the author.
- **GitHub:** source code and version history.
- **Codemagic:** configured Android debug APK builds.
- **Android device:** APK installation and personal use, as described by the author.

## License

No license file was present in the reviewed repository. No open-source license has been assigned by this documentation update; contact the repository owner about reuse or redistribution permissions.

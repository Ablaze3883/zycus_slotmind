# SlotMind — AI-Powered Appointment Rescheduling

SlotMind helps clinics respond when a provider becomes unavailable or an appointment slot is cancelled. It identifies affected appointments, recommends alternative times with an AI scheduling advisor, and sends suggestions to the front desk for review. If AI is unavailable, a rule-based scheduler provides recommendations instead.

Accepting a suggestion is handled transactionally by the backend, with optimistic locking and a database uniqueness constraint preventing double bookings.

- Architecture overview: [`ADR.md`](ADR.md)
- Decision log and requirement traceability: [`decision.md`](decision.md)
- Technology: Java 21, Spring Boot 3, React 18, Vite, and TypeScript

## Run locally

Install **Java 21 or newer**, **Maven 3.9 or newer**, and **Node.js 18 or newer**. The checked-in Maven wrapper can be used if Maven is not installed.

### Start the backend

```bash
cd backend

./mvnw spring-boot:run
```

On Windows, run `mvnw.cmd spring-boot:run` from PowerShell or Command Prompt. The backend is available at `http://localhost:8080/api`.

The backend uses an H2 database and seeds sample clinics, providers, appointments, and waitlist data on startup. It connects to the configured OpenAI-compatible AI gateway when an API key is available; otherwise, AI suggestions fall back to rule-based scheduling. H2 is in-memory by default, so data resets when the server restarts.

### Enable the AI advisor

The default provider is LiteLLM (`qwen-cursor`). The API key is not included in the repository. In PowerShell, set it for the current terminal before starting the backend:

```powershell
$env:SLOTMIND_AI_API_KEY = Read-Host "Enter your LiteLLM API key"
cd backend
.\mvnw.cmd spring-boot:run
```

Alternatively, create `backend/src/main/resources/application-local.properties` and add `slotmind.ai.api-key=YOUR_KEY`. This local file is git-ignored; never commit or share it. Restart the backend after configuring the key. If the key is missing or invalid, scheduling falls back to the rule-based strategy.

### Enable SMS and email notifications

Appointment confirmations, reschedule updates, and waitlist offers use the notification outbox. Configure SMTP for email by setting Spring Boot's `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, and `SPRING_MAIL_PASSWORD` environment variables, plus `SLOTMIND_EMAIL_FROM` to a sender address authorized by your mail provider. Configure TLS/authentication according to the provider; for example, set `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true` and `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` when required.

SMS delivery uses Twilio. Set `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and `TWILIO_FROM_NUMBER` in the backend environment. Patient phone numbers must be in E.164 format (for example, `+15550123456`). Keep provider credentials in environment variables or a local, ignored configuration file, never in source control.

If a channel is not configured or a provider rejects delivery, the booking remains successful and the outbox records the notification as `FAILED`; check the notifications/activity views and backend logs. A missing contact is recorded as a front-desk log entry.

### Staff appointment notes

Staff and administrators can open **Appointments → Notes** to save multiple timestamped notes for a booking. Notes are private to the authenticated clinic staff/admin interface, tied to the appointment, and can be deleted there; patient portal accounts cannot access the notes endpoints. Notes are not exposed in patient appointment views. The local demo database is in-memory, so notes reset when the backend restarts.

### Authentication and roles

- **Patient:** signs in with a seeded patient user ID and password, then can view and act on suggestions for their own appointment only. Demo IDs are `john.martinez`, `emily.watson`, `aisha.patel`, and `oliver.brown`; the local demo password is `PatientDemo123!`. Override it with `SLOTMIND_DEMO_PATIENT_PASSWORD`.
- **Staff:** signs in with Auth0 and can use the front-desk API.
- **Admin:** signs in with Auth0 and can use the front desk, including changing the scheduling strategy. The backend enforces role permissions.
- **Local role demo:** the default `demo` profile also enables `staff.demo` / `StaffDemo123!` and `admin.demo` / `AdminDemo123!`. These credentials return local, backend-issued sessions; they are not Auth0 credentials or tokens. They are for local demos only; set `SLOTMIND_DEMO_ROLES_ENABLED=false` to disable them. To override passwords, set `SLOTMIND_DEMO_STAFF_PASSWORD` or `SLOTMIND_DEMO_ADMIN_PASSWORD` for the backend and `VITE_DEMO_STAFF_PASSWORD` or `VITE_DEMO_ADMIN_PASSWORD` for the frontend before starting/building.

Auth0 setup:

1. Create an Auth0 API for SlotMind and assign the `STAFF` and `ADMIN` roles to your Auth0 users.
2. Add a **Post Login** Action that puts the assigned role names in the access token:

   ```js
   exports.onExecutePostLogin = async (event, api) => {
     api.accessToken.setCustomClaim(
       'https://slotmind.dev/roles',
       event.authorization?.roles ?? []
     );
   };
   ```

3. Configure the backend with `AUTH0_ISSUER_URI` (your tenant issuer URL, including its trailing slash), `AUTH0_AUDIENCE` (the API identifier), and optionally `AUTH0_ROLES_CLAIM` if you use a different custom-claim URL.
4. Configure the frontend with `VITE_AUTH0_DOMAIN`, `VITE_AUTH0_CLIENT_ID`, and `VITE_AUTH0_AUDIENCE` (the same API identifier). If you change the role-claim URL, set `VITE_AUTH0_ROLES_CLAIM` to the same value as `AUTH0_ROLES_CLAIM`. Add `http://localhost:5173` and `http://127.0.0.1:5173` to your Auth0 application's allowed callback, logout, and web-origin URLs.
5. Restart the backend and frontend after setting their environment variables. Never commit Auth0 secrets or credentials.

The default `demo` Spring profile enables the seeded patient and local role demo accounts for local use. Set `SPRING_PROFILES_ACTIVE=prod` in a deployment to disable those demo accounts; provide a managed patient identity store before using patient login in production. Demo-role sessions are in memory and cleared when the backend restarts. Never deploy the demo profile or demo credentials to a public or production environment.

### Start the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` to use the front desk. Run the frontend tests with `npm test`. To run the backend tests, use `mvnw test` (or `mvnw.cmd test` on Windows) from the `backend` directory.

The patient portal is available at `http://localhost:5173/#/portal/APT-001`.

## Features by sprint

| Sprint | Highlights |
|---|---|
| 1 | Domain model and state transitions; REST API; transactional rescheduling with optimistic locking; runtime-switchable AI and rule-based schedulers; AI response validation and fallback; background scheduling workflow with duplicate-suggestion prevention; front-desk interface; and an SSE reasoning stream. |
| 2 | Room and equipment requirements by appointment type; room-aware scheduling; explicit and inferred patient time preferences; appointment-specific durations; and provider daily appointment limits. |
| 3 | Automatic waitlist offers when a slot opens; configurable Twilio SMS and SMTP email notifications through a notification outbox; multi-clinic routing; provider queue pressure in AI recommendations; patient self-service portal; and staff-only appointment notes. |

## Five-minute demo

1. **Start with the queue.** Show that it is empty, point out the provider rail and day view, and confirm the strategy is set to *AI advisor*.
2. **Book an appointment.** Go to **Appointments → New appointment** and book a patient into an open slot.
3. **Make a provider unavailable.** In the provider rail, mark **Dr. Sarah Chen** offline. This calls `PATCH /api/providers/PRV-001/status` and returns immediately. The background scheduler creates suggestions for John Martinez and Emily Watson, including confidence scores and reasoning for staff. Show the activity feed while it runs.
4. **Accept a suggestion.** Choose **Patient agreed · Accept**. The appointment moves to the new time, the original slot opens, and a notification is logged. The day view reflects the change.
5. **Show conflict handling.** Switch to the rule-based strategy and create a case where two suggestions target the same slot. Accept one suggestion; accepting the other returns **409 Conflict** with a “Slot no longer free” message.
6. **Cancel a slot.** In the day view, select Aisha Patel's slot and choose **Cancel slot**. The scheduler creates a new suggestion using a different prompt, prioritizing the same provider and checking for the triage room required by the urgent visit.
7. **Explore the later sprints.** Show the room filter, the waitlist offer created when a slot opens, the patient portal, and the SSE stream available through **Suggest another**.

### API walkthrough

```bash
# Mark Dr. Sarah Chen offline
curl -X PATCH http://localhost:8080/api/providers/PRV-001/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"OFFLINE"}'

# List pending suggestions
curl 'http://localhost:8080/api/suggestions?status=PENDING'

# Accept a suggestion
curl -X PATCH http://localhost:8080/api/suggestions/<id> \
  -H 'Content-Type: application/json' \
  -d '{"status":"ACCEPTED"}'
```

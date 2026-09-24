# PocketForge Local Autonomy

PocketForge now contains a local autonomy supervisor that runs Paperclip and OpenClaw inside the existing private Ubuntu/PRoot runtime. The existing API/provider agent system is unchanged.

## First run

1. Open **Autonomy** in PocketForge.
2. Tap **Install & Start**.
3. PocketForge installs pinned Paperclip/OpenClaw packages into the persistent Linux runtime, starts both services on loopback, and provisions two Paperclip employees:
   - **Coder** → Claude Code
   - **Tester** → deterministic local test runner
4. Tap **Open local dashboard** on Paperclip to inspect employees, tasks and runs.
5. Tap **Test Loop** to submit a synthetic incident to the local bridge and verify assignment/wakeup.

## Local endpoints

- Paperclip: `http://127.0.0.1:3100`
- OpenClaw: `http://127.0.0.1:18789`
- PocketForge incident bridge: `http://127.0.0.1:31900`

All three are loopback-only. The incident bridge requires the generated `X-PocketForge-Token` header.

## Incident contract

`POST /incident` accepts JSON fields:

```json
{
  "title": "Parser failed",
  "description": "Production-safe diagnostic details",
  "priority": "high",
  "workspace": "pocketforge-autonomy",
  "testCommand": "./scripts/smoke.sh"
}
```

PocketForge turns the incident into a Paperclip issue assigned to **Coder**. The Coder instructions explicitly hand the same issue to **Tester** when implementation is ready. Tester runs the configured deterministic command and reports the result back to Paperclip.

## Safety boundary

The autonomy runtime is local, but Claude Code and other model providers may still use the provider/API credentials already configured in PocketForge. Autonomous workers are instructed not to deploy production changes or alter credentials/broker settings.

## Runtime notes

The first installation requires network access for the pinned npm packages. After installation, packages persist in PocketForge's private Linux runtime. Android process death is handled by a restartable foreground supervisor; service/task state is persisted under the app's private `autonomy/` directory.

## Build verification

The source tree can be assembled with the project's Gradle wrapper. In environments without external network access, Gradle distribution/bootstrap downloads may be unavailable; that limitation is environmental rather than a source-level implementation result.

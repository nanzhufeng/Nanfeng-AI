# MM-O4-G Android Compare Platform Boundary Evidence

## Scope

- Added the unregistered `AndroidOpenRouterComparePlatformAdapter` as the Android-only, reviewed credential/HTTPS boundary for Compare.
- Added two safe HTTP outcomes to the OpenAI-compatible core: absent protected credential and bounded-response rejection.
- No UI, `AppContainer`, Activity, ViewModel, normal-chat owner, database migration, device installation, OPPO state, or real network call was changed.

## Contract boundaries

- The adapter can be created only from a live two-branch ChatGPT+Claude OpenRouter grant set. Construction does not load a credential or open a connection.
- It is both the opaque credential handle and HTTP client. A request must match the fixed OpenRouter preset and one exact remaining provider-facing model; each model is one-shot for the batch.
- Cancellation, expiry, handle/model mismatch, repeated model, missing credential, timeout, malformed/oversized response and platform exceptions fail closed. The credential is a temporary `CharArray`, cleared in `finally`; no raw text, response, Key, Authorization or endpoint enters Room or a safe result.
- The concrete HTTPS path has fixed POST endpoint, header whitelist, redirect disablement, 8 s connect timeout, 30 s read timeout and 1 MiB response cap. A small injected connection seam is test-only and does not add a production endpoint choice.
- It remains unreferenced by `AppContainer`, UI, Activity and ViewModel. `RoomCompareBranchExecutionPorts` remains separately unregistered, so this increment cannot issue a request or write a receipt in the running app.

## Verification

- `AndroidOpenRouterComparePlatformAdapterContractsTest`: 4 tests, 0 failures, 0 errors, 0 skipped.
  - construction from grants performs zero credential reads and zero connection opens;
  - expired/wrong logical-model grants reject construction;
  - only a grant’s exact model can read a credential and the branch cannot be reused;
  - cancellation before dispatch reads no credential and opens no connection.
- Regressions: `OpenAiCompatibleProviderTransportContractsTest` (7), `OpenRouterCompareBatchDispatchAdapterContractsTest` (3), and `MMO4FCompareBranchExecutionPortsContractsTest` (3). Targeted total: 17 tests, 0 failures, 0 errors, 0 skipped.
- `:app:assembleDebug` completed successfully.

## Not verified / intentionally absent

- No saved API Key was read, no HTTP request was sent and no ChatGPT/Claude Compare test occurred.
- Existing normal-chat confirmation reports `EGRESS_UNREGISTERED` and `isConfirmable=false`; no Compare visible confirmation/production composition is registered. This is not legal app-visible consent, so it cannot authorize egress.
- No emulator visual QA, real service, real Usage, restart, Android device, OPPO, Desktop, signing or release validation was performed.

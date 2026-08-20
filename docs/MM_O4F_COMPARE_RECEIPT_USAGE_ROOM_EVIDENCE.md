# MM-O4-F Compare Receipt / Usage Room Evidence

## Scope

- Added Schema 32→33 migration and the `compare_branch_execution_receipts` table only.
- Added unregistered `RoomCompareBranchExecutionPorts` for Compare branch receipt state plus existing append-only Usage Ledger reservation/release facts.
- No Android/Desktop UI, ViewModel, Workspace, credential store, OpenAI-compatible HTTP client, AppContainer execution composition, device, or network path was changed.

## Contract boundaries

- Every port call resolves and validates the existing Schema 32 branch by execution ID before write: session/branch, invocation, attempt, request fingerprint, conversation, parent user node, provider handle and provider-facing model must agree.
- The receipt stores only execution identity, state/timestamps and safe error code. `appendPartial` does not persist its delta.
- No `message_nodes`, `Conversation.currentLeafMessageId`, `conversation_runtime_states`, P3 receipt record, prompt, response, Key, or HTTP payload is written.
- The port is not constructed or injected by `AppContainer`; defaults of the Compare adapter remain disabled credential/HTTP/receipt/Usage.

## Verification

- `MMO4FCompareBranchExecutionPortsContractsTest`: 3 tests, 0 failures, 0 errors, 0 skipped.
  - independent branch receipt/reservation leaves the shared message tree and P3 runtime unchanged;
  - failure release is idempotent and an execution cannot be rebound to another branch after restart;
  - Schema 32→33 preserves existing Compare/P3/Usage tables and adds only the receipt table.
- `OpenRouterCompareBatchDispatchAdapterContractsTest`: 3 tests, 0 failures, 0 errors, 0 skipped.
- Extended O4 regression: `MMO4CCompareConversationRoomContractsTest` (4), `CompareExecutionApplicationOwnerContractsTest` (10), and `RealTextExecutionCoordinatorContractsTest` (4). Total targeted run: 24 tests, 0 failures, 0 errors, 0 skipped.
- Targeted command:

  ```text
  JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1' \
  ./gradlew --no-daemon :app:testDebugUnitTest \
    --tests com.nanzhufeng.ai.data.MMO4FCompareBranchExecutionPortsContractsTest \
    --tests com.nanzhufeng.ai.ai.OpenRouterCompareBatchDispatchAdapterContractsTest \
    --tests com.nanzhufeng.ai.data.MMO4CCompareConversationRoomContractsTest \
    --tests com.nanzhufeng.ai.domain.RealTextExecutionCoordinatorContractsTest \
    --tests com.nanzhufeng.ai.domain.CompareExecutionApplicationOwnerContractsTest
  ```

- `:app:assembleDebug` completed successfully.

## Not verified / intentionally absent

- No credential load/decrypt, HTTP request, provider execution, user-visible consent, UI visual QA, emulator/OPPO install, or real service/Usage evidence.
- A future Android credential/HTTP adapter still needs independent review and explicit user-authorized visible egress before it can be injected.

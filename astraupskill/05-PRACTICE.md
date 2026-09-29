# Practice ladder: from local commands to a mid-level CRUD service

These are proposed extensions, not delivered features. Start each with an observable contract and a failing test. Preserve existing learning data and use a separate directory for format experiments. Work through the earlier exercises before adding an HTTP framework so you can explain the guarantees your controller is exposing.

## 1. Introduce a shared transfer identifier

Each accepted transfer currently produces two different transaction IDs. Add a shared transfer ID while preserving the individual record IDs. Two transfers with identical accounts, amount, description, and timestamp must still be distinguishable. Acceptance tests should retrieve exactly two matching sides for one transfer and reject a malformed imported pair. Plan a versioned snapshot migration; do not infer old pairs solely from matching timestamps or amounts. Explain whether incomplete historical pairs are reported, quarantined, or left explicitly uncorrelated.

## 2. Add account closing instead of deleting history

Introduce an account status and a `closeAccount` command. Decide whether closing requires zero balance and which operations remain allowed afterward. A reasonable exercise keeps history readable while rejecting new deposits, withdrawals, and transfers involving a closed account. Rename behavior should be an explicit policy too. Test every entry point, including transfers where the closed account is the destination. A UI-only disabled button is not enforcement.

## 3. Inject time and identity generation

Replace direct `LocalDateTime.now()` and UUID generation with narrow dependencies or constructor-supplied collaborators. Write deterministic tests for transaction ordering and transfer correlation. Do not make time a global mutable test switch: parallel tests should be able to use different clocks. Explain the distinction between an event timestamp, a stable ordering key, and a request idempotency key; none automatically substitutes for the others.

## 4. Make command persistence one unit of work

The current CLI mutates memory and then saves. Design a command handler that either publishes a fully persisted new state or leaves the previous state authoritative. One local approach constructs a candidate service from a snapshot, runs the command, saves the candidate, and then swaps the live reference under a single coordinator. Test a failed save and a competing command. Explain why swapping without coordination can lose updates, and why this approach is still limited to one process.

## 5. Build a database-backed HTTP adapter

Expose account creation, listing, lookup, rename, restricted deletion or closing, deposits, withdrawals, and transfers. Define JSON amount representation and error responses before writing controllers. Move related balance/history changes into one database transaction with appropriate row/version protection. Test insufficient balance, invalid precision, missing destination, repeated deletion, and rollback after a deliberately failed history insert. Source-level unit tests do not prove your HTTP status codes or database constraints.

## 6. Add authenticated ownership and idempotent commands

Scope every query and command to an authenticated principal. A transfer's source must belong to the caller under your explicit simulation policy. Add an idempotency key for money-changing requests: the same key and normalized payload returns the original outcome, while the same key with a different payload conflicts. Store the idempotency result in the same transaction as the operation. Test two simultaneous retries, not just two sequential calls.

Finish with a review that names the validation boundary, consistency boundary, persistence boundary, and authorization boundary. Demonstrate one failure at each. The goal is a small service whose behavior you can defend under invalid input, retries, and concurrent requests, with clear limits rather than a long list of unverified framework features.

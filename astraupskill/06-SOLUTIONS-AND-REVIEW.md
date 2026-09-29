# Solution guidance: make each guarantee explicit

Try the [practice ladder](05-PRACTICE.md) before reading these suggestions. They are design sketches for future work. The delivered simulator does not yet contain transfer correlation IDs, closed-account status, injected clocks, a database, HTTP endpoints, ownership, or request idempotency.

For transfer correlation, create one operation ID before building the outgoing and incoming records, then include it in both. Validate that a completed transfer has exactly one outgoing and one incoming entry with matching amount and reciprocal accounts. Keep the individual transaction IDs unique. A migration must identify old records as uncorrelated when pairing cannot be proven. Guessing a pair because two timestamps match can produce a false history explanation.

For account closing, use a state transition method with a small policy table. For example, an open zero-balance account can close; a closed account remains readable; commands that change balance reject closed source or destination accounts. Test both transfer directions. Physical deletion remains appropriate only for genuinely unused records under the current rule. The difference is a domain decision, not a limitation of the delete button.

For deterministic time, inject a `Clock` and derive timestamps with an explicit zone policy. Inject an ID supplier independently. A fixed clock is excellent for testing ties, but ordering still needs a stable secondary key. UUID ordering is deterministic for a captured dataset but does not establish true event sequence. If sequence matters, model a sequence number or database ordering contract deliberately.

A candidate-state unit of work might follow this sketch:

```text
acquire coordinator lock
  capture current state
  construct isolated candidate service
  apply validated command to candidate
  persist candidate snapshot
  replace authoritative service reference
release coordinator lock
return accepted result
```

A failed save must skip replacement and return failure. Every reader and writer must respect the coordinator; otherwise the live reference can escape and bypass the boundary. This is a local teaching design. A database implementation should generally express the related writes and idempotency record in one database transaction instead of cloning the entire dataset for every request.

For HTTP responses, define contracts from the domain's outcomes. An invalid decimal amount might map to 400, a missing visible account to 404, and an account-state or version conflict to 409. A successful create should return an identity that can be read through a corresponding endpoint. Avoid exposing a raw exception stack or trusting an owner ID merely because it appears in JSON. Controllers translate; the command path enforces.

For idempotency, store a normalized request fingerprint with the accepted result. Reusing the key with the same payload should replay that result; a different payload must not silently create another operation. A unique database constraint and transactional result storage are stronger than an in-memory `if not found then insert` check. Include an ambiguous-response test where the operation committed but the client did not receive the response.

Review the final work by asking for demonstrations: a rejected sub-cent amount, a denied closed-account transfer, a failed persistence operation, a cross-owner lookup, and two competing identical retries. Require the author to explain which test exercises each behavior and which boundary owns it. Mid-level understanding appears in these failure explanations, not in the number of layers added to the folder tree.

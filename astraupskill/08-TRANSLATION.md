# 08. Translation: the same rules in C# and EF Core

[Course index](README.md)

| here (Java) | C# / EF Core |
|---|---|
| `Money` as `BigDecimal` with an enforced scale of 2 | `decimal` with `HasPrecision(18, 2)` in the model builder; enforce the scale in the value object (`decimal.Round(v, 2) == v` or throw) because `decimal` itself allows 28 digits |
| the detached-copy rule (callers get copies, never the stored object) | an `AsNoTracking()` read model for queries; a tracked entity only inside the command that changes it. The Java course hand-copies to get what EF gives you by choosing the tracking mode |
| the transfer pair (debit + credit) as one unit | two tracked changes and one `SaveChangesAsync()` inside `BeginTransactionAsync()`, with a shared `TransferId` (a `Guid`) on both ledger rows, which is what practice exercise 4 asks for |
| commit before publish (this CLI does the reverse: it mutates memory, then saves; practice exercise 6) | `SaveChangesAsync()` first, then update any in-memory read model or publish an event. Never mutate the cached balance before the commit returns |
| the stale-write check this app lacks (two sessions can overwrite each other; practice exercise 3) | `[ConcurrencyCheck]` or `[Timestamp]` on a `Version` property of `Account`; `DbUpdateConcurrencyException` becomes the "reload and retry" message |
| closing an account instead of deleting history (practice exercise 7) | `ClosedAt DateTime?` plus a global query filter; ledger rows keep their foreign key |

## The gap the course names, in C#

`BankService` publishes the new balance to memory before the snapshot is saved. In EF Core the equivalent mistake is updating a cached DTO or raising a domain event *before* `SaveChangesAsync` returns. The fix has the same shape in both: build the candidate, persist it, and only then publish; and the regression test injects a failing `SaveChangesAsync` (an in-memory provider with a `SaveChangesFailed` interceptor, or a fake `DbContext`) and asserts the read model is unchanged.

## Exercise

Implement `Transfer(fromId, toId, amount)` in EF Core: one transaction, two account updates with `[ConcurrencyCheck]`, two ledger rows sharing a `TransferId`, and the test that makes the second account update fail and asserts the first was rolled back.

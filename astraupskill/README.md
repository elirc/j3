# Bank simulator: consistent commands and durable snapshots

This course turns a small Java CLI into a focused lesson about state ownership, related writes, and meaningful CRUD rules. It simulates balances and transactions locally. There are no real funds, bank integrations, HTTP endpoints, or browser screens. The next-step exercises explain how to build a CRUD web adapter without mistaking the CLI's guarantees for database transactions or production authorization.

The starting defect is easy to demonstrate: the original `getAccountById` returned the service's actual mutable account. A caller could increase its balance without creating a transaction. Copying only the surrounding list did not solve that problem, because the list still contained the same mutable objects. The delivered service now owns its account instances and returns detached copies. This is the first boundary to understand before adding controllers.

A second boundary is persistence. Previously, the CLI wrote accounts and transaction history to separate files. Failure between those writes could leave one newer than the other. The application now captures both collections together and saves one versioned snapshot through atomic replacement. Old `accounts.txt` and `transactions.txt` files are read for initial migration and retained unchanged; once `bank.snapshot.txt` exists, it is authoritative.

## Start with a separate learning dataset

Install JDK 17 or newer and set `JAVA_HOME` to your JDK directory if needed. From the project root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\learning-bank
```

The second path is a directory, not a filename. The runner compiles the Java packages and passes that directory to `App`. With no override, it uses the project's `data` directory. The test command executes 28 regression tests, using temporary data for storage cases. It does not connect to external services.

Create Alice and Bob, deposit 10.00 into Alice, then transfer 2.50 to Bob. After a restart, inspect both balances and both sides of the transfer history. Rename Alice through menu 8. Try menu 9 on Alice and observe the refusal: an account with history cannot be deleted even after its balance later returns to zero. Create a separate unused account to exercise confirmed deletion.

## Learning sequence

Read the [code map](01-CODEBASE-MAP.md), then the [concepts](02-CONCEPTS.md) and [worked implementation](03-WORKED-CHANGE.md). Run the [tests and debugging exercises](04-TESTING-AND-DEBUGGING.md) and [CLI trace](07-TRACE-LAB.md). Attempt the [practice ladder](05-PRACTICE.md) before consulting [solution guidance](06-SOLUTIONS-AND-REVIEW.md). Finish with the [verification record](VERIFICATION.md), which distinguishes executed checks from future features.

A strong outcome is the ability to explain five things: who owns mutable state, which related changes happen together, when a command becomes durable, why deletion has domain restrictions, and where concurrency protection stops. The implementation is intentionally small enough to trace every method involved. Its in-memory synchronization and atomic file replacement are useful foundations, while request idempotency, cross-process concurrency, database transactions, transfer correlation IDs, and authenticated ownership remain explicit next steps.

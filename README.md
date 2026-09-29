# Bank Account Simulator

A Java 17 command-line learning project for accounts, deposits, withdrawals, transfers, and transaction history. It now includes holder renaming, restricted deletion, consistent snapshots, and regression tests. Start the detailed [junior-to-mid-level CRUD course](astraupskill/README.md) for source explanations, worked examples, debugging, exercises, and review guidance.

## Run and test

Install JDK 17 or newer. Set `JAVA_HOME` to the JDK directory if Java is not on PATH. From this project root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\learning-bank
```

`-Test` runs 28 dependency-free Java regression tests. `DataPath` is a directory; use a separate learning directory for experiments. Without an override the runner uses the project's `data` directory. The runner recompiles all source; existing old `out` binaries should not be used as the current implementation.

Manual compilation remains possible:

```powershell
javac --release 17 -Xlint:all -d out (Get-ChildItem -Recurse -Path src -Filter *.java | ForEach-Object FullName)
java -cp out bankapp.App .\data\learning-bank
```

## Structure and responsibilities

- `App`: gathers input, invokes commands, saves, then reports success.
- `BankService`: owns accounts/history, validates references, serializes operations on one service instance, and captures consistent snapshots.
- `BankAccount`: account value with guarded balance adjustments and detached copying.
- `Transaction` and `TransactionType`: immutable validated history entries and allowed event types.
- `Money`: shared decimal and required-text validation.
- `BankSnapshot`: accounts and transactions captured together, with defensive account copies.
- `FileStorage`: strict parsing, legacy migration, versioned snapshot writing, and atomic file replacement.
- `test/bankapp/RegressionTests.java`: behavior, concurrency, migration, and filesystem regression cases.

The [code map](astraupskill/01-CODEBASE-MAP.md) traces these collaborators method by method.

## Menu and behavior

Options 1–7 retain create, list, deposit, withdraw, transfer, history, and exit. Option 8 renames the account holder without changing identity or history. Option 9 deletes only a zero-balance account with no transaction references, after confirmation. Used accounts remain protected even when their balance returns to zero.

Amounts must be positive and exactly representable with two decimal places. Sub-cent inputs are rejected rather than rounded silently. Accounts may have a zero balance. Invalid operations preserve balances and history. Service reads return detached accounts so callers cannot bypass command history by mutating a returned object.

## Persistence and migration

The current file is `data/bank.snapshot.txt`, or that filename inside the selected directory. It contains a version header, tagged account/history records, and count footer. Saving validates and serializes a complete snapshot, writes a temporary sibling, and atomically replaces the destination. A failed or unsupported atomic replacement is reported; there is no non-atomic fallback.

When no snapshot exists, a complete legacy pair of `accounts.txt` and `transactions.txt` is loaded. A new directory with neither file starts empty. Only one legacy file is an error. The first successful save writes the snapshot while preserving the legacy files. Thereafter the snapshot is authoritative, and corruption does not silently fall back to stale legacy data. Old binaries still use the old format; do not alternate versions against the same dataset.

## Verification and next steps

Delivery verification passed 28 Java tests and seven separate-process CLI scenarios. See [verification](astraupskill/VERIFICATION.md) for evidence and limits. This remains a local simulator: it has no HTTP API, browser UI, authentication, or database. The [practice ladder](astraupskill/05-PRACTICE.md) develops those skills through correlation IDs, account closing, deterministic dependencies, units of work, transactions, ownership, and idempotent requests.

Synchronization is limited to one service instance. Atomic replacement does not prevent stale saves from independent processes or prove power-loss durability. In-memory commands precede persistence; a save failure exits the CLI without reporting success. Historical balance reconciliation and shared transfer IDs remain future work.

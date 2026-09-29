# Worked change: commit balances and history as one file

Consider the original `App.saveData`: it called `saveAccounts` and then `saveTransactions`. Imagine a transfer has changed both balances and appended two history records in memory. Writing accounts succeeds, but writing transactions fails. On the next launch, the application sees the new balances and the old history. Reordering the two calls only reverses which half can become stale; it does not solve the two-write problem.

The delivered design introduces `BankSnapshot`, captured by `BankService.snapshot` under the same monitor used for commands. The storage adapter accepts that single value through `saveSnapshot`. It validates the account and transaction collections together, serializes everything, and only then touches a temporary file. The final replace operation changes the authoritative file as one unit.

The format is deliberately inspectable:

```text
# bank-snapshot-v1
A|A|Alice|7.50|2026-01-02T03:04
A|B|Bob|2.50|2026-01-02T03:04
T|out-id|A|TRANSFER_OUT|2.50|2026-01-02T03:05|Lunch|B
T|in-id|B|TRANSFER_IN|2.50|2026-01-02T03:05|Lunch|A
END|2|2
```

This is an illustrative snapshot fragment, not a claim that the listed history explains the opening balance. The `A` and `T` tags distinguish record shapes. Text fields escape backslashes, pipes, line feeds, and carriage returns. The footer counts parsed accounts and transactions. A missing footer, unexpected record type, bad amount, duplicate ID, or missing referenced account rejects the load.

Migration is explicit. When `bank.snapshot.txt` is absent, the loader accepts a complete `accounts.txt` and `transactions.txt` pair, or an entirely new directory with neither file. Only one legacy file is an error. The first successful save creates the snapshot and leaves both legacy files unchanged. Subsequent launches use the snapshot even if someone edits a legacy file. A corrupt authoritative snapshot does not trigger fallback to older legacy data, because that would silently roll the application backward.

The ownership correction complements persistence. The service constructor copies loaded account objects rather than retaining caller references. Every outward account read returns a copy. `BankSnapshot.accounts()` also returns copies, so a consumer cannot alter the stored snapshot through a getter. Immutable transaction objects can be shared safely through an immutable list.

The command correction constructs transaction values before changing balances. Input failures therefore occur before the balance mutations. The synchronized transfer then debits, credits, and appends both events without allowing another command or snapshot read on the same instance to interleave. This is an in-memory critical section, not a database transaction.

Read the [tests](../test/bankapp/RegressionTests.java) for invalid-save byte preservation, failed replacement cleanup, migration authority, and competing withdrawals. Notice the evidence each provides: real file contents establish persistence behavior, while concurrent service calls establish only the single-instance lock boundary. The future database exercise must replace both the persistence approach and the command/commit coordination rather than simply wrapping this CLI service in an HTTP controller.

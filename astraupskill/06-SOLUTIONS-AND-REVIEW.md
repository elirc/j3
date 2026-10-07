# Solution guidance and a senior review of the delivered code

Try the [practice ladder](05-PRACTICE.md) before reading this. The first half is a review of the code as delivered. On 2026-10-06, findings 1, 2, 3, 5 and 7 were reproduced as real CLI runs against a scratch build outside the repo. Findings 4 and 6 come from reading the code at the cited lines. The second half sketches solutions. The simulator has no transfer correlation ids, closed-account status, injected clock, database, HTTP layer, ownership or idempotency yet.

## Verified findings

Each finding names the code, the observed behavior, and why a reviewer would raise it.

1. **Failures exit 0.** `App.main` catches `IOException`, prints it, and returns (`src/bankapp/App.java:34-40`). Starting on a snapshot whose first line is `garbage` prints `Failed to load or save bank data: Unsupported or missing bank snapshot header.` and exits with status 0. `run.ps1` ends with `exit $LASTEXITCODE`, so it reports 0 too. The data is protected (the file is not rewritten), but automation cannot tell success from failure. → Exercise 1.

2. **Scientific notation is accepted as money input.** `readAmount` uses `new BigDecimal(amountText)` (`App.java:229`). `1e3` becomes a deposit of `1000.00`. `Money.nonNegative` (`src/bankapp/model/Money.java:9`) correctly rejects `1.001`, but it validates the *number*, so it cannot see that the *text* was surprising. Input-format rules belong at the adapter, value rules in the domain. → Exercise 2.

3. **Lost update between two sessions.** Each process loads once (`App.java:28-32`) and later replaces the whole file (`src/bankapp/storage/FileStorage.java:73-85`). It never compares against what is on disk now. Reproduced: session A opens, session B creates Bob and exits, then A creates Alice and exits. The final snapshot contains only Alice. `synchronized` (`src/bankapp/service/BankService.java:44-195`) guards one JVM only, and `ATOMIC_MOVE` (`FileStorage.java:83`) guarantees a whole file, not a current one. → Exercise 3.

4. **Transfer halves cannot be paired from storage.** The two records get independent UUIDs (`BankService.java:233-235`) and separate `LocalDateTime.now()` calls (`BankService.java:227`). `getAllTransactions` orders by timestamp, then by the random id (`BankService.java:165-171`). That is also the save order, so even adjacency in the file is not a reliable pairing. The only link is the reciprocal `relatedAccountId`, which is ambiguous when the same transfer repeats. → Exercise 4.

5. **Balances are trusted, not derived.** Load-time validation (`BankService.java:27-42`, called from `FileStorage.java:68-71`) checks ids and references only. The `END|accounts|transactions` footer (`FileStorage.java:40-43`) counts records; it is not a checksum. Reproduced: changing an account's balance field from `1000.00` to `999999.00` in the snapshot loads silently and lists `$999999.00`. → Exercise 5. The fix has a real cost: the test fixture seeds balances with no history (`test/bankapp/RegressionTests.java:20`), so a naive reconciliation check made 16 of the 28 regression tests fail on a scratch copy.

6. **Memory changes before persistence.** For example, `deposit` mutates the account and appends history (`BankService.java:82-83`), and only afterwards does `App.saveData` run (`App.java:134`, `235-237`). An `IOException` escapes the menu loop, because that loop catches only `IllegalArgumentException | IllegalStateException` (`App.java:70`), and the process ends. In this CLI that is acceptable. If you reuse `BankService` in a server, the next request would see state that was never saved. → Exercise 6.

7. **Every exit path rewrites the file.** Menu 7 and the EOF handler both call `saveData` (`App.java:61-65`, `74-76`), even for a session that only listed accounts. Reproduced: a session that only lists accounts and then hits EOF replaces the file with identical bytes, and the modification time changes. Each exit is therefore another full write (and another lost-update window, see 3). Each command also re-sorts and rewrites the entire history (`FileStorage.java:76-77`), which costs O(history) per command. That is fine for a simulator and a reason to use row-level writes in a database.

What is solid and worth copying: detached copies on every outward read (`BankService.java:57`, `61`, `68`, `174`, plus `BankSnapshot.accounts()`); transaction values built *before* balances change, so validation failures leave no partial state (`BankService.java:123-141`); a strict escaping parser that rejects unknown escapes and wrong field counts (`FileStorage.java:101-119`); and an invalid snapshot is rejected before the temporary file is created (`FileStorage.java:74`, `80`).

## Solution sketches

**Exit codes (1).** In `main`'s catch, print to `System.err` and call `System.exit(1)`. Keep the menu's own `Operation failed` messages as non-fatal. A failed command is not a failed process.

**Plain decimals (2).** Check the text with `amountText.matches("[0-9]+([.][0-9]{1,2})?")` before parsing. This turned `exercise 2` green on a scratch copy. Leave sub-cent rejection to `Money`; the two checks now have different jobs.

**Stale saves (3).** In `FileStorage`, remember the bytes read by `loadSnapshot` (null for a new directory). In `saveSnapshot`, re-read the file and throw an `IOException` mentioning `conflict` when it differs, and refresh the baseline after each successful move. This also turned `exercise 3` green on a scratch copy. Be honest about the limit: another process can still write between your comparison and your `Files.move`. A sibling lock file narrows the window, and a database row version closes it.

**Transfer id (4).** Create one id before building both records and pass it into `createTransaction`. Validate on load that every v2 transfer id appears exactly twice: one `TRANSFER_OUT` and one `TRANSFER_IN`, with the same amount and reciprocal accounts. Load v1 records as uncorrelated; do not infer pairs.

**Reconciliation (5).** Sum the signed history per account in the constructor and compare it with the balance. Fix the fixtures by modelling an opening balance explicitly (an `OPENING` entry or a migration), not by disabling the check.

**Unit of work (6).**

```text
acquire coordinator lock
  candidate = new BankService(current.snapshot())
  apply validated command to candidate
  storage.saveSnapshot(candidate.snapshot())   // throws -> keep current
  current = candidate
release lock; return result
```

Every reader and writer must go through the coordinator, or the live reference escapes. A database version should put the related writes and the idempotency record in one transaction instead of cloning the dataset per request.

**HTTP contracts (8).** Map invalid input to 400, a missing or foreign account to 404 (do not reveal that it exists), and insufficient funds or a version conflict to 409. Controllers translate; the command path enforces. For idempotency, store a normalized request fingerprint with the accepted result under a unique constraint. The same key with the same payload replays the result; the same key with a different payload is a conflict. Include the ambiguous case where the commit succeeded but the client never saw the response.

## Review checklist

Ask the author to demonstrate a rejected sub-cent amount, a rejected `1e3`, a nonzero exit on a corrupt file, a refused stale save, a denied closed-account transfer, and two competing identical retries. For each one, they should name the test that shows it and the boundary that owns it.

# Trace lab: balances, history, migration, and refusal paths

Use a new learning directory so this exercise cannot change the normal dataset. From the project root, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\trace-bank
```

Create Alice and Bob through menu 1 and record their displayed IDs. Menu 2 should show both with zero balances. Deposit `10.00` into Alice through menu 3, including a short description. Transfer `2.50` from Alice to Bob through menu 5. List the accounts: Alice should have 7.50 and Bob 2.50. View each account's history through menu 6 and identify the outgoing and incoming records.

Exit with 7, restart with the same directory, and repeat the reads. This matters because reading the same service object immediately after a command does not test persistence. Inspect `data/trace-bank/bank.snapshot.txt`: it should contain the version header, account records, transaction records, and a final footer with both record counts. The transaction IDs are distinct; explain why there is currently no reliable shared transfer ID for correlating otherwise identical repeated transfers.

Now submit a deposit of `1.001`. The CLI should reject the amount and preserve both balance and history. Try a transfer larger than Alice's available balance and a transfer from Alice to herself. Both should fail without partial movement. Use menu 8 to rename Alice; restart and verify that the name changed while identity, balance, and history remained.

Try deleting Alice with menu 9 and confirmation `yes`. The service must refuse. Even if you later withdraw the balance to zero, history still protects the account from deletion. Create a third unused account, first cancel its deletion with `no`, then confirm with `yes`. Verify its absence after restarting. This demonstrates a domain-specific delete contract rather than indiscriminate removal of referenced records.

For a corruption experiment, close the application and copy the learning directory to a fresh destination. Use an unused destination name; `Copy-Item` into an existing directory can create a different nesting layout than expected.

```powershell
Copy-Item -LiteralPath .\data\trace-bank -Destination .\data\trace-bank-corrupt -Recurse
Add-Content -LiteralPath .\data\trace-bank-corrupt\bank.snapshot.txt -Value 'broken'
$beforeHash = (Get-FileHash -LiteralPath .\data\trace-bank-corrupt\bank.snapshot.txt).Hash
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\trace-bank-corrupt
$afterHash = (Get-FileHash -LiteralPath .\data\trace-bank-corrupt\bank.snapshot.txt).Hash
$beforeHash -eq $afterHash
```

Startup should report a storage error before the welcome menu and preserve the file hash. Keep the valid source directory as your recovery copy. Do not delete the authoritative snapshot merely to force legacy fallback; the legacy files may represent an older state.

Finally, run the [regression harness](../test/bankapp/RegressionTests.java). Compare the migration test with the manual corruption trace: one proves that a first snapshot can be created without overwriting the old pair, while the other proves that an existing invalid snapshot is not silently replaced. Write down the exact commit point and explain why a second simultaneously running application instance remains outside the tested guarantee.

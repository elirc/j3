# Ownership, transactions, and why CRUD has business rules

A service should be the authority for its own state. If another object can mutate an account obtained from a read method, the service's deposit and withdrawal rules become optional. The original implementation had exactly this problem. A defensive list copy protected only the list structure; the elements were still shared references.

```java
BankAccount view = service.getAccountById("A");
view.increaseBalance(new BigDecimal("5.00"));
// The delivered service's balance and history do not change.
// view is a detached object owned by the caller.
```

This is distinct from immutability. `BankAccount` remains mutable, but owned service instances never escape. `Transaction` is immutable. A snapshot combines detached account values and immutable transaction values. When designing an API, serializing a DTO often provides a natural boundary, but internal components still need an ownership policy before serialization happens.

A transfer changes multiple related facts: source balance, destination balance, outgoing history, and incoming history. The relevant invariant is that the accepted transfer moves the same positive amount between accounts and records both directions. `synchronized` protects those related changes within one service instance. It also protects snapshot capture. Without synchronized capture, reading balances and then history in two calls could observe different points in time even if each individual command were internally correct.

Atomic persistence is a different boundary. The snapshot file is replaced as one unit, preventing the previous two-file split where balances could be newer than history. A footer records expected account and transaction counts so a normally truncated or incomplete file is rejected. The footer is not a cryptographic integrity check and does not prove that balances reconcile with all historical events.

Neither boundary creates a cross-process database transaction. Two application instances still have independent service locks and can overwrite the same destination with stale snapshots. Likewise, a save error occurs after the in-memory command has executed. The CLI exits without reporting success, but a future reusable web service should coordinate command mutation and durable commit more deliberately.

Decimal amounts are validated with `RoundingMode.UNNECESSARY`. A submitted `1.001` is rejected rather than silently changed to `1.00`. Model validation also applies when records arrive from disk. Zero is allowed for an account balance but not for a deposit, withdrawal, or transfer amount. This difference illustrates why a field's type alone rarely captures all domain rules.

CRUD does not mean every resource supports arbitrary physical deletion. `renameAccount` changes holder text while retaining identity, creation time, balance, and history. `deleteUnusedAccount` permits removal only when balance is zero and no transaction references the account. A used zero-balance account still carries history and cannot be deleted through this command. An archive or close-account workflow would be a separate state transition with its own rules.

Finally, a transfer's two records currently have distinct transaction IDs and reciprocal account references, but no shared transfer ID. Timestamps and amounts are insufficient to correlate identical repeated transfers reliably. Recognizing this remaining limitation is part of the learning objective: build guarantees around explicit identifiers rather than guesses based on similar-looking records.

## Glossary

*Money*: an amount with an enforced scale, never a float. *Detached copy*: what callers receive; changing it does not change the account. *Transfer pair*: the debit and the credit that must both exist or neither. *Snapshot*: the whole state written to one file. *Commit before publish*: save first, then update memory or report success. *Stale write*: a save based on a baseline storage has moved past. *Correlation id*: the shared id on both halves of a transfer (exercise 1).


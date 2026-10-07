# Practice ladder: from local commands to a mid-level CRUD service

These are proposed extensions, not delivered features. Exercises 1-5 each come from a behavior you can observe in the current code (see the findings in [06](06-SOLUTIONS-AND-REVIEW.md)) and have a runnable **Check**. Exercises 6-8 are design capstones whose check is a test you write yourself. Do them in order: the earlier ones teach the guarantees your later HTTP controller will expose.

## The check file

Save the file below **outside the repository** (for example `C:\practice\j3\bank-cli.test.mjs`). It needs only Node 20+ and JDK 17+ on PATH: it compiles `src/` into a fresh `os.tmpdir()` folder, drives the real `bankapp.App` CLI through stdin with throwaway data directories, and deletes its temporary folder at the end. It never writes inside the repo.

```powershell
# from the repo root
node --test C:\practice\j3\bank-cli.test.mjs
```

Against the delivered code (re-run 2026-10-06, JDK 17.0.10, Node 22.16) the result is **1 pass, 5 fail**: the baseline passes and every exercise check fails for the reason it names. That is the starting point, not a broken file. Each exercise is done when its check turns green *and* `RegressionTests` still reports `failed=0`. The whole file takes about a minute because each scenario starts several JVMs.

```js
// Dependency-free checks for the j3 practice ladder (Node 20+, JDK 17+ on PATH).
// From the repo root: node --test path/to/bank-cli.test.mjs   (or set J3_ROOT to the repo root)
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawn, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync, readdirSync, statSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const root = process.env.J3_ROOT ?? process.cwd();
const work = mkdtempSync(join(tmpdir(), 'j3-practice-'));
const classes = join(work, 'classes');

function javaFiles(dir) {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    return statSync(p).isDirectory() ? javaFiles(p) : p.endsWith('.java') ? [p] : [];
  });
}
before(() => {
  execFileSync('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', classes, ...javaFiles(join(root, 'src'))]);
});
after(() => rmSync(work, { recursive: true, force: true }));
const fresh = (name) => mkdtempSync(join(work, name + '-'));
const run = (dir, input) =>
  spawnSync('java', ['-cp', classes, 'bankapp.App', dir], { input, encoding: 'utf8', timeout: 30_000 });
const snapshot = (dir) => readFileSync(join(dir, 'bank.snapshot.txt'), 'utf8');
const accountIds = (dir) => snapshot(dir).split(/\r?\n/).filter((l) => l.startsWith('A|')).map((l) => l.split('|')[1]);

test('baseline: create, deposit, restart keeps balance', () => {
  const dir = fresh('baseline');
  assert.equal(run(dir, '1\nAlice\n7\n').status, 0);
  const [id] = accountIds(dir);
  run(dir, `3\n${id}\n10.00\nseed\n7\n`);
  assert.match(run(dir, '2\n7\n').stdout, /Balance: \$10\.00/);
});

test('exercise 1: a load failure exits nonzero and leaves the file untouched', () => {
  const dir = fresh('corrupt');
  writeFileSync(join(dir, 'bank.snapshot.txt'), 'broken\n');
  const r = run(dir, '7\n');
  assert.match(r.stdout + r.stderr, /Failed to load/);
  assert.equal(readFileSync(join(dir, 'bank.snapshot.txt'), 'utf8'), 'broken\n');
  assert.notEqual(r.status, 0, 'App.main must signal failure with a nonzero exit code');
});

test('exercise 2: amounts must be plain decimals (no 1e3)', () => {
  const dir = fresh('sci');
  run(dir, '1\nAlice\n7\n');
  const [id] = accountIds(dir);
  const r = run(dir, `3\n${id}\n1e3\n\n2\n7\n`);
  assert.match(r.stdout, /Operation failed/);
  assert.match(r.stdout, /Balance: \$0\.00/);
});

test('exercise 3: a stale second session must not erase another session\'s save', async () => {
  const dir = fresh('race');
  // Session A loads the empty directory and waits at the menu.
  const a = spawn('java', ['-cp', classes, 'bankapp.App', dir]);
  let aOut = '';
  a.stdout.on('data', (d) => (aOut += d));
  const deadline = Date.now() + 30_000;
  while (!aOut.includes('Choose an option')) {
    if (Date.now() > deadline) { a.kill(); assert.fail('session A never showed the menu'); }
    await new Promise((ok) => setTimeout(ok, 50));
  }
  // Session B starts later, creates Bob, and exits: Bob is now on disk.
  assert.equal(run(dir, '1\nBob\n7\n').status, 0);
  assert.match(snapshot(dir), /\|Bob\|/);
  // Session A, still holding its old in-memory view, creates Alice and exits.
  a.stdin.end('1\nAlice\n7\n');
  await new Promise((ok) => a.on('close', ok));
  const file = snapshot(dir);
  const conflictReported = /conflict|changed on disk|reload/i.test(aOut);
  assert.ok(/\|Bob\|/.test(file) || conflictReported,
    'Bob was silently overwritten by the stale session (lost update)');
});

test('exercise 4: both halves of a transfer share a transfer id (new last T| field)', () => {
  const dir = fresh('pair');
  run(dir, '1\nAlice\n1\nBob\n7\n');
  const [alice, bob] = accountIds(dir);
  run(dir, `3\n${alice}\n10.00\n\n5\n${alice}\n${bob}\n2.50\nLunch\n7\n`);
  const rows = snapshot(dir).split(/\r?\n/).filter((l) => l.startsWith('T|') && /TRANSFER_/.test(l)).map((l) => l.split('|'));
  assert.equal(rows.length, 2);
  for (const r of rows) assert.equal(r.length, 9, 'expected T|id|account|type|amount|time|description|related|transferId');
  assert.ok(rows[0][8] && rows[0][8] === rows[1][8]);
  assert.notEqual(rows[0][1], rows[1][1], 'individual transaction ids stay unique');
});

test('exercise 5: a balance that does not match history is rejected at startup', () => {
  const dir = fresh('reconcile');
  run(dir, '1\nAlice\n7\n');
  const [id] = accountIds(dir);
  run(dir, `3\n${id}\n10.00\n\n7\n`);
  const path = join(dir, 'bank.snapshot.txt');
  writeFileSync(path, snapshot(dir).replace('|10.00|2', '|999999.00|2'));
  assert.match(snapshot(dir), /\|999999\.00\|/);
  const r = run(dir, '2\n7\n');
  assert.doesNotMatch(r.stdout, /Balance: \$999999\.00/);
  assert.match(r.stdout + r.stderr, /Failed to load/);
});
```

## 1. Make startup and save failures exit nonzero

**Goal.** `App.main` catches `IOException`, prints `Failed to load or save bank data: ...`, and returns normally (`src/bankapp/App.java:34-40`), so a corrupt snapshot ends with exit code **0**. `run.ps1` forwards that 0, and a script or CI job would treat the failed startup as success. Make load and save failures exit with a nonzero status while still printing the message and leaving the file untouched.

**Check.** `exercise 1` turns green. Then run `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath <dir with a broken snapshot>; $LASTEXITCODE` and see a nonzero value.

## 2. Accept only plain decimal amounts

**Goal.** `readAmount` passes the typed text straight to `new BigDecimal(...)` (`App.java:224-233`), which accepts scientific notation: entering `1e3` deposits **1000.00**. `Money.positive` cannot catch this because, by the time it sees the value, `1e3` and `1000` are the same number. Validate the *text* at the CLI boundary (digits with up to two decimals) and keep `Money` as the domain rule. Decide on and document whether `+5` or ` 5 ` are accepted.

**Check.** `exercise 2` turns green: `1e3` is reported as `Operation failed`, and the balance stays `0.00`.

## 3. Refuse stale saves from a second session

**Goal.** Each CLI process loads the snapshot once and later replaces the whole file (`FileStorage.java:73-85`). If two sessions are open on the same directory, the session that saves later silently erases what the other one saved: a lost update. Atomic replacement prevents torn files, not stale writes. Keep the bytes (or a revision number) you loaded. Before replacing the file, compare them with what is on disk now and refuse with a message containing `conflict` if they differ. Refresh the baseline after each successful save of your own.

**Check.** `exercise 3` turns green. In the scenario, session A opens first, B creates Bob and exits, then A creates Alice. Today the final file holds only Alice. After your change, A reports a conflict, or Bob survives. Then explain in one paragraph why a compare-then-move is still a race between processes and what a lock file or a database row version adds.

## 4. Give both halves of a transfer one transfer id

**Goal.** `transfer` builds two records with independent `TXN-<uuid>` ids and separate `LocalDateTime.now()` calls (`BankService.java:123-136`, `210-231`). Nothing on disk says which `TRANSFER_OUT` belongs to which `TRANSFER_IN`. Add a `transferId` to `Transaction` and write it as a **new last field** of every `T|` record, empty for deposits and withdrawals. That changes the format, so bump the header to `# bank-snapshot-v2` and keep reading v1 files, where `transferId` is absent and the old pairs stay explicitly uncorrelated. Never guess them from matching amounts and timestamps.

**Check.** `exercise 4` turns green. Each transfer row has 9 `|`-separated parts, both share a non-empty last field, and the individual ids still differ. Add a `RegressionTests` case showing that a v1 snapshot still loads.

## 5. Reconcile balances with history on load

**Goal.** The `BankService` constructor checks duplicate ids and missing accounts (`BankService.java:27-42`), but it never checks that a stored balance equals the sum of that account's history. If you edit an account line from `10.00` to `999999.00`, the file loads and lists `$999999.00` with one 10.00 deposit behind it. Add the reconciliation.

There is a real design decision here. The test fixture `service()` (`test/bankapp/RegressionTests.java:20`) and any legacy data hold balances with no history behind them. A naive check makes **16 of the 28** regression tests fail (measured on a scratch copy). Introduce an explicit opening-balance entry or migration step, and do not weaken the check.

**Check.** `exercise 5` turns green (startup reports `Failed to load`) and `RegressionTests` is back to `failed=0`.

## 6. Unit of work: publish only persisted state (capstone)

Today a command mutates the service and then `saveData` runs (`App.java:133-134`, `235-237`). In the CLI a save failure ends the process, so the divergence is invisible. In a long-lived web service it would not be. Build a coordinator that runs the command on a candidate copy of the state, saves the candidate, and only then swaps it in. **Check (yours):** a `RegressionTests` case whose storage fails on save and whose service still reports the previous balance and history afterwards.

## 7. Close accounts instead of deleting history (capstone)

Add an account status and `closeAccount`. Decide the rules, for example: it requires a zero balance, history stays readable, and deposits, withdrawals and transfers that touch a closed account are rejected. Rename is your own policy decision. **Check (yours):** one test per entry point, including a transfer *into* a closed account.

## 8. Database-backed HTTP adapter with ownership and idempotency (capstone)

Expose create, list, lookup, rename, close, deposit, withdraw and transfer. Define the JSON amount format and error bodies before writing controllers. Put the balance update, the history rows and the idempotency record in one database transaction with row-version protection. Scope every query to the authenticated owner. **Check (yours):** HTTP-level tests for invalid precision (400), a missing or foreign account (404), insufficient funds or a version conflict (409), rollback after a deliberately failed history insert, and two *simultaneous* retries with the same idempotency key that produce one transfer.

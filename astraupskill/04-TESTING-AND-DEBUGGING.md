# Regression tests that expose ownership and consistency failures

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test`. The Java 17 runner compiles production and test sources with `--release 17 -Xlint:all`. The dependency-free harness reports each named case and exits nonzero on failure. It runs 28 cases; the delivery's separate CLI orchestration adds seven more scenarios in new Java processes.

Six initial checks failed against the original source: a returned account could mutate service balance, constructor input remained shared, duplicate account IDs were accepted, orphan transactions were accepted, sub-cent deposits were silently rounded, and negative model balances were allowed. These failures describe behavior, not the number of getters or classes in the implementation.

The ownership test deliberately calls a valid mutator on a returned account and then rereads the authoritative service state. Merely comparing object references would be weaker: it would prove different objects, but not necessarily that every relevant nested value was isolated. The suite repeats the check through list, creation, saving, and snapshot access paths because each is a possible escape route.

The competing-withdrawal test begins with a balance of 10.00 and submits 64 withdrawals of 1.00 through eight worker threads. A latch releases the queued work, and every future is observed with a timeout. Exactly ten operations must succeed, balance must finish at zero, and history must contain ten entries. A second case captures snapshots while transfers run and checks that total balance and paired history remain internally consistent. These tests exercise one shared service instance; they say nothing about two independent processes sharing a file.

For storage debugging, inspect three boundaries: parsed model values, validated snapshot, and destination replacement. A malformed amount is converted into an IOException with file and line context; it should not leak as an uncaught number parser failure. An invalid snapshot passed to saving is rejected before the previous file changes. A nonempty directory at the destination forces a move failure, allowing the test to verify preservation and temporary-file cleanup.

A useful debugger sequence is:

1. Break in `transfer` before transaction construction and inspect both balances.
2. Step through debit, credit, and history append while observing the held service monitor.
3. Break in `snapshot` and inspect a detached result.
4. Break before `Files.move` and compare the old destination with the temporary sibling.
5. Restart the process and inspect `loadSnapshot`, not the old in-memory object.

To investigate corruption, work on a copied learning directory. Remove the footer from its snapshot and start the CLI. It should report a load error before displaying the welcome menu. Compare the file hash before and after; startup must not rewrite the rejected file. The [trace lab](07-TRACE-LAB.md) provides a step-by-step manual version.

Keep the test scope honest. No browser, HTTP routing, database rollback, request retry, or multi-process lock is verified here. The compiler required reviewed full-access execution in this environment because restricted `--release` compilation could not close resources. That successful compilation and all test results are recorded centrally; a tooling error should never be counted as an application test pass.

## The competing-withdrawal case

Here is the concurrency case described above, from `test/bankapp/RegressionTests.java:50-57`. The latch is what makes the 64 submissions compete; the three assertions cover balance, success count and history together, because any one of them alone could pass over a broken guard.

```java
        test("competing withdrawals cannot overdraw one service",()->{
            var s=service();var executor=Executors.newFixedThreadPool(8);var gate=new CountDownLatch(1);var futures=new java.util.ArrayList<Future<Boolean>>();
            try {
                for(int i=0;i<64;i++) futures.add(executor.submit(()->{gate.await();try{s.withdraw("A",money("1"),"");return true;}catch(IllegalStateException error){return false;}}));
                gate.countDown();int successful=0;for(var future:futures) if(future.get(10,TimeUnit.SECONDS)) successful++;
                equal(10,successful);equal(money("0.00"),s.getAccountById("A").getBalance());equal(10,s.getAllTransactions().size());
            } finally {gate.countDown();executor.shutdownNow();}
        });
```


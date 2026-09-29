package bankapp;
import bankapp.model.*;
import bankapp.service.BankService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import bankapp.storage.FileStorage;
import java.util.concurrent.*;
public class RegressionTests {
    static int passed,failed;
    interface Check { void run() throws Exception; }
    static void test(String name,Check check) { try { check.run();passed++;System.out.println("PASS "+name); } catch(Throwable error) { failed++;System.out.println("FAIL "+name+": "+error); } }
    static void equal(Object expected,Object actual) { if(!expected.equals(actual)) throw new AssertionError("Expected "+expected+"; got "+actual); }
    static void rejects(Class<? extends Throwable> type,Check check) throws Exception { try { check.run(); } catch(Throwable error) { if(type.isInstance(error)) return;throw error; } throw new AssertionError("Expected "+type.getSimpleName()); }
    static BigDecimal money(String value) { return new BigDecimal(value); }
    static BankAccount account(String id,String balance) { return new BankAccount(id,"Learner",money(balance),LocalDateTime.of(2026,1,2,3,4)); }
    static BankService service() { return new BankService(List.of(account("A","10.00"),account("B","0.00")),List.of()); }
    interface WithDirectory { void run(Path directory) throws Exception; }
    static void withDirectory(WithDirectory check) throws Exception {
        Path directory=Files.createTempDirectory("j3-regression-").toAbsolutePath().normalize();
        Path temporaryRoot=Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if(!directory.startsWith(temporaryRoot) || !directory.getFileName().toString().startsWith("j3-regression-")) throw new AssertionError("Unsafe temporary path");
        try { check.run(directory); }
        finally { try(var files=Files.walk(directory)) { for(Path file:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file); } }
    }
    public static void main(String[] args) throws Exception {
        test("returned account cannot change service balance",()->{var s=service();s.getAccountById("A").increaseBalance(money("5"));equal(money("10.00"),s.getAccountById("A").getBalance());});
        test("constructor does not retain caller-owned mutable account",()->{var a=account("A","10.00");var s=new BankService(List.of(a),List.of());a.decreaseBalance(money("1"));equal(money("10.00"),s.getAccountById("A").getBalance());});
        test("duplicate account ids rejected",()->rejects(IllegalArgumentException.class,()->new BankService(List.of(account("A","1"),account("A","2")),List.of())));
        test("orphan transaction rejected",()->rejects(IllegalArgumentException.class,()->new BankService(List.of(),List.of(new Transaction("T","missing",TransactionType.DEPOSIT,money("1"),LocalDateTime.now(),null,null)))));
        test("sub-cent deposit rejected without balance or history mutation",()->{var s=service();rejects(IllegalArgumentException.class,()->s.deposit("A",money("1.001"),""));equal(money("10.00"),s.getAccountById("A").getBalance());equal(0,s.getAllTransactions().size());});
        test("negative model balance rejected",()->rejects(IllegalArgumentException.class,()->account("A","-1")));
        test("all read and creation paths detach mutable accounts",()->{
            var s=service();s.getAllAccounts().get(0).increaseBalance(money("1"));s.getAccountsForSaving().iterator().next().increaseBalance(money("1"));
            var created=s.createAccount("New");created.increaseBalance(money("3"));equal(money("0.00"),s.getAccountById(created.getId()).getBalance());equal(money("10.00"),s.getAccountById("A").getBalance());
        });
        test("snapshot accessors isolate account mutations",()->{var s=service();var snapshot=s.snapshot();snapshot.accounts().get(0).increaseBalance(money("1"));equal(money("10.00"),snapshot.accounts().get(0).getBalance());equal(money("10.00"),s.getAccountById("A").getBalance());});
        test("model rejects overdraft and invalid balance adjustments",()->{var a=account("A","1");rejects(IllegalStateException.class,()->a.decreaseBalance(money("2")));rejects(IllegalArgumentException.class,()->a.increaseBalance(money("-1")));equal(money("1.00"),a.getBalance());});
        test("duplicate transaction ids rejected",()->{var t=new Transaction("T","A",TransactionType.DEPOSIT,money("1"),LocalDateTime.now(),null,null);rejects(IllegalArgumentException.class,()->new BankService(List.of(account("A","1")),List.of(t,t)));});
        test("transfer related account must exist",()->{var t=new Transaction("T","A",TransactionType.TRANSFER_OUT,money("1"),LocalDateTime.now(),null,"missing");rejects(IllegalArgumentException.class,()->new BankService(List.of(account("A","1")),List.of(t)));});
        test("transaction model rejects invalid amount and relationship",()->{
            rejects(IllegalArgumentException.class,()->new Transaction("T","A",TransactionType.DEPOSIT,money("0"),LocalDateTime.now(),null,null));
            rejects(IllegalArgumentException.class,()->new Transaction("T","A",TransactionType.TRANSFER_OUT,money("1"),LocalDateTime.now(),null,"A"));
        });
        test("valid transfer conserves balance and records both directions",()->{var s=service();var result=s.transfer("A","B",money("2.50"),"Lunch");equal(2,result.size());equal(money("7.50"),s.getAccountById("A").getBalance());equal(money("2.50"),s.getAccountById("B").getBalance());equal(TransactionType.TRANSFER_OUT,result.get(0).getType());equal(TransactionType.TRANSFER_IN,result.get(1).getType());equal("B",result.get(0).getRelatedAccountId());});
        test("failed transfers preserve both accounts and history",()->{var s=service();rejects(IllegalStateException.class,()->s.transfer("A","B",money("11"),""));rejects(IllegalArgumentException.class,()->s.transfer("A","A",money("1"),""));rejects(IllegalArgumentException.class,()->s.transfer("A","missing",money("1"),""));equal(money("10.00"),s.getAccountById("A").getBalance());equal(money("0.00"),s.getAccountById("B").getBalance());equal(0,s.getAllTransactions().size());});
        test("competing withdrawals cannot overdraw one service",()->{
            var s=service();var executor=Executors.newFixedThreadPool(8);var gate=new CountDownLatch(1);var futures=new java.util.ArrayList<Future<Boolean>>();
            try {
                for(int i=0;i<64;i++) futures.add(executor.submit(()->{gate.await();try{s.withdraw("A",money("1"),"");return true;}catch(IllegalStateException error){return false;}}));
                gate.countDown();int successful=0;for(var future:futures) if(future.get(10,TimeUnit.SECONDS)) successful++;
                equal(10,successful);equal(money("0.00"),s.getAccountById("A").getBalance());equal(10,s.getAllTransactions().size());
            } finally {gate.countDown();executor.shutdownNow();}
        });
        test("snapshot stays internally consistent during transfers",()->{
            var s=service();var executor=Executors.newSingleThreadExecutor();var gate=new CountDownLatch(1);
            try {
                var writer=executor.submit(()->{gate.await();for(int i=0;i<100;i++){s.transfer("A","B",money("0.01"),"");s.transfer("B","A",money("0.01"),"");}return true;});
                gate.countDown();for(int i=0;i<100;i++){var snapshot=s.snapshot();equal(money("10.00"),snapshot.accounts().stream().map(BankAccount::getBalance).reduce(money("0.00"),BigDecimal::add));equal(0,snapshot.transactions().size()%2);}
                writer.get(10,TimeUnit.SECONDS);equal(400,s.getAllTransactions().size());
            } finally {executor.shutdownNow();}
        });
        test("rename preserves identity balance and history",()->{var s=service();s.deposit("A",money("1"),"");var renamed=s.renameAccount("A","  New Name  ");equal("A",renamed.getId());equal("New Name",renamed.getAccountHolderName());equal(money("11.00"),renamed.getBalance());equal(1,s.getTransactionsForAccount("A").size());rejects(IllegalArgumentException.class,()->s.renameAccount("A"," "));equal("New Name",s.getAccountById("A").getAccountHolderName());});
        test("only unused zero balance accounts can be deleted",()->{var s=service();equal(true,s.deleteUnusedAccount("B"));equal(false,s.deleteUnusedAccount("B"));rejects(IllegalStateException.class,()->s.deleteUnusedAccount("A"));s.withdraw("A",money("10"),"");rejects(IllegalStateException.class,()->s.deleteUnusedAccount("A"));equal(true,s.getAccountById("A")!=null);});
        test("empty directory loads empty snapshot",()->withDirectory(dir->equal(0,new FileStorage(dir).loadSnapshot().accounts().size())));
        test("snapshot round trip preserves escaped fields and paired transfer",()->withDirectory(dir->{var s=service();s.renameAccount("A","Learner | path\\name");s.transfer("A","B",money("2"),"line\nnext\rpipe|slash\\");var storage=new FileStorage(dir);storage.saveSnapshot(s.snapshot());var loaded=storage.loadSnapshot();var copy=new BankService(loaded.accounts(),loaded.transactions());equal("Learner | path\\name",copy.getAccountById("A").getAccountHolderName());equal("line\nnext\rpipe|slash\\",copy.getAllTransactions().get(0).getDescription());equal(money("8.00"),copy.getAccountById("A").getBalance());equal(2,copy.getAllTransactions().size());}));
        test("legacy migration retains original files and makes snapshot authoritative",()->withDirectory(dir->{
            Files.writeString(dir.resolve("accounts.txt"),"A|Learner|1.00|2026-01-02T03:04\n");Files.writeString(dir.resolve("transactions.txt"),"T|A|DEPOSIT|1.00|2026-01-02T03:04||\n");String original=Files.readString(dir.resolve("accounts.txt"));
            var storage=new FileStorage(dir);var snapshot=storage.loadSnapshot();equal(1,snapshot.accounts().size());equal(1,snapshot.transactions().size());storage.saveSnapshot(snapshot);equal(original,Files.readString(dir.resolve("accounts.txt")));
            Files.writeString(dir.resolve("accounts.txt"),"broken");equal(1,storage.loadSnapshot().accounts().size());
        }));
        test("incomplete legacy pair fails explicitly",()->withDirectory(dir->{Files.writeString(dir.resolve("accounts.txt"),"");rejects(IOException.class,new FileStorage(dir)::loadSnapshot);}));
        test("corrupt snapshot never falls back to valid legacy files",()->withDirectory(dir->{Files.writeString(dir.resolve("accounts.txt"),"");Files.writeString(dir.resolve("transactions.txt"),"");Files.writeString(dir.resolve("bank.snapshot.txt"),"broken\n");rejects(IOException.class,new FileStorage(dir)::loadSnapshot);}));
        test("truncated snapshot and wrong counts are rejected",()->withDirectory(dir->{var storage=new FileStorage(dir);storage.saveSnapshot(service().snapshot());Path file=dir.resolve("bank.snapshot.txt");var lines=Files.readAllLines(file);Files.write(file,lines.subList(0,lines.size()-1));rejects(IOException.class,storage::loadSnapshot);lines.set(lines.size()-1,"END|1|0");Files.write(file,lines);rejects(IOException.class,storage::loadSnapshot);}));
        test("invalid snapshot save preserves previous file",()->withDirectory(dir->{var storage=new FileStorage(dir);storage.saveSnapshot(service().snapshot());String before=Files.readString(dir.resolve("bank.snapshot.txt"));var invalid=new BankSnapshot(List.of(account("A","1"),account("A","2")),List.of());rejects(IOException.class,()->storage.saveSnapshot(invalid));equal(before,Files.readString(dir.resolve("bank.snapshot.txt")));}));
        test("failed replacement preserves destination and cleans temporary file",()->withDirectory(dir->{Path target=dir.resolve("bank.snapshot.txt");Files.createDirectory(target);Files.writeString(target.resolve("keep.txt"),"keep");rejects(IOException.class,()->new FileStorage(dir).saveSnapshot(service().snapshot()));equal("keep",Files.readString(target.resolve("keep.txt")));try(var files=Files.list(dir)){equal(1L,files.count());}}));
        test("malformed legacy number becomes a storage error",()->withDirectory(dir->{Files.writeString(dir.resolve("accounts.txt"),"A|Learner|invalid|2026-01-02T03:04\n");Files.writeString(dir.resolve("transactions.txt"),"");rejects(IOException.class,new FileStorage(dir)::loadSnapshot);}));
        test("repeated snapshot replacement writes latest complete state",()->withDirectory(dir->{var s=service();var storage=new FileStorage(dir);storage.saveSnapshot(s.snapshot());s.deposit("A",money("2"),"");storage.saveSnapshot(s.snapshot());var loaded=storage.loadSnapshot();equal(money("12.00"),loaded.accounts().get(0).getBalance());equal(1,loaded.transactions().size());try(var files=Files.list(dir)){equal(1L,files.count());}}));
        System.out.println("RESULT passed="+passed+" failed="+failed);
        if(failed!=0) System.exit(1);
    }
}

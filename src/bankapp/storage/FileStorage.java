package bankapp.storage;

import bankapp.model.*;
import bankapp.service.BankService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One versioned atomic snapshot; the old two-file format is read only for migration. */
public class FileStorage {
    private static final String HEADER = "# bank-snapshot-v1";
    private final Path directory;
    private final Path snapshotFile;
    public FileStorage(Path dataDirectory) {
        directory=dataDirectory.toAbsolutePath().normalize();
        snapshotFile=directory.resolve("bank.snapshot.txt");
    }

    public BankSnapshot loadSnapshot() throws IOException {
        var accounts=new ArrayList<BankAccount>();
        var transactions=new ArrayList<Transaction>();
        if(!Files.notExists(snapshotFile)) {
            List<String> lines=Files.readAllLines(snapshotFile);
            if(lines.size()<2 || !HEADER.equals(lines.get(0))) throw new IOException("Unsupported or missing bank snapshot header.");
            for(int i=1;i<lines.size()-1;i++) {
                String line=lines.get(i);
                if(line.isBlank()) continue;
                try {
                    if(line.startsWith("A|")) accounts.add(parseAccount(line.substring(2)));
                    else if(line.startsWith("T|")) transactions.add(parseTransaction(line.substring(2)));
                    else throw new IllegalArgumentException("Unknown record type.");
                } catch(RuntimeException error) { throw new IOException("Invalid bank snapshot record on line "+(i+1)+".",error); }
            }
            try {
                String[] footer=lines.get(lines.size()-1).split("\\|",-1);
                if(footer.length!=3 || !footer[0].equals("END") || Integer.parseInt(footer[1])!=accounts.size() || Integer.parseInt(footer[2])!=transactions.size())
                    throw new IllegalArgumentException("Record counts do not match.");
            } catch(RuntimeException error) { throw new IOException("Missing or invalid bank snapshot footer.",error); }
        } else {
            Path accountsFile=directory.resolve("accounts.txt");
            Path transactionsFile=directory.resolve("transactions.txt");
            boolean noAccounts=Files.notExists(accountsFile), noTransactions=Files.notExists(transactionsFile);
            if(noAccounts != noTransactions) throw new IOException("Incomplete legacy data: both accounts.txt and transactions.txt are required.");
            if(!noAccounts) {
                readLegacy(accountsFile,accounts,transactions,true);
                readLegacy(transactionsFile,accounts,transactions,false);
            }
        }
        return validate(accounts,transactions);
    }

    private void readLegacy(Path file,List<BankAccount> accounts,List<Transaction> transactions,boolean accountFile) throws IOException {
        List<String> lines=Files.readAllLines(file);
        for(int i=0;i<lines.size();i++) {
            if(lines.get(i).isBlank()) continue;
            try {
                if(accountFile) accounts.add(parseAccount(lines.get(i)));
                else transactions.add(parseTransaction(lines.get(i)));
            } catch(RuntimeException error) { throw new IOException("Invalid legacy "+file.getFileName()+" record on line "+(i+1)+".",error); }
        }
    }

    private BankSnapshot validate(List<BankAccount> accounts,List<Transaction> transactions) throws IOException {
        try { return new BankService(accounts,transactions).snapshot(); }
        catch(RuntimeException error) { throw new IOException("Invalid bank snapshot: "+error.getMessage(),error); }
    }

    public void saveSnapshot(BankSnapshot snapshot) throws IOException {
        BankSnapshot validated=validate(snapshot.accounts(),snapshot.transactions());
        var lines=new ArrayList<String>();lines.add(HEADER);
        for(BankAccount account:validated.accounts()) lines.add("A|"+join(account.getId(),account.getAccountHolderName(),account.getBalance().toPlainString(),account.getCreatedAt().toString()));
        for(Transaction transaction:validated.transactions()) lines.add("T|"+join(transaction.getId(),transaction.getAccountId(),transaction.getType().name(),transaction.getAmount().toPlainString(),transaction.getTimestamp().toString(),transaction.getDescription(),transaction.getRelatedAccountId()));
        lines.add("END|"+validated.accounts().size()+"|"+validated.transactions().size());
        Files.createDirectories(directory);
        Path temporary=Files.createTempFile(directory,".bank-",".tmp");
        try {
            Files.write(temporary,lines);
            Files.move(temporary,snapshotFile,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private BankAccount parseAccount(String line) {
        List<String> fields=split(line,4);
        return new BankAccount(fields.get(0),fields.get(1),new BigDecimal(fields.get(2)),LocalDateTime.parse(fields.get(3)));
    }
    private Transaction parseTransaction(String line) {
        List<String> fields=split(line,7);
        return new Transaction(fields.get(0),fields.get(1),TransactionType.valueOf(fields.get(2)),new BigDecimal(fields.get(3)),LocalDateTime.parse(fields.get(4)),nullable(fields.get(5)),nullable(fields.get(6)));
    }
    private String nullable(String text) { return text.isEmpty()?null:text; }
    private String join(String... fields) {
        var escaped=new ArrayList<String>();
        for(String field:fields) escaped.add(escape(field==null?"":field));
        return String.join("|",escaped);
    }
    private String escape(String value) { return value.replace("\\","\\\\").replace("|","\\|").replace("\n","\\n").replace("\r","\\r"); }
    private List<String> split(String line,int expected) {
        var fields=new ArrayList<String>();var current=new StringBuilder();boolean escaping=false;
        for(int i=0;i<line.length();i++) {
            char character=line.charAt(i);
            if(escaping) {
                current.append(switch(character) {
                    case 'n' -> '\n';case 'r' -> '\r';case '|' -> '|';case '\\' -> '\\';
                    default -> throw new IllegalArgumentException("Unknown escape sequence.");
                });escaping=false;
            } else if(character=='\\') escaping=true;
            else if(character=='|') { fields.add(current.toString());current.setLength(0); }
            else current.append(character);
        }
        if(escaping) throw new IllegalArgumentException("Incomplete escape sequence.");
        fields.add(current.toString());
        if(fields.size()!=expected) throw new IllegalArgumentException("Unexpected field count.");
        return fields;
    }
}

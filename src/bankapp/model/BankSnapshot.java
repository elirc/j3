package bankapp.model;
import java.util.List;
/** Detached accounts and immutable transactions captured from one service state. */
public record BankSnapshot(List<BankAccount> accounts, List<Transaction> transactions) {
    public BankSnapshot {
        accounts=accounts.stream().map(BankAccount::copy).toList();
        transactions=List.copyOf(transactions);
    }
    @Override public List<BankAccount> accounts() {
        return accounts.stream().map(BankAccount::copy).toList();
    }
}

package bankapp.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class BankAccount {
    private final String id;
    private final String accountHolderName;
    private BigDecimal balance;
    private final LocalDateTime createdAt;

    public BankAccount(String id, String accountHolderName, BigDecimal balance, LocalDateTime createdAt) {
        this.id = Money.text(id, "Account id");
        this.accountHolderName = Money.text(accountHolderName, "Account holder name");
        this.balance = Money.nonNegative(balance);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public BankAccount copy() {
        return new BankAccount(id, accountHolderName, balance, createdAt);
    }

    public String getId() {
        return id;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void increaseBalance(BigDecimal amount) {
        balance = balance.add(Money.positive(amount));
    }

    public void decreaseBalance(BigDecimal amount) {
        BigDecimal cleanAmount = Money.positive(amount);
        if(balance.compareTo(cleanAmount)<0) throw new IllegalStateException("Insufficient balance.");
        balance = balance.subtract(cleanAmount);
    }
}

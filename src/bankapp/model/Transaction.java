package bankapp.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class Transaction {
    private final String id;
    private final String accountId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final LocalDateTime timestamp;
    private final String description;
    private final String relatedAccountId;

    public Transaction(
            String id,
            String accountId,
            TransactionType type,
            BigDecimal amount,
            LocalDateTime timestamp,
            String description,
            String relatedAccountId
    ) {
        this.id = Money.text(id, "Transaction id");
        this.accountId = Money.text(accountId, "Account id");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.amount = Money.positive(amount);
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.description = description;
        boolean transfer = type == TransactionType.TRANSFER_IN || type == TransactionType.TRANSFER_OUT;
        if(transfer) {
            this.relatedAccountId = Money.text(relatedAccountId, "Related account id");
            if(this.accountId.equals(this.relatedAccountId)) throw new IllegalArgumentException("Transfer accounts must differ.");
        } else {
            if(relatedAccountId != null) throw new IllegalArgumentException("Only transfers have related accounts.");
            this.relatedAccountId = null;
        }
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    public String getRelatedAccountId() {
        return relatedAccountId;
    }
}

package bankapp.service;

import bankapp.model.BankAccount;
import bankapp.model.Transaction;
import bankapp.model.TransactionType;
import bankapp.model.Money;
import bankapp.model.BankSnapshot;
import java.util.HashSet;
import java.util.Objects;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BankService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final Map<String, BankAccount> accountsById = new LinkedHashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();

    public BankService(List<BankAccount> existingAccounts, List<Transaction> existingTransactions) {
        for (BankAccount account : existingAccounts) {
            Objects.requireNonNull(account, "Account cannot be null.");
            if(accountsById.putIfAbsent(account.getId(), account.copy()) != null)
                throw new IllegalArgumentException("Duplicate account id: " + account.getId());
        }
        var transactionIds = new HashSet<String>();
        for(Transaction transaction : existingTransactions) {
            Objects.requireNonNull(transaction, "Transaction cannot be null.");
            if(!transactionIds.add(transaction.getId())) throw new IllegalArgumentException("Duplicate transaction id: " + transaction.getId());
            if(!accountsById.containsKey(transaction.getAccountId())) throw new IllegalArgumentException("Transaction references a missing account.");
            if(transaction.getRelatedAccountId()!=null && !accountsById.containsKey(transaction.getRelatedAccountId()))
                throw new IllegalArgumentException("Transfer references a missing related account.");
            transactions.add(transaction);
        }
    }

    public synchronized BankAccount createAccount(String accountHolderName) {
        String cleanName = accountHolderName == null ? "" : accountHolderName.trim();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("Account holder name cannot be empty.");
        }

        BankAccount account = new BankAccount(
                generateId("ACC"),
                cleanName,
                ZERO,
                LocalDateTime.now()
        );
        accountsById.put(account.getId(), account);
        return account.copy();
    }

    public synchronized List<BankAccount> getAllAccounts() {
        List<BankAccount> accounts = new ArrayList<>(accountsById.values().stream().map(BankAccount::copy).toList());
        accounts.sort(Comparator.comparing(BankAccount::getCreatedAt).thenComparing(BankAccount::getId));
        return accounts;
    }

    public synchronized BankAccount getAccountById(String accountId) {
        BankAccount account = accountsById.get(accountId);
        return account == null ? null : account.copy();
    }

    public synchronized Transaction deposit(String accountId, BigDecimal amount, String description) {
        BankAccount account = requireAccount(accountId);
        BigDecimal cleanAmount = validateAmount(amount, "Deposit");

        Transaction transaction = createTransaction(
                account.getId(),
                TransactionType.DEPOSIT,
                cleanAmount,
                description,
                null
        );
        account.increaseBalance(cleanAmount);
        transactions.add(transaction);
        return transaction;
    }

    public synchronized Transaction withdraw(String accountId, BigDecimal amount, String description) {
        BankAccount account = requireAccount(accountId);
        BigDecimal cleanAmount = validateAmount(amount, "Withdrawal");

        if (account.getBalance().compareTo(cleanAmount) < 0) {
            throw new IllegalStateException("Insufficient balance for this withdrawal.");
        }

        Transaction transaction = createTransaction(
                account.getId(),
                TransactionType.WITHDRAWAL,
                cleanAmount,
                description,
                null
        );
        account.decreaseBalance(cleanAmount);
        transactions.add(transaction);
        return transaction;
    }

    public synchronized List<Transaction> transfer(String fromAccountId, String toAccountId, BigDecimal amount, String description) {
        if (fromAccountId == null || toAccountId == null) {
            throw new IllegalArgumentException("Source and destination account ids are required.");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("You cannot transfer money to the same account.");
        }

        BankAccount sourceAccount = requireAccount(fromAccountId);
        BankAccount destinationAccount = requireAccount(toAccountId);
        BigDecimal cleanAmount = validateAmount(amount, "Transfer");

        if (sourceAccount.getBalance().compareTo(cleanAmount) < 0) {
            throw new IllegalStateException("Insufficient balance for this transfer.");
        }

        Transaction transferOut = createTransaction(
                sourceAccount.getId(),
                TransactionType.TRANSFER_OUT,
                cleanAmount,
                description,
                destinationAccount.getId()
        );
        Transaction transferIn = createTransaction(
                destinationAccount.getId(),
                TransactionType.TRANSFER_IN,
                cleanAmount,
                description,
                sourceAccount.getId()
        );

        sourceAccount.decreaseBalance(cleanAmount);
        destinationAccount.increaseBalance(cleanAmount);
        transactions.add(transferOut);
        transactions.add(transferIn);

        List<Transaction> result = new ArrayList<>();
        result.add(transferOut);
        result.add(transferIn);
        return result;
    }

    public synchronized List<Transaction> getTransactionsForAccount(String accountId) {
        requireAccount(accountId);

        List<Transaction> accountTransactions = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (transaction.getAccountId().equals(accountId)) {
                accountTransactions.add(transaction);
            }
        }

        accountTransactions.sort(Comparator
                .comparing(Transaction::getTimestamp)
                .thenComparing(Transaction::getId));
        return accountTransactions;
    }

    public synchronized List<Transaction> getAllTransactions() {
        List<Transaction> copy = new ArrayList<>(transactions);
        copy.sort(Comparator
                .comparing(Transaction::getTimestamp)
                .thenComparing(Transaction::getId));
        return copy;
    }

    public synchronized Collection<BankAccount> getAccountsForSaving() {
        return new ArrayList<>(accountsById.values().stream().map(BankAccount::copy).toList());
    }

    public synchronized BankSnapshot snapshot() {
        return new BankSnapshot(getAllAccounts(), getAllTransactions());
    }

    public synchronized BankAccount renameAccount(String accountId, String holderName) {
        BankAccount current = requireAccount(accountId);
        BankAccount replacement = new BankAccount(current.getId(), holderName, current.getBalance(), current.getCreatedAt());
        accountsById.put(accountId, replacement);
        return replacement.copy();
    }

    public synchronized boolean deleteUnusedAccount(String accountId) {
        BankAccount account = accountsById.get(accountId);
        if(account==null) return false;
        if(account.getBalance().signum()!=0 || transactions.stream().anyMatch(t -> accountId.equals(t.getAccountId()) || accountId.equals(t.getRelatedAccountId())))
            throw new IllegalStateException("Only a zero-balance account with no transaction history can be deleted.");
        accountsById.remove(accountId);
        return true;
    }

    private BankAccount requireAccount(String accountId) {
        BankAccount account = accountsById.get(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account;
    }

    private BigDecimal validateAmount(BigDecimal amount, String actionName) {
        try { return Money.positive(amount); }
        catch(IllegalArgumentException error) { throw new IllegalArgumentException(actionName + ": " + error.getMessage(), error); }
    }

    private Transaction createTransaction(
            String accountId,
            TransactionType type,
            BigDecimal amount,
            String description,
            String relatedAccountId
    ) {
        String cleanDescription = description == null ? null : description.trim();
        if (cleanDescription != null && cleanDescription.isEmpty()) {
            cleanDescription = null;
        }

        return new Transaction(
                generateId("TXN"),
                accountId,
                type,
                amount,
                LocalDateTime.now(),
                cleanDescription,
                relatedAccountId
        );
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}

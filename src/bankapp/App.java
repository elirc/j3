package bankapp;

import bankapp.model.BankAccount;
import bankapp.model.Transaction;
import bankapp.service.BankService;
import bankapp.storage.FileStorage;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.NoSuchElementException;
import bankapp.model.BankSnapshot;

public class App {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Scanner scanner = new Scanner(System.in);
    private final FileStorage fileStorage;
    private final BankService bankService;

    public App() throws IOException {
        this(Path.of("data"));
    }

    public App(Path dataDirectory) throws IOException {
        this.fileStorage = new FileStorage(dataDirectory);
        BankSnapshot snapshot = fileStorage.loadSnapshot();
        this.bankService = new BankService(snapshot.accounts(), snapshot.transactions());
    }

    public static void main(String[] args) {
        try {
            new App(args.length == 0 ? Path.of("data") : Path.of(args[0])).run();
        } catch (IOException exception) {
            System.out.println("Failed to load or save bank data: " + exception.getMessage());
        }
    }

    public void run() throws IOException {
        boolean running = true;

        System.out.println("Welcome to the Bank Account Simulator");
        System.out.println("Accounts loaded: " + bankService.getAllAccounts().size());

        try {
            while (running) {
                printMenu();
                String choice = scanner.nextLine().trim();

                try {
                    switch (choice) {
                        case "1" -> createAccount();
                        case "2" -> listAccounts();
                        case "3" -> deposit();
                        case "4" -> withdraw();
                        case "5" -> transfer();
                        case "6" -> viewTransactions();
                        case "7" -> {
                            saveData();
                            running = false;
                            System.out.println("Data saved. Goodbye.");
                        }
                        case "8" -> renameAccount();
                        case "9" -> deleteUnusedAccount();
                        default -> System.out.println("Please choose a valid menu option.");
                    }
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    System.out.println("Operation failed: " + exception.getMessage());
                }
            }
        } catch (NoSuchElementException exception) {
            saveData();
            System.out.println("Input ended. Completed operations are saved.");
        } finally {
            scanner.close();
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("1 Create account");
        System.out.println("2 List accounts");
        System.out.println("3 Deposit");
        System.out.println("4 Withdraw");
        System.out.println("5 Transfer");
        System.out.println("6 View account transactions");
        System.out.println("7 Exit");
        System.out.println("8 Rename account holder");
        System.out.println("9 Delete unused account");
        System.out.print("Choose an option: ");
    }

    private void createAccount() throws IOException {
        System.out.print("Account holder name: ");
        String accountHolderName = scanner.nextLine();

        BankAccount account = bankService.createAccount(accountHolderName);
        saveData();

        System.out.println("Account created successfully.");
        System.out.println("Account ID: " + account.getId());
    }

    private void listAccounts() {
        List<BankAccount> accounts = bankService.getAllAccounts();

        if (accounts.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }

        System.out.println();
        for (BankAccount account : accounts) {
            System.out.println("ID: " + account.getId());
            System.out.println("Holder: " + account.getAccountHolderName());
            System.out.println("Balance: $" + account.getBalance().toPlainString());
            System.out.println("Created: " + account.getCreatedAt().format(DATE_TIME_FORMATTER));
            System.out.println();
        }
    }

    private void deposit() throws IOException {
        System.out.print("Account ID: ");
        String accountId = scanner.nextLine().trim();
        BigDecimal amount = readAmount("Deposit amount: ");

        System.out.print("Description (optional): ");
        String description = scanner.nextLine();

        bankService.deposit(accountId, amount, description);
        saveData();

        System.out.println("Deposit completed successfully.");
    }

    private void withdraw() throws IOException {
        System.out.print("Account ID: ");
        String accountId = scanner.nextLine().trim();
        BigDecimal amount = readAmount("Withdrawal amount: ");

        System.out.print("Description (optional): ");
        String description = scanner.nextLine();

        bankService.withdraw(accountId, amount, description);
        saveData();

        System.out.println("Withdrawal completed successfully.");
    }

    private void transfer() throws IOException {
        System.out.print("Source account ID: ");
        String fromAccountId = scanner.nextLine().trim();

        System.out.print("Destination account ID: ");
        String toAccountId = scanner.nextLine().trim();

        BigDecimal amount = readAmount("Transfer amount: ");

        System.out.print("Description (optional): ");
        String description = scanner.nextLine();

        bankService.transfer(fromAccountId, toAccountId, amount, description);
        saveData();

        System.out.println("Transfer completed successfully.");
    }

    private void viewTransactions() {
        System.out.print("Account ID: ");
        String accountId = scanner.nextLine().trim();

        List<Transaction> transactions = bankService.getTransactionsForAccount(accountId);
        if (transactions.isEmpty()) {
            System.out.println("No transactions found for this account.");
            return;
        }

        System.out.println();
        for (Transaction transaction : transactions) {
            System.out.println("Transaction ID: " + transaction.getId());
            System.out.println("Type: " + transaction.getType());
            System.out.println("Amount: $" + transaction.getAmount().toPlainString());
            System.out.println("Time: " + transaction.getTimestamp().format(DATE_TIME_FORMATTER));
            if (transaction.getRelatedAccountId() != null) {
                System.out.println("Related Account: " + transaction.getRelatedAccountId());
            }
            if (transaction.getDescription() != null) {
                System.out.println("Description: " + transaction.getDescription());
            }
            System.out.println();
        }
    }

    private void renameAccount() throws IOException {
        System.out.print("Account ID: ");
        String accountId = scanner.nextLine().trim();
        System.out.print("New holder name: ");
        String holderName = scanner.nextLine();
        bankService.renameAccount(accountId, holderName);
        saveData();
        System.out.println("Account holder updated.");
    }

    private void deleteUnusedAccount() throws IOException {
        System.out.print("Account ID: ");
        String accountId = scanner.nextLine().trim();
        if(bankService.getAccountById(accountId)==null) {
            System.out.println("Account not found.");
            return;
        }
        System.out.print("Delete unused account? Type yes to confirm: ");
        if(!scanner.nextLine().trim().equalsIgnoreCase("yes")) {
            System.out.println("Deletion cancelled.");
            return;
        }
        bankService.deleteUnusedAccount(accountId);
        saveData();
        System.out.println("Unused account deleted.");
    }

    private BigDecimal readAmount(String prompt) {
        System.out.print(prompt);
        String amountText = scanner.nextLine().trim();

        try {
            return new BigDecimal(amountText);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Please enter a valid number for the amount.");
        }
    }

    private void saveData() throws IOException {
        fileStorage.saveSnapshot(bankService.snapshot());
    }
}

package bankapp.model;
import java.math.BigDecimal;
import java.math.RoundingMode;
public final class Money {
    private Money() { }
    public static BigDecimal nonNegative(BigDecimal amount) {
        if(amount==null) throw new IllegalArgumentException("Amount is required.");
        BigDecimal normalized;
        try { normalized=amount.setScale(2,RoundingMode.UNNECESSARY); }
        catch(ArithmeticException error) { throw new IllegalArgumentException("Amount must use at most 2 decimal places.",error); }
        if(normalized.signum()<0) throw new IllegalArgumentException("Amount cannot be negative.");
        return normalized;
    }
    public static BigDecimal positive(BigDecimal amount) {
        BigDecimal normalized=nonNegative(amount);
        if(normalized.signum()==0) throw new IllegalArgumentException("Amount must be greater than zero.");
        return normalized;
    }
    public static String text(String value,String field) {
        if(value==null || value.trim().isEmpty()) throw new IllegalArgumentException(field+" is required.");
        return value.trim();
    }
}

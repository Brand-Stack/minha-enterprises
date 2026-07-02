package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Ledger - Records individual payment transactions.
 * 
 * BEST PRACTICE: In production billing systems, payments should be stored
 * separately from invoices for:
 * 1. Audit trail - Track each payment individually
 * 2. Mixed payments - Handle cash + online accurately
 * 3. Refunds - Track refund transactions
 * 4. Reconciliation - Match payments to bank statements
 */
@Document(collection = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    
    @Id
    private String id;
    
    // Reference to parent invoice
    private String invoiceId;
    private String invoiceNumber;
    
    // Payment details
    private BigDecimal amount;
    private PaymentMode paymentMode;  // CASH, ONLINE, CHEQUE
    private OnlinePaymentMethod onlinePaymentMethod;  // GPay, PhonePe, etc.
    private String paymentReference;  // UPI ID, Cheque No, etc.
    
    // Timestamp
    private LocalDateTime paymentDateTime;
    private LocalDateTime createdAt;
    
    // Status for refunds/reversals
    private PaymentStatus status;
    
    // Audit fields
    private String createdBy;
    private String notes;
    
    public enum PaymentMode {
        CASH, ONLINE, CHEQUE
    }
    
    public enum OnlinePaymentMethod {
        GPAY, PHONEPE, PAYTM, OTHER_UPI, BANK_TRANSFER, OTHERS
    }
    
    public enum PaymentStatus {
        COMPLETED, REFUNDED, CANCELLED
    }
}

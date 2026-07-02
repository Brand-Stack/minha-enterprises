package com.app.billing.util;

import com.app.billing.dao.InvoiceRepository;
import com.app.billing.model.Invoice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {
    
    private final InvoiceRepository invoiceRepository;
    private final CodeGenerator codeGenerator;
    
    /**
     * Generate next invoice number for a specific bill type series
     * GST bills use "INV" prefix (configurable, default: "INV")
     * Estimate bills use "EST" prefix
     * Preserves zero-padding based on existing numbers in the series
     */
    public String generateNextInvoiceNumber(Invoice.BillType billType) {
        String prefix = (billType == Invoice.BillType.GST) ? "INV" : "EST";
        
        // Get all invoices of the same bill type
        List<Invoice> invoices = invoiceRepository.findAll()
                .stream()
                .filter(inv -> inv.getBillType() == billType && inv.getInvoiceNumber() != null)
                .toList();
        
        // Find the maximum numeric suffix and detect padding length
        int maxSequence = 0;
        int maxPaddingLength = 3; // Default to 3 digits
        Pattern pattern = Pattern.compile("^" + prefix + "[-_]?(\\d+)$", Pattern.CASE_INSENSITIVE);
        
        for (Invoice invoice : invoices) {
            String invNum = invoice.getInvoiceNumber();
            if (invNum != null) {
                Matcher matcher = pattern.matcher(invNum);
                if (matcher.matches()) {
                    try {
                        String numericPart = matcher.group(1);
                        int seq = Integer.parseInt(numericPart);
                        maxSequence = Math.max(maxSequence, seq);
                        // Detect padding length: count leading zeros + digits
                        // If numericPart is "00100", padding length is 5
                        maxPaddingLength = Math.max(maxPaddingLength, numericPart.length());
                    } catch (NumberFormatException e) {
                        // Ignore invalid numbers
                    }
                }
            }
        }
        
        // Generate next number: prefix + separator + zero-padded number
        // Preserve the maximum padding length found in the series
        int nextSequence = maxSequence + 1;
        return prefix + "-" + String.format("%0" + maxPaddingLength + "d", nextSequence);
    }
    
    /**
     * Parse and extract numeric suffix from a bill number
     * Returns the numeric value, or -1 if parsing fails
     */
    public int extractNumericSuffix(String billNumber, String prefix) {
        if (billNumber == null || prefix == null) {
            return -1;
        }
        
        Pattern pattern = Pattern.compile("^" + prefix + "[-_]?(\\d+)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(billNumber);
        
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        
        return -1;
    }
}


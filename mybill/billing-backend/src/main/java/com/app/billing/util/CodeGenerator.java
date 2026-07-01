package com.app.billing.util;

import com.app.billing.model.Client;
import com.app.billing.model.SmallClient;
import org.springframework.stereotype.Component;

@Component
public class CodeGenerator {
    
    public String generateInvoiceNumber(String lastInvoiceNumber) {
        // Format: INV-00001, INV-00002, etc.
        // This method is kept for backward compatibility
        // New code should use InvoiceNumberGenerator.generateNextInvoiceNumber(BillType)
        if (lastInvoiceNumber == null || lastInvoiceNumber.isEmpty() || !lastInvoiceNumber.startsWith("INV-")) {
            return "INV-001";
        }
        
        // Extract sequence number from "INV-00001" or "INV-001"
        try {
            String sequenceStr = lastInvoiceNumber.substring(4); // Remove "INV-"
            int nextSequence = Integer.parseInt(sequenceStr) + 1;
            // Use 3-digit padding for consistency with new format
            return "INV-" + String.format("%03d", nextSequence);
        } catch (Exception e) {
            // If parsing fails, start from 1
            return "INV-001";
        }
    }
    
    /**
     * Generate client code (CU/SU/BT prefix) based on client type.
     */
    public String generateClientCode(Client.ClientType partyType, String lastPartyCode) {
        String prefix = (partyType == Client.ClientType.CUSTOMER) ? "CU" :
                        (partyType == Client.ClientType.SUPPLIER) ? "SU" : "BT";
        
        if (lastPartyCode == null || !lastPartyCode.startsWith(prefix)) {
            // If last code doesn't start with expected prefix, start from 001
            return prefix + "001";
        }
        
        String sequence = lastPartyCode.substring(2); // Extract numeric part after prefix
        try {
            int nextSequence = Integer.parseInt(sequence) + 1;
            return prefix + String.format("%03d", nextSequence);
        } catch (Exception e) {
            return prefix + "001";
        }
    }
    
    @Deprecated
    public String generatePartyCode(String lastPartyCode) {
        return generateClientCode(Client.ClientType.SUPPLIER, lastPartyCode);
    }
    
    public String generateItemCode(String lastItemCode) {
        if (lastItemCode == null || lastItemCode.isEmpty()) {
            return "ITM001";
        }
        
        // Only process codes that match the expected format "ITM###"
        if (!lastItemCode.startsWith("ITM") || lastItemCode.length() < 6) {
            // If last code doesn't match format, start from 1
            return "ITM001";
        }
        
        try {
            String sequence = lastItemCode.substring(3); // Extract "001", "002", etc.
            // Validate that sequence contains only digits
            if (!sequence.matches("\\d+")) {
                return "ITM001";
            }
            int nextSequence = Integer.parseInt(sequence) + 1;
            return "ITM" + String.format("%03d", nextSequence);
        } catch (Exception e) {
            // If parsing fails for any reason, start from 1
            return "ITM001";
        }
    }
    
    /** Small Client master codes: SC001/SS001/SB001, … */
    public String generateSmallClientCode(SmallClient.SmallClientType partyType, String lastPartyCode) {
        String prefix = (partyType == SmallClient.SmallClientType.CUSTOMER) ? "SC"
                : (partyType == SmallClient.SmallClientType.SUPPLIER) ? "SS" : "SB";
        if (lastPartyCode == null || !lastPartyCode.startsWith(prefix)) {
            return prefix + "001";
        }
        String sequence = lastPartyCode.substring(prefix.length());
        try {
            if (!sequence.matches("\\d+")) {
                return prefix + "001";
            }
            int nextSequence = Integer.parseInt(sequence) + 1;
            return prefix + String.format("%03d", nextSequence);
        } catch (Exception e) {
            return prefix + "001";
        }
    }

    /** Collection Customer master codes: COL001, COL002, … */
    public String generateCollectionCustomerCode(String lastPartyCode) {
        String prefix = "COL";
        if (lastPartyCode == null || !lastPartyCode.startsWith(prefix)) {
            return prefix + "001";
        }
        String sequence = lastPartyCode.substring(prefix.length());
        try {
            int next = Integer.parseInt(sequence) + 1;
            return prefix + String.format("%03d", next);
        } catch (Exception e) {
            return prefix + "001";
        }
    }

    public String generateEmployeeCode(String lastEmployeeCode) {
        if (lastEmployeeCode == null) {
            return "EMP001";
        }
        
        String sequence = lastEmployeeCode.substring(3);
        int nextSequence = Integer.parseInt(sequence) + 1;
        return "EMP" + String.format("%03d", nextSequence);
    }
    
}


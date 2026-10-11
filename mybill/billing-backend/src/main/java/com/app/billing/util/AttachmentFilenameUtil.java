package com.app.billing.util;

import com.app.billing.dto.CourierQuotationDto;
import com.app.billing.dto.MonthlyCourierQuotationDto;
import com.app.billing.dto.SmallClientEntryQuotationDto;
import org.springframework.http.ContentDisposition;
import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Sanitized attachment names for downloads (Windows-safe, UTF-8 Content-Disposition).
 */
public final class AttachmentFilenameUtil {

    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");

    private AttachmentFilenameUtil() {
    }

    /** Removes characters unsafe in file names; normalizes internal whitespace. */
    public static String sanitizePreserveSpaces(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "download";
        }
        String cleaned = ILLEGAL.matcher(name.trim()).replaceAll("");
        cleaned = cleaned.replaceAll("\\s+", " ");
        if (cleaned.isBlank()) {
            return "download";
        }
        return cleaned;
    }

    public static String cleanPartyName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "Download";
        }
        return name.replaceAll("[^a-zA-Z0-9]", "");
    }

    public static String monthlyInvoiceBase(MonthlyCourierQuotationDto dto) {
        String cleanParty = cleanPartyName(dto.getCustomerName());
        String month = dto.getMonth() != null ? dto.getMonth().trim().toUpperCase() : "ALL";
        String year = dto.getYear() != null ? String.valueOf(dto.getYear()) : "ALL";
        return cleanParty + "_" + month + "_" + year;
    }

    public static String smallClientInvoiceBase(SmallClientEntryQuotationDto dto) {
        String cleanParty = cleanPartyName(dto.getCustomerName());
        String month = dto.getMonth() != null ? dto.getMonth().trim().toUpperCase() : "ALL";
        String year = dto.getYear() != null ? String.valueOf(dto.getYear()) : "ALL";
        return cleanParty + "_" + month + "_" + year;
    }

    public static String monthlyInvoicePdf(MonthlyCourierQuotationDto dto) {
        return monthlyInvoiceBase(dto) + ".pdf";
    }

    public static String monthlyInvoiceXlsx(MonthlyCourierQuotationDto dto) {
        return monthlyInvoiceBase(dto) + ".xlsx";
    }

    public static String monthlyBreakupPdf(MonthlyCourierQuotationDto dto) {
        return monthlyInvoiceBase(dto) + "_Breakup.pdf";
    }

    public static String monthlyInvoiceDocx(MonthlyCourierQuotationDto dto) {
        return monthlyInvoiceBase(dto) + ".docx";
    }

    public static String smallClientInvoicePdf(SmallClientEntryQuotationDto dto) {
        return smallClientInvoiceBase(dto) + ".pdf";
    }

    public static String smallClientInvoiceXlsx(SmallClientEntryQuotationDto dto) {
        return smallClientInvoiceBase(dto) + ".xlsx";
    }

    public static String smallClientBreakupPdf(SmallClientEntryQuotationDto dto) {
        return smallClientInvoiceBase(dto) + "_Breakup.pdf";
    }

    public static String smallClientInvoiceDocx(SmallClientEntryQuotationDto dto) {
        return smallClientInvoiceBase(dto) + ".docx";
    }

    public static String courierQuotationPdf(CourierQuotationDto dto) {
        return cleanPartyName(dto.getCustomerName()) + ".pdf";
    }

    public static String courierQuotationPdf(String qNo, String customerName) {
        return cleanPartyName(customerName) + "_" + sanitizePreserveSpaces(qNo) + ".pdf";
    }

    public static String courierQuotationXlsx(CourierQuotationDto dto) {
        return cleanPartyName(dto.getCustomerName()) + ".xlsx";
    }

    public static String courierQuotationXlsx(String qNo, String customerName) {
        return cleanPartyName(customerName) + "_" + sanitizePreserveSpaces(qNo) + ".xlsx";
    }

    public static ContentDisposition attachmentUtf8(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
    }

    public static ContentDisposition inlineUtf8(String filename) {
        return ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
    }
}

package com.app.billing.service;

import com.app.billing.dao.ClientRepository;
import com.app.billing.dao.ZoneConfigurationRepository;
import com.app.billing.dto.CourierQuotationDto;
import com.app.billing.dto.MonthlyCourierEmailRequestDto;
import com.app.billing.dto.MonthlyCourierEntryDto;
import com.app.billing.dto.MonthlyCourierQuotationDto;
import com.app.billing.dto.SmallClientEntryDto;
import com.app.billing.dto.SmallClientEntryQuotationDto;
import com.app.billing.model.SmallClient;
import com.app.billing.dao.SmallClientRepository;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.app.billing.model.ZoneConfiguration;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierEmailService {

    private final CourierQuotationService courierQuotationService;
    private final CourierQuotationPdfService courierQuotationPdfService;
    private final MonthlyCourierQuotationService monthlyCourierQuotationService;
    private final MonthlyCourierQuotationPdfService monthlyCourierQuotationPdfService;
    private final MonthlyCourierEntryService monthlyCourierEntryService;
    private final SmallClientEntryQuotationService smallClientEntryQuotationService;
    private final SmallClientEntryQuotationPdfService smallClientEntryQuotationPdfService;
    private final SmallClientEntryService smallClientEntryService;
    private final MonthlyShipmentBreakupExcelService monthlyShipmentBreakupExcelService;
    private final ClientRepository clientRepository;
    private final SmallClientRepository smallClientRepository;
    private final ZoneConfigurationRepository zoneConfigurationRepository;
    private final EmailDispatchService emailDispatchService;
    private final CompanySettingsService companySettingsService;

    private static final DateTimeFormatter SUBJECT_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public void sendCourierRateQuotationEmail(String quotationId) {
        CourierQuotationDto dto = courierQuotationService.findById(quotationId);
        Client customer = getCustomer(dto.getCustomerId());
        String to = requireCustomerEmail(customer);

        byte[] pdf = courierQuotationPdfService.generate(dto);
        String filename = "courier-quotation-" + (dto.getQuotationNumber() != null ? dto.getQuotationNumber() : quotationId) + ".pdf";

        List<EmailDispatchService.EmailAttachment> attachments = List.of(
                new EmailDispatchService.EmailAttachment(filename, "application/pdf", pdf)
        );

        List<EmailDispatchService.EmailInlineAttachment> inline = List.of(
            new EmailDispatchService.EmailInlineAttachment("franchExpressLogo", new org.springframework.core.io.ClassPathResource("images/franchExpress.png"))
        );

        emailDispatchService.sendWithAttachments(
                to,
                buildCourierQuotationSubject(),
                buildBody(true, false),
                true,
                attachments,
                inline
        );
        log.info("Courier quotation email sent to {}", to);
    }

    public void sendMonthlyCourierEmail(String quotationId, MonthlyCourierEmailRequestDto request) {
        MonthlyCourierQuotationDto dto = monthlyCourierQuotationService.findById(quotationId);

        String emailType = request != null && request.getEmailType() != null ? request.getEmailType().toUpperCase() : "MONTHLY";
        String format = request != null && request.getAttachmentFormat() != null ? request.getAttachmentFormat().toUpperCase() : "PDF";
        boolean includeAmount = request == null || request.getIncludeAmountInShipmentBreakup() == null || Boolean.TRUE.equals(request.getIncludeAmountInShipmentBreakup());
        boolean includeGstAndFuel = request == null || request.getIncludeGstAndFuel() == null || Boolean.TRUE.equals(request.getIncludeGstAndFuel());
        boolean includeGst = request != null && request.getIncludeGst() != null ? request.getIncludeGst() : includeGstAndFuel;
        boolean includeFuel = request != null && request.getIncludeFuel() != null ? request.getIncludeFuel() : includeGstAndFuel;
        boolean includeWeight = request == null || request.getIncludeWeight() == null || Boolean.TRUE.equals(request.getIncludeWeight());
        LocalDate fromDate = request != null ? request.getFromDate() : null;
        LocalDate toDate = request != null ? request.getToDate() : null;

        List<MonthlyCourierEntryDto> entries = monthlyCourierEntryService.findByQuotationId(quotationId);
        if ("DAILY".equals(emailType)) {
            if (fromDate == null) {
                throw new IllegalArgumentException("Please select From Date for Daily email.");
            }
            LocalDate rangeTo = toDate != null ? toDate : fromDate;
            if (rangeTo.isBefore(fromDate)) {
                throw new IllegalArgumentException("To Date cannot be before From Date.");
            }
            if (request == null || request.getSelectedEntryIds() == null || request.getSelectedEntryIds().isEmpty()) {
                throw new IllegalArgumentException("Please select at least one record to send email");
            }
            final LocalDate from = fromDate;
            entries = entries.stream()
                    .filter(e -> {
                        if (e.getEntryDate() == null) return false;
                        return !e.getEntryDate().isBefore(from) && !e.getEntryDate().isAfter(rangeTo);
                    })
                    .toList();
            Set<String> wanted = new HashSet<>(request.getSelectedEntryIds());
            entries = entries.stream()
                    .filter(e -> e.getId() != null && wanted.contains(e.getId()))
                    .toList();
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("None of the selected shipments fall in the chosen date range.");
            }
        } else {
            if (fromDate != null || toDate != null) {
                final LocalDate from = fromDate;
                final LocalDate rangeTo = toDate;
                entries = entries.stream()
                        .filter(e -> {
                            if (e.getEntryDate() == null) return false;
                            if (from != null && e.getEntryDate().isBefore(from)) return false;
                            if (rangeTo != null && e.getEntryDate().isAfter(rangeTo)) return false;
                            return true;
                        })
                        .toList();
            }
            if (request != null && request.getSelectedEntryIds() != null && !request.getSelectedEntryIds().isEmpty()) {
                Set<String> wantedMonthly = new HashSet<>(request.getSelectedEntryIds());
                entries = entries.stream()
                        .filter(e -> e.getId() != null && wantedMonthly.contains(e.getId()))
                        .toList();
            }
        }

        Map<String, String> zoneIdToName = zoneConfigurationRepository.findByIsDeletedFalse().stream()
                .collect(Collectors.toMap(ZoneConfiguration::getId, z -> z.getZoneName() != null ? z.getZoneName() : z.getId(), (a, b) -> a));

        List<EmailDispatchService.EmailAttachment> attachments = new ArrayList<>();

        if ("DAILY".equals(emailType)) {
            String dateSuffix = fromDate.equals(toDate != null ? toDate : fromDate)
                    ? fromDate.format(SUBJECT_DATE_FMT)
                    : fromDate.format(SUBJECT_DATE_FMT) + "_to_" + (toDate != null ? toDate.format(SUBJECT_DATE_FMT) : fromDate.format(SUBJECT_DATE_FMT));
            if ("PDF".equals(format)) {
                // PDF respects includeAmount
                byte[] breakupPdf = monthlyCourierQuotationPdfService.generateShipmentBreakupOnly(dto, entries, zoneIdToName, includeAmount, includeWeight);
                String pdfName = "shipment-breakup-" + dateSuffix + ".pdf";
                attachments.add(new EmailDispatchService.EmailAttachment(pdfName, "application/pdf", breakupPdf));
            } else {
                byte[] breakupExcel = monthlyShipmentBreakupExcelService.generate(entries, includeAmount, includeWeight, zoneIdToName);
                String xlsxName = "shipment-breakup-" + dateSuffix + ".xlsx";
                attachments.add(new EmailDispatchService.EmailAttachment(xlsxName, XLSX_MIME, breakupExcel));
            }
        } else {
            if ("PDF".equals(format)) {
                byte[] fullPdf = monthlyCourierQuotationPdfService.generate(dto, includeAmount, includeGst, includeFuel, includeWeight, true);
                attachments.add(new EmailDispatchService.EmailAttachment("monthly-courier-invoice-" + safe(dto.getTitle()) + ".pdf", "application/pdf", fullPdf));
            } else {
                byte[] breakupExcel = monthlyShipmentBreakupExcelService.generate(entries, includeAmount, includeWeight, zoneIdToName);
                attachments.add(new EmailDispatchService.EmailAttachment("monthly-shipment-breakup.xlsx", XLSX_MIME, breakupExcel));
            }
        }

        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("Please select attachment format: PDF or EXCEL.");
        }
        List<String> toAddresses = request != null ? request.getToAddresses() : null;
        String to;
        if (toAddresses != null && !toAddresses.isEmpty()) {
            to = String.join(",", toAddresses);
        } else {
            if (dto.getCustomerId() == null) {
                throw new IllegalArgumentException("No email addresses provided and no Customer selected to fetch email from.");
            }
            Client customer = getCustomer(dto.getCustomerId());
            to = requireCustomerEmail(customer);
        }

        String emailSubject = resolveMonthlyEmailSubject(emailType, dto, fromDate, toDate, entries);
        List<EmailDispatchService.EmailInlineAttachment> inline = List.of(
            new EmailDispatchService.EmailInlineAttachment("franchExpressLogo", new org.springframework.core.io.ClassPathResource("images/franchExpress.png"))
        );

        emailDispatchService.sendWithAttachments(
                to,
                emailSubject,
                buildBody("MONTHLY".equals(emailType) && "PDF".equals(format), !attachments.isEmpty()),
                true,
                attachments,
                inline
        );
        log.info("Monthly courier email sent to {}", to);
    }

    public void sendSmallClientEntryEmail(String quotationId, MonthlyCourierEmailRequestDto request) {
        SmallClientEntryQuotationDto dto = smallClientEntryQuotationService.findById(quotationId);

        String emailType = request != null && request.getEmailType() != null ? request.getEmailType().toUpperCase() : "MONTHLY";
        String format = request != null && request.getAttachmentFormat() != null ? request.getAttachmentFormat().toUpperCase() : "PDF";
        boolean includeAmount = request == null || request.getIncludeAmountInShipmentBreakup() == null || Boolean.TRUE.equals(request.getIncludeAmountInShipmentBreakup());
        boolean includeGstAndFuel = request == null || request.getIncludeGstAndFuel() == null || Boolean.TRUE.equals(request.getIncludeGstAndFuel());
        boolean includeGst = request != null && request.getIncludeGst() != null ? request.getIncludeGst() : includeGstAndFuel;
        boolean includeFuel = request != null && request.getIncludeFuel() != null ? request.getIncludeFuel() : includeGstAndFuel;
        boolean includeWeight = request == null || request.getIncludeWeight() == null || Boolean.TRUE.equals(request.getIncludeWeight());
        LocalDate fromDate = request != null ? request.getFromDate() : null;
        LocalDate toDate = request != null ? request.getToDate() : null;

        List<SmallClientEntryDto> entries = smallClientEntryService.findByQuotationId(quotationId);
        entries = filterSmallClientEntriesForEmail(entries, request, emailType, fromDate, toDate);

        Map<String, String> zoneIdToName = zoneConfigurationRepository.findByIsDeletedFalse().stream()
                .collect(Collectors.toMap(ZoneConfiguration::getId, z -> z.getZoneName() != null ? z.getZoneName() : z.getId(), (a, b) -> a));

        List<EmailDispatchService.EmailAttachment> attachments = new ArrayList<>();

        if ("DAILY".equals(emailType)) {
            String dateSuffix = fromDate.equals(toDate != null ? toDate : fromDate)
                    ? fromDate.format(SUBJECT_DATE_FMT)
                    : fromDate.format(SUBJECT_DATE_FMT) + "_to_" + (toDate != null ? toDate.format(SUBJECT_DATE_FMT) : fromDate.format(SUBJECT_DATE_FMT));
            if ("PDF".equals(format)) {
                byte[] breakupPdf = smallClientEntryQuotationPdfService.generateShipmentBreakupOnly(dto, entries, zoneIdToName, includeAmount, includeWeight);
                attachments.add(new EmailDispatchService.EmailAttachment("shipment-breakup-" + dateSuffix + ".pdf", "application/pdf", breakupPdf));
            } else {
                byte[] breakupExcel = monthlyShipmentBreakupExcelService.generateForSmallClient(entries, includeAmount, includeWeight, zoneIdToName);
                attachments.add(new EmailDispatchService.EmailAttachment("shipment-breakup-" + dateSuffix + ".xlsx", XLSX_MIME, breakupExcel));
            }
        } else {
            if ("PDF".equals(format)) {
                byte[] fullPdf = smallClientEntryQuotationPdfService.generate(dto, includeAmount, includeGst, includeFuel, includeWeight, true);
                attachments.add(new EmailDispatchService.EmailAttachment("small-client-invoice-" + safe(dto.getTitle()) + ".pdf", "application/pdf", fullPdf));
            } else {
                byte[] breakupExcel = monthlyShipmentBreakupExcelService.generateForSmallClient(entries, includeAmount, includeWeight, zoneIdToName);
                attachments.add(new EmailDispatchService.EmailAttachment("small-client-shipment-breakup.xlsx", XLSX_MIME, breakupExcel));
            }
        }

        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("Please select attachment format: PDF or EXCEL.");
        }
        List<String> toAddresses = request != null ? request.getToAddresses() : null;
        String to;
        if (toAddresses != null && !toAddresses.isEmpty()) {
            to = String.join(",", toAddresses);
        } else {
            if (dto.getCustomerId() == null) {
                throw new IllegalArgumentException("No email addresses provided and no Customer selected to fetch email from.");
            }
            SmallClient customer = getSmallClient(dto.getCustomerId());
            to = requireSmallClientEmail(customer);
        }

        String emailSubject = resolveSmallClientEmailSubject(emailType, dto, fromDate, toDate, entries);
        List<EmailDispatchService.EmailInlineAttachment> inline = List.of(
                new EmailDispatchService.EmailInlineAttachment("franchExpressLogo", new org.springframework.core.io.ClassPathResource("images/franchExpress.png"))
        );

        emailDispatchService.sendWithAttachments(
                to,
                emailSubject,
                buildBody("MONTHLY".equals(emailType) && "PDF".equals(format), !attachments.isEmpty()),
                true,
                attachments,
                inline
        );
        log.info("Small client entry email sent to {}", to);
    }

    private List<SmallClientEntryDto> filterSmallClientEntriesForEmail(
            List<SmallClientEntryDto> entries,
            MonthlyCourierEmailRequestDto request,
            String emailType,
            LocalDate fromDate,
            LocalDate toDate) {
        if ("DAILY".equals(emailType)) {
            if (fromDate == null) {
                throw new IllegalArgumentException("Please select From Date for Daily email.");
            }
            LocalDate rangeTo = toDate != null ? toDate : fromDate;
            if (rangeTo.isBefore(fromDate)) {
                throw new IllegalArgumentException("To Date cannot be before From Date.");
            }
            if (request == null || request.getSelectedEntryIds() == null || request.getSelectedEntryIds().isEmpty()) {
                throw new IllegalArgumentException("Please select at least one record to send email");
            }
            final LocalDate from = fromDate;
            entries = entries.stream()
                    .filter(e -> e.getEntryDate() != null && !e.getEntryDate().isBefore(from) && !e.getEntryDate().isAfter(rangeTo))
                    .toList();
            Set<String> wanted = new HashSet<>(request.getSelectedEntryIds());
            entries = entries.stream().filter(e -> e.getId() != null && wanted.contains(e.getId())).toList();
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("None of the selected shipments fall in the chosen date range.");
            }
        } else {
            if (fromDate != null || toDate != null) {
                final LocalDate from = fromDate;
                final LocalDate rangeTo = toDate;
                entries = entries.stream()
                        .filter(e -> {
                            if (e.getEntryDate() == null) return false;
                            if (from != null && e.getEntryDate().isBefore(from)) return false;
                            if (rangeTo != null && e.getEntryDate().isAfter(rangeTo)) return false;
                            return true;
                        })
                        .toList();
            }
            if (request != null && request.getSelectedEntryIds() != null && !request.getSelectedEntryIds().isEmpty()) {
                Set<String> wanted = new HashSet<>(request.getSelectedEntryIds());
                entries = entries.stream().filter(e -> e.getId() != null && wanted.contains(e.getId())).toList();
            }
        }
        return entries;
    }

    private SmallClient getSmallClient(String customerId) {
        return smallClientRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Small client not found: " + customerId));
    }

    private String requireSmallClientEmail(SmallClient customer) {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new IllegalArgumentException("Customer email is not configured. Please update the customer email before sending the email.");
        }
        return customer.getEmail().trim();
    }

    private String buildSmallClientInvoiceSubject(SmallClientEntryQuotationDto dto, List<SmallClientEntryDto> entries) {
        String company = getCompanyName();
        if (company.isEmpty()) {
            company = "Courier";
        }
        String period = resolveSmallClientShipmentPeriodLabel(dto, entries);
        return company + " " + period + " - Invoice and Breakup";
    }

    private String resolveSmallClientShipmentPeriodLabel(SmallClientEntryQuotationDto dto, List<SmallClientEntryDto> entries) {
        Set<YearMonth> yms = new TreeSet<>();
        for (SmallClientEntryDto e : entries) {
            if (e.getEntryDate() != null) {
                yms.add(YearMonth.from(e.getEntryDate()));
            }
        }
        if (yms.isEmpty()) {
            String month = dto.getMonth() != null ? dto.getMonth().trim() : "";
            String year = dto.getYear() != null ? String.valueOf(dto.getYear()) : "";
            return (month + " " + year).trim();
        }
        if (yms.size() == 1) {
            YearMonth ym = yms.iterator().next();
            return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
        }
        List<YearMonth> sorted = new ArrayList<>(yms);
        sorted.sort(Comparator.naturalOrder());
        return formatYearMonthEnglish(sorted.get(0)) + " – " + formatYearMonthEnglish(sorted.get(sorted.size() - 1));
    }

    private String resolveSmallClientEmailSubject(String emailType, SmallClientEntryQuotationDto dto,
            LocalDate fromDate, LocalDate toDate, List<SmallClientEntryDto> entries) {
        if ("MONTHLY".equalsIgnoreCase(emailType)) {
            return buildSmallClientInvoiceSubject(dto, entries);
        }
        return buildDailyCourierBookingSubject(fromDate, toDate);
    }

    private Client getCustomer(String customerId) {
        return clientRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
    }

    private String requireCustomerEmail(Client customer) {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new IllegalArgumentException("Customer email is not configured. Please update the customer email before sending the email.");
        }
        return customer.getEmail().trim();
    }

    private String getCompanyName() {
        try {
            String name = companySettingsService.getSettings().getCompanyName();
            return (name != null && !name.isBlank()) ? name.trim() : "";
        } catch (Exception e) {
            log.warn("Could not load company name from settings: {}", e.getMessage());
            return "";
        }
    }

    /** Subject for Courier Rate Quotation email: {CompanyName} Courier Quotation */
    private String buildCourierQuotationSubject() {
        String companyName = getCompanyName();
        return (companyName.isEmpty() ? "Courier Quotation" : companyName + " Courier Quotation");
    }

    /**
     * Monthly: "{Company} {period} - Invoice and Breakup" where period is derived from shipment
     * entry dates when available, otherwise quotation month/year. Multiple calendar months collapse to a range.
     */
    private String buildMonthlyCourierInvoiceSubject(MonthlyCourierQuotationDto dto, List<MonthlyCourierEntryDto> entries) {
        String company = getCompanyName();
        if (company.isEmpty()) {
            company = "Courier";
        }
        String period = resolveShipmentPeriodLabel(dto, entries);
        return company + " " + period + " - Invoice and Breakup";
    }

    private String resolveShipmentPeriodLabel(MonthlyCourierQuotationDto dto, List<MonthlyCourierEntryDto> entries) {
        Set<YearMonth> yms = new TreeSet<>();
        for (MonthlyCourierEntryDto e : entries) {
            if (e.getEntryDate() != null) {
                yms.add(YearMonth.from(e.getEntryDate()));
            }
        }
        if (yms.isEmpty()) {
            String month = dto.getMonth() != null ? dto.getMonth().trim() : "";
            String year = dto.getYear() != null ? String.valueOf(dto.getYear()) : "";
            return (month + " " + year).trim();
        }
        if (yms.size() == 1) {
            YearMonth ym = yms.iterator().next();
            return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
        }
        List<YearMonth> sorted = new ArrayList<>(yms);
        sorted.sort(Comparator.naturalOrder());
        YearMonth a = sorted.get(0);
        YearMonth b = sorted.get(sorted.size() - 1);
        return formatYearMonthEnglish(a) + " – " + formatYearMonthEnglish(b);
    }

    private static String formatYearMonthEnglish(YearMonth ym) {
        return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
    }

    private String buildDailyCourierBookingSubject(LocalDate fromDate, LocalDate toDate) {
        LocalDate from = fromDate != null ? fromDate : LocalDate.now();
        LocalDate end = toDate != null ? toDate : from;
        if (from.equals(end)) {
            return "FRANCH EXPRESS Courier Booking Details - " + from.format(SUBJECT_DATE_FMT);
        }
        return "FRANCH EXPRESS Courier Booking Details - " + from.format(SUBJECT_DATE_FMT) + " to " + end.format(SUBJECT_DATE_FMT);
    }

    private String resolveMonthlyEmailSubject(String emailType, MonthlyCourierQuotationDto dto,
            LocalDate fromDate, LocalDate toDate, List<MonthlyCourierEntryDto> entries) {
        if ("MONTHLY".equalsIgnoreCase(emailType)) {
            return buildMonthlyCourierInvoiceSubject(dto, entries);
        }
        return buildDailyCourierBookingSubject(fromDate, toDate);
    }

    private String getOwnerNameForSignature() {
        try {
            String o = companySettingsService.getSettings().getOwnerName();
            if (o != null && !o.isBlank()) {
                return o.trim();
            }
        } catch (Exception e) {
            log.warn("Owner name not loaded: {}", e.getMessage());
        }
        return "SHEIK BAREETH";
    }

    private String buildBody(boolean includeInvoice, boolean includeShipmentBreakup) {
        String companyName = getCompanyName();
        if (companyName.isEmpty()) companyName = "World Wide Courier";
        String owner = escapeHtml(getOwnerNameForSignature());
        StringBuilder body = new StringBuilder();
        body.append("<html><body>")
                .append("<p>Dear Customer,</p>")
                .append("<p>Please find the attached Courier Booking details for your reference.</p>");

        if (includeShipmentBreakup && !includeInvoice) {
            body.append("<p>As selected, the shipment breakup Excel file is attached.</p>");
        } else if (includeInvoice && includeShipmentBreakup) {
            body.append("<p>As selected, both invoice PDF and shipment breakup Excel files are attached.</p>");
        }

        body.append("<p>Kindly review the details.</p>")
                .append("<p><strong>Note:</strong> Reply to this same email if you have any queries.</p>")
                .append("<p>Thank you for choosing our services.</p>")
                .append("<p>Thanks and Regards,<br>")
                .append("<strong>").append(owner).append("</strong></p>")
                .append("<br><img src='cid:franchExpressLogo' alt='Franch Express Logo' style='max-width:200px;'/>")
                .append("</body></html>");
        return body.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "quotation";
        }
        return value.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}

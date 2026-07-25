package com.app.billing.service;

import com.app.billing.dto.MonthlyCourierEntryDto;
import com.app.billing.util.MonthlyCourierBreakupSortUtil;
import com.app.billing.dto.MonthlyCourierQuotationDto;
import com.app.billing.model.CompanySettings;
import com.app.billing.model.ZoneConfiguration;
import com.app.billing.dao.ZoneConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyCourierQuotationWordService {

    private final MonthlyCourierEntryService entryService;
    private final ZoneConfigurationRepository zoneConfigurationRepository;
    private final CompanySettingsService companySettingsService;
    private final MonthlyCourierQuotationService quotationService;
    private final com.app.billing.dao.CourierQuotationRepository courierQuotationRepository;
    private final MonthlyCourierInvoiceGrandTotalService invoiceGrandTotalService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String DEFAULT_HSN_SAC = "996812";

    public byte[] generate(MonthlyCourierQuotationDto dto, boolean includeAmountInBreakup, boolean includeGstAndFuel) {
        return generate(dto, includeAmountInBreakup, includeGstAndFuel, includeGstAndFuel, true);
    }

    public byte[] generate(MonthlyCourierQuotationDto dto, boolean includeAmountInBreakup, boolean includeGst, boolean includeFuel) {
        return generate(dto, includeAmountInBreakup, includeGst, includeFuel, true);
    }

    public byte[] generate(MonthlyCourierQuotationDto dto, boolean includeAmountInBreakup, boolean includeGst, boolean includeFuel, boolean includeWeight) {
        String invoiceNumber = (dto.getInvoiceNumber() != null && !dto.getInvoiceNumber().isEmpty())
                ? dto.getInvoiceNumber() : quotationService.getOrGenerateInvoiceNumber(dto.getId());

        log.info("Generating Monthly Courier Invoice Word Document for: {}", invoiceNumber);

        List<MonthlyCourierEntryDto> entries = MonthlyCourierBreakupSortUtil.sortedCopy(
                entryService.findByQuotationId(dto.getId()));

        MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext calcCtx =
                MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                        dto.getCustomerId(),
                        dto.getFuelChargePercentage(),
                        dto.getFovCharges(),
                        dto.getGstPercentage(),
                        includeFuel,
                        includeGst,
                        dto.getIncludeFov());
        List<com.app.billing.model.MonthlyCourierEntry> monthlyEntries = entries.stream()
                .map(this::toMonthlyEntryForTotals)
                .toList();
        MonthlyCourierInvoiceGrandTotalService.PdfInvoiceTotals totals =
                invoiceGrandTotalService.computePdfTotals(calcCtx, monthlyEntries);

        double courierAmount = totals.baseAmount();
        double fuelPct = totals.fuelPct();
        double fuelAmt = totals.fuelAmount();
        double cgst = totals.cgst();
        double sgst = totals.sgst();
        double roundOff = totals.roundOff();
        double nettAmount = totals.nettAmount();
        double cgstHalfPct = totals.gstPct() / 2.0;
        boolean showGstAndFuel = includeGst || includeFuel;

        try (XWPFDocument doc = new XWPFDocument()) {

            // 1. Invoice Page
            createInvoiceHeader(doc);
            createInvoiceTitle(doc, dto.getMonth());
            createCustomerAndInvoiceDetails(doc, dto);
            createInvoiceTable(doc, dto, courierAmount, fuelPct, fuelAmt, cgstHalfPct, cgst, sgst, roundOff, nettAmount, showGstAndFuel);
            createInvoiceFooter(doc);

            // Page Break
            XWPFParagraph p = doc.createParagraph();
            p.setPageBreak(true);

            // 2. Shipment Breakup
            createShipmentBreakupHeader(doc, dto);
            Map<String, String> zoneMap = zoneConfigurationRepository.findByIsDeletedFalse().stream()
                    .collect(Collectors.toMap(ZoneConfiguration::getId, z -> z.getZoneName() != null ? z.getZoneName() : z.getId(), (a, b) -> a));
            createShipmentBreakupTable(doc, entries, zoneMap, includeAmountInBreakup, includeWeight);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating Monthly Courier Invoice Word doc: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Word document", e);
        }
    }

    private void createInvoiceHeader(XWPFDocument doc) {
        CompanySettings cs = getCompanySettings();
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        
        XWPFRun titleRun = p.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(20);
        titleRun.setText(cs.getCompanyName() != null ? cs.getCompanyName() : "COMPANY NAME");
        
        XWPFParagraph addrP = doc.createParagraph();
        addrP.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun addrRun = addrP.createRun();
        addrRun.setFontSize(10);
        
        StringBuilder addr = new StringBuilder();
        if (cs.getAddress() != null) addr.append(cs.getAddress());
        if (cs.getCity() != null && !cs.getCity().isEmpty()) addr.append(", ").append(cs.getCity());
        if (cs.getPincode() != null) addr.append(" - ").append(cs.getPincode());
        
        addrRun.setText(addr.toString());
        addrRun.addBreak();
        addrRun.setText("Phone: " + (cs.getPhone() != null ? cs.getPhone() : "") + (cs.getMobile() != null ? " / " + cs.getMobile() : ""));
    }

    private void createInvoiceTitle(XWPFDocument doc, String month) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(200);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(12);
        r.setText("TAX INVOICE");
        if (month != null && !month.isBlank()) {
            r.setText(" - " + month);
        }
    }

    private void createCustomerAndInvoiceDetails(XWPFDocument doc, MonthlyCourierQuotationDto dto) {
        CompanySettings cs = getCompanySettings();
        XWPFTable table = doc.createTable(1, 2);
        table.setWidth("100%");
        table.removeBorders();

        XWPFTableRow row = table.getRow(0);

        // Left: Customer Info
        XWPFParagraph leftP = row.getCell(0).getParagraphs().get(0);
        XWPFRun lr = leftP.createRun();
        lr.setBold(true);
        lr.setText("To:");
        lr.addBreak();
        lr.setText(dto.getCustomerName() != null ? dto.getCustomerName() : "");
        lr.setBold(false);

        // Right: Invoice Info
        XWPFParagraph rightP = row.getCell(1).getParagraphs().get(0);
        rightP.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun rr = rightP.createRun();
        rr.setBold(true);
        String invNo = (dto.getInvoiceNumber() != null && !dto.getInvoiceNumber().isEmpty())
                ? dto.getInvoiceNumber() : quotationService.getOrGenerateInvoiceNumber(dto.getId());
        rr.setText("Invoice No: " + invNo);
        rr.addBreak();
        LocalDate invoiceDate = dto.getInvoiceDate() != null ? dto.getInvoiceDate() : quotationService.getInvoiceDate(dto.getId());
        rr.setText("Date: " + invoiceDate.format(DATE_FMT));
        rr.setBold(false);
        if (cs.getLutArnNo() != null && !cs.getLutArnNo().isBlank()) {
            rr.addBreak();
            rr.setText("LUT ARN NO: " + cs.getLutArnNo().trim());
        }
    }

    private void createInvoiceTable(XWPFDocument doc, MonthlyCourierQuotationDto dto, double courierAmount,
            double fuelPct, double fuelAmt, double cgstHalfPct, double cgst, double sgst, double roundOff,
            double nettAmount, boolean includeGstAndFuel) {
        XWPFTable table = doc.createTable(7, 3);
        table.setWidth("100%");

        // Header
        XWPFTableRow hr = table.getRow(0);
        setCellText(hr.getCell(0), "Description", true);
        setCellText(hr.getCell(1), "HSN / SAC", true);
        setCellText(hr.getCell(2), "Amount (Rs.)", true);

        // Row 1: Courier Charges
        XWPFTableRow r1 = table.getRow(1);
        setCellText(r1.getCell(0), "Courier Charges for " + (dto.getMonth() != null ? dto.getMonth() : ""), false);
        setCellText(r1.getCell(1), DEFAULT_HSN_SAC, false);
        setCellText(r1.getCell(2), String.format("%.2f", courierAmount), false);

        // Row 2: Fuel
        XWPFTableRow r2 = table.getRow(2);
        setCellText(r2.getCell(0), "Fuel Surcharge @ " + fuelPct + "%", false);
        setCellText(r2.getCell(1), "", false);
        setCellText(r2.getCell(2), String.format("%.2f", fuelAmt), false);

        // Row 3: CGST
        XWPFTableRow r3 = table.getRow(3);
        setCellText(r3.getCell(0), "CGST @ " + String.format("%.1f", cgstHalfPct) + "%", false);
        setCellText(r3.getCell(1), "", false);
        setCellText(r3.getCell(2), String.format("%.2f", cgst), false);

        // Row 4: SGST
        XWPFTableRow r4 = table.getRow(4);
        setCellText(r4.getCell(0), "SGST @ " + String.format("%.1f", cgstHalfPct) + "%", false);
        setCellText(r4.getCell(1), "", false);
        setCellText(r4.getCell(2), String.format("%.2f", sgst), false);

        // Row 5: Round off
        XWPFTableRow r5 = table.getRow(5);
        setCellText(r5.getCell(0), "Round Off", false);
        setCellText(r5.getCell(1), "", false);
        setCellText(r5.getCell(2), String.format("%.2f", roundOff), false);

        // Row 6: Total
        XWPFTableRow r6 = table.getRow(6);
        setCellText(r6.getCell(0), "Total Amount", true);
        setCellText(r6.getCell(1), "", true);
        setCellText(r6.getCell(2), String.format("%.2f", nettAmount), true);
    }

    private void createInvoiceFooter(XWPFDocument doc) {
        CompanySettings cs = getCompanySettings();
        XWPFParagraph spacing = doc.createParagraph();
        spacing.setSpacingBefore(300);

        // Brand image - center
        XWPFParagraph imgPara = doc.createParagraph();
        imgPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun imgRun = imgPara.createRun();
        try (InputStream is = new ClassPathResource("images/brandPartner.png").getInputStream()) {
            imgRun.addPicture(is, XWPFDocument.PICTURE_TYPE_PNG, "brandPartner.png", 180, 22);
        } catch (Exception e) {
            log.debug("Footer brand image not found: {}", e.getMessage());
        }

        // Company address - center below image
        StringBuilder addr = new StringBuilder();
        if (cs.getAddress() != null && !cs.getAddress().isEmpty()) addr.append(cs.getAddress());
        if (cs.getCity() != null && !cs.getCity().isEmpty()) addr.append(addr.length() > 0 ? ", " : "").append(cs.getCity());
        if (cs.getPincode() != null && !cs.getPincode().isEmpty()) addr.append(addr.length() > 0 ? " - " : "").append(cs.getPincode());
        if (cs.getPhone() != null && !cs.getPhone().isEmpty()) addr.append(addr.length() > 0 ? ". Off : " : "").append(cs.getPhone());
        if (addr.length() > 0) {
            XWPFParagraph addrPara = doc.createParagraph();
            addrPara.setAlignment(ParagraphAlignment.CENTER);
            addrPara.setSpacingAfter(120);
            XWPFRun addrRun = addrPara.createRun();
            addrRun.setFontSize(8);
            addrRun.setText(addr.toString());
        }

        // Red & Yellow line - full width (100%), same as PDF
        XWPFTable lineTable = doc.createTable(1, 2);
        lineTable.setWidth("100%");
        XWPFTableRow lineRow = lineTable.getRow(0);
        if (lineRow.getCell(0).getParagraphs().isEmpty()) lineRow.getCell(0).addParagraph();
        if (lineRow.getCell(1).getParagraphs().isEmpty()) lineRow.getCell(1).addParagraph();
        lineRow.getCell(0).setColor("D23232");
        lineRow.getCell(1).setColor("FFA500");
    }

    private void createShipmentBreakupHeader(XWPFDocument doc, MonthlyCourierQuotationDto dto) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(14);
        r.setText("SHIPMENT BREAKUP");
        if (dto.getMonth() != null && !dto.getMonth().isBlank()) {
            r.setText(" - " + dto.getMonth());
        }
    }

    private void createShipmentBreakupTable(XWPFDocument doc, List<MonthlyCourierEntryDto> entries, Map<String, String> zoneMap, boolean includeAmountInBreakup, boolean includeWeight) {
        int cols = 8 + (includeWeight ? 1 : 0) + (includeAmountInBreakup ? 1 : 0);
        XWPFTable table = doc.createTable(entries.size() + 1, cols);
        table.setWidth("100%");

        String[] headers = buildWordBreakupHeaders(includeAmountInBreakup, includeWeight);

        XWPFTableRow hr = table.getRow(0);
        for (int i = 0; i < cols; i++) {
            setCellText(hr.getCell(i), headers[i], true);
        }

        double totalAmt = 0.0;
        int rowIndex = 1;
        for (MonthlyCourierEntryDto e : entries) {
            XWPFTableRow row = table.getRow(rowIndex++);
            int c = 0;
            setCellText(row.getCell(c++), String.valueOf(rowIndex - 1), false);
            setCellText(row.getCell(c++), e.getEntryDate() != null ? e.getEntryDate().format(DATE_FMT) : "", false);
            setCellText(row.getCell(c++), e.getCourierType() != null ? e.getCourierType() : "", false);
            setCellText(row.getCell(c++), e.getTrackingNumber() != null ? e.getTrackingNumber() : "", false);
            setCellText(row.getCell(c++), e.getConsigneeAddress() != null ? e.getConsigneeAddress() : "", false);
            if (includeWeight) {
                setCellText(row.getCell(c++), e.getWeight() != null ? String.valueOf(e.getWeight()) : "", false);
            }
            setCellText(row.getCell(c++), e.getItemType() != null ? e.getItemType() : "", false);

            String zoneDisp = e.getZone() != null ? zoneMap.getOrDefault(e.getZone(), e.getZone()) : "";
            setCellText(row.getCell(c++), zoneDisp, false);

            setCellText(row.getCell(c++), rateTypeToDisplayWord(e.getRateType()), false);

            if (includeAmountInBreakup) {
                double amt = (e.getAmount() != null ? e.getAmount() : 0.0) + (e.getAdditionalCharges() != null ? e.getAdditionalCharges() : 0.0);
                totalAmt += amt;
                setCellText(row.getCell(c), String.format("%.2f", amt), false);
            }
        }

        if (includeAmountInBreakup) {
            XWPFTableRow totalRow = table.createRow();
            int lastCol = cols - 1;
            CTTcPr tcPr = totalRow.getCell(0).getCTTc().addNewTcPr();
            tcPr.addNewHMerge().setVal(STMerge.RESTART);
            setCellText(totalRow.getCell(0), "Total", true);
            totalRow.getCell(0).getParagraphs().get(0).setAlignment(ParagraphAlignment.RIGHT);
            for (int i = 1; i < lastCol; i++) {
                CTTcPr pr = totalRow.getCell(i).getCTTc().addNewTcPr();
                pr.addNewHMerge().setVal(STMerge.CONTINUE);
            }
            setCellText(totalRow.getCell(lastCol), String.format("%.2f", totalAmt), true);
        }
    }

    private static String[] buildWordBreakupHeaders(boolean includeAmount, boolean includeWeight) {
        java.util.List<String> h = new java.util.ArrayList<>();
        h.add("S.No");
        h.add("Date");
        h.add("Courier");
        h.add("AWB NO");
        h.add("Destination");
        if (includeWeight) {
            h.add("Weight");
        }
        h.add("Item");
        h.add("Zone");
        h.add("RateType");
        if (includeAmount) {
            h.add("Courier Cost");
        }
        return h.toArray(new String[0]);
    }

    private static String rateTypeToDisplayWord(String rateType) {
        if (rateType == null || rateType.isBlank()) return "";
        return switch (rateType) {
            case "STANDARD" -> "Standard";
            case "EXPRESS_RATE" -> "Express Rate";
            case "SURFACE_RATE" -> "Surface Rate";
            case "SafetyPlus" -> "Surface";
            case "PriorityClass" -> "Safety(Priority)";
            default -> rateType;
        };
    }

    private void setCellText(XWPFTableCell cell, String text, boolean bold) {
        if (cell.getParagraphs().isEmpty()) cell.addParagraph();
        XWPFParagraph p = cell.getParagraphs().get(0);
        if (p.getRuns().isEmpty()) p.createRun();
        XWPFRun r = p.getRuns().get(0);
        r.setText(text != null ? text : "");
        r.setBold(bold);
        r.setFontSize(9);
    }

    private CompanySettings getCompanySettings() {
        try {
            var s = companySettingsService.getSettings();
            return CompanySettings.builder()
                    .companyName(s.getCompanyName()).address(s.getAddress())
                    .city(s.getCity()).phone(s.getPhone()).mobile(s.getMobile()).pincode(s.getPincode()).email(s.getEmail()).build();
        } catch (Exception e) {
            log.warn("Could not load company settings: {}", e.getMessage());
            return CompanySettings.builder().build();
        }
    }

    private com.app.billing.model.MonthlyCourierEntry toMonthlyEntryForTotals(MonthlyCourierEntryDto e) {
        com.app.billing.model.MonthlyCourierEntry m = new com.app.billing.model.MonthlyCourierEntry();
        m.setAmount(e.getAmount());
        m.setAdditionalCharges(e.getAdditionalCharges());
        m.setGstApplicable(e.getGstApplicable());
        m.setFuelApplicable(e.getFuelApplicable());
        m.setFovApplicable(e.getFovApplicable());
        return m;
    }
}

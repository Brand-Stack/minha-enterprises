package com.app.billing.service;

import com.app.billing.dao.CourierQuotationRepository;
import com.app.billing.dao.ZoneConfigurationRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.dto.SmallClientEntryDto;
import com.app.billing.dto.SmallClientEntryQuotationDto;
import com.app.billing.model.CompanySettings;
import com.app.billing.model.CourierQuotation;
import com.app.billing.model.MonthlyCourierEntry;
import com.app.billing.model.ZoneConfiguration;
import com.app.billing.util.MonthlyCourierBreakupSortUtil;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.OverflowWrapPropertyValue;
import com.itextpdf.layout.properties.Property;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates Monthly Courier Invoice PDF matching the standard TAX INVOICE
 * layout.
 * Page 1: Invoice summary (company, customer, charges, taxes, RTGS/NEFT,
 * guidelines).
 * Page 2+: MONTHLY SHIPMENT BREAKUP table (paginates with bottom margin
 * reserved for fixed footer).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmallClientEntryQuotationPdfService {

    private final SmallClientEntryService entryService;
    private final CompanySettingsService companySettingsService;
    private final com.app.billing.dao.SmallClientRepository smallClientRepository;
    private final SmallClientEntryQuotationService quotationService;
    private final CourierQuotationRepository courierQuotationRepository;
    private final ZoneConfigurationRepository zoneConfigurationRepository;
    private final MonthlyCourierInvoiceGrandTotalService invoiceGrandTotalService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DeviceRgb TAX_INVOICE_BG = new DeviceRgb(128, 128, 128);
    private static final DeviceRgb HEADER_BG = new DeviceRgb(240, 240, 240);
    private static final DeviceRgb BORDER_CLR = new DeviceRgb(0, 0, 0);
    private static final DeviceRgb NETT_AMOUNT_BG = new DeviceRgb(0, 0, 0);
    private static final float PAGE_MARGIN = 24f;
    /**
     * Narrower left/right on landscape breakup pages (Document margins: top, right, bottom, left)
     * so the table uses more horizontal space and the last columns are not clipped.
     */
    private static final float BREAKUP_SIDE_MARGIN = 15f;
    /**
     * Exact usable width on landscape A4 breakup pages.
     * {@code useAllAvailableWidth()} resolves against the Document's default page size (portrait),
     * NOT the current page's size after an AreaBreak â€” so we must set the table width explicitly.
     */
    private static final float BREAKUP_TABLE_WIDTH =
            PageSize.A4.rotate().getWidth() - 2 * BREAKUP_SIDE_MARGIN;
    /**
     * Flow layout bottom margin must clear the fixed footer in
     * {@link #addPageDecorations} (brand image ~22pt,
     * address lines, 6pt bar, padding). Footer base y â‰ˆ PAGE_MARGIN + 5; without
     * extra reserve, tables overlap it.
     * Page 1 signatory sits above that block â€” same clearance keeps invoice body
     * clear.
     */
    private static final float CONTENT_BOTTOM_MARGIN_PT = PAGE_MARGIN + 118f;
    private static final double DEFAULT_FUEL_PCT = 0.0;
    /** Avoid extreme cell content breaking layout or memory. */
    private static final int MAX_PDF_CELL_TEXT_LENGTH = 4000;
    private static final String DEFAULT_HSN_SAC = "996812";

    /**
     * Full PDF (invoice + breakup). Defaults to including amount in breakup and
     * GST/Fuel.
     */
    public byte[] generate(SmallClientEntryQuotationDto dto) {
        return generate(dto, true, true, true, true, true);
    }

    public byte[] generate(SmallClientEntryQuotationDto dto, boolean includeAmountInBreakup) {
        return generate(dto, includeAmountInBreakup, true, true, true, true);
    }

    /**
     * Full PDF (invoice + breakup). Use includeAmountInBreakup to show/hide Amount
     * column in breakup table.
     * includeGstAndFuel determines if GST and Fuel amounts are calculated and
     * added.
     */
    public byte[] generate(SmallClientEntryQuotationDto dto, boolean includeAmountInBreakup, boolean includeGstAndFuel) {
        return generate(dto, includeAmountInBreakup, includeGstAndFuel, includeGstAndFuel, true, true);
    }

    /**
     * Full PDF with separate GST and Fuel flags. When false, respective value is 0.
     * Weight column defaults on.
     */
    public byte[] generate(SmallClientEntryQuotationDto dto, boolean includeAmountInBreakup, boolean includeGst,
            boolean includeFuel) {
        return generate(dto, includeAmountInBreakup, includeGst, includeFuel, true, true);
    }

    /** Full PDF with GST, Fuel, and optional Weight column in shipment breakup. */
    public byte[] generate(SmallClientEntryQuotationDto dto, boolean includeAmountInBreakup, boolean includeGst,
            boolean includeFuel, boolean includeWeight, boolean includeBreakup) {
        final String quotationId = dto.getId();
        try {
            log.info("Monthly Courier PDF start quotationId={} customer={} month={}",
                    quotationId, dto.getCustomerName(), dto.getMonth());

            List<SmallClientEntryDto> entries = entryService.findByQuotationId(quotationId);
            log.info("Monthly Courier PDF quotationId={} loaded {} shipment entries", quotationId, entries.size());

            CompanySettings cs = getCompanySettings();
            CompanySettingsDto csDto = companySettingsService.getSettings();
            com.app.billing.model.SmallClient customer = smallClientRepository.findById(dto.getCustomerId()).orElse(null);
            String invoiceNumber = (dto.getInvoiceNumber() != null && !dto.getInvoiceNumber().isEmpty())
                    ? dto.getInvoiceNumber()
                    : "-";
            LocalDate invoiceDate = dto.getInvoiceDate() != null ? dto.getInvoiceDate()
                    : quotationService.getInvoiceDate(quotationId);
            double fuelPct = getFuelChargePercentage(dto.getCustomerId());

            MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext calcCtx =
                    MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                            dto.getCustomerId(),
                            dto.getFuelChargePercentage(),
                            dto.getFovCharges(),
                            dto.getGstPercentage(),
                            includeFuel,
                            includeGst,
                            dto.getIncludeFov());
            List<MonthlyCourierEntry> monthlyEntries = entries.stream()
                    .map(this::toMonthlyEntryForTotals)
                    .toList();
            MonthlyCourierInvoiceGrandTotalService.PdfInvoiceTotals totals =
                    invoiceGrandTotalService.computePdfTotals(calcCtx, monthlyEntries);
            double courierAmount = totals.baseAmount();
            fuelPct = totals.fuelPct();
            double fuelAmt = totals.fuelAmount();
            double fovAmt = totals.fovAmount();
            double subTotal = totals.subTotal();
            double cgst = totals.cgst();
            double sgst = totals.sgst();
            double roundOff = totals.roundOff();
            double nettAmount = totals.nettAmount();

            // Bank details: from quotation's selected bank account, else legacy company
            // single bank
            String acName = null, acNo = null, bankName = null, branch = null, ifsc = null;
            java.util.List<CourierQuotation> quotes = courierQuotationRepository.findByCustomerId(dto.getCustomerId());
            CourierQuotation activeQuote = quotes != null ? quotes.stream()
                    .filter(q -> q.getStatus() == CourierQuotation.QuotationStatus.ACTIVE)
                    .findFirst().orElse(null) : null;
            if (activeQuote != null && activeQuote.getSelectedBankAccountId() != null && csDto.getBankAccounts() != null
                    && !csDto.getBankAccounts().isEmpty()) {
                var sel = csDto.getBankAccounts().stream()
                        .filter(b -> activeQuote.getSelectedBankAccountId().equals(b.getId()))
                        .findFirst().orElse(null);
                if (sel != null) {
                    acName = sel.getAccountName();
                    acNo = sel.getAccountNumber();
                    bankName = sel.getBankName();
                    branch = sel.getBranch();
                    ifsc = sel.getIfscCode();
                }
            }
            if (acName == null && csDto.getBankAccountName() != null) {
                acName = csDto.getBankAccountName();
                acNo = csDto.getBankAccountNumber();
                bankName = csDto.getBankName();
                branch = csDto.getBankBranch();
                ifsc = csDto.getBankIfscCode();
            }
            String hsnSac = (csDto.getHsnSacCode() != null && !csDto.getHsnSacCode().isEmpty()) ? csDto.getHsnSacCode()
                    : DEFAULT_HSN_SAC;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            ensureTaxInvoiceBackground(pdfDoc);
            Document doc = new Document(pdfDoc, PageSize.A4, false); // immediateFlush=false: allow footers after
                                                                     // multi-page layout
            doc.setMargins(PAGE_MARGIN, PAGE_MARGIN, CONTENT_BOTTOM_MARGIN_PT, PAGE_MARGIN);

            PdfFont regular = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);

            // ========== PAGE 1: INVOICE SUMMARY ==========
            addPage1Header(doc, cs, bold, regular);
            addTaxInvoiceBanner(doc, bold);
            String lutArn = (csDto.getLutArnNo() != null && !csDto.getLutArnNo().isBlank()) ? csDto.getLutArnNo().trim()
                    : null;
            addCustomerAndInvoiceParticulars(doc, customer, dto, invoiceDate, invoiceNumber, hsnSac, lutArn, bold,
                    regular);
            addInvoiceFinancialSection(doc, acName, acNo, bankName, branch, ifsc, courierAmount, fuelPct, fuelAmt,
                    totals.fovPct(), fovAmt, subTotal, totals.gstPct(), cgst, sgst, roundOff, nettAmount, bold, regular);
            addGuidelines(doc, regular);
            log.info("Monthly Courier PDF quotationId={} page1 (invoice) done", quotationId);

            // ========== PAGE 2+: MONTHLY SHIPMENT BREAKUP ==========
            if (includeBreakup) {
                // Critical: set default page size before table layout so ALL overflow breakup pages
                // (page 3, 4, ...) remain landscape. A single AreaBreak(PageSize) only affects next page.
                pdfDoc.setDefaultPageSize(PageSize.A4.rotate());
                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                doc.setMargins(PAGE_MARGIN, BREAKUP_SIDE_MARGIN, CONTENT_BOTTOM_MARGIN_PT, BREAKUP_SIDE_MARGIN);
                Map<String, String> zoneIdToName = zoneConfigurationRepository.findByIsDeletedFalse().stream()
                        .collect(Collectors.toMap(ZoneConfiguration::getId,
                                z -> z.getZoneName() != null ? z.getZoneName() : z.getId(), (a, b) -> a));
                addShipmentBreakupTable(doc, entries, zoneIdToName, bold, regular, includeAmountInBreakup, includeWeight);
                log.info("Monthly Courier PDF quotationId={} shipment breakup table done, pageCount={}", quotationId,
                        pdfDoc.getNumberOfPages());
            }

            addPageDecorations(pdfDoc, doc, true);
            log.info("Monthly Courier PDF quotationId={} page decorations done, closing", quotationId);
            doc.close();
            log.info("Monthly Courier PDF quotationId={} SUCCESS size={} bytes", quotationId, baos.size());
            return baos.toByteArray();
        } catch (Throwable t) {
            log.error("Monthly Courier PDF FAILED quotationId={} â€” {}: {}",
                    quotationId, t.getClass().getName(), t.getMessage() != null ? t.getMessage() : "(no message)", t);
            throw new RuntimeException("Failed to generate PDF: "
                    + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()), t);
        }
    }

    private void addPage1Header(Document doc, CompanySettings cs, PdfFont bold, PdfFont regular) {
        float[] w = { 75, 25 };
        Table t = new Table(UnitValue.createPercentArray(w)).setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(4);

        Cell left = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.TOP);
        try {
            left.add(new Image(ImageDataFactory.create(new ClassPathResource("images/minhaEnterprises.jpeg").getURL()))
                    .setHeight(50));
        } catch (Exception e) {
            log.debug("Could not load company logo minhaEnterprises.jpeg");
        }
        StringBuilder addr = new StringBuilder();
        if (cs.getAddress() != null)
            addr.append(cs.getAddress());
        if (cs.getCity() != null)
            addr.append(cs.getCity() != null && !cs.getCity().isEmpty() ? ", " + cs.getCity() : "");
        if (cs.getPincode() != null)
            addr.append(" - ").append(cs.getPincode());
        String phones = "";
        if (cs.getMobile() != null && !cs.getMobile().isEmpty())
            phones = cs.getMobile();
        if (cs.getPhone() != null && !cs.getPhone().isEmpty())
            phones += (phones.isEmpty() ? "" : " / ") + cs.getPhone();
        if (cs.getEmail() != null && !cs.getEmail().isEmpty())
            phones += (phones.isEmpty() ? "" : ", ") + cs.getEmail();

        left.add(new Paragraph(cs.getCompanyName() != null ? cs.getCompanyName() : "DEMO COMPANY")
                .setFont(bold).setFontSize(12).setMarginTop(4).setMarginBottom(2));
        left.add(new Paragraph("(INTERNATIONAL / DOMESTIC / COURIER & CARGO)").setFont(regular).setFontSize(8)
                .setMarginBottom(2));
        if (addr.length() > 0)
            left.add(new Paragraph(addr.toString()).setFont(regular).setFontSize(8).setMarginBottom(2));
        if (!phones.isEmpty())
            left.add(new Paragraph("Phone Nos : " + phones).setFont(regular).setFontSize(8).setMarginBottom(2));

        if (cs.getGstin() != null && !cs.getGstin().isEmpty())
            left.add(new Paragraph("GSTIN No. : " + cs.getGstin()).setFont(regular).setFontSize(8));
        t.addCell(left);

        Cell right = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.TOP)
                .setTextAlignment(TextAlignment.RIGHT);
        try {
            Image franchLogo = new Image(
                    ImageDataFactory.create(new ClassPathResource("images/franchExpress.png").getURL()));
            franchLogo.setHeight(52);
            franchLogo.setHorizontalAlignment(HorizontalAlignment.RIGHT);
            right.add(franchLogo);
        } catch (Exception e) {
            log.debug("Could not load franch express logo franchExpress.png");
        }
        t.addCell(right);
        doc.add(t);
    }

    private void addTaxInvoiceBanner(Document doc, PdfFont bold) {
        Table t = new Table(new float[] { 100 }).setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(4).setMarginBottom(8);
        Cell c = new Cell().setBackgroundColor(TAX_INVOICE_BG)
                .add(new Paragraph("TAX INVOICE").setFont(bold).setFontSize(14)
                        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE))
                .setTextAlignment(TextAlignment.CENTER).setPadding(6);
        t.addCell(c);
        doc.add(t);
    }

    private void addCustomerAndInvoiceParticulars(Document doc, com.app.billing.model.SmallClient customer,
            SmallClientEntryQuotationDto dto,
            LocalDate invoiceDate, String invoiceNumber, String hsnSacCode, String lutArnNo, PdfFont bold,
            PdfFont regular) {
        float[] w = { 68, 32 };
        Table t = new Table(UnitValue.createPercentArray(w)).setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(8);

        Cell toCell = new Cell(3, 1).setBorder(new SolidBorder(BORDER_CLR, 1f)).setPadding(8);
        toCell.add(new Paragraph("To,").setFont(regular).setFontSize(9).setMarginBottom(2));
        String custName = dto.getCustomerName() != null ? dto.getCustomerName() : "";
        toCell.add(new Paragraph("M/s. " + custName).setFont(bold).setFontSize(11).setMarginBottom(4));
        if (customer != null) {
            if (customer.getAddress() != null)
                toCell.add(new Paragraph(customer.getAddress()).setFont(regular).setFontSize(8).setMarginBottom(1));
            if (customer.getCity() != null || customer.getPincode() != null) {
                String cityPin = (customer.getCity() != null ? customer.getCity() : "")
                        + (customer.getPincode() != null ? "-" + customer.getPincode() : "");
                toCell.add(new Paragraph(cityPin).setFont(regular).setFontSize(8).setMarginBottom(1));
            }
            if (customer.getGstin() != null && !customer.getGstin().isEmpty()) {
                toCell.add(new Paragraph("Cust.GSTIN : " + customer.getGstin()).setFont(regular).setFontSize(8)
                        .setMarginBottom(2));
            }
            if (customer.getState() != null) {
                toCell.add(new Paragraph(customer.getState()).setFont(regular).setFontSize(8).setMarginBottom(2));
            }
        }
        toCell.add(new Paragraph("Code : PAN").setFont(regular).setFontSize(8).setTextAlignment(TextAlignment.LEFT)
                .setMarginTop(2));
        t.addCell(toCell);

        t.addCell(new Cell().setBorder(new SolidBorder(BORDER_CLR, 1f)).setPadding(8)
                .add(new Paragraph("Invoice No : " + invoiceNumber).setFont(bold).setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph("Invoice Date : " + invoiceDate.format(DATE_FMT)).setFont(bold).setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER).setMarginTop(4)));
        t.addCell(new Cell().setBorder(new SolidBorder(BORDER_CLR, 1f)).setPadding(8)
                .add(new Paragraph("Period From : " + getFirstDayOfMonth(dto.getYear(), dto.getMonth()))
                        .setFont(regular).setFontSize(8).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("To : " + getLastDayOfMonth(dto.getYear(), dto.getMonth())).setFont(regular)
                        .setFontSize(8).setTextAlignment(TextAlignment.RIGHT).setMarginTop(2)));
        Cell hsnLutCell = new Cell().setBorder(new SolidBorder(BORDER_CLR, 1f)).setPadding(6);
        hsnLutCell.add(new Paragraph("HSN/SAC Code : " + (hsnSacCode != null ? hsnSacCode : "")).setFont(regular)
                .setFontSize(8).setTextAlignment(TextAlignment.RIGHT));
        if (lutArnNo != null && !lutArnNo.isEmpty()) {
            hsnLutCell.add(new Paragraph("LUT ARN NO : " + lutArnNo).setFont(regular).setFontSize(8)
                    .setTextAlignment(TextAlignment.RIGHT).setMarginTop(2));
        }
        t.addCell(hsnLutCell);

        doc.add(t);
    }

    private void addInvoiceFinancialSection(Document doc, String acName, String acNo, String bankName, String branch,
            String ifsc,
            double courierAmount, double fuelPct, double fuelAmt, double fovPct, double fovAmt,
            double subTotal, double gstPct, double cgst, double sgst, double roundOff, double nettAmount, PdfFont bold,
            PdfFont regular) {
        Table section = new Table(UnitValue.createPercentArray(new float[] { 45, 55 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(8);

        Cell left = new Cell().setBorder(new SolidBorder(BORDER_CLR, 1f)).setPadding(0);
        addRtgsNeftBoxToCell(left, acName, acNo, bankName, branch, ifsc, bold, regular);
        left.add(new Paragraph("Rupees " + amountInWords(nettAmount) + " Only")
                .setFont(regular).setFontSize(9).setMarginTop(10).setMarginLeft(8).setMarginBottom(8));
        section.addCell(left);

        Cell right = new Cell().setBorder(new SolidBorder(BORDER_CLR, 1f)).setPadding(6);
        Table charges = new Table(UnitValue.createPercentArray(new float[] { 75, 25 }))
                .setWidth(UnitValue.createPercentValue(100));
        charges.addCell(chargeLabelCell("Domestic / International Courier Charges :", bold));
        charges.addCell(chargeValueCell(String.format("%.2f", courierAmount), regular));
        charges.addCell(chargeLabelCell("Fuel Charges @ " + String.format("%.2f", fuelPct) + "% :", regular));
        charges.addCell(chargeValueCell(String.format("%.2f", fuelAmt), regular));
        if (fovAmt > 0.005) {
            charges.addCell(chargeLabelCell("FOV Charges @ " + String.format("%.2f", fovPct) + "% :", regular));
            charges.addCell(chargeValueCell(String.format("%.2f", fovAmt), regular));
        }
        charges.addCell(chargeLabelCell("SUB TOTAL :", bold));
        charges.addCell(chargeValueCell(String.format("%.2f", subTotal), bold));
        double cgstHalfPct = gstPct / 2.0;
        charges.addCell(chargeLabelCell("CGST @ " + String.format("%.1f", cgstHalfPct) + "% :", regular));
        charges.addCell(chargeValueCell(String.format("%.2f", cgst), regular));
        charges.addCell(chargeLabelCell("SGST @ " + String.format("%.1f", cgstHalfPct) + "% :", regular));
        charges.addCell(chargeValueCell(String.format("%.2f", sgst), regular));
        charges.addCell(chargeLabelCell("Round Off :", regular));
        charges.addCell(chargeValueCell(String.format("%.2f", roundOff), regular));
        right.add(charges);

        Table nett = new Table(UnitValue.createPercentArray(new float[] { 60, 40 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(6);
        nett.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .add(new Paragraph("Nett Amount Payable :").setFont(bold).setFontSize(10)
                        .setTextAlignment(TextAlignment.RIGHT)));
        nett.addCell(new Cell().setBorder(new SolidBorder(BORDER_CLR, 2f)).setPadding(4)
                .add(new Paragraph(String.format("%.2f", nettAmount)).setFont(bold).setFontSize(12)
                        .setTextAlignment(TextAlignment.RIGHT)));
        right.add(nett);
        section.addCell(right);

        doc.add(section);
    }

    private void addChargesSummary(Document doc, double courierAmount, double fuelPct, double fuelAmt,
            double subTotal, double gstPct, double cgst, double sgst, double roundOff, double nettAmount, PdfFont bold,
            PdfFont regular) {
        float[] colW = { 70, 30 };
        Table t = new Table(UnitValue.createPercentArray(colW)).setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(8);

        t.addCell(chargeLabelCell("Domestic / International Courier Charges :", regular));
        t.addCell(chargeValueCell(String.format("%.2f", courierAmount), regular));
        t.addCell(chargeLabelCell("Fuel Charges @ " + String.format("%.2f", fuelPct) + "% :", regular));
        t.addCell(chargeValueCell(String.format("%.2f", fuelAmt), regular));
        t.addCell(new Cell(1, 2)
                .add(new Paragraph("SUB TOTAL : " + String.format("%.2f", subTotal)).setFont(bold).setFontSize(10))
                .setBorder(Border.NO_BORDER).setPadding(2));
        double cgstHalfPct = gstPct / 2.0;
        t.addCell(chargeLabelCell("CGST @ " + String.format("%.1f", cgstHalfPct) + "% :", regular));
        t.addCell(chargeValueCell(String.format("%.2f", cgst), regular));
        t.addCell(chargeLabelCell("SGST @ " + String.format("%.1f", cgstHalfPct) + "% :", regular));
        t.addCell(chargeValueCell(String.format("%.2f", sgst), regular));
        t.addCell(chargeLabelCell("Round Off :", regular));
        t.addCell(chargeValueCell(String.format("%.2f", roundOff), regular));

        Cell nettCell = new Cell(1, 2)
                .add(new Paragraph("Nett Amount Payable : " + String.format("%.2f", nettAmount)).setFont(bold)
                        .setFontSize(11).setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE))
                .setBackgroundColor(NETT_AMOUNT_BG).setPadding(8).setTextAlignment(TextAlignment.CENTER);
        t.addCell(nettCell);

        doc.add(t);
        doc.add(new Paragraph("Rupees " + amountInWords(nettAmount) + " Only").setFont(regular).setFontSize(9)
                .setMarginBottom(8));
    }

    private Cell chargeLabelCell(String text, PdfFont f) {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(2).add(new Paragraph(text).setFont(f).setFontSize(9));
    }

    private Cell chargeValueCell(String text, PdfFont f) {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(2).add(new Paragraph(text).setFont(f).setFontSize(9))
                .setTextAlignment(TextAlignment.RIGHT);
    }

    private void addRtgsNeftBox(Document doc, CompanySettings cs, PdfFont bold, PdfFont regular) {
        boolean hasAny = (cs.getBankAccountName() != null && !cs.getBankAccountName().isEmpty())
                || (cs.getBankName() != null && !cs.getBankName().isEmpty())
                || (cs.getBankAccountNumber() != null && !cs.getBankAccountNumber().isEmpty())
                || (cs.getBankBranch() != null && !cs.getBankBranch().isEmpty())
                || (cs.getBankIfscCode() != null && !cs.getBankIfscCode().isEmpty());
        if (!hasAny)
            return;

        doc.add(new Paragraph("RTGS / NEFT Details :").setFont(bold).setFontSize(9).setMarginBottom(4));
        Table t = new Table(UnitValue.createPercentArray(new float[] { 25, 75 }))
                .setWidth(UnitValue.createPercentValue(50))
                .setBorder(new SolidBorder(BORDER_CLR, 1f)).setPadding(8).setMarginBottom(8);
        if (cs.getBankAccountName() != null) {
            t.addCell(metaCell("A/c Name :", bold));
            t.addCell(metaCell(cs.getBankAccountName(), regular));
        }
        if (cs.getBankAccountNumber() != null) {
            t.addCell(metaCell("A/c No :", bold));
            t.addCell(metaCell(cs.getBankAccountNumber(), regular));
        }
        if (cs.getBankName() != null) {
            t.addCell(metaCell("Bank :", bold));
            t.addCell(metaCell(cs.getBankName(), regular));
        }
        if (cs.getBankBranch() != null) {
            t.addCell(metaCell("Branch :", bold));
            t.addCell(metaCell(cs.getBankBranch(), regular));
        }
        if (cs.getBankIfscCode() != null) {
            t.addCell(metaCell("IFSC Code :", bold));
            t.addCell(metaCell(cs.getBankIfscCode(), regular));
        }
        doc.add(t);
    }

    private void addRtgsNeftBoxToCell(Cell parent, String acName, String acNo, String bankName, String branch,
            String ifsc, PdfFont bold, PdfFont regular) {
        boolean hasAny = (acName != null && !acName.isEmpty()) || (acNo != null && !acNo.isEmpty())
                || (bankName != null && !bankName.isEmpty()) || (branch != null && !branch.isEmpty())
                || (ifsc != null && !ifsc.isEmpty());
        if (!hasAny)
            return;
        parent.add(
                new Paragraph("RTGS / NEFT Details :").setFont(bold).setFontSize(9).setMarginTop(2).setMarginLeft(8));
        Table t = new Table(UnitValue.createPercentArray(new float[] { 28, 72 }))
                .setWidth(UnitValue.createPercentValue(96))
                .setMarginLeft(6)
                .setMarginBottom(6)
                .setBorder(new SolidBorder(BORDER_CLR, 1f));
        if (acName != null) {
            t.addCell(metaCell("A/c Name :", bold));
            t.addCell(metaCell(acName, regular));
        }
        if (acNo != null) {
            t.addCell(metaCell("A/c No :", bold));
            t.addCell(metaCell(acNo, regular));
        }
        if (bankName != null) {
            t.addCell(metaCell("Bank :", bold));
            t.addCell(metaCell(bankName, regular));
        }
        if (branch != null) {
            t.addCell(metaCell("Branch :", bold));
            t.addCell(metaCell(branch, regular));
        }
        if (ifsc != null) {
            t.addCell(metaCell("IFSC Code :", bold));
            t.addCell(metaCell(ifsc, regular));
        }
        parent.add(t);
    }

    private void addGuidelines(Document doc, PdfFont regular) {
        doc.add(new Paragraph("Statutory Guidelines").setFont(regular).setBold().setItalic().setFontSize(8)
                .setMarginBottom(2));
        doc.add(new Paragraph(
                "1. Payment should be made ONLY by crossed cheque or DD or RTGS/NEFT in favour of company.\n"
                        + "2. PAYMENT DUE DATE : 15 days\n"
                        + "3. Any delay in payment after due date will be charged 24% per annum on pro-rata basis.\n"
                        + "4. All disputes are subject to Chennai jurisdiction only.")
                .setFont(regular).setFontSize(7).setMarginLeft(8).setMarginBottom(4));
        doc.add(new Paragraph("General Guidelines").setFont(regular).setBold().setItalic().setFontSize(8)
                .setMarginBottom(2));
        doc.add(new Paragraph(
                "1. Kindly acknowledge the receipt of the bill by handing over the bill acknowledgement.\n"
                        + "2. While making the payment please hand over the payment advice with full details.\n"
                        + "3. Any mistake/correction in invoice has to be reported in writing within seven days.\n"
                        + "4. This is a computer-generated invoice and does not require signature.\n"
                        + "5. For any queries please contact us immediately.")
                .setFont(regular).setFontSize(7).setMarginLeft(8).setMarginBottom(8));
    }

    private void addShipmentBreakupTable(Document doc, List<SmallClientEntryDto> entries,
            Map<String, String> zoneIdToName, PdfFont bold, PdfFont regular, boolean includeAmount,
            boolean includeWeight) {
        doc.add(new Paragraph("MONTHLY SHIPMENT BREAKUP").setFont(bold).setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(12));
        addShipmentBreakupTableContent(doc, entries, zoneIdToName, bold, regular, includeAmount, includeWeight);
    }

    /** Shipment breakup only (e.g. Daily email). Defaults weight column on. */
    public byte[] generateShipmentBreakupOnly(SmallClientEntryQuotationDto dto, List<SmallClientEntryDto> entries,
            java.util.Map<String, String> zoneIdToName, boolean includeAmount) {
        return generateShipmentBreakupOnly(dto, entries, zoneIdToName, includeAmount, true);
    }

    /**
     * Generates PDF with only the shipment breakup table. Same footer as Invoice;
     * no Authorised Signatory.
     */
    public byte[] generateShipmentBreakupOnly(SmallClientEntryQuotationDto dto, List<SmallClientEntryDto> entries,
            java.util.Map<String, String> zoneIdToName, boolean includeAmount, boolean includeWeight) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdfDoc, PageSize.A4.rotate(), false); // landscape: wide breakup table
            doc.setMargins(PAGE_MARGIN, BREAKUP_SIDE_MARGIN, CONTENT_BOTTOM_MARGIN_PT, BREAKUP_SIDE_MARGIN);
            PdfFont regular = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
            doc.add(new Paragraph("SHIPMENT BREAKUP").setFont(bold).setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(12));
            addShipmentBreakupTableContent(doc, entries, zoneIdToName, bold, regular, includeAmount, includeWeight);
            addPageDecorations(pdfDoc, doc, false); // footer only, no Authorised Signatory
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating shipment breakup PDF", e);
            throw new RuntimeException("Failed to generate shipment breakup PDF", e);
        }
    }

    /**
     * One logical table: header repeats only on page breaks (iText
     * {@code addHeaderCell}), not mid-page.
     * Column order: S.No, Date, Courier, AWB NO, Destination, [Weight], Item, Zone,
     * RateType, [Courier Cost].
     */
    private void addShipmentBreakupTableContent(Document doc, List<SmallClientEntryDto> entries,
            Map<String, String> zoneIdToName, PdfFont bold, PdfFont regular, boolean includeAmount,
            boolean includeWeight) {
        List<SmallClientEntryDto> sortedEntries = MonthlyCourierBreakupSortUtil.sortedCopySmallClient(entries);
        float[] colWidths = resolveBreakupColWidths(includeAmount, includeWeight);
        String[] headers = resolveBreakupHeaders(includeAmount, includeWeight);

        double total = 0;
        for (SmallClientEntryDto e : sortedEntries) {
            double amt = (e.getAmount() != null ? e.getAmount() : 0.0)
                    + (e.getAdditionalCharges() != null ? e.getAdditionalCharges() : 0.0);
            total += amt;
        }

        Table t = new Table(UnitValue.createPercentArray(colWidths))
                .setFixedLayout()
                .setWidth(BREAKUP_TABLE_WIDTH);
        for (String h : headers) {
            t.addHeaderCell(breakupHeaderCell(h, bold));
        }

        if (sortedEntries.isEmpty()) {
            doc.add(t);
            return;
        }

        int sno = 1;
        for (SmallClientEntryDto e : sortedEntries) {
            double amt = (e.getAmount() != null ? e.getAmount() : 0.0)
                    + (e.getAdditionalCharges() != null ? e.getAdditionalCharges() : 0.0);
            String zoneDisplay = (zoneIdToName != null && e.getZone() != null && zoneIdToName.containsKey(e.getZone()))
                    ? zoneIdToName.get(e.getZone())
                    : (e.getZone() != null ? e.getZone() : "");
            String rateTypeDisplay = rateTypeToDisplay(e.getRateType());
            t.addCell(breakupCellCompact(String.valueOf(sno++), regular, TextAlignment.CENTER));
            t.addCell(breakupCellCompact(e.getEntryDate() != null ? e.getEntryDate().format(DATE_FMT) : "", regular,
                    TextAlignment.CENTER));
            t.addCell(breakupCellWrap(safePdfCellText(e.getCourierType()), regular));
            t.addCell(breakupCellWrap(safePdfCellText(e.getTrackingNumber()), regular, TextAlignment.CENTER));
            t.addCell(breakupCellWrap(safePdfCellText(e.getConsigneeAddress()), regular));
            if (includeWeight) {
                String w = e.getWeight() != null ? String.format("%.2f", e.getWeight()) : "0.00";
                t.addCell(breakupCellCompact(w, regular, TextAlignment.RIGHT));
            }
            t.addCell(breakupCellWrap(safePdfCellText(e.getItemType()), regular));
            t.addCell(breakupCellWrap(safePdfCellText(zoneDisplay), regular));
            t.addCell(breakupCellWrap(safePdfCellText(rateTypeDisplay), regular));
            if (includeAmount) {
                t.addCell(breakupCellCompact(String.format("%.2f", amt), regular, TextAlignment.RIGHT));
            }
        }
        doc.add(t);

        if (includeAmount) {
            int colCount = headers.length;
            Table totalTable = new Table(UnitValue.createPercentArray(colWidths))
                    .setFixedLayout()
                    .setWidth(BREAKUP_TABLE_WIDTH)
                    .setMarginTop(4);
            totalTable.addCell(new Cell(1, colCount - 1)
                    .add(breakupParagraph("Total", bold, 8, TextAlignment.RIGHT))
                    .setBackgroundColor(HEADER_BG)
                    .setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                    .setPadding(3)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE));
            totalTable.addCell(new Cell(1, 1)
                    .add(breakupParagraph(String.format("%.2f", total), bold, 7, TextAlignment.RIGHT))
                    .setBackgroundColor(HEADER_BG)
                    .setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                    .setPadding(3)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE));
            doc.add(totalTable);
        }
    }

    /**
     * Column percent shares (must sum to 100).
     * Landscape A4 gives ~812pt usable width; allocations balanced so
     * Destination wraps gracefully and Courier Cost is never clipped.
     */
    private static float[] resolveBreakupColWidths(boolean includeAmount, boolean includeWeight) {
        // 10 columns: S.No Date Courier AWB Destination Weight Item Zone RateType Cost
        if (includeAmount && includeWeight) {
            return new float[] { 3, 6, 9, 10, 22, 5, 12, 11, 11, 11 };
        }
        // 9 columns (no weight)
        if (includeAmount) {
            return new float[] { 4, 6, 9, 11, 24, 13, 11, 11, 11 };
        }
        // 9 columns (no amount)
        if (includeWeight) {
            return new float[] { 3, 6, 9, 11, 28, 5, 13, 12, 13 };
        }
        // 8 columns
        return new float[] { 4, 7, 10, 12, 28, 14, 13, 12 };
    }

    private static String[] resolveBreakupHeaders(boolean includeAmount, boolean includeWeight) {
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

    private static Paragraph breakupParagraph(String text, PdfFont font, float fontSize, TextAlignment alignment) {
        return new Paragraph(text != null ? text : "")
                .setFont(font)
                .setFontSize(fontSize)
                .setTextAlignment(alignment)
                .setMultipliedLeading(1.1f);
    }

    private static Cell breakupHeaderCell(String headerText, PdfFont bold) {
        return new Cell()
                .add(breakupParagraph(headerText, bold, 8, TextAlignment.CENTER))
                .setBackgroundColor(HEADER_BG)
                .setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(2.5f)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    /** Numeric / short cells: no keepTogether so rows can split across pages without overlap. */
    private static Cell breakupCellCompact(String text, PdfFont regular, TextAlignment alignment) {
        return new Cell()
                .add(breakupParagraph(text != null ? text : "", regular, 7, alignment))
                .setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(2.5f)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    /** Long text (destination, item, â€¦): wrap, optional break for long tokens, top-aligned for multi-line rows. */
    private static Cell breakupCellWrap(String text, PdfFont regular) {
        return breakupCellWrap(text, regular, TextAlignment.LEFT);
    }

    private static Cell breakupCellWrap(String text, PdfFont regular, TextAlignment alignment) {
        Cell c = new Cell()
                .add(breakupParagraph(text != null ? text : "", regular, 7, alignment))
                .setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(2.5f)
                .setVerticalAlignment(VerticalAlignment.TOP);
        c.setProperty(Property.OVERFLOW_WRAP, OverflowWrapPropertyValue.BREAK_WORD);
        return c;
    }

    private static String safePdfCellText(String text) {
        if (text == null)
            return "";
        if (text.length() <= MAX_PDF_CELL_TEXT_LENGTH)
            return text;
        return text.substring(0, MAX_PDF_CELL_TEXT_LENGTH - 3) + "...";
    }

    private static String rateTypeToDisplay(String rateType) {
        if (rateType == null || rateType.isBlank())
            return "";
        return switch (rateType) {
            case "STANDARD" -> "Standard";
            case "EXPRESS_RATE" -> "Express Rate";
            case "SURFACE_RATE" -> "Surface Rate";
            case "SafetyPlus" -> "Surface";
            case "PriorityClass" -> "Safety(Priority)";
            default -> rateType;
        };
    }

    private Cell cell(String text, PdfFont f, TextAlignment alignment) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "").setFont(f).setFontSize(8).setTextAlignment(alignment))
                .setBorder(new SolidBorder(BORDER_CLR, 0.5f)).setPadding(4)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell metaCell(String text, PdfFont f) {
        return new Cell().add(new Paragraph(text != null ? text : "").setFont(f).setFontSize(8))
                .setBorder(Border.NO_BORDER).setPadding(2);
    }

    private MonthlyCourierEntry toMonthlyEntryForTotals(SmallClientEntryDto e) {
        MonthlyCourierEntry m = new MonthlyCourierEntry();
        m.setAmount(e.getAmount());
        m.setAdditionalCharges(e.getAdditionalCharges());
        m.setGstApplicable(e.getGstApplicable());
        m.setFuelApplicable(e.getFuelApplicable());
        m.setFovApplicable(e.getFovApplicable());
        return m;
    }

    private double getFuelChargePercentage(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return DEFAULT_FUEL_PCT;
        }
        List<CourierQuotation> quotes = courierQuotationRepository.findByCustomerId(customerId);
        return quotes.stream()
                .filter(q -> q.getStatus() == CourierQuotation.QuotationStatus.ACTIVE)
                .findFirst()
                .map(CourierQuotation::getFuelChargePercentage)
                .filter(p -> p != null && !p.isNaN() && p >= 0)
                .orElse(DEFAULT_FUEL_PCT);
    }

    private double roundToRupee(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String amountInWords(double amount) {
        int rupees = (int) amount;
        if (rupees == 0)
            return "Zero";
        String[] ones = { "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine" };
        String[] tens = { "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety" };
        String[] teens = { "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
                "Eighteen", "Nineteen" };
        if (rupees < 10)
            return ones[rupees];
        if (rupees < 20)
            return teens[rupees - 10];
        if (rupees < 100)
            return tens[rupees / 10] + (rupees % 10 > 0 ? " " + ones[rupees % 10] : "");
        if (rupees < 1000)
            return ones[rupees / 100] + " Hundred" + (rupees % 100 > 0 ? " " + amountInWords(rupees % 100) : "");
        if (rupees < 100000)
            return amountInWords(rupees / 1000) + " Thousand"
                    + (rupees % 1000 > 0 ? " " + amountInWords(rupees % 1000) : "");
        if (rupees < 10000000)
            return amountInWords(rupees / 100000) + " Lakh"
                    + (rupees % 100000 > 0 ? " " + amountInWords(rupees % 100000) : "");
        return amountInWords(rupees / 10000000) + " Crore"
                + (rupees % 10000000 > 0 ? " " + amountInWords(rupees % 10000000) : "");
    }

    private String getFirstDayOfMonth(Integer year, String month) {
        if (year == null || month == null)
            return "";
        return String.format("01/%02d/%d", monthToInt(month), year % 100);
    }

    private String getLastDayOfMonth(Integer year, String month) {
        if (year == null || month == null)
            return "";
        int m = monthToInt(month);
        int lastDay = java.time.YearMonth.of(year, m).lengthOfMonth();
        return String.format("%02d/%02d/%d", lastDay, m, year % 100);
    }

    private int monthToInt(String month) {
        String[] months = { "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER",
                "OCTOBER", "NOVEMBER", "DECEMBER" };
        for (int i = 0; i < months.length; i++)
            if (months[i].equalsIgnoreCase(month))
                return i + 1;
        return 1;
    }

    private ImageData loadBackgroundImageData() {
        try (InputStream is = new ClassPathResource("images/bgImage.png").getInputStream()) {
            byte[] bytes = is.readAllBytes();
            return ImageDataFactory.create(bytes);
        } catch (Exception e) {
            log.debug("Could not load bgImage.png from classpath: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Ensures page 1 exists and paints full-page watermark before any invoice
     * content is written.
     */
    private void ensureTaxInvoiceBackground(PdfDocument pdfDoc) {
        ImageData bgData = loadBackgroundImageData();
        if (bgData == null) {
            log.warn("Background image images/bgImage.png not found - ensure it exists in src/main/resources/images/");
            return;
        }
        if (pdfDoc.getNumberOfPages() == 0) {
            pdfDoc.addNewPage(PageSize.A4);
        }
        PdfPage firstPage = pdfDoc.getPage(1);
        Rectangle pageSize = firstPage.getPageSize();
        PdfCanvas underCanvas = new PdfCanvas(firstPage.newContentStreamBefore(), firstPage.getResources(), pdfDoc);
        PdfExtGState gState = new PdfExtGState();
        gState.setFillOpacity(0.22f);
        gState.setStrokeOpacity(0.22f);
        underCanvas.saveState();
        underCanvas.setExtGState(gState);
        underCanvas.addImageWithTransformationMatrix(
                bgData,
                pageSize.getWidth(), 0,
                0, pageSize.getHeight(),
                0, 0,
                false);
        underCanvas.restoreState();
    }

    private void addPageDecorations(PdfDocument pdfDoc, Document doc, boolean includeAuthorisedSignatoryOnPage1) {
        int totalPages = pdfDoc.getNumberOfPages();
        // Use each page's size (portrait invoice p1 + landscape breakup pages) so footer/signature align.
        float defaultW = PageSize.A4.getWidth();
        float defaultH = PageSize.A4.getHeight();

        // Avoid post-layout PdfCanvas(page) access for every page here.
        // With larger documents, iText can throw NPE from PdfPage internals
        // (rotation/media box lookup).
        // Footer/signature rendering below uses fixed-position layout elements and is
        // stable.

        CompanySettings cs = getCompanySettings();
        try {
            PdfFont regularFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
            var brandImageOriginal = ImageDataFactory.create(new ClassPathResource("images/brandPartner.png").getURL());

            float footerLeft = PAGE_MARGIN + 10f;
            float footerBottom = PAGE_MARGIN + 5f;

            for (int pageNo = 1; pageNo <= totalPages; pageNo++) {
                float pageW = defaultW;
                float pageH = defaultH;
                try {
                    Rectangle r = pdfDoc.getPage(pageNo).getPageSizeWithRotation();
                    if (r != null) {
                        pageW = r.getWidth();
                        pageH = r.getHeight();
                    }
                } catch (Exception ex) {
                    log.debug("Page size for footer page {}: {}", pageNo, ex.getMessage());
                }
                float footerWidth = pageW - (2 * footerLeft);

                // Page 1 only (and only for full invoice): Authorised Signatory at bottom-right
                // (above footer)
                if (pageNo == 1 && includeAuthorisedSignatoryOnPage1) {
                    float sigBottom = footerBottom + 42f; // above footer block
                    Table sigTable = new Table(UnitValue.createPercentArray(new float[] { 1 }))
                            .setWidth(footerWidth)
                            .setFixedPosition(pageNo, footerLeft, sigBottom, footerWidth);
                    Cell sigCell = new Cell().setBorder(Border.NO_BORDER).setPadding(0)
                            .setTextAlignment(TextAlignment.RIGHT).setVerticalAlignment(VerticalAlignment.BOTTOM);
                    try {
                        Image sigImg = new Image(
                                ImageDataFactory.create(new ClassPathResource("images/sign.png").getURL()));
                        sigImg.setHeight(36);
                        sigImg.setHorizontalAlignment(HorizontalAlignment.RIGHT);
                        sigCell.add(sigImg);
                    } catch (Exception e) {
                        // ignore
                    }
                    sigCell.add(new Paragraph("Authorised Signatory").setFont(regularFont).setFontSize(9)
                            .setTextAlignment(TextAlignment.RIGHT));
                    sigTable.addCell(sigCell);
                    doc.add(sigTable);
                }

                // Footer: brand (center), address (center), full-width Red+Yellow line at
                // absolute bottom
                Table absoluteFooter = new Table(1)
                        .setWidth(footerWidth)
                        .setFixedPosition(pageNo, footerLeft, footerBottom, footerWidth);

                Cell containerCell = new Cell().setBorder(Border.NO_BORDER).setPadding(0);

                // Brand image - center
                Table centerTable = new Table(UnitValue.createPercentArray(new float[] { 1 }))
                        .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(2);
                Cell imgCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER);
                Image img = new Image(brandImageOriginal);
                img.scaleToFit(160, 20);
                img.setHorizontalAlignment(HorizontalAlignment.CENTER);
                imgCell.add(img);
                centerTable.addCell(imgCell);
                containerCell.add(centerTable);

                // Company address - center below image
                StringBuilder addr = new StringBuilder();
                if (cs.getAddress() != null && !cs.getAddress().isEmpty())
                    addr.append(cs.getAddress());
                if (cs.getCity() != null && !cs.getCity().isEmpty())
                    addr.append(addr.length() > 0 ? ", " : "").append(cs.getCity());
                if (cs.getPincode() != null && !cs.getPincode().isEmpty())
                    addr.append(addr.length() > 0 ? " - " : "").append(cs.getPincode());
                if (cs.getPhone() != null && !cs.getPhone().isEmpty())
                    addr.append(addr.length() > 0 ? ". Off : " : "").append(cs.getPhone());
                if (addr.length() > 0) {
                    containerCell.add(new Paragraph(addr.toString()).setFont(regularFont).setFontSize(7)
                            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
                }

                // Red & Yellow line - full width (100%) at absolute bottom
                Table footerBar = new Table(UnitValue.createPercentArray(new float[] { 80, 20 }))
                        .setWidth(UnitValue.createPercentValue(100));
                Cell redBar = new Cell().setBorder(Border.NO_BORDER)
                        .setBackgroundColor(new DeviceRgb(210, 50, 50)).setHeight(6f);
                Cell orangeBar = new Cell().setBorder(Border.NO_BORDER)
                        .setBackgroundColor(new DeviceRgb(255, 165, 0)).setHeight(6f);
                footerBar.addCell(redBar);
                footerBar.addCell(orangeBar);
                containerCell.add(footerBar);

                absoluteFooter.addCell(containerCell);
                doc.add(absoluteFooter);
            }
        } catch (Exception e) {
            log.error("Page decorations / footer failed (brand image, address, or fixed layout): {}", e.getMessage(),
                    e);
            throw new RuntimeException("PDF footer/page decoration failed", e);
        }
    }

    private CompanySettings getCompanySettings() {
        try {
            var s = companySettingsService.getSettings();
            return CompanySettings.builder()
                    .companyName(s.getCompanyName())
                    .address(s.getAddress())
                    .city(s.getCity())
                    .state(s.getState())
                    .pincode(s.getPincode())
                    .phone(s.getPhone())
                    .mobile(s.getMobile())
                    .email(s.getEmail())
                    .gstin(s.getGstin())
                    .bankAccountName(s.getBankAccountName())
                    .bankAccountNumber(s.getBankAccountNumber())
                    .bankName(s.getBankName())
                    .bankBranch(s.getBankBranch())
                    .bankIfscCode(s.getBankIfscCode())
                    .build();
        } catch (Exception e) {
            log.warn("Could not load company settings: {}", e.getMessage());
            return CompanySettings.builder().build();
        }
    }
}


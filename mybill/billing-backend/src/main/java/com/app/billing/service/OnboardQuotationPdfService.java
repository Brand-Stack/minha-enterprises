package com.app.billing.service;

import com.app.billing.dao.ZoneConfigurationRepository;
import com.app.billing.dto.OnboardQuotationDto;
import com.app.billing.dto.OnboardQuotationDto.OnboardSlabDto;
import com.app.billing.dto.ZoneRateConfigDto;
import com.app.billing.model.CompanySettings;
import com.app.billing.model.ZoneConfiguration;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardQuotationPdfService {

    private final CompanySettingsService companySettingsService;
    private final ZoneConfigurationRepository zoneConfigRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(220, 220, 220);
    private static final DeviceRgb SUB_HDR_BG = new DeviceRgb(238, 238, 238);
    private static final DeviceRgb SLAB_HDR_BG = new DeviceRgb(210, 225, 245);
    private static final DeviceRgb BORDER_CLR = new DeviceRgb(100, 100, 100);
    private static final DeviceRgb ZONE_BG = new DeviceRgb(248, 248, 248);
    private static final float PAGE_MARGIN = 8f;

    public byte[] generate(OnboardQuotationDto dto) {
        log.info("Generating Onboard Courier Quotation PDF for: {}", dto.getQuotationNumber());

        List<ZoneConfiguration> allZones = zoneConfigRepository.findActiveZones();
        List<String[]> s1Zones = allZones.stream()
                .filter(z -> "EXPRESS_SURFACE".equals(z.getZoneType()))
                .map(z -> new String[]{z.getId(), z.getZoneName()})
                .collect(Collectors.toList());

        List<String[]> s2Zones = allZones.stream()
                .filter(z -> "PRIORITY_SAFETY".equals(z.getZoneType()))
                .map(z -> new String[]{z.getId(), z.getZoneName()})
                .collect(Collectors.toList());

        List<OnboardSlabDto> selectedSlabs = dto.getSlabs() != null ?
                dto.getSlabs().stream()
                        .filter(s -> Boolean.TRUE.equals(s.getSelected()))
                        .collect(Collectors.toList()) :
                List.of();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));

            // Add thin page border event handler matching Courier Quotation
            pdfDoc.addEventHandler(com.itextpdf.kernel.events.PdfDocumentEvent.END_PAGE,
                    new com.itextpdf.kernel.events.IEventHandler() {
                        @Override
                        public void handleEvent(com.itextpdf.kernel.events.Event event) {
                            com.itextpdf.kernel.events.PdfDocumentEvent docEvent = (com.itextpdf.kernel.events.PdfDocumentEvent) event;
                            com.itextpdf.kernel.pdf.PdfPage page = docEvent.getPage();
                            com.itextpdf.kernel.geom.Rectangle pageSize = page.getPageSize();
                            com.itextpdf.kernel.pdf.canvas.PdfCanvas canvas = new com.itextpdf.kernel.pdf.canvas.PdfCanvas(
                                    page.newContentStreamBefore(), page.getResources(), pdfDoc);
                            canvas.setStrokeColor(new com.itextpdf.kernel.colors.DeviceRgb(150, 150, 150))
                                    .setLineWidth(1f)
                                    .rectangle(10f, 10f, pageSize.getWidth() - 20f, pageSize.getHeight() - 20f)
                                    .stroke();
                        }
                    });

            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(PAGE_MARGIN + 10f, PAGE_MARGIN + 10f, PAGE_MARGIN + 10f, PAGE_MARGIN + 10f);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            CompanySettings cs = getCompanySettings();

            addCompanyHeader(doc, cs, bold, regular);
            addMeta(doc, dto, bold, regular);

            doc.add(new Paragraph("ONBOARD QUOTATION FOR DOMESTIC COURIER SERVICE")
                    .setFont(bold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(1));

            if (selectedSlabs.isEmpty()) {
                Paragraph pEmpty = new Paragraph("No active slabs selected for this quotation.")
                        .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.CENTER).setMarginTop(20);
                doc.add(pEmpty);
            } else {
                for (OnboardSlabDto slab : selectedSlabs) {
                    // Slab Name Header Banner
                    Table slabHeader = new Table(UnitValue.createPercentArray(new float[]{1f}));
                    slabHeader.setWidth(UnitValue.createPercentValue(100));
                    slabHeader.setMarginTop(6);

                    Cell cSlabName = new Cell()
                            .add(new Paragraph("SLAB: " + (slab.getSlabName() != null ? slab.getSlabName().toUpperCase() : "SLAB"))
                                    .setFont(bold).setFontSize(9).setFontColor(new DeviceRgb(20, 40, 90)))
                            .setBackgroundColor(SLAB_HDR_BG)
                            .setBorder(new SolidBorder(BORDER_CLR, 0.75f))
                            .setPadding(3);
                    slabHeader.addCell(cSlabName);
                    doc.add(slabHeader);

                    Map<String, String> r = buildRateMap(slab.getZoneRates());

                    // Section 1 Table: Express + Surface
                    if (!s1Zones.isEmpty()) {
                        addSection1(doc, r, s1Zones, bold, regular);
                    }

                    // Section 2 Table: Priority + Safety Plus
                    if (!s2Zones.isEmpty()) {
                        doc.add(new Paragraph("").setMarginTop(1));
                        addSection2(doc, r, s2Zones, bold, regular);
                    }

                    // Section 3 Table: Standard Weight Slabs
                    List<ZoneRateConfigDto> standardRates = getStandardZoneRatesFromList(slab.getZoneRates());
                    if (!standardRates.isEmpty()) {
                        doc.add(new Paragraph("").setMarginTop(1));
                        addStandardSection(doc, standardRates, bold, regular);
                    }
                }
            }

            addCourierFooter(doc);
            addTermsAndConditionsPage(pdfDoc, doc, dto, bold, regular);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate Onboard Quotation PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Could not generate Onboard Quotation PDF: " + e.getMessage(), e);
        }
    }

    private void addCompanyHeader(Document doc, CompanySettings cs, PdfFont bold, PdfFont regular) {
        float[] w = { 20, 60, 20 };
        Table t = new Table(UnitValue.createPercentArray(w)).setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(2);

        Cell c1 = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE).setTextAlignment(TextAlignment.LEFT);
        try {
            ImageData d1 = ImageDataFactory.create(new ClassPathResource("images/minhaEnterprises.jpeg").getURL());
            Image img1 = new Image(d1);
            img1.setHeight(24);
            c1.add(img1);
        } catch (Exception e) {
        }
        t.addCell(c1);

        Cell c2 = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE).setTextAlignment(TextAlignment.CENTER);
        String name = cs.getCompanyName() != null ? cs.getCompanyName() : "";
        c2.add(new Paragraph(name).setFont(bold).setFontSize(10).setMarginBottom(0));

        StringBuilder addr = new StringBuilder();
        if (cs.getAddress() != null)
            addr.append(cs.getAddress());
        if (cs.getCity() != null) {
            if (addr.length() > 0)
                addr.append(", ");
            addr.append(cs.getCity());
        }
        if (cs.getPhone() != null)
            addr.append("  Off: ").append(cs.getPhone());
        if (addr.length() > 0) {
            c2.add(new Paragraph(addr.toString()).setFont(regular).setFontSize(5).setMarginBottom(0));
        }
        t.addCell(c2);

        Cell c3 = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE).setTextAlignment(TextAlignment.RIGHT);
        try {
            ImageData d3 = ImageDataFactory.create(new ClassPathResource("images/franchExpress.png").getURL());
            Image img3 = new Image(d3);
            img3.setHeight(24);
            c3.add(img3);
        } catch (Exception e) {
        }
        t.addCell(c3);

        doc.add(t);
        doc.add(new Paragraph().setBorderBottom(new SolidBorder(BORDER_CLR, 0.5f)).setMarginBottom(2));
    }

    private void addMeta(Document doc, OnboardQuotationDto dto, PdfFont bold, PdfFont regular) {
        Table meta = new Table(UnitValue.createPercentArray(new float[] { 20, 30, 20, 30 }))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(1);
        addMetaRow(meta, "Quotation No:", dto.getQuotationNumber(), bold, regular);
        addMetaRow(meta, "Customer:", dto.getCustomerName(), bold, regular);
        addMetaRow(meta, "Branch:", dto.getBranchName(), bold, regular);
        addMetaRow(meta, "Effective Date:",
                dto.getEffectiveDate() != null ? dto.getEffectiveDate().format(DATE_FMT) : "", bold, regular);
        addMetaRow(meta, "Valid Till:", dto.getValidTillDate() != null ? dto.getValidTillDate().format(DATE_FMT) : "",
                bold, regular);
        addMetaRow(meta, "Status:", dto.getStatus() != null ? dto.getStatus().name() : "", bold, regular);
        if (dto.getFuelChargePercentage() != null) {
            addMetaRow(meta, "Fuel Charge:", dto.getFuelChargePercentage() + "%", bold, regular);
        }
        if (dto.getFovCharges() != null) {
            addMetaRow(meta, "FOV Charges:", String.valueOf(dto.getFovCharges()), bold, regular);
        }
        if (dto.getRemarks() != null && !dto.getRemarks().isEmpty()) {
            addMetaRow(meta, "Remarks:", dto.getRemarks(), bold, regular);
        }
        doc.add(meta);
    }

    private void addMetaRow(Table t, String label, String value, PdfFont bold, PdfFont regular) {
        t.addCell(new Cell().add(new Paragraph(label != null ? label : "").setFont(bold).setFontSize(6))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(0.5f));
        t.addCell(new Cell().add(new Paragraph(value != null ? value : "").setFont(regular).setFontSize(6))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(0.5f));
    }

    private void addSection1(Document doc, Map<String, String> r, List<String[]> zones, PdfFont bold, PdfFont regular) {
        float[] w = { 24, 14, 16, 13, 14, 14 };
        Table t = new Table(UnitValue.createPercentArray(w)).setWidth(UnitValue.createPercentValue(100));

        t.addHeaderCell(spanCell("ZONE", 1, 2, bold, HEADER_BG, 7.5f, 2f));
        t.addHeaderCell(spanCell("Express Rate", 3, 1, bold, HEADER_BG, 7.5f, 2f));
        t.addHeaderCell(spanCell("Surface Rate", 1, 1, bold, HEADER_BG, 7.5f, 2f));
        t.addHeaderCell(spanCell("Surface Rate", 1, 1, bold, HEADER_BG, 7.5f, 2f));

        t.addHeaderCell(subHdr("First 250 Gms", bold, SUB_HDR_BG, 6.5f, 2f));
        t.addHeaderCell(subHdr("Every Add\n500 Gms\nupto 3 Kg", bold, SUB_HDR_BG, 6.5f, 2f));
        t.addHeaderCell(subHdr("Above 3 Kg\nPer Kg", bold, SUB_HDR_BG, 6.5f, 2f));
        t.addHeaderCell(subHdr("Above 10 Kg\nUpto 200 Kg", bold, SUB_HDR_BG, 6.5f, 2f));
        t.addHeaderCell(subHdr("Above 200 Kg\nUpto 500 Kg", bold, SUB_HDR_BG, 6.5f, 2f));

        for (String[] zone : zones) {
            String k = zone[0];
            t.addCell(zoneCell(zone[1], regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_E1", ""), regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_E2", ""), regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_E3", ""), regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_S1", ""), regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_S2", ""), regular, 7f, 2f));
        }

        doc.add(t);
    }

    private void addSection2(Document doc, Map<String, String> r, List<String[]> zones, PdfFont bold, PdfFont regular) {
        float[] w = { 20, 14, 14, 13, 20, 14 };
        Table t = new Table(UnitValue.createPercentArray(w)).setWidth(UnitValue.createPercentValue(100));

        t.addHeaderCell(spanCell("ZONE", 1, 2, bold, HEADER_BG, 7.5f, 2f));
        t.addHeaderCell(spanCell("Safety(Priority)", 3, 1, bold, HEADER_BG, 7.5f, 2f));
        t.addHeaderCell(spanCell("Surface", 2, 1, bold, HEADER_BG, 7.5f, 2f));

        t.addHeaderCell(subHdr("First 250 Gms", bold, SUB_HDR_BG, 6.5f, 2f));
        t.addHeaderCell(subHdr("Every Add\n500 Gms\nupto 3 Kg", bold, SUB_HDR_BG, 6.5f, 2f));
        t.addHeaderCell(subHdr("Above 3 Kg\nPer Kg", bold, SUB_HDR_BG, 6.5f, 2f));
        t.addHeaderCell(subHdr("Above 10 Kg\nUpto 200 Kg", bold, SUB_HDR_BG, 6.5f, 2f));
        t.addHeaderCell(subHdr("Above 200 Kg\nUpto 500 Kg", bold, SUB_HDR_BG, 6.5f, 2f));

        for (String[] zone : zones) {
            String k = zone[0];
            t.addCell(zoneCell(zone[1], regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_PC1", ""), regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_PC2", ""), regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_PC3", ""), regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_SP1", ""), regular, 7f, 2f));
            t.addCell(rateCell(r.getOrDefault(k + "_SP2", ""), regular, 7f, 2f));
        }

        doc.add(t);
    }

    private List<ZoneRateConfigDto> getStandardZoneRatesFromList(List<ZoneRateConfigDto> list) {
        if (list == null) return List.of();
        return list.stream().filter(this::hasStandardRates).collect(Collectors.toList());
    }

    private boolean hasStandardRates(ZoneRateConfigDto zr) {
        if (zr.getStandardRate1Kg() != null && zr.getStandardRate1Kg() > 0) return true;
        if (zr.getStandardRate2Kg() != null && zr.getStandardRate2Kg() > 0) return true;
        if (zr.getStandardRate3Kg() != null && zr.getStandardRate3Kg() > 0) return true;
        if (zr.getStandardRate4Kg() != null && zr.getStandardRate4Kg() > 0) return true;
        if (zr.getStandardRate5Kg() != null && zr.getStandardRate5Kg() > 0) return true;
        if (zr.getStandardPerKgAbove3() != null && zr.getStandardPerKgAbove3() > 0) return true;
        return false;
    }

    private void addStandardSection(Document doc, List<ZoneRateConfigDto> standardZoneRates, PdfFont bold, PdfFont regular) {
        float[] w = { 20, 13, 13, 13, 13, 13, 15 };
        Table t = new Table(UnitValue.createPercentArray(w)).setWidth(UnitValue.createPercentValue(100));

        t.addHeaderCell(cell("ZONE", bold, HEADER_BG));
        t.addHeaderCell(cell("1 Kg", bold, HEADER_BG));
        t.addHeaderCell(cell("2 Kg", bold, HEADER_BG));
        t.addHeaderCell(cell("3 Kg", bold, HEADER_BG));
        t.addHeaderCell(cell("4 Kg", bold, HEADER_BG));
        t.addHeaderCell(cell("5 Kg", bold, HEADER_BG));
        t.addHeaderCell(cell("Above 5 Kg\nPer Kg", bold, HEADER_BG));

        for (ZoneRateConfigDto zr : standardZoneRates) {
            String zoneName = (zr.getZoneName() != null && !zr.getZoneName().isBlank())
                    ? zr.getZoneName()
                    : "Standard";
            t.addCell(zoneCell(zoneName, regular, 7f, 2f));
            t.addCell(rateCell(formatRate(zr.getStandardRate1Kg()), regular, 7f, 2f));
            t.addCell(rateCell(formatRate(zr.getStandardRate2Kg()), regular, 7f, 2f));
            t.addCell(rateCell(formatRate(zr.getStandardRate3Kg()), regular, 7f, 2f));
            t.addCell(rateCell(formatRate(zr.getStandardRate4Kg()), regular, 7f, 2f));
            t.addCell(rateCell(formatRate(zr.getStandardRate5Kg()), regular, 7f, 2f));
            t.addCell(rateCell(formatRate(zr.getStandardPerKgAbove3()), regular, 7f, 2f));
        }

        doc.add(t);
    }

    private Cell spanCell(String text, int colspan, int rowspan, PdfFont f, DeviceRgb bg, float fontSize, float padding) {
        return new Cell(rowspan, colspan)
                .add(new Paragraph(text).setFont(f).setFontSize(fontSize).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(bg).setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(padding).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell subHdr(String text, PdfFont f, DeviceRgb bg, float subHeaderFontSize, float padding) {
        return new Cell()
                .add(new Paragraph(text).setFont(f).setFontSize(subHeaderFontSize).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(bg).setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(padding).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell zoneCell(String text, PdfFont f, float fontSize, float padding) {
        return new Cell()
                .add(new Paragraph(text).setFont(f).setFontSize(fontSize).setTextAlignment(TextAlignment.LEFT))
                .setBackgroundColor(ZONE_BG).setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(padding).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell rateCell(String value, PdfFont f, float fontSize, float padding) {
        String display = (value == null || value.trim().isEmpty()) ? "—" : "Rs. " + value.trim();
        return new Cell()
                .add(new Paragraph(display).setFont(f).setFontSize(fontSize).setTextAlignment(TextAlignment.CENTER))
                .setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(padding).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell cell(String text, PdfFont f, DeviceRgb bg) {
        Cell c = new Cell().add(new Paragraph(text != null ? text : "").setFont(f).setFontSize(7.5f))
                .setBorder(new SolidBorder(BORDER_CLR, 0.5f)).setPadding(2f)
                .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (bg != null) c.setBackgroundColor(bg);
        return c;
    }

    private static String formatRate(Double value) {
        if (value == null) return "";
        return String.valueOf(value);
    }

    private void addCourierFooter(Document doc) {
        try {
            float footerLeft = PAGE_MARGIN + 10f;
            float footerBottom = PAGE_MARGIN + 5f;
            float footerWidth = PageSize.A4.getWidth() - (2 * footerLeft);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            CompanySettings cs = getCompanySettings();

            Table absoluteFooter = new Table(UnitValue.createPercentArray(new float[] { 1 }))
                    .setWidth(footerWidth)
                    .setFixedPosition(footerLeft, footerBottom, footerWidth);

            Cell containerCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(0);

            // Brand image - center
            Table imgTable = new Table(UnitValue.createPercentArray(new float[] { 1 }))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(2);
            Cell imgCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER).setPadding(2);
            try {
                ImageData data = ImageDataFactory.create(new ClassPathResource("images/brandPartner.png").getURL());
                Image img = new Image(data);
                img.setHeight(20f);
                img.setHorizontalAlignment(HorizontalAlignment.CENTER);
                imgCell.add(img);
            } catch (Exception e) {
                log.debug("brandPartner.png not found: {}", e.getMessage());
                imgCell.add(new Paragraph("Brand Partners").setFont(bold).setFontSize(8)
                        .setTextAlignment(TextAlignment.CENTER));
            }
            imgTable.addCell(imgCell);
            containerCell.add(imgTable);

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
                containerCell.add(new Paragraph(addr.toString()).setFont(regular).setFontSize(6)
                        .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
            }

            // Red & Yellow line - full width (100%) at bottom
            Table footerBar = new Table(UnitValue.createPercentArray(new float[] { 80, 20 }))
                    .setWidth(UnitValue.createPercentValue(100));
            Cell redBar = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBackgroundColor(new DeviceRgb(210, 50, 50)).setHeight(6f);
            Cell orangeBar = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBackgroundColor(new DeviceRgb(255, 165, 0)).setHeight(6f);
            footerBar.addCell(redBar);
            footerBar.addCell(orangeBar);
            containerCell.add(footerBar);

            absoluteFooter.addCell(containerCell);
            doc.add(absoluteFooter);
        } catch (Exception e) {
            log.warn("Error adding courier footer", e);
        }
    }

    private void addTermsAndConditionsPage(PdfDocument pdfDoc, Document doc, OnboardQuotationDto dto, PdfFont bold,
                                           PdfFont regular) {
        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

        // Company branding header replacement
        try {
            ImageData logoData = ImageDataFactory
                    .create(new ClassPathResource("images/minhaEnterprisesFullLogo.jpeg").getURL());
            Image logo = new Image(logoData);
            logo.scaleToFit(220, 80);
            logo.setHorizontalAlignment(HorizontalAlignment.CENTER);
            logo.setMarginBottom(15);
            doc.add(logo);
        } catch (Exception e) {
            log.debug("Terms page full logo not found: {}", e.getMessage());
        }

        // Heading
        doc.add(new Paragraph("GENERAL TERMS AND CONDITIONS")
                .setFont(bold).setFontSize(11).setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(18).setUnderline(1.0f, -2f));

        // Centered content block
        Table contentBlock = new Table(UnitValue.createPercentArray(new float[] { 1 }))
                .setWidth(UnitValue.createPercentValue(96))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
        Cell contentCell = new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(0)
                .setTextAlignment(TextAlignment.LEFT);

        float fontSize = 9f;
        float spacing = 8f;

        contentCell.add(termItem(bold, regular, "1.", "PAYMENTS:",
                "Our monthly-computerized bills will be submitted to you in the second week of every month and you are requested to settle the payment within 30 days from the date of receipt of Invoice.",
                fontSize, spacing));
        contentCell.add(termItem(bold, regular, "2.", "DECLARATION FORM ON-DOCS / PARCELS:",
                "You are requested to provide us with 3copies of Declaration, indicating the contents of your parcels and consignments for the purpose of security check, Octroi and Sales Tax Dept. If it is not accompanied with proper documents, we are no responsible for delayed delivery.",
                fontSize, spacing));
        contentCell.add(termItem(bold, regular, "3.", "FOR PROMPT DELIVERIES:",
                "All your documents should be handed over to our staff before our specified cutoff timings.", fontSize,
                spacing));
        contentCell.add(termItem(bold, regular, "4.", "SURFACE CARGO:",
                "For Surface Cargo 10 kgs will be minimum chargeable rate.", fontSize, spacing));
        contentCell.add(termItem(bold, regular, "5.", "PROOF OF DELIVERIES (i.e. P.O.D's):",
                "POD'S can be made available on your request should be made within 30 days of the booking of the consignment.",
                fontSize, spacing));
        contentCell.add(termItem(bold, regular, "6.", "DELIVERY - TIME SCHEDULE:",
                "Schedule time for delivery of documents to major cities & towns in Tamil Nadu within 24/48 hours and that for all other places in India deliveries will be made within 24/48/72hours (By Air Transit).",
                fontSize, spacing));
        contentCell.add(termItem(bold, regular, "7.", "EXTRA DELIVERY LOCATION:",
                "EDL charges for Every 50 Kms additional courier charges of Rs.550/-", fontSize, spacing));
        contentCell.add(termItem(bold, regular, "8.", "VALIDITY OF THIS CONTRACT:",
                "This contract is Valid for 12 Months from the date of contract.", fontSize, spacing));

        String fovPct = formatPercent(dto.getFovCharges());
        contentCell.add(termItem(bold, regular, "9.", "FOV CHARGES:",
                "FOV charges " + fovPct + " of Cargo Value will be applicable if necessary.", fontSize, spacing));

        contentCell.add(termItem(bold, regular, "10.", "WEIGHT CALCULATION FOR LARGE LIGHT WEIGHT SHIPMENTS:",
                "We comply with IATA regulations and charge the greater of either the volumetric weight or the actual physical weight. To calculate the volumetric weight, multiply in centimeters the length, breadth, height of your shipment and divide the total by 5000.",
                fontSize, spacing));
        contentCell.add(termItem(bold, regular, "11.", "INSURANCE:",
                "You are requested to make your own arrangements to insure your consignments, if it exceeds the value of Rs.5000/-.",
                fontSize, spacing));

        String fuelPct = formatPercent(dto.getFuelChargePercentage());
        contentCell.add(termItem(bold, regular, "12.", "FUEL CHARGES:",
                "Fuel Charges will be charged " + fuelPct + " on total amount of courier charges.", fontSize, spacing));

        contentBlock.addCell(contentCell);
        doc.add(contentBlock);

        // Footer Section - Absolute Position at Bottom
        float footerLeft = PAGE_MARGIN + 10f;
        float footerBottom = PAGE_MARGIN + 5f;
        float footerWidth = PageSize.A4.getWidth() - (2 * footerLeft);

        Table absoluteFooter = new Table(1)
                .setWidth(footerWidth)
                .setFixedPosition(footerLeft, footerBottom, footerWidth);

        Cell containerCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(0);

        String customerName = dto.getCustomerName() != null ? dto.getCustomerName() : "CUSTOMER";
        CompanySettings cs = getCompanySettings();
        String companyName = cs.getCompanyName() != null && !cs.getCompanyName().isEmpty() ? cs.getCompanyName()
                : "Company";

        Table sigTable = new Table(UnitValue.createPercentArray(new float[] { 50, 50 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(8f);

        Cell leftSigCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setTextAlignment(TextAlignment.LEFT)
                .setVerticalAlignment(VerticalAlignment.BOTTOM);
        leftSigCell.add(new Paragraph("For " + customerName)
                .setFont(bold).setFontSize(9).setMarginTop(2));
        sigTable.addCell(leftSigCell);

        Cell rightSigCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT)
                .setVerticalAlignment(VerticalAlignment.BOTTOM);
        try {
            ImageData signData = ImageDataFactory.create(new ClassPathResource("images/sign.png").getURL());
            Image signImg = new Image(signData);
            signImg.scaleToFit(80, 40);
            signImg.setHorizontalAlignment(HorizontalAlignment.RIGHT);
            rightSigCell.add(signImg);
        } catch (Exception e) {
            rightSigCell.add(new Paragraph("\n\n\n"));
        }
        rightSigCell.add(new Paragraph("For " + companyName)
                .setFont(bold).setFontSize(9).setMarginTop(2).setTextAlignment(TextAlignment.RIGHT));
        sigTable.addCell(rightSigCell);
        containerCell.add(sigTable);

        // Brand image (center)
        try {
            ImageData data = ImageDataFactory.create(new ClassPathResource("images/brandPartner.png").getURL());
            Image img = new Image(data);
            img.setHeight(20f);
            img.setHorizontalAlignment(HorizontalAlignment.CENTER);
            Table imgTable = new Table(UnitValue.createPercentArray(new float[] { 1 }))
                    .setWidth(UnitValue.createPercentValue(100));
            Cell imgCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER).setPadding(2);
            imgCell.add(img);
            imgTable.addCell(imgCell);
            containerCell.add(imgTable);
        } catch (Exception e) {
            log.debug("brandPartner.png not found on terms page: {}", e.getMessage());
        }

        // Company address (center)
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
            containerCell.add(new Paragraph(addr.toString())
                    .setFont(bold).setFontSize(8).setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
        }

        // Red & Yellow line
        Table footerBar = new Table(UnitValue.createPercentArray(new float[] { 80, 20 }))
                .setWidth(UnitValue.createPercentValue(100));
        Cell redBar = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBackgroundColor(new DeviceRgb(210, 50, 50)).setHeight(6f);
        Cell orangeBar = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBackgroundColor(new DeviceRgb(255, 165, 0)).setHeight(6f);
        footerBar.addCell(redBar);
        footerBar.addCell(orangeBar);
        containerCell.add(footerBar);

        absoluteFooter.addCell(containerCell);
        doc.add(absoluteFooter);
    }

    private Paragraph termItem(PdfFont bold, PdfFont regular, String number, String title, String body, float fontSize,
                              float spacing) {
        Paragraph p = new Paragraph();
        p.add(new com.itextpdf.layout.element.Text(number).setFont(regular).setFontSize(fontSize));
        p.add(new com.itextpdf.layout.element.Text("  " + title).setFont(bold).setFontSize(fontSize));
        p.add(new com.itextpdf.layout.element.Text(" " + body).setFont(regular).setFontSize(fontSize));
        p.setTextAlignment(TextAlignment.LEFT)
                .setMarginBottom(spacing)
                .setMarginLeft(22f)
                .setFirstLineIndent(-22f)
                .setMultipliedLeading(1.5f);
        return p;
    }

    private String formatPercent(Double value) {
        if (value == null)
            return "—%";
        double rounded = Math.round(value * 100.0) / 100.0;
        if (Math.floor(rounded) == rounded) {
            return ((long) rounded) + "%";
        }
        return String.valueOf(rounded) + "%";
    }

    private Map<String, String> buildRateMap(List<ZoneRateConfigDto> zoneRates) {
        Map<String, String> r = new HashMap<>();
        if (zoneRates != null) {
            zoneRates.forEach(zr -> {
                String id = zr.getZoneId();
                if (zr.getExpressBaseRate() != null) r.put(id + "_E1", zr.getExpressBaseRate().toString());
                if (zr.getExpressIncrementalRate() != null) r.put(id + "_E2", zr.getExpressIncrementalRate().toString());
                if (zr.getExpressPerKgRate() != null) r.put(id + "_E3", zr.getExpressPerKgRate().toString());
                if (zr.getSurfaceSlab1Rate() != null) r.put(id + "_S1", zr.getSurfaceSlab1Rate().toString());
                if (zr.getSurfaceSlab2Rate() != null) r.put(id + "_S2", zr.getSurfaceSlab2Rate().toString());

                if (zr.getExpressBaseRate() != null) r.put(id + "_PC1", zr.getExpressBaseRate().toString());
                if (zr.getExpressIncrementalRate() != null) r.put(id + "_PC2", zr.getExpressIncrementalRate().toString());
                if (zr.getExpressPerKgRate() != null) r.put(id + "_PC3", zr.getExpressPerKgRate().toString());
                if (zr.getSurfaceSlab1Rate() != null) r.put(id + "_SP1", zr.getSurfaceSlab1Rate().toString());
                if (zr.getSurfaceSlab2Rate() != null) r.put(id + "_SP2", zr.getSurfaceSlab2Rate().toString());

                if (zr.getStandardRate1Kg() != null) r.put(id + "_STD_1", zr.getStandardRate1Kg().toString());
                if (zr.getStandardRate2Kg() != null) r.put(id + "_STD_2", zr.getStandardRate2Kg().toString());
                if (zr.getStandardRate3Kg() != null) r.put(id + "_STD_3", zr.getStandardRate3Kg().toString());
                if (zr.getStandardRate4Kg() != null) r.put(id + "_STD_4", zr.getStandardRate4Kg().toString());
                if (zr.getStandardRate5Kg() != null) r.put(id + "_STD_5", zr.getStandardRate5Kg().toString());
                if (zr.getStandardPerKgAbove3() != null) r.put(id + "_STD_ABOVE3", zr.getStandardPerKgAbove3().toString());
            });
        }
        return r;
    }

    private CompanySettings getCompanySettings() {
        try {
            var s = companySettingsService.getSettings();
            return CompanySettings.builder()
                    .companyName(s.getCompanyName())
                    .address(s.getAddress())
                    .city(s.getCity())
                    .phone(s.getPhone())
                    .email(s.getEmail())
                    .build();
        } catch (Exception e) {
            log.warn("Could not load company settings: {}", e.getMessage());
            return CompanySettings.builder().build();
        }
    }
}

package com.app.billing.service;

import com.app.billing.dto.CourierQuotationDto;
import com.app.billing.dto.ZoneRateConfigDto;
import com.app.billing.model.CompanySettings;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import org.springframework.core.io.ClassPathResource;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.element.AreaBreak;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.app.billing.model.ZoneConfiguration;
import com.app.billing.dao.ZoneConfigurationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierQuotationPdfService {

    private final CompanySettingsService companySettingsService;
    private final ZoneConfigurationRepository zoneConfigRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(220, 220, 220);
    private static final DeviceRgb SUB_HDR_BG = new DeviceRgb(238, 238, 238);
    private static final DeviceRgb BORDER_CLR = new DeviceRgb(100, 100, 100);
    private static final DeviceRgb ZONE_BG = new DeviceRgb(248, 248, 248);
    private static final float PAGE_MARGIN = 8f;

    public byte[] generate(CourierQuotationDto dto) {
        log.info("Generating fixed-structure courier quotation PDF for: {}", dto.getQuotationNumber());

        Map<String, String> r = new java.util.HashMap<>();
        if (dto.getZoneRates() != null) {
            dto.getZoneRates().forEach(zr -> {
                String id = zr.getZoneId();
                if (zr.getExpressBaseRate() != null) {
                    r.put(id + "_E1", zr.getExpressBaseRate().toString());
                    r.put(id + "_PC1", zr.getExpressBaseRate().toString());
                }
                if (zr.getExpressIncrementalRate() != null) {
                    r.put(id + "_E2", zr.getExpressIncrementalRate().toString());
                    r.put(id + "_PC2", zr.getExpressIncrementalRate().toString());
                }
                if (zr.getExpressPerKgRate() != null) {
                    r.put(id + "_E3", zr.getExpressPerKgRate().toString());
                    r.put(id + "_PC3", zr.getExpressPerKgRate().toString());
                }
                if (zr.getSurfaceSlab1Rate() != null) {
                    r.put(id + "_S1", zr.getSurfaceSlab1Rate().toString());
                    r.put(id + "_SP1", zr.getSurfaceSlab1Rate().toString());
                }
                if (zr.getSurfaceSlab2Rate() != null) {
                    r.put(id + "_S2", zr.getSurfaceSlab2Rate().toString());
                    r.put(id + "_SP2", zr.getSurfaceSlab2Rate().toString());
                }
            });
        }

        List<ZoneConfiguration> allZones = zoneConfigRepository.findActiveZones();
        List<String[]> s1Zones = allZones.stream()
                .filter(z -> "EXPRESS_SURFACE".equals(z.getZoneType()))
                .map(z -> new String[] { z.getId(), z.getZoneName() })
                .collect(java.util.stream.Collectors.toList());

        List<String[]> s2Zones = allZones.stream()
                .filter(z -> "PRIORITY_SAFETY".equals(z.getZoneType()))
                .map(z -> new String[] { z.getId(), z.getZoneName() })
                .collect(java.util.stream.Collectors.toList());
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));

            // Add a thin, professional border to all pages
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
            PdfFont rateFont = createUnicodeFontForRupee(regular);
            boolean useRupeeSymbol = (rateFont != regular);

            CompanySettings cs = getCompanySettings();

            addCompanyHeader(doc, cs, bold, regular);
            addMeta(doc, dto, bold, regular);

            doc.add(new Paragraph("QUOTATION FOR DOMESTIC COURIER SERVICE")
                    .setFont(bold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(1));

            addSection1(doc, r, s1Zones, bold, regular, rateFont, useRupeeSymbol);

            doc.add(new Paragraph("").setMarginTop(1));
            addSection2(doc, r, s2Zones, bold, regular, rateFont, useRupeeSymbol);

            List<ZoneRateConfigDto> standardZoneRates = getStandardZoneRatesFromDto(dto);
            if (!standardZoneRates.isEmpty()) {
                doc.add(new Paragraph("").setMarginTop(1));
                addStandardSection(doc, standardZoneRates, bold, regular, rateFont, useRupeeSymbol);
            }

            addCourierFooter(doc);

            addTermsAndConditionsPage(pdfDoc, doc, dto, bold, regular);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating courier quotation PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate courier quotation PDF", e);
        }
    }

    public byte[] generateReportPdf(List<CourierQuotationDto> list, LocalDate startDate, LocalDate endDate) {
        log.info("Generating quotation report PDF with {} quotations", list.size());
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            CompanySettings cs = getCompanySettings();
            String companyName = cs.getCompanyName() != null ? cs.getCompanyName() : "";
            doc.add(new Paragraph("Quotation Report").setFont(bold).setFontSize(14).setMarginBottom(4));
            doc.add(new Paragraph(companyName).setFont(regular).setFontSize(10).setMarginBottom(2));
            String dateRange = (startDate != null ? startDate.format(DATE_FMT) : "All") + " to "
                    + (endDate != null ? endDate.format(DATE_FMT) : "All");
            doc.add(new Paragraph("Period: " + dateRange).setFont(regular).setFontSize(9).setMarginBottom(12));

            float[] w = { 18, 25, 18, 12, 12, 12 };
            Table t = new Table(UnitValue.createPercentArray(w)).setWidth(UnitValue.createPercentValue(100));
            t.addHeaderCell(cell("Quotation No", bold, HEADER_BG));
            t.addHeaderCell(cell("Customer", bold, HEADER_BG));
            t.addHeaderCell(cell("Branch", bold, HEADER_BG));
            t.addHeaderCell(cell("Effective", bold, HEADER_BG));
            t.addHeaderCell(cell("Valid Till", bold, HEADER_BG));
            t.addHeaderCell(cell("Status", bold, HEADER_BG));

            for (CourierQuotationDto d : list) {
                t.addCell(cell(d.getQuotationNumber() != null ? d.getQuotationNumber() : "", regular, null));
                t.addCell(cell(d.getCustomerName() != null ? d.getCustomerName() : "", regular, null));
                t.addCell(cell(d.getBranchName() != null ? d.getBranchName() : "", regular, null));
                t.addCell(
                        cell(d.getEffectiveDate() != null ? d.getEffectiveDate().format(DATE_FMT) : "", regular, null));
                t.addCell(
                        cell(d.getValidTillDate() != null ? d.getValidTillDate().format(DATE_FMT) : "", regular, null));
                t.addCell(cell(d.getStatus() != null ? d.getStatus().name() : "", regular, null));
            }

            doc.add(t);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating quotation report PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate report PDF", e);
        }
    }

    private Cell cell(String text, PdfFont f, DeviceRgb bg) {
        Cell c = new Cell().add(new Paragraph(text != null ? text : "").setFont(f).setFontSize(8))
                .setBorder(new SolidBorder(BORDER_CLR, 0.5f)).setPadding(4);
        if (bg != null)
            c.setBackgroundColor(bg);
        return c;
    }

    private PdfFont createUnicodeFontForRupee(PdfFont fallback) {
        String[] fontPaths = { "fonts/FreeSans.ttf", "fonts/NotoSans-Regular.ttf", "fonts/DejaVuSans.ttf" };
        for (String path : fontPaths) {
            try (InputStream is = new ClassPathResource(path).getInputStream()) {
                byte[] fontBytes = is.readAllBytes();
                return PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            } catch (Exception e) {
            }
        }
        log.warn("No Unicode font found in fonts/. Add FreeSans.ttf for ₹ symbol. Using Rs. fallback.");
        return fallback;
    }

    private void addSection1(Document doc, Map<String, String> r, List<String[]> zones, PdfFont bold, PdfFont regular,
            PdfFont rateFont, boolean useRupeeSymbol) {
        float[] w = { 24, 14, 16, 13, 14, 14 };
        Table t = new Table(UnitValue.createPercentArray(w)).setWidth(UnitValue.createPercentValue(100));

        t.addHeaderCell(spanCell("ZONE", 1, 2, bold, HEADER_BG));
        t.addHeaderCell(spanCell("Express Rate", 3, 1, bold, HEADER_BG));
        t.addHeaderCell(spanCell("Surface Rate", 1, 1, bold, HEADER_BG));
        t.addHeaderCell(spanCell("Surface Rate", 1, 1, bold, HEADER_BG));

        t.addHeaderCell(subHdr("First 250 Gms", bold, SUB_HDR_BG));
        t.addHeaderCell(subHdr("Every Add\n500 Gms\nupto 3 Kg", bold, SUB_HDR_BG));
        t.addHeaderCell(subHdr("Above 3 Kg\nPer Kg", bold, SUB_HDR_BG));
        t.addHeaderCell(subHdr("Above 10 Kg\nUpto 200 Kg", bold, SUB_HDR_BG));
        t.addHeaderCell(subHdr("Above 200 Kg\nUpto 500 Kg", bold, SUB_HDR_BG));

        for (String[] zone : zones) {
            String k = zone[0];
            t.addCell(zoneCell(zone[1], regular));
            t.addCell(rateCell(r.getOrDefault(k + "_E1", ""), rateFont, useRupeeSymbol));
            t.addCell(rateCell(r.getOrDefault(k + "_E2", ""), rateFont, useRupeeSymbol));
            t.addCell(rateCell(r.getOrDefault(k + "_E3", ""), rateFont, useRupeeSymbol));
            t.addCell(rateCell(r.getOrDefault(k + "_S1", ""), rateFont, useRupeeSymbol));
            t.addCell(rateCell(r.getOrDefault(k + "_S2", ""), rateFont, useRupeeSymbol));
        }

        doc.add(t);
    }

    private void addSection2(Document doc, Map<String, String> r, List<String[]> zones, PdfFont bold, PdfFont regular,
            PdfFont rateFont, boolean useRupeeSymbol) {
        float[] w = { 20, 14, 14, 13, 20, 14 };
        Table t = new Table(UnitValue.createPercentArray(w)).setWidth(UnitValue.createPercentValue(100));

        t.addHeaderCell(spanCell("ZONE", 1, 2, bold, HEADER_BG));
        t.addHeaderCell(spanCell("Safety(Priority)", 3, 1, bold, HEADER_BG));
        t.addHeaderCell(spanCell("Surface", 2, 1, bold, HEADER_BG));

        t.addHeaderCell(subHdr("First 250 Gms", bold, SUB_HDR_BG));
        t.addHeaderCell(subHdr("Every Add\n500 Gms\nupto 3 Kg", bold, SUB_HDR_BG));
        t.addHeaderCell(subHdr("Above 3 Kg\nPer Kg", bold, SUB_HDR_BG));
        t.addHeaderCell(subHdr("Above 10 Kg\nUpto 200 Kg", bold, SUB_HDR_BG));
        t.addHeaderCell(subHdr("Above 200 Kg\nUpto 500 Kg", bold, SUB_HDR_BG));

        for (String[] zone : zones) {
            String k = zone[0];
            t.addCell(zoneCell(zone[1], regular));
            t.addCell(rateCell(r.getOrDefault(k + "_PC1", ""), rateFont, useRupeeSymbol));
            t.addCell(rateCell(r.getOrDefault(k + "_PC2", ""), rateFont, useRupeeSymbol));
            t.addCell(rateCell(r.getOrDefault(k + "_PC3", ""), rateFont, useRupeeSymbol));
            t.addCell(rateCell(r.getOrDefault(k + "_SP1", ""), rateFont, useRupeeSymbol));
            t.addCell(rateCell(r.getOrDefault(k + "_SP2", ""), rateFont, useRupeeSymbol));
        }

        doc.add(t);
    }

    /**
     * Zones from zoneRates that have Standard rate values (new 4 slabs or legacy 2
     * fields).
     */
    private List<ZoneRateConfigDto> getStandardZoneRatesFromDto(CourierQuotationDto dto) {
        if (dto.getZoneRates() == null)
            return java.util.Collections.emptyList();
        return dto.getZoneRates().stream()
                .filter(zr -> hasStandardRates(zr))
                .collect(java.util.stream.Collectors.toList());
    }

    private static boolean hasStandardRates(ZoneRateConfigDto zr) {
        if (zr.getStandardRate1Kg() != null && zr.getStandardRate1Kg() > 0)
            return true;
        if (zr.getStandardRate2Kg() != null && zr.getStandardRate2Kg() > 0)
            return true;
        if (zr.getStandardRate3Kg() != null && zr.getStandardRate3Kg() > 0)
            return true;
        if (zr.getStandardRate4Kg() != null && zr.getStandardRate4Kg() > 0)
            return true;
        if (zr.getStandardRate5Kg() != null && zr.getStandardRate5Kg() > 0)
            return true;
        if (zr.getStandardPerKgAbove3() != null && zr.getStandardPerKgAbove3() > 0)
            return true;
        if (zr.getStandardBaseRate3Kg() != null && zr.getStandardBaseRate3Kg() > 0)
            return true;
        if (zr.getStandardAdditionalPerKg() != null && zr.getStandardAdditionalPerKg() > 0)
            return true;
        return false;
    }

    private void addStandardSection(Document doc, List<ZoneRateConfigDto> standardZoneRates, PdfFont bold,
            PdfFont regular, PdfFont rateFont, boolean useRupeeSymbol) {
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
            t.addCell(zoneCell(zoneName, regular));
            t.addCell(rateCell(formatRate(zr.getStandardRate1Kg()), rateFont, useRupeeSymbol));
            t.addCell(rateCell(formatRate(zr.getStandardRate2Kg()), rateFont, useRupeeSymbol));
            t.addCell(rateCell(formatRate(zr.getStandardRate3Kg()), rateFont, useRupeeSymbol));
            t.addCell(rateCell(formatRate(zr.getStandardRate4Kg()), rateFont, useRupeeSymbol));
            t.addCell(rateCell(formatRate(zr.getStandardRate5Kg()), rateFont, useRupeeSymbol));
            t.addCell(rateCell(formatRate(zr.getStandardPerKgAbove3()), rateFont, useRupeeSymbol));
        }

        doc.add(t);
    }

    private static String formatRate(Double value) {
        if (value == null)
            return "";
        return String.valueOf(value);
    }

    private Cell spanCell(String text, int colspan, int rowspan, PdfFont f, DeviceRgb bg) {
        return new Cell(rowspan, colspan)
                .add(new Paragraph(text).setFont(f).setFontSize(6).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(bg).setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(1).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell subHdr(String text, PdfFont f, DeviceRgb bg) {
        return new Cell()
                .add(new Paragraph(text).setFont(f).setFontSize(5).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(bg).setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(1).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell zoneCell(String text, PdfFont f) {
        return new Cell()
                .add(new Paragraph(text).setFont(f).setFontSize(6).setTextAlignment(TextAlignment.LEFT))
                .setBackgroundColor(ZONE_BG).setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(1).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static final String RUPEE = "\u20B9";
    private static final String RUPEE_FALLBACK = "Rs.";

    private Cell rateCell(String value, PdfFont f, boolean useRupeeSymbol) {
        String display;
        if (value == null || value.trim().isEmpty()) {
            display = "";
        } else {
            String num = value.trim();
            display = (useRupeeSymbol ? RUPEE : RUPEE_FALLBACK) + " " + num;
        }
        return new Cell()
                .add(new Paragraph(display).setFont(f).setFontSize(6).setTextAlignment(TextAlignment.CENTER))
                .setBorder(new SolidBorder(BORDER_CLR, 0.5f))
                .setPadding(1).setVerticalAlignment(VerticalAlignment.MIDDLE);
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

    /**
     * First page: fixed-position footer at absolute bottom. Brand + address
     * (center), full-width Red+Yellow line.
     */
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

    private void addMeta(Document doc, CourierQuotationDto dto, PdfFont bold, PdfFont regular) {
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

    private void addTermsAndConditionsPage(PdfDocument pdfDoc, Document doc, CourierQuotationDto dto, PdfFont bold,
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

        // Footer Section - Absolute Position at Bottom: Signatures (left/right) then
        // Brand Image, Company Address, Red & Yellow line
        float footerLeft = PAGE_MARGIN + 10f;
        float footerBottom = PAGE_MARGIN + 5f;
        float footerWidth = PageSize.A4.getWidth() - (2 * footerLeft);

        Table absoluteFooter = new Table(1)
                .setWidth(footerWidth)
                .setFixedPosition(footerLeft, footerBottom, footerWidth);

        Cell containerCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(0);

        // Signature row: Left = "For CustomerName", Right = "For CompanyName" (same
        // horizontal line)
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

    private CompanySettings getCompanySettings() {
        try {
            var s = companySettingsService.getSettings();
            return CompanySettings.builder()
                    .companyName(s.getCompanyName()).address(s.getAddress())
                    .city(s.getCity()).phone(s.getPhone()).email(s.getEmail()).build();
        } catch (Exception e) {
            log.warn("Could not load company settings: {}", e.getMessage());
            return CompanySettings.builder().build();
        }
    }
}

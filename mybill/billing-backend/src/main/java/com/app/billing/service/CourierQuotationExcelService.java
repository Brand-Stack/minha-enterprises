package com.app.billing.service;

import com.app.billing.dto.CourierQuotationDto;
import com.app.billing.dto.ZoneRateConfigDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.app.billing.model.ZoneConfiguration;
import com.app.billing.dao.ZoneConfigurationRepository;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierQuotationExcelService {

    private final ResourceLoader resourceLoader;
    private final ZoneConfigurationRepository zoneConfigRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generateExcel(CourierQuotationDto dto) {
        Map<String, String> r = new HashMap<>();
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

        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Quotation " + dto.getQuotationNumber());

            sheet.setColumnWidth(0, 12000);
            sheet.setColumnWidth(1, 4000);
            sheet.setColumnWidth(2, 4500);
            sheet.setColumnWidth(3, 4000);
            sheet.setColumnWidth(4, 4000);
            sheet.setColumnWidth(5, 4000);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle subHeaderStyle = createSubHeaderStyle(workbook);
            CellStyle zoneCellStyle = createZoneCellStyle(workbook);
            CellStyle rateCellStyle = createRateCellStyle(workbook);

            int currentRow = createHeaderAndMeta(workbook, sheet, dto, 0);
            currentRow++;

            currentRow = createSection1(sheet, r, s1Zones, currentRow, headerStyle, subHeaderStyle, zoneCellStyle,
                    rateCellStyle);
            currentRow += 2;

            currentRow = createSection2(sheet, r, s2Zones, currentRow, headerStyle, subHeaderStyle, zoneCellStyle,
                    rateCellStyle);

            List<ZoneRateConfigDto> standardZoneRates = getStandardZoneRatesFromDto(dto);
            if (!standardZoneRates.isEmpty()) {
                currentRow += 2;
                createStandardSection(sheet, standardZoneRates, currentRow, headerStyle, zoneCellStyle, rateCellStyle);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate Excel quotation: {}", e.getMessage(), e);
            throw new RuntimeException("Error generating layout: " + e.getMessage());
        }
    }

    public byte[] generateListExcel(List<CourierQuotationDto> list) {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Quotations");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);
            cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            cellStyle.setWrapText(true);

            String[] headers = { "Quotation No", "Customer", "Branch", "Effective Date", "Valid Till", "Status" };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }
            sheet.setColumnWidth(0, 5000);
            sheet.setColumnWidth(1, 12000);
            sheet.setColumnWidth(2, 8000);
            sheet.setColumnWidth(3, 4500);
            sheet.setColumnWidth(4, 4500);
            sheet.setColumnWidth(5, 4000);

            int rowNum = 1;
            for (CourierQuotationDto dto : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(dto.getQuotationNumber() != null ? dto.getQuotationNumber() : "");
                row.createCell(1).setCellValue(dto.getCustomerName() != null ? dto.getCustomerName() : "");
                row.createCell(2).setCellValue(dto.getBranchName() != null ? dto.getBranchName() : "");
                row.createCell(3).setCellValue(
                        dto.getEffectiveDate() != null ? dto.getEffectiveDate().format(DATE_FORMATTER) : "");
                row.createCell(4).setCellValue(
                        dto.getValidTillDate() != null ? dto.getValidTillDate().format(DATE_FORMATTER) : "");
                row.createCell(5).setCellValue(dto.getStatus() != null ? dto.getStatus().name() : "");
                for (int i = 0; i < 6; i++) {
                    row.getCell(i).setCellStyle(cellStyle);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate list Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Error generating list Excel: " + e.getMessage());
        }
    }

    private int createHeaderAndMeta(Workbook workbook, Sheet sheet, CourierQuotationDto dto, int startRow) {
        int currentRow = startRow;

        try {
            Resource leftLogo = resourceLoader.getResource("classpath:images/minhaEnterprises.jpeg");
            if (leftLogo.exists()) {
                InputStream is = leftLogo.getInputStream();
                byte[] bytes = IOUtils.toByteArray(is);
                int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG);
                is.close();
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
                anchor.setCol1(0);
                anchor.setRow1(currentRow);
                Picture pict = drawing.createPicture(anchor, pictureIdx);
                pict.resize(0.5);
            }
        } catch (Exception e) {
            log.warn("Could not load left logo for excel", e);
        }

        try {
            Resource rightLogo = resourceLoader.getResource("classpath:images/franchExpress.png");
            if (rightLogo.exists()) {
                InputStream is = rightLogo.getInputStream();
                byte[] bytes = IOUtils.toByteArray(is);
                int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
                is.close();
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
                anchor.setCol1(4);
                anchor.setRow1(currentRow);
                Picture pict = drawing.createPicture(anchor, pictureIdx);
                pict.resize(0.8);
            }
        } catch (Exception e) {
            log.warn("Could not load right logo for excel", e);
        }

        currentRow += 4;

        CellStyle metaStyle = workbook.createCellStyle();
        Font metaFont = workbook.createFont();
        metaFont.setBold(true);
        metaFont.setFontHeightInPoints((short) 12);
        metaStyle.setFont(metaFont);

        Row companyRow = sheet.createRow(currentRow++);
        Cell c1 = companyRow.createCell(0);
        c1.setCellValue("MINHA ENTERPRISES");
        c1.setCellStyle(metaStyle);

        Row meta1 = sheet.createRow(currentRow++);
        meta1.createCell(0).setCellValue("Customer: " + (dto.getCustomerName() != null ? dto.getCustomerName() : ""));
        meta1.createCell(3).setCellValue(
                "Date: " + (dto.getEffectiveDate() != null ? dto.getEffectiveDate().format(DATE_FORMATTER) : ""));

        Row meta2 = sheet.createRow(currentRow++);
        meta2.createCell(0).setCellValue("Branch: " + (dto.getBranchName() != null ? dto.getBranchName() : ""));

        return currentRow;
    }

    private int createSection1(Sheet sheet, Map<String, String> rates, List<String[]> zones, int startRow,
            CellStyle headerStyle,
            CellStyle subHeaderStyle, CellStyle zoneStyle, CellStyle rateStyle) {
        int r = startRow;

        Row hr1 = sheet.createRow(r++);
        Cell hZone = hr1.createCell(0);
        hZone.setCellValue("ZONE");
        hZone.setCellStyle(headerStyle);
        Cell hExp = hr1.createCell(1);
        hExp.setCellValue("Express Rate");
        hExp.setCellStyle(headerStyle);
        hr1.createCell(2).setCellStyle(headerStyle);
        hr1.createCell(3).setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 1, 3));

        Cell hSur = hr1.createCell(4);
        hSur.setCellValue("Surface Rate");
        hSur.setCellStyle(headerStyle);
        hr1.createCell(5).setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 4, 5));

        Row hr2 = sheet.createRow(r++);
        hr2.createCell(0).setCellStyle(subHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(r - 2, r - 1, 0, 0));

        String[] cols = { "First 250 Gms", "Every Add 500 Gms\nupto 3 Kg", "Above 3 Kg\nPer Kg",
                "Above 10 Kg\nUpto 200 Kg", "Above 200 Kg\nUpto 500 Kg" };
        for (int i = 0; i < cols.length; i++) {
            Cell c = hr2.createCell(i + 1);
            c.setCellValue(cols[i]);
            c.setCellStyle(subHeaderStyle);
        }

        for (String[] zone : zones) {
            Row tr = sheet.createRow(r++);
            Cell zCell = tr.createCell(0);
            zCell.setCellValue(zone[1]);
            zCell.setCellStyle(zoneStyle);

            String[] suffixes = { "_E1", "_E2", "_E3", "_S1", "_S2" };
            for (int i = 0; i < suffixes.length; i++) {
                Cell c = tr.createCell(i + 1);
                String rate = rates != null ? rates.get(zone[0] + suffixes[i]) : null;
                c.setCellValue(rate != null && !rate.trim().isEmpty() ? "\u20B9 " + rate.trim() : "");
                c.setCellStyle(rateStyle);
            }
        }
        return r;
    }

    private int createSection2(Sheet sheet, Map<String, String> rates, List<String[]> zones, int startRow,
            CellStyle headerStyle,
            CellStyle subHeaderStyle, CellStyle zoneStyle, CellStyle rateStyle) {
        int r = startRow;

        Row hr1 = sheet.createRow(r++);
        Cell hZone = hr1.createCell(0);
        hZone.setCellValue("ZONE");
        hZone.setCellStyle(headerStyle);
        Cell hPri = hr1.createCell(1);
        hPri.setCellValue("Safety(Priority)");
        hPri.setCellStyle(headerStyle);
        hr1.createCell(2).setCellStyle(headerStyle);
        hr1.createCell(3).setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 1, 3));

        Cell hSaf = hr1.createCell(4);
        hSaf.setCellValue("Surface");
        hSaf.setCellStyle(headerStyle);
        hr1.createCell(5).setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 4, 5));

        Row hr2 = sheet.createRow(r++);
        hr2.createCell(0).setCellStyle(subHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(r - 2, r - 1, 0, 0));

        String[] cols = { "First 250 Gms", "Every Add 500 Gms\nupto 3 Kg", "Above 3 Kg\nPer Kg",
                "Above 10 Kg\nUpto 200 Kg", "Above 200 Kg\nUpto 500 Kg" };
        for (int i = 0; i < cols.length; i++) {
            Cell c = hr2.createCell(i + 1);
            c.setCellValue(cols[i]);
            c.setCellStyle(subHeaderStyle);
        }

        for (String[] zone : zones) {
            Row tr = sheet.createRow(r++);
            Cell zCell = tr.createCell(0);
            zCell.setCellValue(zone[1]);
            zCell.setCellStyle(zoneStyle);

            String[] suffixes = { "_PC1", "_PC2", "_PC3", "_SP1", "_SP2" };
            for (int i = 0; i < suffixes.length; i++) {
                Cell c = tr.createCell(i + 1);
                String rate = rates != null ? rates.get(zone[0] + suffixes[i]) : null;
                c.setCellValue(rate != null && !rate.trim().isEmpty() ? "\u20B9 " + rate.trim() : "");
                c.setCellStyle(rateStyle);
            }
        }
        return r;
    }

    private List<ZoneRateConfigDto> getStandardZoneRatesFromDto(CourierQuotationDto dto) {
        if (dto.getZoneRates() == null) return java.util.Collections.emptyList();
        return dto.getZoneRates().stream()
                .filter(this::hasStandardRates)
                .collect(java.util.stream.Collectors.toList());
    }

    private boolean hasStandardRates(ZoneRateConfigDto zr) {
        if (zr.getStandardRate1Kg() != null && zr.getStandardRate1Kg() > 0) return true;
        if (zr.getStandardRate2Kg() != null && zr.getStandardRate2Kg() > 0) return true;
        if (zr.getStandardRate3Kg() != null && zr.getStandardRate3Kg() > 0) return true;
        if (zr.getStandardRate4Kg() != null && zr.getStandardRate4Kg() > 0) return true;
        if (zr.getStandardRate5Kg() != null && zr.getStandardRate5Kg() > 0) return true;
        if (zr.getStandardPerKgAbove3() != null && zr.getStandardPerKgAbove3() > 0) return true;
        if (zr.getStandardBaseRate3Kg() != null && zr.getStandardBaseRate3Kg() > 0) return true;
        if (zr.getStandardAdditionalPerKg() != null && zr.getStandardAdditionalPerKg() > 0) return true;
        return false;
    }

    private int createStandardSection(Sheet sheet, List<ZoneRateConfigDto> standardZoneRates, int startRow,
            CellStyle headerStyle, CellStyle zoneStyle, CellStyle rateStyle) {
        int r = startRow;

        Row headerRow = sheet.createRow(r++);
        String[] headers = { "ZONE", "1 Kg", "2 Kg", "3 Kg", "4 Kg", "5 Kg", "Above 5 Kg Per Kg" };
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        for (ZoneRateConfigDto zr : standardZoneRates) {
            Row row = sheet.createRow(r++);
            String zoneName = (zr.getZoneName() != null && !zr.getZoneName().isBlank()) ? zr.getZoneName() : "Standard";
            row.createCell(0).setCellValue(zoneName);
            row.getCell(0).setCellStyle(zoneStyle);

            Double rate1 = zr.getStandardRate1Kg();
            Double rate2 = zr.getStandardRate2Kg();
            Double rate3 = zr.getStandardRate3Kg() != null ? zr.getStandardRate3Kg() : zr.getStandardBaseRate3Kg();
            Double rate4 = zr.getStandardRate4Kg();
            Double rate5 = zr.getStandardRate5Kg();
            Double rateAbove3 = zr.getStandardPerKgAbove3() != null ? zr.getStandardPerKgAbove3() : zr.getStandardAdditionalPerKg();
            Double[] rates = { rate1, rate2, rate3, rate4, rate5, rateAbove3 };
            for (int i = 0; i < rates.length; i++) {
                Cell c = row.createCell(i + 1);
                if (rates[i] != null) {
                    c.setCellValue("\u20B9 " + rates[i]);
                }
                c.setCellStyle(rateStyle);
            }
        }
        return r;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        return style;
    }

    private CellStyle createSubHeaderStyle(Workbook wb) {
        CellStyle style = createHeaderStyle(wb);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setWrapText(true);
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private CellStyle createZoneCellStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createRateCellStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}

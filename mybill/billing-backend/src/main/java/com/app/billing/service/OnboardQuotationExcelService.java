package com.app.billing.service;

import com.app.billing.dao.ZoneConfigurationRepository;
import com.app.billing.dto.OnboardQuotationDto;
import com.app.billing.dto.OnboardQuotationDto.OnboardSlabDto;
import com.app.billing.dto.ZoneRateConfigDto;
import com.app.billing.model.ZoneConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardQuotationExcelService {

    private final ZoneConfigurationRepository zoneConfigRepository;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public byte[] generateExcel(OnboardQuotationDto dto) {
        log.info("Generating Onboard Quotation Excel for: {}", dto.getQuotationNumber());

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
                dto.getSlabs().stream().filter(s -> Boolean.TRUE.equals(s.getSelected())).collect(Collectors.toList()) :
                List.of();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Onboard Quotation");

            // Fonts & Styles
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            Font slabFont = workbook.createFont();
            slabFont.setBold(true);
            slabFont.setFontHeightInPoints((short) 11);

            CellStyle slabHeaderStyle = workbook.createCellStyle();
            slabHeaderStyle.setFont(slabFont);
            slabHeaderStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            slabHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle borderStyle = workbook.createCellStyle();
            borderStyle.setBorderBottom(BorderStyle.THIN);
            borderStyle.setBorderTop(BorderStyle.THIN);
            borderStyle.setBorderLeft(BorderStyle.THIN);
            borderStyle.setBorderRight(BorderStyle.THIN);

            int rowIdx = 0;

            // Title Row
            Row rTitle = sheet.createRow(rowIdx++);
            Cell cTitle = rTitle.createCell(0);
            cTitle.setCellValue("ONBOARD COURIER RATE QUOTATION");
            cTitle.setCellStyle(titleStyle);

            rowIdx++;

            // Meta Details
            createMetaRow(sheet, rowIdx++, "Quotation Number:", dto.getQuotationNumber(), "Customer Name:", dto.getCustomerName());
            createMetaRow(sheet, rowIdx++, "Effective Date:", dto.getEffectiveDate() != null ? dto.getEffectiveDate().format(DATE_FMT) : "", "Valid Till Date:", dto.getValidTillDate() != null ? dto.getValidTillDate().format(DATE_FMT) : "");
            createMetaRow(sheet, rowIdx++, "Fuel Charge %:", dto.getFuelChargePercentage() != null ? dto.getFuelChargePercentage() + "%" : "N/A", "FOV Charges:", dto.getFovCharges() != null ? String.valueOf(dto.getFovCharges()) : "N/A");

            rowIdx++;

            for (OnboardSlabDto slab : selectedSlabs) {
                // Slab Header
                Row rSlab = sheet.createRow(rowIdx++);
                Cell cSlab = rSlab.createCell(0);
                cSlab.setCellValue("SLAB: " + (slab.getSlabName() != null ? slab.getSlabName().toUpperCase() : "SLAB"));
                cSlab.setCellStyle(slabHeaderStyle);

                Map<String, String> rMap = buildRateMap(slab.getZoneRates());

                // Section 1: Express + Surface
                if (!s1Zones.isEmpty()) {
                    Row rHeader1 = sheet.createRow(rowIdx++);
                    String[] headers1 = {"Zone", "Express: 1st 250g", "Express: Add 500g", "Express: Above 3kg/kg", "Surface: 10-200kg", "Surface: 200-500kg"};
                    for (int i = 0; i < headers1.length; i++) {
                        Cell c = rHeader1.createCell(i);
                        c.setCellValue(headers1[i]);
                        c.setCellStyle(headerStyle);
                    }

                    for (String[] z : s1Zones) {
                        Row rData = sheet.createRow(rowIdx++);
                        rData.createCell(0).setCellValue(z[1]);
                        rData.createCell(1).setCellValue(getVal(rMap, z[0] + "_E1"));
                        rData.createCell(2).setCellValue(getVal(rMap, z[0] + "_E2"));
                        rData.createCell(3).setCellValue(getVal(rMap, z[0] + "_E3"));
                        rData.createCell(4).setCellValue(getVal(rMap, z[0] + "_S1"));
                        rData.createCell(5).setCellValue(getVal(rMap, z[0] + "_S2"));
                        for (int i = 0; i < 6; i++) rData.getCell(i).setCellStyle(borderStyle);
                    }
                    rowIdx++;
                }

                // Section 2: Priority + Safety
                if (!s2Zones.isEmpty()) {
                    Row rHeader2 = sheet.createRow(rowIdx++);
                    String[] headers2 = {"Zone", "Priority: 1st 250g", "Priority: Add 500g", "Priority: Above 3kg/kg", "Safety: 10-200kg", "Safety: 200-500kg"};
                    for (int i = 0; i < headers2.length; i++) {
                        Cell c = rHeader2.createCell(i);
                        c.setCellValue(headers2[i]);
                        c.setCellStyle(headerStyle);
                    }

                    for (String[] z : s2Zones) {
                        Row rData = sheet.createRow(rowIdx++);
                        rData.createCell(0).setCellValue(z[1]);
                        rData.createCell(1).setCellValue(getVal(rMap, z[0] + "_PC1"));
                        rData.createCell(2).setCellValue(getVal(rMap, z[0] + "_PC2"));
                        rData.createCell(3).setCellValue(getVal(rMap, z[0] + "_PC3"));
                        rData.createCell(4).setCellValue(getVal(rMap, z[0] + "_SP1"));
                        rData.createCell(5).setCellValue(getVal(rMap, z[0] + "_SP2"));
                        for (int i = 0; i < 6; i++) rData.getCell(i).setCellStyle(borderStyle);
                    }
                    rowIdx++;
                }
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating Onboard Quotation Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Could not generate Excel: " + e.getMessage(), e);
        }
    }

    private void createMetaRow(Sheet sheet, int rowIdx, String k1, String v1, String k2, String v2) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(k1);
        row.createCell(1).setCellValue(v1 != null ? v1 : "");
        row.createCell(3).setCellValue(k2);
        row.createCell(4).setCellValue(v2 != null ? v2 : "");
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
            });
        }
        return r;
    }

    private String getVal(Map<String, String> map, String key) {
        String v = map.get(key);
        return v != null ? v : "—";
    }
}

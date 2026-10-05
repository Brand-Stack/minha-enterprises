package com.app.billing.service;

import com.app.billing.dto.MonthlyCourierEntryDto;
import com.app.billing.dto.SmallClientEntryDto;
import com.app.billing.util.MonthlyCourierBreakupSortUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MonthlyShipmentBreakupExcelService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** Generate with zone names (no IDs). Pass zoneIdToName from ZoneConfigurationRepository. Weight column optional. */
    public byte[] generate(List<MonthlyCourierEntryDto> entries, boolean includeAmount, Map<String, String> zoneIdToName) {
        return generate(entries, includeAmount, true, zoneIdToName);
    }

    public byte[] generateForSmallClient(List<SmallClientEntryDto> entries, boolean includeAmount, boolean includeWeight,
            Map<String, String> zoneIdToName) {
        return generate(toMonthlyEntryDtos(entries), includeAmount, includeWeight, zoneIdToName);
    }

    private static List<MonthlyCourierEntryDto> toMonthlyEntryDtos(List<SmallClientEntryDto> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream().map(MonthlyShipmentBreakupExcelService::toMonthlyEntryDto).toList();
    }

    private static MonthlyCourierEntryDto toMonthlyEntryDto(SmallClientEntryDto s) {
        MonthlyCourierEntryDto m = new MonthlyCourierEntryDto();
        m.setId(s.getId());
        m.setMonthlyQuotationId(s.getMonthlyQuotationId());
        m.setEntryDate(s.getEntryDate());
        m.setConsignor(s.getConsignor());
        m.setReceiverName(s.getReceiverName());
        m.setReceiverPhoneNo(s.getReceiverPhoneNo());
        m.setPincode(s.getPincode());
        m.setAreaName(s.getAreaName());
        m.setState(s.getState());
        m.setDestinationCity(s.getDestinationCity());
        m.setFullAddress(s.getFullAddress());
        m.setConsigneeAddress(s.getConsigneeAddress());
        m.setCourierType(s.getCourierType());
        m.setWeight(s.getWeight());
        m.setTrackingNumber(s.getTrackingNumber());
        m.setItemType(s.getItemType());
        m.setDeliveryStatus(s.getDeliveryStatus());
        m.setZone(s.getZone());
        m.setRateType(s.getRateType());
        m.setRate(s.getRate());
        m.setAmount(s.getAmount());
        m.setAmountStatus(s.getAmountStatus());
        m.setAmountOverridden(s.getAmountOverridden());
        m.setAdditionalCharges(s.getAdditionalCharges());
        m.setAdditionalChargesDescription(s.getAdditionalChargesDescription());
        m.setGstApplicable(s.getGstApplicable());
        m.setFuelApplicable(s.getFuelApplicable());
        m.setFovApplicable(s.getFovApplicable());
        return m;
    }

    public byte[] generate(List<MonthlyCourierEntryDto> entries, boolean includeAmount, boolean includeWeight, Map<String, String> zoneIdToName) {
        return generate(entries, includeAmount, includeWeight, zoneIdToName, null);
    }

    public byte[] generate(List<MonthlyCourierEntryDto> entries, boolean includeAmount, boolean includeWeight, Map<String, String> zoneIdToName,
            MonthlyCourierInvoiceGrandTotalService.PdfInvoiceTotals totals) {
        entries = MonthlyCourierBreakupSortUtil.sortedCopy(entries);
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Monthly Shipment Breakup");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            int col = 0;
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(col).setCellValue("S.No"); headerRow.getCell(col++).setCellStyle(headerStyle);
            headerRow.createCell(col).setCellValue("Date"); headerRow.getCell(col++).setCellStyle(headerStyle);
            headerRow.createCell(col).setCellValue("Courier"); headerRow.getCell(col++).setCellStyle(headerStyle);
            headerRow.createCell(col).setCellValue("AWB NO"); headerRow.getCell(col++).setCellStyle(headerStyle);
            headerRow.createCell(col).setCellValue("Destination"); headerRow.getCell(col++).setCellStyle(headerStyle);
            if (includeWeight) {
                headerRow.createCell(col).setCellValue("Weight"); headerRow.getCell(col++).setCellStyle(headerStyle);
            }
            headerRow.createCell(col).setCellValue("Item"); headerRow.getCell(col++).setCellStyle(headerStyle);
            headerRow.createCell(col).setCellValue("Zone"); headerRow.getCell(col++).setCellStyle(headerStyle);
            headerRow.createCell(col).setCellValue("RateType"); headerRow.getCell(col++).setCellStyle(headerStyle);
            if (includeAmount) {
                headerRow.createCell(col).setCellValue("Courier Cost");
                headerRow.getCell(col++).setCellStyle(headerStyle);
            }
            final int columnCount = col;

            int rowIdx = 1;
            int sno = 1;
            for (MonthlyCourierEntryDto e : entries) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                String zoneDisplay = (zoneIdToName != null && e.getZone() != null && zoneIdToName.containsKey(e.getZone()))
                        ? zoneIdToName.get(e.getZone()) : (e.getZone() != null ? e.getZone() : "");
                String rateTypeDisplay = rateTypeToDisplay(e.getRateType());
                row.createCell(c++).setCellValue(sno++);
                row.createCell(c++).setCellValue(e.getEntryDate() != null ? e.getEntryDate().format(DATE_FORMATTER) : "");
                row.createCell(c++).setCellValue(e.getCourierType() != null ? e.getCourierType() : "");
                row.createCell(c++).setCellValue(e.getTrackingNumber() != null ? e.getTrackingNumber() : "");
                row.createCell(c++).setCellValue(e.getConsigneeAddress() != null ? e.getConsigneeAddress() : "");
                if (includeWeight) {
                    row.createCell(c++).setCellValue(e.getWeight() != null ? e.getWeight() : 0.0);
                }
                row.createCell(c++).setCellValue(e.getItemType() != null ? e.getItemType() : "");
                row.createCell(c++).setCellValue(zoneDisplay);
                row.createCell(c++).setCellValue(rateTypeDisplay);
                if (includeAmount) {
                    double amt = (e.getAmount() != null ? e.getAmount() : 0.0) + (e.getAdditionalCharges() != null ? e.getAdditionalCharges() : 0.0);
                    row.createCell(c++).setCellValue(amt);
                }

                for (int i = 0; i < columnCount; i++) {
                    Cell cell = row.getCell(i);
                    if (cell != null) {
                        cell.setCellStyle(dataStyle);
                    }
                }
            }

            if (includeAmount && totals != null) {
                // Add blank row
                Row blankRow = sheet.createRow(rowIdx++);
                
                // Add Domestic / International Courier Charges
                Row rBase = sheet.createRow(rowIdx++);
                rBase.createCell(0).setCellValue("Domestic / International Courier Charges :");
                rBase.createCell(columnCount - 1).setCellValue(totals.baseAmount());
                rBase.getCell(0).setCellStyle(headerStyle);
                rBase.getCell(columnCount - 1).setCellStyle(dataStyle);
                
                // Add Fuel Charges
                Row rFuel = sheet.createRow(rowIdx++);
                rFuel.createCell(0).setCellValue("Fuel Charges @ " + String.format("%.2f", totals.fuelPct()) + "% :");
                rFuel.createCell(columnCount - 1).setCellValue(totals.fuelAmount());
                rFuel.getCell(0).setCellStyle(headerStyle);
                rFuel.getCell(columnCount - 1).setCellStyle(dataStyle);
                
                // Add FOV Charges
                if (totals.fovAmount() > 0.005) {
                    Row rFov = sheet.createRow(rowIdx++);
                    rFov.createCell(0).setCellValue("FOV Charges @ " + String.format("%.2f", totals.fovPct()) + "% :");
                    rFov.createCell(columnCount - 1).setCellValue(totals.fovAmount());
                    rFov.getCell(0).setCellStyle(headerStyle);
                    rFov.getCell(columnCount - 1).setCellStyle(dataStyle);
                }
                
                // Add SUB TOTAL
                Row rSub = sheet.createRow(rowIdx++);
                rSub.createCell(0).setCellValue("SUB TOTAL :");
                rSub.createCell(columnCount - 1).setCellValue(totals.subTotal());
                rSub.getCell(0).setCellStyle(headerStyle);
                rSub.getCell(columnCount - 1).setCellStyle(headerStyle);
                
                // Add CGST
                double cgstHalfPct = totals.gstPct() / 2.0;
                Row rCgst = sheet.createRow(rowIdx++);
                rCgst.createCell(0).setCellValue("CGST @ " + String.format("%.1f", cgstHalfPct) + "% :");
                rCgst.createCell(columnCount - 1).setCellValue(totals.cgst());
                rCgst.getCell(0).setCellStyle(headerStyle);
                rCgst.getCell(columnCount - 1).setCellStyle(dataStyle);
                
                // Add SGST
                Row rSgst = sheet.createRow(rowIdx++);
                rSgst.createCell(0).setCellValue("SGST @ " + String.format("%.1f", cgstHalfPct) + "% :");
                rSgst.createCell(columnCount - 1).setCellValue(totals.sgst());
                rSgst.getCell(0).setCellStyle(headerStyle);
                rSgst.getCell(columnCount - 1).setCellStyle(dataStyle);
                
                // Add Round Off
                Row rRound = sheet.createRow(rowIdx++);
                rRound.createCell(0).setCellValue("Round Off :");
                rRound.createCell(columnCount - 1).setCellValue(totals.roundOff());
                rRound.getCell(0).setCellStyle(headerStyle);
                rRound.getCell(columnCount - 1).setCellStyle(dataStyle);
                
                // Add Nett Amount Payable
                Row rNett = sheet.createRow(rowIdx++);
                rNett.createCell(0).setCellValue("Nett Amount Payable :");
                rNett.createCell(columnCount - 1).setCellValue(totals.nettAmount());
                rNett.getCell(0).setCellStyle(headerStyle);
                rNett.getCell(columnCount - 1).setCellStyle(headerStyle);
            }

            for (int i = 0; i < columnCount; i++) {
                if (includeAmount && i == columnCount - 1) {
                    sheet.setColumnWidth(i, 4000);
                } else {
                    sheet.setColumnWidth(i, 5000);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate monthly shipment breakup excel", e);
            throw new RuntimeException("Failed to generate shipment breakup excel", e);
        }
    }

    public byte[] generate(List<MonthlyCourierEntryDto> entries, boolean includeAmount) {
        return generate(entries, includeAmount, null);
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
        style.setFont(font);
        return style;
    }

    private static String rateTypeToDisplay(String rateType) {
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

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}

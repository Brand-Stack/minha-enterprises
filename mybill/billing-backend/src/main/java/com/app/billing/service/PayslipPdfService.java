package com.app.billing.service;

import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.PayrollRecord;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayslipPdfService {

    private final CompanySettingsService companySettingsService;

    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(26, 29, 46);
    private static final DeviceRgb ACCENT_COLOR = new DeviceRgb(91, 111, 232);
    private static final DeviceRgb HEADER_BG = new DeviceRgb(240, 242, 245);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(210, 215, 225);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generatePayslipPdf(PayrollRecord payroll) {
        log.info("Generating Payslip PDF for employee {} month {}", payroll.getEmployeeName(), payroll.getPayrollMonth());
        CompanySettingsDto settings = companySettingsService.getSettings();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36f, 36f, 36f, 36f);

            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            // --- Header: Company Logo & Info ---
            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                    .useAllAvailableWidth()
                    .setMarginBottom(10);

            Cell logoCell = new Cell().setBorder(Border.NO_BORDER);
            try {
                com.itextpdf.io.image.ImageData logoData = com.itextpdf.io.image.ImageDataFactory.create(
                        new org.springframework.core.io.ClassPathResource("images/minhaEnterprisesFullLogo.jpeg").getURL());
                com.itextpdf.layout.element.Image logoImg = new com.itextpdf.layout.element.Image(logoData);
                logoImg.setHeight(45f);
                logoCell.add(logoImg);
            } catch (Exception e) {
                log.debug("Header logo minhaEnterprisesFullLogo.jpeg fallback: {}", e.getMessage());
                String companyName = settings.getCompanyName() != null && !settings.getCompanyName().isBlank()
                        ? settings.getCompanyName() : "Minha Enterprises";
                logoCell.add(new Paragraph(companyName).setFont(boldFont).setFontSize(16).setFontColor(PRIMARY_COLOR));
            }
            headerTable.addCell(logoCell);

            Cell compInfoCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
            String compName = settings.getCompanyName() != null && !settings.getCompanyName().isBlank()
                    ? settings.getCompanyName() : "MINHA ENTERPRISES";
            compInfoCell.add(new Paragraph(compName).setFont(boldFont).setFontSize(14).setFontColor(PRIMARY_COLOR));

            StringBuilder address = new StringBuilder();
            if (settings.getAddress() != null) address.append(settings.getAddress());
            if (settings.getCity() != null) address.append(", ").append(settings.getCity());
            if (settings.getState() != null) address.append(", ").append(settings.getState());
            if (settings.getPincode() != null) address.append(" - ").append(settings.getPincode());
            if (address.length() > 0) {
                compInfoCell.add(new Paragraph(address.toString())
                        .setFont(regularFont)
                        .setFontSize(8)
                        .setFontColor(new DeviceRgb(90, 90, 90)));
            }
            if (settings.getMobile() != null && !settings.getMobile().isBlank()) {
                compInfoCell.add(new Paragraph("Phone: " + settings.getMobile())
                        .setFont(regularFont)
                        .setFontSize(8)
                        .setFontColor(new DeviceRgb(90, 90, 90)));
            }
            headerTable.addCell(compInfoCell);
            document.add(headerTable);

            document.add(new Paragraph("PAYSLIP FOR THE MONTH OF " + (payroll.getPayrollMonth() != null ? payroll.getPayrollMonth().toUpperCase() : ""))
                    .setFont(boldFont)
                    .setFontSize(13)
                    .setFontColor(ACCENT_COLOR)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(5)
                    .setMarginBottom(12));

            // --- Employee & Attendance Details Table ---
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{2, 3, 2, 3}))
                    .useAllAvailableWidth()
                    .setMarginBottom(15);

            addInfoCell(infoTable, "Employee Code:", payroll.getEmployeeCode(), boldFont, regularFont);
            addInfoCell(infoTable, "Payslip No:", payroll.getPayslipNumber(), boldFont, regularFont);
            addInfoCell(infoTable, "Employee Name:", payroll.getEmployeeName(), boldFont, regularFont);
            addInfoCell(infoTable, "Department:", payroll.getDepartment() != null ? payroll.getDepartment() : "General", boldFont, regularFont);
            addInfoCell(infoTable, "Designation:", payroll.getDesignation() != null ? payroll.getDesignation() : "Staff", boldFont, regularFont);
            addInfoCell(infoTable, "Joining Date:", payroll.getJoiningDate() != null ? payroll.getJoiningDate().format(DATE_FORMATTER) : "N/A", boldFont, regularFont);
            addInfoCell(infoTable, "Total Days:", payroll.getTotalWorkingDays() != null ? String.valueOf(payroll.getTotalWorkingDays()) : "0", boldFont, regularFont);
            addInfoCell(infoTable, "Present Days:", payroll.getPresentDays() != null ? String.valueOf(payroll.getPresentDays()) : "0", boldFont, regularFont);
            addInfoCell(infoTable, "Leave Days:", payroll.getLeaveDays() != null ? String.valueOf(payroll.getLeaveDays()) : "0", boldFont, regularFont);
            addInfoCell(infoTable, "LOP Days:", payroll.getLopDays() != null ? String.valueOf(payroll.getLopDays()) : "0", boldFont, regularFont);

            if (payroll.getBankName() != null && !payroll.getBankName().isBlank()) {
                addInfoCell(infoTable, "Bank Name:", payroll.getBankName(), boldFont, regularFont);
                addInfoCell(infoTable, "Account No:", payroll.getAccountNumber() != null ? payroll.getAccountNumber() : "N/A", boldFont, regularFont);
            }

            document.add(infoTable);

            // --- Earnings & Deductions Table ---
            Table salaryTable = new Table(UnitValue.createPercentArray(new float[]{3, 2, 3, 2}))
                    .useAllAvailableWidth()
                    .setMarginBottom(15);

            // Headers
            salaryTable.addHeaderCell(createHeaderCell("Earnings", boldFont));
            salaryTable.addHeaderCell(createHeaderCell("Amount (₹)", boldFont));
            salaryTable.addHeaderCell(createHeaderCell("Deductions", boldFont));
            salaryTable.addHeaderCell(createHeaderCell("Amount (₹)", boldFont));

            // Rows
            addSalaryRow(salaryTable, "Basic Salary", format(payroll.getBasicSalary()), "Provident Fund (PF)", format(payroll.getPfAmount()), regularFont);
            addSalaryRow(salaryTable, "HRA", format(payroll.getHra()), "Professional Tax", format(payroll.getProfessionalTax()), regularFont);
            addSalaryRow(salaryTable, "Allowances", format(payroll.getAllowances()), "Income Tax", format(payroll.getTaxAmount()), regularFont);
            addSalaryRow(salaryTable, "Overtime Pay", format(payroll.getOvertimeAmount()), "Loss of Pay (LOP)", format(payroll.getLopAmount()), regularFont);
            addSalaryRow(salaryTable, "Bonus", format(payroll.getBonusAmount()), "Salary Advance Deduction", format(payroll.getAdvanceDeductionAmount()), regularFont);
            addSalaryRow(salaryTable, "Yearly Bonus", format(payroll.getYearlyBonusAmount()), "Other Deductions", format(payroll.getOtherDeductions()), regularFont);

            // Totals Row
            salaryTable.addCell(createTotalCell("Gross Earnings", boldFont));
            salaryTable.addCell(createTotalCell("₹" + format(payroll.getGrossSalary()), boldFont));
            salaryTable.addCell(createTotalCell("Total Deductions", boldFont));
            salaryTable.addCell(createTotalCell("₹" + format(payroll.getTotalDeductions()), boldFont));

            document.add(salaryTable);

            // --- Net Salary Banner ---
            Table netTable = new Table(UnitValue.createPercentArray(new float[]{3, 2}))
                    .useAllAvailableWidth()
                    .setMarginBottom(20);

            Cell netLabel = new Cell().add(new Paragraph("NET SALARY PAYABLE")
                    .setFont(boldFont)
                    .setFontSize(14)
                    .setFontColor(PRIMARY_COLOR))
                    .setBorder(new SolidBorder(ACCENT_COLOR, 1))
                    .setPadding(8);
            netTable.addCell(netLabel);

            Cell netValue = new Cell().add(new Paragraph("₹" + format(payroll.getNetSalary()))
                    .setFont(boldFont)
                    .setFontSize(16)
                    .setFontColor(ACCENT_COLOR)
                    .setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(new SolidBorder(ACCENT_COLOR, 1))
                    .setPadding(8);
            netTable.addCell(netValue);

            document.add(netTable);

            // --- Salary Advance Recovery Summary Box ---
            if (payroll.getOriginalAdvanceAmount() != null || (payroll.getAdvanceDeductionAmount() != null && payroll.getAdvanceDeductionAmount().compareTo(BigDecimal.ZERO) > 0)) {
                Table advTable = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}))
                        .useAllAvailableWidth()
                        .setMarginBottom(15);

                advTable.addHeaderCell(createHeaderCell("Original Advance", boldFont));
                advTable.addHeaderCell(createHeaderCell("Current Recovery", boldFont));
                advTable.addHeaderCell(createHeaderCell("Total Recovered", boldFont));
                advTable.addHeaderCell(createHeaderCell("Outstanding Balance", boldFont));

                advTable.addCell(new Cell().add(new Paragraph("₹" + format(payroll.getOriginalAdvanceAmount() != null ? payroll.getOriginalAdvanceAmount() : BigDecimal.ZERO)).setFont(regularFont).setFontSize(9)).setBorder(new SolidBorder(BORDER_COLOR, 0.5f)));
                advTable.addCell(new Cell().add(new Paragraph("₹" + format(payroll.getAdvanceDeductionAmount() != null ? payroll.getAdvanceDeductionAmount() : BigDecimal.ZERO)).setFont(regularFont).setFontSize(9)).setBorder(new SolidBorder(BORDER_COLOR, 0.5f)));
                advTable.addCell(new Cell().add(new Paragraph("₹" + format(payroll.getTotalAdvanceRecoveredSoFar() != null ? payroll.getTotalAdvanceRecoveredSoFar() : BigDecimal.ZERO)).setFont(regularFont).setFontSize(9)).setBorder(new SolidBorder(BORDER_COLOR, 0.5f)));
                advTable.addCell(new Cell().add(new Paragraph("₹" + format(payroll.getRemainingAdvanceBalance() != null ? payroll.getRemainingAdvanceBalance() : BigDecimal.ZERO)).setFont(regularFont).setFontSize(9)).setBorder(new SolidBorder(BORDER_COLOR, 0.5f)));

                document.add(new Paragraph("SALARY ADVANCE RECOVERY SUMMARY")
                        .setFont(boldFont)
                        .setFontSize(10)
                        .setFontColor(PRIMARY_COLOR)
                        .setMarginBottom(4));
                document.add(advTable);
            }

            // --- Footer ---
            document.add(new Paragraph("This is a computer-generated payslip and does not require a physical signature.")
                    .setFont(regularFont)
                    .setFontSize(8)
                    .setFontColor(new DeviceRgb(120, 120, 120))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(30));

            // --- Watermark & Close ---
            PdfWatermarkHelper.applyWatermarkToAllPages(pdfDoc, 0.15f);
            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate payslip PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF payslip", e);
        }
    }

    private void addInfoCell(Table table, String label, String val, PdfFont boldFont, PdfFont regularFont) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(boldFont).setFontSize(9)).setBorder(Border.NO_BORDER));
        table.addCell(new Cell().add(new Paragraph(val != null ? val : "—").setFont(regularFont).setFontSize(9)).setBorder(Border.NO_BORDER));
    }

    private void addSalaryRow(Table table, String earnLabel, String earnVal, String dedLabel, String dedVal, PdfFont font) {
        table.addCell(new Cell().add(new Paragraph(earnLabel).setFont(font).setFontSize(9)).setBorder(new SolidBorder(BORDER_COLOR, 0.5f)));
        table.addCell(new Cell().add(new Paragraph(earnVal).setFont(font).setFontSize(9).setTextAlignment(TextAlignment.RIGHT)).setBorder(new SolidBorder(BORDER_COLOR, 0.5f)));
        table.addCell(new Cell().add(new Paragraph(dedLabel).setFont(font).setFontSize(9)).setBorder(new SolidBorder(BORDER_COLOR, 0.5f)));
        table.addCell(new Cell().add(new Paragraph(dedVal).setFont(font).setFontSize(9).setTextAlignment(TextAlignment.RIGHT)).setBorder(new SolidBorder(BORDER_COLOR, 0.5f)));
    }

    private Cell createHeaderCell(String title, PdfFont font) {
        return new Cell().add(new Paragraph(title).setFont(font).setFontSize(10).setFontColor(PRIMARY_COLOR))
                .setBackgroundColor(HEADER_BG)
                .setBorder(new SolidBorder(BORDER_COLOR, 1))
                .setPadding(6);
    }

    private Cell createTotalCell(String title, PdfFont font) {
        return new Cell().add(new Paragraph(title).setFont(font).setFontSize(10).setFontColor(PRIMARY_COLOR))
                .setBackgroundColor(HEADER_BG)
                .setBorder(new SolidBorder(BORDER_COLOR, 1))
                .setPadding(6);
    }

    private String format(BigDecimal amt) {
        return amt != null ? String.format("%.2f", amt) : "0.00";
    }
}

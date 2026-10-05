package com.app.billing.service;

import com.app.billing.dao.AttendanceDeviceRepository;
import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.EmployeeAdvanceAccountRepository;
import com.app.billing.dao.LeaveRequestRepository;
import com.app.billing.dao.PayrollRecordRepository;
import com.app.billing.dao.PermissionRequestRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.AttendanceDevice;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.EmployeeAdvanceAccount;
import com.app.billing.model.LeaveRequest;
import com.app.billing.model.PayrollRecord;
import com.app.billing.model.PermissionRequest;
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
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PermissionRequestRepository permissionRepository;
    private final PayrollRecordRepository payrollRepository;
    private final EmployeeAdvanceAccountRepository employeeAdvanceAccountRepository;
    private final AttendanceDeviceRepository deviceRepository;
    private final CompanySettingsService companySettingsService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(26, 29, 46);
    private static final DeviceRgb HEADER_BG = new DeviceRgb(240, 242, 245);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(210, 215, 225);

    // ==========================================
    // 1 & 2. ATTENDANCE (MY & DAILY) EXPORTS
    // ==========================================

    public byte[] generateAttendanceReportPdf(LocalDate startDate, LocalDate endDate, String employeeId, String status) {
        List<AttendanceRecord> records = fetchFilteredAttendance(startDate, endDate, employeeId, status);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4.rotate());
            doc.setMargins(20f, 20f, 20f, 20f);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("ATTENDANCE REPORT")
                    .setFont(bold).setFontSize(16).setFontColor(PRIMARY_COLOR).setTextAlignment(TextAlignment.CENTER));
            String dateRange = (startDate != null ? startDate.format(DATE_FORMATTER) : "Start") + " to " + (endDate != null ? endDate.format(DATE_FORMATTER) : "End");
            doc.add(new Paragraph("Period: " + dateRange).setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            Table table = new Table(UnitValue.createPercentArray(new float[]{2, 2, 3, 2, 2, 2, 2, 2, 2, 3})).useAllAvailableWidth();
            String[] headers = {"Date", "Emp Code", "Emp Name", "Department", "In", "Out", "Hours", "Status", "Late (m)", "Remarks"};
            for (String h : headers) {
                table.addHeaderCell(new Cell().add(new Paragraph(h).setFont(bold).setFontSize(9)).setBackgroundColor(HEADER_BG));
            }

            for (AttendanceRecord rec : records) {
                table.addCell(new Cell().add(new Paragraph(rec.getDate() != null ? rec.getDate().format(DATE_FORMATTER) : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(rec.getEmployeeCode() != null ? rec.getEmployeeCode() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(rec.getEmployeeName() != null ? rec.getEmployeeName() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(rec.getDepartment() != null ? rec.getDepartment() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(rec.getCheckInTime() != null ? rec.getCheckInTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "-").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(rec.getCheckOutTime() != null ? rec.getCheckOutTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "-").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(rec.getTotalWorkingHours() != null ? String.format("%.1f", rec.getTotalWorkingHours()) : "0").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(rec.getStatus() != null ? rec.getStatus().name() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(rec.getLateMinutes() != null ? String.valueOf(rec.getLateMinutes()) : "0").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(rec.getRemarks() != null ? rec.getRemarks() : "").setFont(regular).setFontSize(8)));
            }

            doc.add(table);
            PdfWatermarkHelper.applyWatermarkToAllPages(pdfDoc, 0.15f);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Attendance PDF: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public byte[] generateAttendanceReportExcel(LocalDate startDate, LocalDate endDate, String employeeId, String status) throws IOException {
        List<AttendanceRecord> records = fetchFilteredAttendance(startDate, endDate, employeeId, status);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Attendance");
            CellStyle headerStyle = createHeaderStyle(workbook);
            int rowNum = 0;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Date", "Emp Code", "Emp Name", "Department", "Check In", "Check Out", "Working Hours", "Status", "Late (min)", "Early (min)", "Overtime (hrs)", "Remarks"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (AttendanceRecord rec : records) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rec.getDate() != null ? rec.getDate().format(DATE_FORMATTER) : "");
                row.createCell(1).setCellValue(rec.getEmployeeCode() != null ? rec.getEmployeeCode() : "");
                row.createCell(2).setCellValue(rec.getEmployeeName() != null ? rec.getEmployeeName() : "");
                row.createCell(3).setCellValue(rec.getDepartment() != null ? rec.getDepartment() : "");
                row.createCell(4).setCellValue(rec.getCheckInTime() != null ? rec.getCheckInTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "");
                row.createCell(5).setCellValue(rec.getCheckOutTime() != null ? rec.getCheckOutTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "");
                row.createCell(6).setCellValue(rec.getTotalWorkingHours() != null ? rec.getTotalWorkingHours() : 0.0);
                row.createCell(7).setCellValue(rec.getStatus() != null ? rec.getStatus().name() : "");
                row.createCell(8).setCellValue(rec.getLateMinutes() != null ? rec.getLateMinutes() : 0);
                row.createCell(9).setCellValue(rec.getEarlyMinutes() != null ? rec.getEarlyMinutes() : 0);
                row.createCell(10).setCellValue(rec.getOvertimeHours() != null ? rec.getOvertimeHours() : 0.0);
                row.createCell(11).setCellValue(rec.getRemarks() != null ? rec.getRemarks() : "");
            }

            return writeToByteArray(workbook);
        }
    }

    private List<AttendanceRecord> fetchFilteredAttendance(LocalDate startDate, LocalDate endDate, String employeeId, String status) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        List<AttendanceRecord> list;
        if (employeeId != null && !employeeId.isBlank()) {
            if (start.equals(end)) {
                list = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId.trim(), start)
                        .map(List::of)
                        .orElse(List.of());
            } else {
                list = attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(employeeId.trim(), start, end);
            }
        } else {
            if (start.equals(end)) {
                list = attendanceRecordRepository.findByDate(start);
            } else {
                list = attendanceRecordRepository.findByDateBetween(start, end);
            }
        }
        if (status != null && !status.isBlank()) {
            list = list.stream().filter(r -> r.getStatus() != null && r.getStatus().name().equalsIgnoreCase(status)).toList();
        }
        return list;
    }

    // ==========================================
    // 3. LEAVE REPORT EXPORTS
    // ==========================================

    public byte[] generateLeaveReportPdf(String employeeId, String leaveType, String status) {
        List<LeaveRequest> requests = fetchFilteredLeaves(employeeId, leaveType, status);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4.rotate());
            doc.setMargins(20f, 20f, 20f, 20f);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("LEAVE MANAGEMENT REPORT")
                    .setFont(bold).setFontSize(16).setFontColor(PRIMARY_COLOR).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            Table table = new Table(UnitValue.createPercentArray(new float[]{2, 3, 2, 2, 2, 1.5f, 2, 3, 2})).useAllAvailableWidth();
            String[] headers = {"Emp Code", "Emp Name", "Leave Type", "From Date", "To Date", "Days", "Status", "Reason", "Approved By"};
            for (String h : headers) {
                table.addHeaderCell(new Cell().add(new Paragraph(h).setFont(bold).setFontSize(9)).setBackgroundColor(HEADER_BG));
            }

            for (LeaveRequest lr : requests) {
                table.addCell(new Cell().add(new Paragraph(lr.getEmployeeCode() != null ? lr.getEmployeeCode() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(lr.getEmployeeName() != null ? lr.getEmployeeName() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(lr.getLeaveType() != null ? lr.getLeaveType().name() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(lr.getFromDate() != null ? lr.getFromDate().format(DATE_FORMATTER) : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(lr.getToDate() != null ? lr.getToDate().format(DATE_FORMATTER) : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(lr.getNumberOfDays() != null ? String.valueOf(lr.getNumberOfDays()) : "0").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(lr.getStatus() != null ? lr.getStatus().name() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(lr.getReason() != null ? lr.getReason() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(lr.getApprovedBy() != null ? lr.getApprovedBy() : "").setFont(regular).setFontSize(8)));
            }

            doc.add(table);
            PdfWatermarkHelper.applyWatermarkToAllPages(pdfDoc, 0.15f);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Leave PDF: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public byte[] generateLeaveReportExcel(String employeeId, String leaveType, String status) throws IOException {
        List<LeaveRequest> requests = fetchFilteredLeaves(employeeId, leaveType, status);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Leaves");
            CellStyle headerStyle = createHeaderStyle(workbook);
            int rowNum = 0;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Emp Code", "Emp Name", "Leave Type", "From Date", "To Date", "Days", "Status", "Reason", "Approved By"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (LeaveRequest lr : requests) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(lr.getEmployeeCode() != null ? lr.getEmployeeCode() : "");
                row.createCell(1).setCellValue(lr.getEmployeeName() != null ? lr.getEmployeeName() : "");
                row.createCell(2).setCellValue(lr.getLeaveType() != null ? lr.getLeaveType().name() : "");
                row.createCell(3).setCellValue(lr.getFromDate() != null ? lr.getFromDate().format(DATE_FORMATTER) : "");
                row.createCell(4).setCellValue(lr.getToDate() != null ? lr.getToDate().format(DATE_FORMATTER) : "");
                row.createCell(5).setCellValue(lr.getNumberOfDays() != null ? lr.getNumberOfDays() : 0);
                row.createCell(6).setCellValue(lr.getStatus() != null ? lr.getStatus().name() : "");
                row.createCell(7).setCellValue(lr.getReason() != null ? lr.getReason() : "");
                row.createCell(8).setCellValue(lr.getApprovedBy() != null ? lr.getApprovedBy() : "");
            }

            return writeToByteArray(workbook);
        }
    }

    private List<LeaveRequest> fetchFilteredLeaves(String employeeId, String leaveType, String status) {
        List<LeaveRequest> list;
        if (employeeId != null && !employeeId.isBlank()) {
            list = leaveRequestRepository.findByEmployeeIdOrderByAppliedDateDesc(employeeId);
        } else {
            list = leaveRequestRepository.findAll();
        }
        if (leaveType != null && !leaveType.isBlank()) {
            list = list.stream().filter(r -> r.getLeaveType() != null && r.getLeaveType().name().equalsIgnoreCase(leaveType)).toList();
        }
        if (status != null && !status.isBlank()) {
            list = list.stream().filter(r -> r.getStatus() != null && r.getStatus().name().equalsIgnoreCase(status)).toList();
        }
        return list;
    }

    // ==========================================
    // 4. PERMISSION REPORT EXPORTS
    // ==========================================

    public byte[] generatePermissionReportPdf(String employeeId, String status) {
        List<PermissionRequest> requests = fetchFilteredPermissions(employeeId, status);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4.rotate());
            doc.setMargins(20f, 20f, 20f, 20f);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("PERMISSION REQUESTS REPORT")
                    .setFont(bold).setFontSize(16).setFontColor(PRIMARY_COLOR).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            Table table = new Table(UnitValue.createPercentArray(new float[]{2, 3, 2, 2, 2, 2, 3, 2})).useAllAvailableWidth();
            String[] headers = {"Emp Code", "Emp Name", "Date", "Start Time", "End Time", "Status", "Reason", "Approved By"};
            for (String h : headers) {
                table.addHeaderCell(new Cell().add(new Paragraph(h).setFont(bold).setFontSize(9)).setBackgroundColor(HEADER_BG));
            }

            for (PermissionRequest pr : requests) {
                table.addCell(new Cell().add(new Paragraph(pr.getEmployeeCode() != null ? pr.getEmployeeCode() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getEmployeeName() != null ? pr.getEmployeeName() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getDate() != null ? pr.getDate().format(DATE_FORMATTER) : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getStartTime() != null ? pr.getStartTime().toString() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getEndTime() != null ? pr.getEndTime().toString() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getStatus() != null ? pr.getStatus().name() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getReason() != null ? pr.getReason() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getApprovedBy() != null ? pr.getApprovedBy() : "").setFont(regular).setFontSize(8)));
            }

            doc.add(table);
            PdfWatermarkHelper.applyWatermarkToAllPages(pdfDoc, 0.15f);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Permission PDF: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public byte[] generatePermissionReportExcel(String employeeId, String status) throws IOException {
        List<PermissionRequest> requests = fetchFilteredPermissions(employeeId, status);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Permissions");
            CellStyle headerStyle = createHeaderStyle(workbook);
            int rowNum = 0;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Emp Code", "Emp Name", "Date", "Start Time", "End Time", "Duration (hrs)", "Status", "Reason", "Approved By"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (PermissionRequest pr : requests) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(pr.getEmployeeCode() != null ? pr.getEmployeeCode() : "");
                row.createCell(1).setCellValue(pr.getEmployeeName() != null ? pr.getEmployeeName() : "");
                row.createCell(2).setCellValue(pr.getDate() != null ? pr.getDate().format(DATE_FORMATTER) : "");
                row.createCell(3).setCellValue(pr.getStartTime() != null ? pr.getStartTime().toString() : "");
                row.createCell(4).setCellValue(pr.getEndTime() != null ? pr.getEndTime().toString() : "");
                row.createCell(5).setCellValue(pr.getDurationMinutes() != null ? (pr.getDurationMinutes() / 60.0) : 0.0);
                row.createCell(6).setCellValue(pr.getStatus() != null ? pr.getStatus().name() : "");
                row.createCell(7).setCellValue(pr.getReason() != null ? pr.getReason() : "");
                row.createCell(8).setCellValue(pr.getApprovedBy() != null ? pr.getApprovedBy() : "");
            }

            return writeToByteArray(workbook);
        }
    }

    private List<PermissionRequest> fetchFilteredPermissions(String employeeId, String status) {
        List<PermissionRequest> list;
        if (employeeId != null && !employeeId.isBlank()) {
            list = permissionRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        } else {
            list = permissionRepository.findAll();
        }
        if (status != null && !status.isBlank()) {
            list = list.stream().filter(r -> r.getStatus() != null && r.getStatus().name().equalsIgnoreCase(status)).toList();
        }
        return list;
    }

    // ==========================================
    // 5. PAYROLL REPORT EXPORTS
    // ==========================================

    public byte[] generatePayrollReportPdf(String month) {
        List<PayrollRecord> payrolls = month != null && !month.isBlank() ? payrollRepository.findByPayrollMonth(month) : payrollRepository.findAll();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4.rotate());
            doc.setMargins(20f, 20f, 20f, 20f);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("PAYROLL REPORT - " + (month != null ? month.toUpperCase() : "ALL"))
                    .setFont(bold).setFontSize(16).setFontColor(PRIMARY_COLOR).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            Table table = new Table(UnitValue.createPercentArray(new float[]{2, 2, 3, 2, 2, 2, 2, 2, 2, 2})).useAllAvailableWidth();
            String[] headers = {"Payslip No", "Emp Code", "Emp Name", "Month", "Gross", "Deductions", "Net Salary", "Status", "Bank Name", "Account No"};
            for (String h : headers) {
                table.addHeaderCell(new Cell().add(new Paragraph(h).setFont(bold).setFontSize(9)).setBackgroundColor(HEADER_BG));
            }

            for (PayrollRecord pr : payrolls) {
                table.addCell(new Cell().add(new Paragraph(pr.getPayslipNumber() != null ? pr.getPayslipNumber() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getEmployeeCode() != null ? pr.getEmployeeCode() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getEmployeeName() != null ? pr.getEmployeeName() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getPayrollMonth() != null ? pr.getPayrollMonth() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getGrossSalary() != null ? pr.getGrossSalary().toString() : "0").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getTotalDeductions() != null ? pr.getTotalDeductions().toString() : "0").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getNetSalary() != null ? pr.getNetSalary().toString() : "0").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getStatus() != null ? pr.getStatus().name() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getBankName() != null ? pr.getBankName() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(pr.getAccountNumber() != null ? pr.getAccountNumber() : "").setFont(regular).setFontSize(8)));
            }

            doc.add(table);
            PdfWatermarkHelper.applyWatermarkToAllPages(pdfDoc, 0.15f);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Payroll PDF: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public byte[] generatePayrollReportExcel(String month) throws IOException {
        List<PayrollRecord> payrolls = month != null && !month.isBlank() ? payrollRepository.findByPayrollMonth(month) : payrollRepository.findAll();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Payroll");
            CellStyle headerStyle = createHeaderStyle(workbook);
            int rowNum = 0;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Payslip No", "Emp Code", "Emp Name", "Department", "Month", "Basic", "HRA", "Allowances", "Overtime", "Bonus", "Gross", "PF", "Tax", "LOP", "Advance Rec", "Net Salary", "Bank Name", "Account No"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (PayrollRecord pr : payrolls) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(pr.getPayslipNumber() != null ? pr.getPayslipNumber() : "");
                row.createCell(1).setCellValue(pr.getEmployeeCode() != null ? pr.getEmployeeCode() : "");
                row.createCell(2).setCellValue(pr.getEmployeeName() != null ? pr.getEmployeeName() : "");
                row.createCell(3).setCellValue(pr.getDepartment() != null ? pr.getDepartment() : "");
                row.createCell(4).setCellValue(pr.getPayrollMonth() != null ? pr.getPayrollMonth() : "");
                row.createCell(5).setCellValue(pr.getBasicSalary() != null ? pr.getBasicSalary().doubleValue() : 0);
                row.createCell(6).setCellValue(pr.getHra() != null ? pr.getHra().doubleValue() : 0);
                row.createCell(7).setCellValue(pr.getAllowances() != null ? pr.getAllowances().doubleValue() : 0);
                row.createCell(8).setCellValue(pr.getOvertimeAmount() != null ? pr.getOvertimeAmount().doubleValue() : 0);
                row.createCell(9).setCellValue(pr.getBonusAmount() != null ? pr.getBonusAmount().doubleValue() : 0);
                row.createCell(10).setCellValue(pr.getGrossSalary() != null ? pr.getGrossSalary().doubleValue() : 0);
                row.createCell(11).setCellValue(pr.getPfAmount() != null ? pr.getPfAmount().doubleValue() : 0);
                row.createCell(12).setCellValue(pr.getTaxAmount() != null ? pr.getTaxAmount().doubleValue() : 0);
                row.createCell(13).setCellValue(pr.getLopAmount() != null ? pr.getLopAmount().doubleValue() : 0);
                row.createCell(14).setCellValue(pr.getAdvanceDeductionAmount() != null ? pr.getAdvanceDeductionAmount().doubleValue() : 0);
                row.createCell(15).setCellValue(pr.getNetSalary() != null ? pr.getNetSalary().doubleValue() : 0);
                row.createCell(16).setCellValue(pr.getBankName() != null ? pr.getBankName() : "");
                row.createCell(17).setCellValue(pr.getAccountNumber() != null ? pr.getAccountNumber() : "");
            }

            return writeToByteArray(workbook);
        }
    }

    // ==========================================
    // 6. SALARY ADVANCE REPORT EXPORTS
    // ==========================================

    public byte[] generateSalaryAdvanceReportPdf(String employeeId, String status) {
        List<EmployeeAdvanceAccount> accounts = fetchFilteredAdvanceAccounts(employeeId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4.rotate());
            doc.setMargins(20f, 20f, 20f, 20f);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("EMPLOYEE SALARY ADVANCE ACCOUNT REPORT")
                    .setFont(bold).setFontSize(16).setFontColor(PRIMARY_COLOR).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            Table table = new Table(UnitValue.createPercentArray(new float[]{2, 3, 3, 3, 3, 3, 2})).useAllAvailableWidth();
            String[] headers = {"Emp Code", "Emp Name", "Total Advance Given", "Total Direct Repaid", "Payroll Deducted", "Outstanding Balance", "Status"};
            for (String h : headers) {
                table.addHeaderCell(new Cell().add(new Paragraph(h).setFont(bold).setFontSize(9)).setBackgroundColor(HEADER_BG));
            }

            for (EmployeeAdvanceAccount acc : accounts) {
                table.addCell(new Cell().add(new Paragraph(acc.getEmployeeCode() != null ? acc.getEmployeeCode() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(acc.getEmployeeName() != null ? acc.getEmployeeName() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph("₹" + (acc.getTotalAdvanceGiven() != null ? acc.getTotalAdvanceGiven() : 0)).setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph("₹" + (acc.getTotalRepaid() != null ? acc.getTotalRepaid() : 0)).setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph("₹" + (acc.getTotalPayrollDeducted() != null ? acc.getTotalPayrollDeducted() : 0)).setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph("₹" + (acc.getOutstandingBalance() != null ? acc.getOutstandingBalance() : 0)).setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(acc.getStatus() != null ? acc.getStatus() : "ACTIVE").setFont(regular).setFontSize(8)));
            }

            doc.add(table);
            PdfWatermarkHelper.applyWatermarkToAllPages(pdfDoc, 0.15f);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Advance PDF: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public byte[] generateSalaryAdvanceReportExcel(String employeeId, String status) throws IOException {
        List<EmployeeAdvanceAccount> accounts = fetchFilteredAdvanceAccounts(employeeId);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Salary Advances");
            CellStyle headerStyle = createHeaderStyle(workbook);
            int rowNum = 0;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Emp Code", "Emp Name", "Total Advance Given", "Total Direct Repaid", "Total Payroll Deducted", "Outstanding Balance", "Status"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (EmployeeAdvanceAccount acc : accounts) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(acc.getEmployeeCode() != null ? acc.getEmployeeCode() : "");
                row.createCell(1).setCellValue(acc.getEmployeeName() != null ? acc.getEmployeeName() : "");
                row.createCell(2).setCellValue(acc.getTotalAdvanceGiven() != null ? acc.getTotalAdvanceGiven().doubleValue() : 0);
                row.createCell(3).setCellValue(acc.getTotalRepaid() != null ? acc.getTotalRepaid().doubleValue() : 0);
                row.createCell(4).setCellValue(acc.getTotalPayrollDeducted() != null ? acc.getTotalPayrollDeducted().doubleValue() : 0);
                row.createCell(5).setCellValue(acc.getOutstandingBalance() != null ? acc.getOutstandingBalance().doubleValue() : 0);
                row.createCell(6).setCellValue(acc.getStatus() != null ? acc.getStatus() : "ACTIVE");
            }

            return writeToByteArray(workbook);
        }
    }

    private List<EmployeeAdvanceAccount> fetchFilteredAdvanceAccounts(String employeeId) {
        if (employeeId != null && !employeeId.isBlank()) {
            return employeeAdvanceAccountRepository.findByEmployeeId(employeeId).map(List::of).orElseGet(List::of);
        } else {
            return employeeAdvanceAccountRepository.findAll();
        }
    }

    // ==========================================
    // 7. BIOMETRIC DEVICE REPORT EXPORTS
    // ==========================================

    public byte[] generateBiometricDeviceReportPdf() {
        List<AttendanceDevice> devices = deviceRepository.findAll();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(20f, 20f, 20f, 20f);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("BIOMETRIC DEVICES REPORT")
                    .setFont(bold).setFontSize(16).setFontColor(PRIMARY_COLOR).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            Table table = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2, 2, 2, 2})).useAllAvailableWidth();
            String[] headers = {"Device Name", "Model", "IP Address", "Serial No", "Status", "Last Sync"};
            for (String h : headers) {
                table.addHeaderCell(new Cell().add(new Paragraph(h).setFont(bold).setFontSize(9)).setBackgroundColor(HEADER_BG));
            }

            for (AttendanceDevice dev : devices) {
                table.addCell(new Cell().add(new Paragraph(dev.getDeviceName() != null ? dev.getDeviceName() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(dev.getDeviceId() != null ? dev.getDeviceId() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(dev.getDeviceIp() != null ? dev.getDeviceIp() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(dev.getSerialNumber() != null ? dev.getSerialNumber() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(dev.getStatus() != null ? dev.getStatus().name() : "").setFont(regular).setFontSize(8)));
                table.addCell(new Cell().add(new Paragraph(dev.getLastSyncTime() != null ? dev.getLastSyncTime().toString() : "Never").setFont(regular).setFontSize(8)));
            }

            doc.add(table);
            PdfWatermarkHelper.applyWatermarkToAllPages(pdfDoc, 0.15f);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Biometric Device PDF: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public byte[] generateBiometricDeviceReportExcel() throws IOException {
        List<AttendanceDevice> devices = deviceRepository.findAll();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Biometric Devices");
            CellStyle headerStyle = createHeaderStyle(workbook);
            int rowNum = 0;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Device Name", "Device ID", "IP Address", "Port", "Serial Number", "Status", "Last Sync Time"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (AttendanceDevice dev : devices) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(dev.getDeviceName() != null ? dev.getDeviceName() : "");
                row.createCell(1).setCellValue(dev.getDeviceId() != null ? dev.getDeviceId() : "");
                row.createCell(2).setCellValue(dev.getDeviceIp() != null ? dev.getDeviceIp() : "");
                row.createCell(3).setCellValue(dev.getPort() != null ? dev.getPort() : 0);
                row.createCell(4).setCellValue(dev.getSerialNumber() != null ? dev.getSerialNumber() : "");
                row.createCell(5).setCellValue(dev.getStatus() != null ? dev.getStatus().name() : "");
                row.createCell(6).setCellValue(dev.getLastSyncTime() != null ? dev.getLastSyncTime().toString() : "Never");
            }

            return writeToByteArray(workbook);
        }
    }

    // ==========================================
    // 8. COMPANY SETTINGS REPORT EXPORTS
    // ==========================================

    public byte[] generateCompanySettingsReportPdf() {
        CompanySettingsDto cs = companySettingsService.getSettings();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(20f, 20f, 20f, 20f);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("COMPANY WORKING HOURS & LEAVE CONFIGURATION REPORT")
                    .setFont(bold).setFontSize(14).setFontColor(PRIMARY_COLOR).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            Table table = new Table(UnitValue.createPercentArray(new float[]{4, 6})).useAllAvailableWidth();

            addSettingRow(table, "Company Name", cs.getCompanyName(), bold, regular);
            addSettingRow(table, "Work Start Time", cs.getWorkingStartTime(), bold, regular);
            addSettingRow(table, "Work End Time", cs.getWorkingEndTime(), bold, regular);
            addSettingRow(table, "Working Hours / Day", String.valueOf(cs.getWorkingHoursPerDay()), bold, regular);
            addSettingRow(table, "Late Grace Period", cs.getLateGracePeriodMinutes() + " mins", bold, regular);
            addSettingRow(table, "Early Checkout Grace", cs.getEarlyCheckoutGracePeriodMinutes() + " mins", bold, regular);
            addSettingRow(table, "Break Duration", cs.getBreakDurationMinutes() + " mins", bold, regular);
            addSettingRow(table, "Overtime Threshold", cs.getOvertimeThresholdMinutes() + " mins", bold, regular);
            addSettingRow(table, "Working Days", cs.getWorkingDays() != null ? String.join(", ", cs.getWorkingDays()) : "", bold, regular);
            addSettingRow(table, "Weekend Days", cs.getWeekendDays() != null ? String.join(", ", cs.getWeekendDays()) : "", bold, regular);
            addSettingRow(table, "Casual Leave Quota / Year", String.valueOf(cs.getCasualLeaveEntitlementPerYear()), bold, regular);
            addSettingRow(table, "Medical Leave Quota / Year", String.valueOf(cs.getMedicalLeaveEntitlementPerYear()), bold, regular);
            addSettingRow(table, "Emergency Leave Quota / Year", String.valueOf(cs.getEmergencyLeaveEntitlementPerYear()), bold, regular);
            addSettingRow(table, "Comp Off Quota / Year", String.valueOf(cs.getCompOffEntitlementPerYear()), bold, regular);
            addSettingRow(table, "Overtime Multiplier", String.valueOf(cs.getOvertimeHourlyRateMultiplier()), bold, regular);
            addSettingRow(table, "LOP Calculation Method", cs.getLopDailyCalculationMethod(), bold, regular);

            doc.add(table);
            PdfWatermarkHelper.applyWatermarkToAllPages(pdfDoc, 0.15f);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Settings PDF: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public byte[] generateCompanySettingsReportExcel() throws IOException {
        CompanySettingsDto cs = companySettingsService.getSettings();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Settings");
            CellStyle headerStyle = createHeaderStyle(workbook);
            int rowNum = 0;

            Row headerRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell c1 = headerRow.createCell(0); c1.setCellValue("Setting Key"); c1.setCellStyle(headerStyle);
            org.apache.poi.ss.usermodel.Cell c2 = headerRow.createCell(1); c2.setCellValue("Setting Value"); c2.setCellStyle(headerStyle);

            addExcelSettingRow(sheet, rowNum++, "Company Name", cs.getCompanyName());
            addExcelSettingRow(sheet, rowNum++, "Work Start Time", cs.getWorkingStartTime());
            addExcelSettingRow(sheet, rowNum++, "Work End Time", cs.getWorkingEndTime());
            addExcelSettingRow(sheet, rowNum++, "Working Hours / Day", String.valueOf(cs.getWorkingHoursPerDay()));
            addExcelSettingRow(sheet, rowNum++, "Late Grace Period (mins)", String.valueOf(cs.getLateGracePeriodMinutes()));
            addExcelSettingRow(sheet, rowNum++, "Early Grace Period (mins)", String.valueOf(cs.getEarlyCheckoutGracePeriodMinutes()));
            addExcelSettingRow(sheet, rowNum++, "Break Duration (mins)", String.valueOf(cs.getBreakDurationMinutes()));
            addExcelSettingRow(sheet, rowNum++, "Overtime Threshold (mins)", String.valueOf(cs.getOvertimeThresholdMinutes()));
            addExcelSettingRow(sheet, rowNum++, "Working Days", cs.getWorkingDays() != null ? String.join(", ", cs.getWorkingDays()) : "");
            addExcelSettingRow(sheet, rowNum++, "Weekend Days", cs.getWeekendDays() != null ? String.join(", ", cs.getWeekendDays()) : "");
            addExcelSettingRow(sheet, rowNum++, "Casual Leave Quota / Year", String.valueOf(cs.getCasualLeaveEntitlementPerYear()));
            addExcelSettingRow(sheet, rowNum++, "Medical Leave Quota / Year", String.valueOf(cs.getMedicalLeaveEntitlementPerYear()));
            addExcelSettingRow(sheet, rowNum++, "Emergency Leave Quota / Year", String.valueOf(cs.getEmergencyLeaveEntitlementPerYear()));
            addExcelSettingRow(sheet, rowNum++, "Comp Off Quota / Year", String.valueOf(cs.getCompOffEntitlementPerYear()));
            addExcelSettingRow(sheet, rowNum++, "Overtime Multiplier", String.valueOf(cs.getOvertimeHourlyRateMultiplier()));
            addExcelSettingRow(sheet, rowNum++, "LOP Calculation Method", cs.getLopDailyCalculationMethod());

            return writeToByteArray(workbook);
        }
    }

    private void addSettingRow(Table table, String key, String val, PdfFont bold, PdfFont regular) {
        table.addCell(new Cell().add(new Paragraph(key).setFont(bold).setFontSize(9)).setBackgroundColor(HEADER_BG));
        table.addCell(new Cell().add(new Paragraph(val != null ? val : "").setFont(regular).setFontSize(9)));
    }

    private void addExcelSettingRow(Sheet sheet, int rowNum, String key, String val) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(val != null ? val : "");
    }

    private CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private byte[] writeToByteArray(SXSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
}

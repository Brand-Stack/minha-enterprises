package com.app.billing.controller;

import com.app.billing.model.Holiday;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.HolidayService;
import com.app.billing.service.PermissionEvaluatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/holidays")
@RequiredArgsConstructor
@Tag(name = "Holiday Calendar Management", description = "Company Holiday Calendar management APIs")
public class HolidayController {

    private final HolidayService holidayService;
    private final PermissionEvaluatorService permissionEvaluatorService;

    @GetMapping
    @Operation(summary = "Get Holidays", description = "Retrieve holiday calendar entries for specified year or date range")
    public ResponseEntity<List<Holiday>> getHolidays(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(holidayService.getHolidaysBetween(startDate, endDate));
        }
        return ResponseEntity.ok(holidayService.getHolidaysForYear(year));
    }

    @PostMapping
    @RequiresPermission(module = Modules.ATTENDANCE_CONFIG, action = Modules.CREATE)
    @Operation(summary = "Create Holiday", description = "Add a new company holiday to the calendar")
    public ResponseEntity<Holiday> createHoliday(@RequestBody Holiday holiday) {
        var current = permissionEvaluatorService.currentEmployee();
        String username = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(holidayService.saveHoliday(holiday, username));
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.ATTENDANCE_CONFIG, action = Modules.EDIT)
    @Operation(summary = "Update Holiday", description = "Update existing holiday details")
    public ResponseEntity<Holiday> updateHoliday(@PathVariable String id, @RequestBody Holiday holiday) {
        holiday.setId(id);
        var current = permissionEvaluatorService.currentEmployee();
        String username = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(holidayService.saveHoliday(holiday, username));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.ATTENDANCE_CONFIG, action = Modules.DELETE)
    @Operation(summary = "Delete Holiday", description = "Delete or cancel a holiday entry")
    public ResponseEntity<Void> deleteHoliday(@PathVariable String id) {
        var current = permissionEvaluatorService.currentEmployee();
        String username = current != null ? current.getEmail() : "Admin";
        holidayService.deleteHoliday(id, username);
        return ResponseEntity.ok().build();
    }
}

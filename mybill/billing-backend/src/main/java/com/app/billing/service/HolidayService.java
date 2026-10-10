package com.app.billing.service;

import com.app.billing.dao.HolidayRepository;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Holiday;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final AuditService auditService;

    public List<Holiday> getHolidaysForYear(Integer year) {
        int targetYear = (year != null && year > 1900) ? year : LocalDate.now().getYear();
        return holidayRepository.findByYearOrderByHolidayDateAsc(targetYear);
    }

    public List<Holiday> getHolidaysBetween(LocalDate startDate, LocalDate endDate) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(startDate, endDate);
    }

    public Holiday saveHoliday(Holiday holiday, String adminUsername) {
        if (holiday.getHolidayDate() == null) {
            throw new IllegalArgumentException("Holiday date is required");
        }
        if ((holiday.getHolidayName() == null || holiday.getHolidayName().isBlank()) && holiday.getName() != null) {
            holiday.setHolidayName(holiday.getName());
        }
        if (holiday.getHolidayName() == null || holiday.getHolidayName().isBlank()) {
            throw new IllegalArgumentException("Holiday name is required");
        }
        if ((holiday.getHolidayType() == null || holiday.getHolidayType().isBlank()) && holiday.getType() != null) {
            holiday.setHolidayType(holiday.getType());
        }

        int year = holiday.getHolidayDate().getYear();
        holiday.setYear(year);
        if (holiday.getActive() == null) {
            holiday.setActive(true);
        }

        // Duplicate date validation
        Optional<Holiday> existing = holidayRepository.findByHolidayDate(holiday.getHolidayDate());
        if (existing.isPresent()) {
            if (holiday.getId() == null || !holiday.getId().equals(existing.get().getId())) {
                throw new IllegalArgumentException("A holiday is already configured for date: " + holiday.getHolidayDate());
            }
        }

        holiday.setCreatedBy(adminUsername);
        Holiday saved = holidayRepository.save(holiday);
        auditService.log("SAVE_HOLIDAY", "ATTENDANCE_CONFIG", saved.getId(), "Configured holiday " + saved.getHolidayName() + " on " + saved.getHolidayDate());
        return saved;
    }

    public void deleteHoliday(String id, String adminUsername) {
        Holiday existing = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found with ID: " + id));

        holidayRepository.deleteById(id);
        auditService.log("DELETE_HOLIDAY", "ATTENDANCE_CONFIG", id, "Deleted holiday " + existing.getHolidayName() + " (" + existing.getHolidayDate() + ") by " + adminUsername);
    }
}

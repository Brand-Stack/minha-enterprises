package com.app.billing.service;

import com.app.billing.model.InvoiceSequenceCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InvoiceSequenceService {

    private final MongoTemplate mongoTemplate;
    private final CompanySettingsService companySettingsService;

    /** Financial year start year as string (April–March). */
    public String financialYearStartYear(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        int year = d.getYear();
        int month = d.getMonthValue();
        int fyStart = (month >= 4) ? year : year - 1;
        return String.valueOf(fyStart);
    }

    /**
     * Atomically allocate the next invoice sequence for the financial year.
     */
    public int getNextSequence(String fy) {
        Query query = new Query(Criteria.where("fy").is(fy));
        Update update = new Update().inc("lastSequence", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(false);
        
        // Try to increment if it already exists
        InvoiceSequenceCounter updated = mongoTemplate.findAndModify(query, update, options, InvoiceSequenceCounter.class);
        if (updated != null) {
            return updated.getLastSequence();
        }

        // If it doesn't exist, try to initialize it
        int startingSequence = resolveStartingSequence();
        InvoiceSequenceCounter counter = new InvoiceSequenceCounter();
        counter.setId(fy);
        counter.setFy(fy);
        counter.setLastSequence(startingSequence);

        try {
            mongoTemplate.insert(counter);
            return startingSequence;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Concurrent execution: another thread created it, now we safely increment it
            updated = mongoTemplate.findAndModify(query, update, options, InvoiceSequenceCounter.class);
            if (updated == null) {
                throw new IllegalStateException("Failed to allocate invoice sequence for FY " + fy);
            }
            return updated.getLastSequence();
        }
    }

    public String formatInvoiceNumber(int sequence, String fy) {
        int padWidth = resolvePadWidth();
        return String.format("%0" + padWidth + "d/%s", sequence, fy);
    }

    public String generateNextInvoiceNumber(LocalDate invoiceDate) {
        String fy = financialYearStartYear(invoiceDate);
        int seq = getNextSequence(fy);
        return formatInvoiceNumber(seq, fy);
    }

    private int resolveStartingSequence() {
        var settings = companySettingsService.getSettings();
        if (settings.getInvoiceStartingSequence() != null && settings.getInvoiceStartingSequence() > 0) {
            return settings.getInvoiceStartingSequence();
        }
        return 1;
    }

    private int resolvePadWidth() {
        var settings = companySettingsService.getSettings();
        Integer start = settings.getInvoiceStartingSequence();
        if (start != null && start > 0) {
            return Math.max(3, String.valueOf(start).length());
        }
        return 3;
    }
}

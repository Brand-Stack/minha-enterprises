package com.app.billing.service;

import com.app.billing.model.InvoiceSequenceCounter;
import lombok.RequiredArgsConstructor;
import com.app.billing.model.MonthlyCourierQuotation;
import com.app.billing.model.SmallClientEntryQuotation;
import com.app.billing.model.CompanySettings;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

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
        int startingSequence = resolveStartingSequenceForFy(fy);
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

    public synchronized String generateNextInvoiceNumber(LocalDate invoiceDate) {
        String fy = financialYearStartYear(invoiceDate);
        var settings = companySettingsService.getSettings();

        int maxSeq = getMaxSequenceFromDb(fy);
        int seq;
        if (maxSeq > 0) {
            seq = maxSeq + 1;
        } else {
            // No invoices generated yet for this financial year
            if ("MANUAL".equalsIgnoreCase(settings.getInvoiceNumberMode())) {
                seq = resolveStartingSequenceForFy(fy);
            } else {
                seq = 1;
            }
        }

        return formatInvoiceNumber(seq, fy);
    }

    private int getMaxSequenceFromDb(String fy) {
        String patternStr = "^\\d+/" + Pattern.quote(fy) + "$";
        Query query = new Query();
        query.addCriteria(Criteria.where("invoiceNumber").regex(patternStr));
        query.fields().include("invoiceNumber");

        List<MonthlyCourierQuotation> list1 = mongoTemplate.find(query, MonthlyCourierQuotation.class);
        List<SmallClientEntryQuotation> list2 = mongoTemplate.find(query, SmallClientEntryQuotation.class);

        int maxSeq = 0;
        for (MonthlyCourierQuotation q : list1) {
            String invNum = q.getInvoiceNumber();
            if (invNum != null) {
                int seq = parseSequence(invNum);
                if (seq > maxSeq) {
                    maxSeq = seq;
                }
            }
        }
        for (SmallClientEntryQuotation q : list2) {
            String invNum = q.getInvoiceNumber();
            if (invNum != null) {
                int seq = parseSequence(invNum);
                if (seq > maxSeq) {
                    maxSeq = seq;
                }
            }
        }
        return maxSeq;
    }

    private int parseSequence(String invoiceNumber) {
        try {
            int slashIndex = invoiceNumber.indexOf('/');
            if (slashIndex > 0) {
                return Integer.parseInt(invoiceNumber.substring(0, slashIndex));
            }
        } catch (NumberFormatException e) {
            // Ignore malformed numbers
        }
        return 0;
    }

    private int resolveStartingSequenceForFy(String fy) {
        var settings = companySettingsService.getSettings();
        int configuredStart = 1;

        String prefix = settings.getInvoicePrefix();
        if (prefix != null && prefix.matches("^\\d+$")) {
            configuredStart = Integer.parseInt(prefix) + 1;
        } else if (settings.getInvoiceStartingSequence() != null && settings.getInvoiceStartingSequence() > 0) {
            configuredStart = settings.getInvoiceStartingSequence();
        }

        String settingsFy = null;
        Query query = new Query().with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).limit(1);
        CompanySettings settingsEntity = mongoTemplate.findOne(query, CompanySettings.class);
        if (settingsEntity != null) {
            if (settingsEntity.getUpdatedAt() != null) {
                settingsFy = financialYearStartYear(settingsEntity.getUpdatedAt().toLocalDate());
            } else if (settingsEntity.getCreatedAt() != null) {
                settingsFy = financialYearStartYear(settingsEntity.getCreatedAt().toLocalDate());
            }
        }
        if (settingsFy == null) {
            settingsFy = financialYearStartYear(LocalDate.now());
        }

        if (fy.equals(settingsFy)) {
            return configuredStart;
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

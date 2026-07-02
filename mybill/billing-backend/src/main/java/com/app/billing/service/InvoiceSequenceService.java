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

    public String formatInvoiceNumber(int sequence, String fy) {
        return String.format("%03d/%s", sequence, fy);
    }

    public synchronized String generateNextInvoiceNumber(LocalDate invoiceDate) {
        String fyString = financialYearStartYear(invoiceDate);
        int fy = Integer.parseInt(fyString);

        // Fetch settings directly from MongoDB to be thread-safe/fresh
        CompanySettings settings = mongoTemplate.findOne(new Query().limit(1), CompanySettings.class);
        if (settings == null) {
            settings = new CompanySettings();
            settings.setInvoiceNumberMode("AUTO");
        }

        int settingsYear = settings.getYear() != null ? settings.getYear() : 0;
        int settingsLastNo = settings.getLastSeriesNo() != null ? settings.getLastSeriesNo() : 0;

        int maxSeq = getMaxSequenceFromDb(fyString);
        int seq;

        if (fy == settingsYear) {
            seq = Math.max(settingsLastNo, maxSeq) + 1;
        } else if (fy > settingsYear) {
            // New financial year transition
            seq = maxSeq + 1;
        } else {
            // Backdated invoice for past financial year
            seq = maxSeq + 1;
        }

        if (fy >= settingsYear) {
            settings.setLastSeriesNo(seq);
            settings.setYear(fy);
            mongoTemplate.save(settings);
        }

        return formatInvoiceNumber(seq, fyString);
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
}

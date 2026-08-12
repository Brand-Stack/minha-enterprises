package com.app.billing.service;

import com.app.billing.dao.GstOutRecordRepository;
import com.app.billing.model.GstOutRecord;
import com.app.billing.util.AuditUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GstOutRecordService {
    private final GstOutRecordRepository repository;
    private final AuditUtil auditUtil;

    public List<GstOutRecord> findAll() {
        return repository.findAll();
    }

    public GstOutRecord save(GstOutRecord record) {
        // Auto-calculate CGST, SGST, and GST Total if they are null or not set
        if (record.getBillingAmount() != null) {
            double amt = record.getBillingAmount();
            if (record.getCgst() == null) {
                record.setCgst(Math.round((amt * 0.09) * 100.0) / 100.0);
            }
            if (record.getSgst() == null) {
                record.setSgst(Math.round((amt * 0.09) * 100.0) / 100.0);
            }
            if (record.getGstTotal() == null) {
                record.setGstTotal(Math.round((record.getCgst() + record.getSgst()) * 100.0) / 100.0);
            }
        }
        if (record.getId() == null || record.getId().isBlank()) {
            record.setId(null); // Ensure null ID for new document
            auditUtil.setCreatedBy(record);
        } else {
            auditUtil.setUpdatedBy(record);
        }
        return repository.save(record);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }
}

package com.app.billing.service;

import com.app.billing.dao.ClientRepository;
import com.app.billing.dao.PaymentOutRepository;
import com.app.billing.dto.PageResponse;
import com.app.billing.dto.PaymentOutDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Client;
import com.app.billing.model.PaymentOut;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PaymentOutService {
    
    private final PaymentOutRepository paymentOutRepository;
    private final ClientRepository clientRepository;
    
    @Transactional
    public PaymentOutDto create(PaymentOutDto dto) {
        Client client = clientRepository.findById(dto.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + dto.getPartyId()));
        
        PaymentOut payment = toEntity(dto);
        payment.setPartyName(client.getPartyName());
        
        if (payment.getDate() == null) {
            payment.setDate(LocalDate.now());
        }
        
        // Auto-generate receipt number if not provided
        if (payment.getReceiptNumber() == null || payment.getReceiptNumber().trim().isEmpty()) {
            payment.setReceiptNumber(generateNextReceiptNumber());
        }
        
        PaymentOut saved = paymentOutRepository.save(payment);
        return toDto(saved);
    }
    
    public PaymentOutDto findById(String id) {
        PaymentOut payment = paymentOutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment-Out not found with id: " + id));
        return toDto(payment);
    }
    
    @Transactional
    public PaymentOutDto update(String id, PaymentOutDto dto) {
        PaymentOut existing = paymentOutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment-Out not found with id: " + id));
        
        Client client = clientRepository.findById(dto.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + dto.getPartyId()));
        
        PaymentOut payment = toEntity(dto);
        payment.setId(id);
        payment.setPartyName(client.getPartyName());
        
        PaymentOut saved = paymentOutRepository.save(payment);
        return toDto(saved);
    }
    
    public PageResponse<PaymentOutDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<PaymentOut> payments = paymentOutRepository.findAll(pageable);
        return PaginationUtil.toPageResponse(payments.map(this::toDto));
    }
    
    public PageResponse<PaymentOutDto> findByDateRange(LocalDate startDate, LocalDate endDate, int page, int size) {
        Pageable pageable = PaginationUtil.createPageable(page, size, "date", "desc");
        Page<PaymentOut> payments = paymentOutRepository.findByDateBetween(startDate, endDate, pageable);
        return PaginationUtil.toPageResponse(payments.map(this::toDto));
    }
    
    @Transactional
    public void delete(String id) {
        PaymentOut payment = paymentOutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment-Out not found with id: " + id));
        paymentOutRepository.delete(payment);
    }
    
    private String generateNextReceiptNumber() {
        long count = paymentOutRepository.count();
        return String.valueOf(count + 1);
    }
    
    private PaymentOut toEntity(PaymentOutDto dto) {
        return PaymentOut.builder()
                .receiptNumber(dto.getReceiptNumber())
                .date(dto.getDate())
                .partyId(dto.getPartyId())
                .partyName(dto.getPartyName())
                .paymentType(dto.getPaymentType())
                .paidAmount(dto.getPaidAmount())
                .description(dto.getDescription())
                .uploadedBillFile(dto.getUploadedBillFile())
                .referenceNumber(dto.getReferenceNumber())
                .build();
    }
    
    private PaymentOutDto toDto(PaymentOut payment) {
        PaymentOutDto dto = new PaymentOutDto();
        dto.setId(payment.getId());
        dto.setReceiptNumber(payment.getReceiptNumber());
        dto.setDate(payment.getDate());
        dto.setPartyId(payment.getPartyId());
        dto.setPartyName(payment.getPartyName());
        dto.setPaymentType(payment.getPaymentType());
        dto.setPaidAmount(payment.getPaidAmount());
        dto.setDescription(payment.getDescription());
        dto.setUploadedBillFile(payment.getUploadedBillFile());
        dto.setReferenceNumber(payment.getReferenceNumber());
        return dto;
    }
}


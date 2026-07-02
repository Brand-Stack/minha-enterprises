package com.app.billing.service;

import com.app.billing.dao.CollectionCustomerRepository;
import com.app.billing.dto.CollectionCustomerDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.CollectionCustomer;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.CodeGenerator;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionCustomerService {

    private static boolean isActiveCustomer(CollectionCustomer c) {
        return !Boolean.FALSE.equals(c.getActive());
    }

    private final CollectionCustomerRepository repository;
    private final CodeGenerator codeGenerator;
    private final AuditUtil auditUtil;
    private final CollectionCustomerAwbService collectionCustomerAwbService;

    @Transactional
    public CollectionCustomerDto create(CollectionCustomerDto dto) {
        String last = repository.findAll().stream()
                .map(CollectionCustomer::getCustomerCode)
                .filter(c -> c != null && c.startsWith("COL"))
                .max(Comparator.naturalOrder())
                .orElse(null);
        dto.setCustomerCode(codeGenerator.generateCollectionCustomerCode(last));
        if (repository.existsByCustomerCode(dto.getCustomerCode())) {
            dto.setCustomerCode(codeGenerator.generateCollectionCustomerCode(dto.getCustomerCode()));
        }
        CollectionCustomer e = toEntity(dto);
        if (e.getActive() == null) {
            e.setActive(Boolean.TRUE);
        }
        auditUtil.setCreatedBy(e);
        e = repository.save(e);
        return toDto(e);
    }

    @Transactional
    public CollectionCustomerDto update(String id, CollectionCustomerDto dto) {
        CollectionCustomer e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found: " + id));
        if (!isActiveCustomer(e)) {
            throw new IllegalArgumentException("This collection customer is inactive and cannot be edited.");
        }
        dto.setCustomerCode(e.getCustomerCode());
        updateEntity(e, dto);
        auditUtil.setUpdatedBy(e);
        e = repository.save(e);
        return toDto(e);
    }

    public CollectionCustomerDto findById(String id) {
        return repository.findById(id)
                .filter(CollectionCustomerService::isActiveCustomer)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found: " + id));
    }

    public PageResponse<CollectionCustomerDto> findAll(int page, int size, String sortBy, String sortDir) {
        List<CollectionCustomer> list = repository.findAll().stream()
                .filter(CollectionCustomerService::isActiveCustomer)
                .sorted(resolveComparator(sortBy, sortDir))
                .collect(Collectors.toList());
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<CollectionCustomerDto> slice = list.subList(Math.min(start, end), end).stream()
                .map(this::toDto).collect(Collectors.toList());
        return PaginationUtil.toPageResponse(slice, page, size, list.size());
    }

    public PageResponse<CollectionCustomerDto> search(String term, int page, int size, String sortBy, String sortDir) {
        String t = term != null ? term.trim() : "";
        List<CollectionCustomer> list = repository.findAll().stream()
                .filter(CollectionCustomerService::isActiveCustomer)
                .filter(c -> t.isEmpty() || matches(c, t))
                .sorted(resolveComparator(sortBy, sortDir))
                .collect(Collectors.toList());
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<CollectionCustomerDto> slice = list.subList(Math.min(start, end), end).stream()
                .map(this::toDto).collect(Collectors.toList());
        return PaginationUtil.toPageResponse(slice, page, size, list.size());
    }

    private static Comparator<CollectionCustomer> resolveComparator(String sortBy, String sortDir) {
        boolean asc = "asc".equalsIgnoreCase(sortDir);
        Comparator<CollectionCustomer> primary;
        String f = sortBy != null ? sortBy.trim() : "";
        switch (f) {
            case "customerName":
                primary = Comparator.comparing(c -> c.getCustomerName() != null ? c.getCustomerName() : "",
                        String.CASE_INSENSITIVE_ORDER);
                break;
            case "customerCode":
                primary = Comparator.comparing(c -> c.getCustomerCode() != null ? c.getCustomerCode() : "",
                        String.CASE_INSENSITIVE_ORDER);
                break;
            default:
                primary = Comparator.comparing(CollectionCustomer::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (!asc) {
            primary = primary.reversed();
        }
        return primary;
    }

    private boolean matches(CollectionCustomer c, String needle) {
        String n = needle.toLowerCase();
        return (c.getCustomerName() != null && c.getCustomerName().toLowerCase().contains(n))
                || (c.getCustomerCode() != null && c.getCustomerCode().toLowerCase().contains(n))
                || (c.getPhone() != null && c.getPhone().toLowerCase().contains(n))
                || (c.getEmail() != null && c.getEmail().toLowerCase().contains(n));
    }

    @Transactional
    public void delete(String id) {
        CollectionCustomer e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found: " + id));
        if (Boolean.FALSE.equals(e.getActive())) {
            return;
        }
        long pendingCount = collectionCustomerAwbService.countPendingForCustomer(id);
        if (pendingCount > 0) {
            throw new IllegalStateException("Cannot delete customer. Pending AWBs must be cleared first.");
        }
        e.setActive(false);
        auditUtil.setUpdatedBy(e);
        repository.save(e);
    }

    private CollectionCustomer toEntity(CollectionCustomerDto dto) {
        return CollectionCustomer.builder()
                .customerCode(dto.getCustomerCode())
                .customerName(dto.getCustomerName())
                .contactPerson(dto.getContactPerson())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .whatsappNumber(dto.getWhatsappNumber())
                .address(dto.getAddress())
                .city(dto.getCity())
                .state(dto.getState())
                .pincode(dto.getPincode())
                .gstin(dto.getGstin())
                .areaName(dto.getAreaName() != null ? dto.getAreaName().trim() : null)
                .active(dto.getActive() != null ? dto.getActive() : Boolean.TRUE)
                .build();
    }

    private void updateEntity(CollectionCustomer e, CollectionCustomerDto dto) {
        e.setCustomerName(dto.getCustomerName());
        e.setContactPerson(dto.getContactPerson());
        e.setEmail(dto.getEmail());
        e.setPhone(dto.getPhone());
        e.setWhatsappNumber(dto.getWhatsappNumber());
        e.setAddress(dto.getAddress());
        e.setCity(dto.getCity());
        e.setState(dto.getState());
        e.setPincode(dto.getPincode());
        e.setGstin(dto.getGstin());
        if (dto.getAreaName() != null) {
            e.setAreaName(dto.getAreaName().trim());
        }
    }

    private CollectionCustomerDto toDto(CollectionCustomer e) {
        return CollectionCustomerDto.builder()
                .id(e.getId())
                .customerCode(e.getCustomerCode())
                .customerName(e.getCustomerName())
                .contactPerson(e.getContactPerson())
                .email(e.getEmail())
                .phone(e.getPhone())
                .whatsappNumber(e.getWhatsappNumber())
                .address(e.getAddress())
                .city(e.getCity())
                .state(e.getState())
                .pincode(e.getPincode())
                .gstin(e.getGstin())
                .areaName(e.getAreaName())
                .active(e.getActive() != null ? e.getActive() : Boolean.TRUE)
                .lastUpdatedBy(e.getLastUpdatedBy())
                .build();
    }
}

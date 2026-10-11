package com.app.billing.service;

import com.app.billing.dao.ZoneConfigurationRepository;
import com.app.billing.dto.PageResponse;
import com.app.billing.dto.ZoneConfigurationDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.CourierQuotation;
import com.app.billing.model.ZoneConfiguration;
import com.app.billing.util.AuditUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneConfigurationService {

    private final ZoneConfigurationRepository repository;
    private final AuditUtil auditUtil;

    public ZoneConfigurationDto create(ZoneConfigurationDto dto) {
        ZoneConfiguration entity = toEntity(dto);
        auditUtil.setCreatedBy(entity);
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        return toDto(repository.save(entity));
    }

    public ZoneConfigurationDto update(String id, ZoneConfigurationDto dto) {
        ZoneConfiguration entity = findEntityById(id);

        entity.setZoneName(dto.getZoneName());
        entity.setZoneType(dto.getZoneType());
        entity.setExpressBaseWeight(dto.getExpressBaseWeight());
        entity.setExpressIncrementalWeight(dto.getExpressIncrementalWeight());
        entity.setExpressPerKgThreshold(dto.getExpressPerKgThreshold());
        entity.setSurfaceSlab1Threshold(dto.getSurfaceSlab1Threshold());
        entity.setSurfaceSlab1Max(dto.getSurfaceSlab1Max());
        entity.setSurfaceSlab2Threshold(dto.getSurfaceSlab2Threshold());
        entity.setSurfaceSlab2Max(dto.getSurfaceSlab2Max());
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        }

        auditUtil.setUpdatedBy(entity);
        return toDto(repository.save(entity));
    }

    public ZoneConfigurationDto findById(String id) {
        return toDto(findEntityById(id));
    }

    public List<ZoneConfigurationDto> findAllActive() {
        return repository.findActiveZones().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns active zones filtered by quotation rate type.
     * EXPRESS_RATE / SURFACE_RATE → zoneType EXPRESS_SURFACE;
     * SafetyPlus / PriorityClass → zoneType PRIORITY_SAFETY.
     * If rateType is null or empty, returns all active zones.
     */
    public List<ZoneConfigurationDto> findAllActiveByRateType(String rateType) {
        if (rateType == null || rateType.trim().isEmpty()) {
            return findAllActive();
        }
        String zoneType = toZoneType(rateType.trim());
        if (zoneType == null) {
            return findAllActive();
        }
        return repository.findActiveZonesByZoneType(zoneType).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private String toZoneType(String rateType) {
        if (CourierQuotation.RATE_TYPE_EXPRESS.equals(rateType) || CourierQuotation.RATE_TYPE_SURFACE.equals(rateType)) {
            return "EXPRESS_SURFACE";
        }
        if (CourierQuotation.RATE_TYPE_SAFETY_PLUS.equals(rateType) || CourierQuotation.RATE_TYPE_PRIORITY_CLASS.equals(rateType)) {
            return "PRIORITY_SAFETY";
        }
        if ("STANDARD".equalsIgnoreCase(rateType)) {
            return "STANDARD";
        }
        return null;
    }

    public PageResponse<ZoneConfigurationDto> findAll(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ZoneConfiguration> ePage = repository.findByIsDeletedFalse(pageable);

        List<ZoneConfigurationDto> content = ePage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResponse.<ZoneConfigurationDto>builder()
                .content(content)
                .page(ePage.getNumber())
                .size(ePage.getSize())
                .totalElements(ePage.getTotalElements())
                .totalPages(ePage.getTotalPages())
                .last(ePage.isLast())
                .build();
    }

    public PageResponse<ZoneConfigurationDto> search(String searchTerm, int page, int size, String sortBy,
            String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ZoneConfiguration> ePage = repository.searchZones(searchTerm, pageable);

        List<ZoneConfigurationDto> content = ePage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResponse.<ZoneConfigurationDto>builder()
                .content(content)
                .page(ePage.getNumber())
                .size(ePage.getSize())
                .totalElements(ePage.getTotalElements())
                .totalPages(ePage.getTotalPages())
                .last(ePage.isLast())
                .build();
    }

    public void delete(String id) {
        ZoneConfiguration entity = findEntityById(id);
        entity.setIsDeleted(true);
        auditUtil.setUpdatedBy(entity);
        repository.save(entity);
    }

    private ZoneConfiguration findEntityById(String id) {
        return repository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("ZoneConfiguration not found with id: " + id));
    }

    private ZoneConfigurationDto toDto(ZoneConfiguration entity) {
        return ZoneConfigurationDto.builder()
                .id(entity.getId())
                .zoneName(entity.getZoneName())
                .zoneType(entity.getZoneType())
                .expressBaseWeight(entity.getExpressBaseWeight())
                .expressIncrementalWeight(entity.getExpressIncrementalWeight())
                .expressPerKgThreshold(entity.getExpressPerKgThreshold())
                .surfaceSlab1Threshold(entity.getSurfaceSlab1Threshold())
                .surfaceSlab1Max(entity.getSurfaceSlab1Max())
                .surfaceSlab2Threshold(entity.getSurfaceSlab2Threshold())
                .surfaceSlab2Max(entity.getSurfaceSlab2Max())
                .isActive(entity.getIsActive())
                .lastUpdatedBy(entity.getLastUpdatedBy())
                .build();
    }

    private ZoneConfiguration toEntity(ZoneConfigurationDto dto) {
        return ZoneConfiguration.builder()
                .zoneName(dto.getZoneName())
                .zoneType(dto.getZoneType())
                .expressBaseWeight(dto.getExpressBaseWeight())
                .expressIncrementalWeight(dto.getExpressIncrementalWeight())
                .expressPerKgThreshold(dto.getExpressPerKgThreshold())
                .surfaceSlab1Threshold(dto.getSurfaceSlab1Threshold())
                .surfaceSlab1Max(dto.getSurfaceSlab1Max())
                .surfaceSlab2Threshold(dto.getSurfaceSlab2Threshold())
                .surfaceSlab2Max(dto.getSurfaceSlab2Max())
                .isActive(dto.getIsActive())
                .build();
    }
}

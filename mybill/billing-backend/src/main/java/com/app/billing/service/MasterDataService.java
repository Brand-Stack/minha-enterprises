package com.app.billing.service;

import com.app.billing.dao.MasterDataRepository;
import com.app.billing.dto.MasterDataDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.MasterData;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MasterDataService {
    
    private final MasterDataRepository masterDataRepository;
    private final AuditUtil auditUtil;
    
    @Transactional
    public MasterDataDto create(MasterDataDto dto) {
        normalizeEmployeeCategory(dto);

        // Validate role field - only allowed for EMPLOYEE_CATEGORY
        if (dto.getRole() != null && !dto.getRole().trim().isEmpty()) {
            if (dto.getType() != MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
                throw new IllegalArgumentException("Role field is only allowed for EMPLOYEE_CATEGORY type");
            }
        }
        // Role is required for EMPLOYEE_CATEGORY
        if (dto.getType() == MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
            if (dto.getRole() == null || dto.getRole().trim().isEmpty()) {
                throw new IllegalArgumentException("Role is required for EMPLOYEE_CATEGORY");
            }
        }
        
        // Check for duplicate name within the same type
        List<MasterData> existing = masterDataRepository.findByType(dto.getType());
        if (dto.getType() == MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
            if (existing.stream().anyMatch(m ->
                    m.getRole() != null && m.getRole().equalsIgnoreCase(dto.getRole().trim()))) {
                throw new ResourceAlreadyExistsException(
                        "Employee category with role '" + dto.getRole() + "' already exists");
            }
        } else if (existing.stream().anyMatch(m ->
                m.getName().equalsIgnoreCase(dto.getName().trim()))) {
            throw new ResourceAlreadyExistsException(
                    "Master data with name '" + dto.getName() + "' already exists for type " + dto.getType());
        }
        
        MasterData masterData = toEntity(dto);
        if (masterData.getActive() == null) {
            masterData.setActive(true);
        }
        // Clear role if not EMPLOYEE_CATEGORY
        if (masterData.getType() != MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
            masterData.setRole(null);
        }
        auditUtil.setCreatedBy(masterData);
        masterData = masterDataRepository.save(masterData);
        return toDto(masterData);
    }
    
    @Transactional
    public MasterDataDto update(String id, MasterDataDto dto) {
        MasterData masterData = masterDataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Master data not found with id: " + id));

        normalizeEmployeeCategory(dto);

        // Validate role field - only allowed for EMPLOYEE_CATEGORY
        if (dto.getRole() != null && !dto.getRole().trim().isEmpty()) {
            if (dto.getType() != MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
                throw new IllegalArgumentException("Role field is only allowed for EMPLOYEE_CATEGORY type");
            }
        }
        // Role is required for EMPLOYEE_CATEGORY
        if (dto.getType() == MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
            if (dto.getRole() == null || dto.getRole().trim().isEmpty()) {
                throw new IllegalArgumentException("Role is required for EMPLOYEE_CATEGORY");
            }
        }
        
        // Check for duplicate name within the same type (excluding current record)
        List<MasterData> existing = masterDataRepository.findByType(dto.getType());
        if (dto.getType() == MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
            if (existing.stream().anyMatch(m ->
                    m.getRole() != null
                            && m.getRole().equalsIgnoreCase(dto.getRole().trim())
                            && !m.getId().equals(id))) {
                throw new ResourceAlreadyExistsException(
                        "Employee category with role '" + dto.getRole() + "' already exists");
            }
        } else if (existing.stream().anyMatch(m ->
                m.getName().equalsIgnoreCase(dto.getName().trim()) && !m.getId().equals(id))) {
            throw new ResourceAlreadyExistsException(
                    "Master data with name '" + dto.getName() + "' already exists for type " + dto.getType());
        }
        
        updateEntity(masterData, dto);
        // Clear role if not EMPLOYEE_CATEGORY
        if (masterData.getType() != MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
            masterData.setRole(null);
        }
        auditUtil.setUpdatedBy(masterData);
        masterData = masterDataRepository.save(masterData);
        return toDto(masterData);
    }
    
    public MasterDataDto findById(String id) {
        MasterData masterData = masterDataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Master data not found with id: " + id));
        return toDto(masterData);
    }
    
    public PageResponse<MasterDataDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<MasterData> masterDataList = masterDataRepository.findAll(pageable);
        return PaginationUtil.toPageResponse(masterDataList.map(this::toDto));
    }
    
    public PageResponse<MasterDataDto> findByType(MasterData.MasterDataType type, int page, int size, String sortBy, String sortDir) {
        List<MasterData> masterDataList = masterDataRepository.findByTypeAndActive(type, true);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), masterDataList.size());
        List<MasterData> pagedList = masterDataList.subList(start, end);
        
        return PaginationUtil.toPageResponse(
                pagedList.stream().map(this::toDto).collect(Collectors.toList()),
                page,
                size,
                masterDataList.size()
        );
    }
    
    public List<MasterDataDto> findAllByType(MasterData.MasterDataType type) {
        return masterDataRepository.findByTypeAndActive(type, true)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    public PageResponse<MasterDataDto> search(String searchTerm, MasterData.MasterDataType type, 
                                               int page, int size, String sortBy, String sortDir) {
        List<MasterData> masterDataList = masterDataRepository.searchByType(searchTerm, type);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), masterDataList.size());
        List<MasterData> pagedList = masterDataList.subList(start, end);
        
        return PaginationUtil.toPageResponse(
                pagedList.stream().map(this::toDto).collect(Collectors.toList()),
                page,
                size,
                masterDataList.size()
        );
    }
    
    @Transactional
    public void delete(String id) {
        if (!masterDataRepository.existsById(id)) {
            throw new ResourceNotFoundException("Master data not found with id: " + id);
        }
        masterDataRepository.deleteById(id);
    }
    
    private MasterData toEntity(MasterDataDto dto) {
        return MasterData.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .type(dto.getType())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .role(dto.getRole())
                .build();
    }
    
    private MasterDataDto toDto(MasterData masterData) {
        MasterDataDto dto = new MasterDataDto();
        dto.setId(masterData.getId());
        dto.setName(masterData.getName());
        dto.setDescription(masterData.getDescription());
        dto.setType(masterData.getType());
        dto.setActive(masterData.getActive());
        dto.setRole(masterData.getRole());
        dto.setLastUpdatedBy(masterData.getLastUpdatedBy());
        return dto;
    }
    
    private void updateEntity(MasterData masterData, MasterDataDto dto) {
        masterData.setName(dto.getName());
        masterData.setDescription(dto.getDescription());
        masterData.setType(dto.getType());
        // Only set role for EMPLOYEE_CATEGORY
        if (dto.getType() == MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
            masterData.setRole(dto.getRole());
        } else {
            masterData.setRole(null);
        }
        if (dto.getActive() != null) {
            masterData.setActive(dto.getActive());
        }
    }

    /** Employee categories use role as the display key; derive name when omitted. */
    private void normalizeEmployeeCategory(MasterDataDto dto) {
        if (dto.getType() != MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
            return;
        }
        if (dto.getRole() == null || dto.getRole().trim().isEmpty()) {
            return;
        }
        dto.setName(dto.getRole().trim());
        if (dto.getActive() == null) {
            dto.setActive(true);
        }
    }
}


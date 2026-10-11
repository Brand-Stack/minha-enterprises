package com.app.billing.service;

import com.app.billing.dao.EmployeeCategoryRepository;
import com.app.billing.dto.EmployeeCategoryDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.EmployeeCategory;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeCategoryService {

    private final EmployeeCategoryRepository employeeCategoryRepository;
    private final EntitlementService entitlementService;
    private final AuditUtil auditUtil;

    @Transactional
    public EmployeeCategoryDto create(EmployeeCategoryDto dto) {
        if (employeeCategoryRepository.existsByNameIgnoreCase(dto.getName().trim())) {
            throw new ResourceAlreadyExistsException("Category '" + dto.getName() + "' already exists");
        }
        EmployeeCategory category = EmployeeCategory.builder()
                .name(dto.getName().trim())
                .description(dto.getDescription())
                .status(dto.getStatus() != null ? dto.getStatus() : EmployeeCategory.Status.ACTIVE)
                .build();
        auditUtil.setCreatedBy(category);
        category = employeeCategoryRepository.save(category);
        return toDto(category);
    }

    @Transactional
    public EmployeeCategoryDto update(String id, EmployeeCategoryDto dto) {
        EmployeeCategory existing = employeeCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        if (dto.getName() != null && !dto.getName().trim().equalsIgnoreCase(existing.getName())
                && employeeCategoryRepository.existsByNameIgnoreCase(dto.getName().trim())) {
            throw new ResourceAlreadyExistsException("Category '" + dto.getName() + "' already exists");
        }

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            existing.setName(dto.getName().trim());
        }
        existing.setDescription(dto.getDescription());
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }
        auditUtil.setUpdatedBy(existing);
        existing = employeeCategoryRepository.save(existing);
        return toDto(existing);
    }

    public EmployeeCategoryDto findById(String id) {
        return employeeCategoryRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    public PageResponse<EmployeeCategoryDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<EmployeeCategory> categories = employeeCategoryRepository.findAll(pageable);
        return PaginationUtil.toPageResponse(categories.map(this::toDto));
    }

    public List<EmployeeCategoryDto> findAllList() {
        return employeeCategoryRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void delete(String id) {
        if (!employeeCategoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }
        // Remove the associated permission matrix as well.
        entitlementService.deleteByCategoryId(id);
        employeeCategoryRepository.deleteById(id);
    }

    private EmployeeCategoryDto toDto(EmployeeCategory category) {
        EmployeeCategoryDto dto = new EmployeeCategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setStatus(category.getStatus());
        dto.setLastUpdatedBy(category.getLastUpdatedBy());
        return dto;
    }
}

package com.app.billing.service;

import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.MasterDataRepository;
import com.app.billing.dto.EmployeeDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Employee;
import com.app.billing.model.MasterData;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.CodeGenerator;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    
    private final EmployeeRepository employeeRepository;
    private final MasterDataRepository masterDataRepository;
    private final PasswordEncoder passwordEncoder;
    private final CodeGenerator codeGenerator;
    private final AuditUtil auditUtil;
    
    @Transactional
    public EmployeeDto create(EmployeeDto dto) {
        // Validate category from Master
        if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
            validateMasterData(dto.getCategory(), MasterData.MasterDataType.EMPLOYEE_CATEGORY, "Category");
            dto.setCategoryId(resolveCategoryId(dto.getCategory(), dto.getCategoryId()));
        }
        
        if (dto.getEmail() != null && !dto.getEmail().isEmpty() && 
            employeeRepository.existsByEmail(dto.getEmail())) {
            throw new ResourceAlreadyExistsException("Employee with email " + dto.getEmail() + " already exists");
        }
        
        // Auto-generate employee code if not provided (null/blank/whitespace safe)
        if (dto.getEmployeeCode() == null || dto.getEmployeeCode().isBlank()) {
            String lastCode = employeeRepository.findAll().stream()
                    .map(Employee::getEmployeeCode)
                    .filter(code -> code != null)
                    .max(String::compareTo)
                    .orElse(null);
            dto.setEmployeeCode(codeGenerator.generateEmployeeCode(lastCode));
        } else {
            dto.setEmployeeCode(dto.getEmployeeCode().trim());
            if (employeeRepository.existsByEmployeeCode(dto.getEmployeeCode())) {
                throw new ResourceAlreadyExistsException("Employee code already exists.");
            }
        }
        
        Employee employee = toEntity(dto);
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            employee.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getStatus() == null) {
            employee.setStatus(Employee.Status.ACTIVE);
        }
        auditUtil.setCreatedBy(employee);
        employee = employeeRepository.save(employee);
        return toDto(employee);
    }
    
    @Transactional
    public EmployeeDto update(String id, EmployeeDto dto) {
        Employee existing = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        
        // Validate category from Master
        if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
            validateMasterData(dto.getCategory(), MasterData.MasterDataType.EMPLOYEE_CATEGORY, "Category");
            dto.setCategoryId(resolveCategoryId(dto.getCategory(), dto.getCategoryId()));
        }
        
        if (dto.getEmail() != null && !dto.getEmail().isEmpty() && 
            !existing.getEmail().equals(dto.getEmail()) && 
            employeeRepository.existsByEmail(dto.getEmail())) {
            throw new ResourceAlreadyExistsException("Employee with email " + dto.getEmail() + " already exists");
        }
        
        // Preserve employee code - don't allow it to be changed
        dto.setEmployeeCode(existing.getEmployeeCode());
        
        updateEntity(existing, dto);
        auditUtil.setUpdatedBy(existing);
        existing = employeeRepository.save(existing);
        return toDto(existing);
    }
    
    public EmployeeDto findById(String id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        return toDto(employee);
    }
    
    public PageResponse<EmployeeDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<Employee> employees = employeeRepository.findAll(pageable);
        return PaginationUtil.toPageResponse(employees.map(this::toDto));
    }
    
    public List<String> getCategories() {
        // Return categories from Master Data instead of from existing employees
        return masterDataRepository.findByTypeAndActive(MasterData.MasterDataType.EMPLOYEE_CATEGORY, true)
                .stream()
                .map(MasterData::getName)
                .collect(Collectors.toList());
    }
    
    public PageResponse<EmployeeDto> search(String searchTerm, int page, int size, String sortBy, String sortDir) {
        List<Employee> employees = employeeRepository.searchEmployees(searchTerm);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), employees.size());
        List<Employee> pagedEmployees = employees.subList(start, end);
        
        return PaginationUtil.toPageResponse(
                pagedEmployees.stream().map(this::toDto).collect(Collectors.toList()),
                page,
                size,
                employees.size()
        );
    }
    
    public List<EmployeeDto> findAllForExport() {
        return employeeRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    private void validateMasterData(String value, MasterData.MasterDataType type, String fieldName) {
        List<MasterData> masterDataList = masterDataRepository.findByTypeAndActive(type, true);
        boolean exists = masterDataList.stream()
                .anyMatch(md -> md.getName().equalsIgnoreCase(value.trim()));
        if (!exists) {
            throw new ResourceNotFoundException(fieldName + " '" + value + "' not found in Master Data. Please create it in Master module first.");
        }
    }
    
    @Transactional
    public void delete(String id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee not found with id: " + id);
        }
        employeeRepository.deleteById(id);
    }
    
    private Employee toEntity(EmployeeDto dto) {
        return Employee.builder()
                .employeeCode(dto.getEmployeeCode())
                .employeeName(dto.getEmployeeName())
                .category(dto.getCategory())
                .designation(dto.getDesignation())
                .gender(dto.getGender())
                .dateOfBirth(dto.getDateOfBirth())
                .dateOfJoining(dto.getDateOfJoining())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .address(dto.getAddress())
                .status(dto.getStatus() != null ? dto.getStatus() : Employee.Status.ACTIVE)
                .role(dto.getRole() != null ? dto.getRole() : Employee.Role.EMPLOYEE)
                .categoryId(dto.getCategoryId())
                .build();
    }
    
    private EmployeeDto toDto(Employee employee) {
        EmployeeDto dto = new EmployeeDto();
        dto.setId(employee.getId());
        dto.setEmployeeCode(employee.getEmployeeCode());
        dto.setEmployeeName(employee.getEmployeeName());
        dto.setCategory(employee.getCategory());
        dto.setDesignation(employee.getDesignation());
        dto.setGender(employee.getGender());
        dto.setDateOfBirth(employee.getDateOfBirth());
        dto.setDateOfJoining(employee.getDateOfJoining());
        dto.setPhone(employee.getPhone());
        dto.setEmail(employee.getEmail());
        dto.setAddress(employee.getAddress());
        dto.setStatus(employee.getStatus());
        dto.setRole(employee.getRole());
        dto.setCategoryId(employee.getCategoryId());
        if (employee.getCategoryId() != null) {
            masterDataRepository.findById(employee.getCategoryId())
                    .filter(md -> md.getType() == MasterData.MasterDataType.EMPLOYEE_CATEGORY)
                    .ifPresent(md -> dto.setCategoryName(md.getName()));
        } else if (employee.getCategory() != null) {
            dto.setCategoryName(employee.getCategory());
        }
        dto.setLastUpdatedBy(employee.getLastUpdatedBy());
        // Password is intentionally not included in DTO for security
        return dto;
    }
    
    private void updateEntity(Employee employee, EmployeeDto dto) {
        employee.setEmployeeName(dto.getEmployeeName());
        employee.setCategory(dto.getCategory());
        employee.setDesignation(dto.getDesignation());
        employee.setGender(dto.getGender());
        employee.setDateOfBirth(dto.getDateOfBirth());
        employee.setDateOfJoining(dto.getDateOfJoining());
        employee.setPhone(dto.getPhone());
        employee.setEmail(dto.getEmail());
        employee.setAddress(dto.getAddress());
        employee.setStatus(dto.getStatus() != null ? dto.getStatus() : employee.getStatus());
        employee.setRole(dto.getRole() != null ? dto.getRole() : employee.getRole());
        if (dto.getCategoryId() != null) {
            employee.setCategoryId(dto.getCategoryId());
        } else if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
            employee.setCategoryId(resolveCategoryId(dto.getCategory(), null));
        }
        
        // Only update password if provided
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            employee.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
    }

    private String resolveCategoryId(String categoryName, String explicitCategoryId) {
        if (explicitCategoryId != null && !explicitCategoryId.isBlank()) {
            masterDataRepository.findById(explicitCategoryId)
                    .filter(md -> md.getType() == MasterData.MasterDataType.EMPLOYEE_CATEGORY)
                    .filter(md -> !Boolean.FALSE.equals(md.getActive()))
                    .ifPresent(md -> {
                        if (categoryName != null && !categoryName.trim().equalsIgnoreCase(md.getName())) {
                            throw new ResourceNotFoundException(
                                    "Category id does not match category name '" + categoryName + "'");
                        }
                    });
            return explicitCategoryId;
        }
        return masterDataRepository.findByTypeAndActive(MasterData.MasterDataType.EMPLOYEE_CATEGORY, true)
                .stream()
                .filter(md -> md.getName().equalsIgnoreCase(categoryName.trim()))
                .map(MasterData::getId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee category '" + categoryName + "' not found in Master Data"));
    }
}


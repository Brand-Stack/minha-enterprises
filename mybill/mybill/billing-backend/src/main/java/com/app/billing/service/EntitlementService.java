package com.app.billing.service;

import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.EntitlementRepository;
import com.app.billing.dao.MasterDataRepository;
import com.app.billing.dto.EntitlementDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Entitlement;
import com.app.billing.model.MasterData;
import com.app.billing.model.ModulePermission;
import com.app.billing.util.AuditUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

/**
 * Loads and persists the permission matrix ({@link Entitlement}) for a category.
 * New categories default to no access (empty matrix) per the default-deny policy.
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final EntitlementRepository entitlementRepository;
    private final MasterDataRepository masterDataRepository;
    private final AuditUtil auditUtil;
    private final AuditService auditService;

    public EntitlementDto getByCategoryId(String categoryId) {
        MasterData category = resolveEmployeeCategory(categoryId);

        Entitlement entitlement = entitlementRepository.findByCategoryId(categoryId)
                .orElse(null);

        return EntitlementDto.builder()
                .id(entitlement != null ? entitlement.getId() : null)
                .categoryId(categoryId)
                .categoryName(category.getName())
                .modulePermissions(entitlement != null ? entitlement.getModulePermissions() : new HashMap<>())
                .build();
    }

    @Transactional
    public EntitlementDto save(EntitlementDto dto) {
        MasterData category = resolveEmployeeCategory(dto.getCategoryId());

        Entitlement entitlement = entitlementRepository.findByCategoryId(dto.getCategoryId())
                .orElseGet(() -> Entitlement.builder()
                        .categoryId(dto.getCategoryId())
                        .modulePermissions(new HashMap<>())
                        .build());

        entitlement.setModulePermissions(
                dto.getModulePermissions() != null ? dto.getModulePermissions() : new HashMap<>());

        if (entitlement.getId() == null) {
            auditUtil.setCreatedBy(entitlement);
        } else {
            auditUtil.setUpdatedBy(entitlement);
        }
        entitlement = entitlementRepository.save(entitlement);

        auditService.log("PERMISSION_CHANGE", "ENTITLEMENT_MGMT", dto.getCategoryId(),
                "Updated permissions for category '" + category.getName() + "'");

        return EntitlementDto.builder()
                .id(entitlement.getId())
                .categoryId(entitlement.getCategoryId())
                .categoryName(category.getName())
                .modulePermissions(entitlement.getModulePermissions())
                .build();
    }

    /** Resolve the effective module permission for a category, or null when not granted. */
    public ModulePermission getModulePermission(String categoryId, String moduleKey) {
        if (categoryId == null || moduleKey == null) {
            return null;
        }
        return entitlementRepository.findByCategoryId(categoryId)
                .map(Entitlement::getModulePermissions)
                .map(m -> m.get(moduleKey))
                .orElse(null);
    }

    @Transactional
    public void deleteByCategoryId(String categoryId) {
        entitlementRepository.deleteByCategoryId(categoryId);
    }

    private MasterData resolveEmployeeCategory(String categoryId) {
        MasterData category = masterDataRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee category not found with id: " + categoryId));
        if (category.getType() != MasterData.MasterDataType.EMPLOYEE_CATEGORY) {
            throw new ResourceNotFoundException("Master data entry is not an employee category: " + categoryId);
        }
        if (Boolean.FALSE.equals(category.getActive())) {
            throw new ResourceNotFoundException("Employee category is inactive: " + category.getName());
        }
        return category;
    }
}

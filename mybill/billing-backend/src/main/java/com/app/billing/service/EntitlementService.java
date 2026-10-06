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

        var map = entitlement != null && entitlement.getModulePermissions() != null 
                ? migratePermissions(entitlement.getModulePermissions()) 
                : new HashMap<String, ModulePermission>();

        return EntitlementDto.builder()
                .id(entitlement != null ? entitlement.getId() : null)
                .categoryId(categoryId)
                .categoryName(category.getName())
                .modulePermissions(map)
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

        var inputMap = dto.getModulePermissions() != null ? dto.getModulePermissions() : new HashMap<String, ModulePermission>();
        var migratedMap = migratePermissions(inputMap);

        entitlement.setModulePermissions(migratedMap);

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
                .map(this::migratePermissions)
                .map(m -> m.get(moduleKey))
                .orElse(null);
    }

    private HashMap<String, ModulePermission> migratePermissions(java.util.Map<String, ModulePermission> original) {
        HashMap<String, ModulePermission> result = new HashMap<>(original);

        // Migrate legacy keys if new key is missing
        if (result.containsKey("ATTENDANCE")) {
            ModulePermission p = result.get("ATTENDANCE");
            result.putIfAbsent(com.app.billing.security.Modules.MASTER_ATTENDANCE, p);
            result.putIfAbsent(com.app.billing.security.Modules.MY_ATTENDANCE, p);
            result.remove("ATTENDANCE");
        }
        if (result.containsKey("LEAVE_MGMT")) {
            result.putIfAbsent(com.app.billing.security.Modules.LEAVES, result.get("LEAVE_MGMT"));
            result.remove("LEAVE_MGMT");
        }
        if (result.containsKey("PERMISSION_MGMT")) {
            result.putIfAbsent(com.app.billing.security.Modules.PERMISSIONS, result.get("PERMISSION_MGMT"));
            result.remove("PERMISSION_MGMT");
        }
        if (result.containsKey("PAYROLL")) {
            result.putIfAbsent(com.app.billing.security.Modules.PAYROLL_PAYSLIPS, result.get("PAYROLL"));
            result.remove("PAYROLL");
        }
        if (result.containsKey("SALARY_ADVANCE")) {
            result.putIfAbsent(com.app.billing.security.Modules.PAYROLL_PAYSLIPS, result.get("SALARY_ADVANCE"));
            result.remove("SALARY_ADVANCE");
        }
        if (result.containsKey("BONUS")) {
            result.putIfAbsent(com.app.billing.security.Modules.PAYROLL_PAYSLIPS, result.get("BONUS"));
            result.remove("BONUS");
        }
        if (result.containsKey("ATTENDANCE_DEVICE")) {
            result.putIfAbsent(com.app.billing.security.Modules.BIOMETRIC_DEVICES, result.get("ATTENDANCE_DEVICE"));
            result.remove("ATTENDANCE_DEVICE");
        }
        if (result.containsKey("OVERTIME")) {
            result.remove("OVERTIME");
        }
        return result;
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

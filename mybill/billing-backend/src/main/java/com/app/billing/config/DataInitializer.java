package com.app.billing.config;

import com.app.billing.dao.EmployeeCategoryRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.EntitlementRepository;
import com.app.billing.dao.MasterDataRepository;
import com.app.billing.model.Employee;
import com.app.billing.model.Entitlement;
import com.app.billing.model.FieldPermission;
import com.app.billing.model.MasterData;
import com.app.billing.model.ModulePermission;
import com.app.billing.model.ModuleRegistry;
import com.app.billing.service.ModuleRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bootstraps RBAC data on startup:
 * <ol>
 *   <li>Syncs the metadata-driven module registry.</li>
 *   <li>Seeds default employee categories ("Billing User", "Party User") with equivalent
 *       permissions so existing non-admin employees are not locked out.</li>
 *   <li>Backfills {@code categoryId} on legacy employees based on their old role.</li>
 *   <li>Ensures the default admin account exists.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String BILLING_USER_CATEGORY = "Billing User";
    private static final String PARTY_USER_CATEGORY = "Party User";

    private final EmployeeRepository employeeRepository;
    private final MasterDataRepository masterDataRepository;
    private final EmployeeCategoryRepository employeeCategoryRepository;
    private final EntitlementRepository entitlementRepository;
    private final ModuleRegistryService moduleRegistryService;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        moduleRegistryService.syncRegistry();

        seedDefaultAdmin();

        String billingCategoryId = seedCategory(BILLING_USER_CATEGORY,
                "Default category for billing users (full business access)", true, null);
        String partyCategoryId = seedCategory(PARTY_USER_CATEGORY,
                "Default category for party / client users (limited access)", false,
                Set.of("DASHBOARD", "CLIENTS", "SMALL_CLIENTS", "COLLECTION_CUSTOMER", "COLLECTION_CENTER", "CASH_BOOKING"));

        migrateLegacyEntitlementsToMasterData();
        backfillEmployeeCategories(billingCategoryId, partyCategoryId);
        migrateInvoiceGeneratedFlags();
        migrateLegacySalaryAdvances();
    }

    private void migrateLegacySalaryAdvances() {
        try {
            if (!mongoTemplate.collectionExists("salary_advances")) {
                return;
            }
            List<org.bson.Document> docs = mongoTemplate.findAll(org.bson.Document.class, "salary_advances");
            if (docs.isEmpty()) {
                return;
            }
            log.info("Found {} legacy salary_advances records to migrate into EmployeeAdvanceAccount ledgers...", docs.size());
            int migratedCount = 0;
            for (org.bson.Document doc : docs) {
                String empId = doc.getString("employeeId");
                if (empId == null || empId.isBlank()) continue;

                Object amtObj = doc.get("approvedAmount") != null ? doc.get("approvedAmount") : doc.get("requestedAmount");
                java.math.BigDecimal amount = java.math.BigDecimal.ZERO;
                if (amtObj instanceof Number) {
                    amount = java.math.BigDecimal.valueOf(((Number) amtObj).doubleValue());
                }

                if (amount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    org.springframework.data.mongodb.core.query.Query q = new org.springframework.data.mongodb.core.query.Query(
                            org.springframework.data.mongodb.core.query.Criteria.where("employeeId").is(empId)
                    );
                    com.app.billing.model.EmployeeAdvanceAccount account = mongoTemplate.findOne(q, com.app.billing.model.EmployeeAdvanceAccount.class);
                    if (account == null) {
                        account = com.app.billing.model.EmployeeAdvanceAccount.builder()
                                .employeeId(empId)
                                .employeeCode(doc.getString("employeeCode"))
                                .employeeName(doc.getString("employeeName"))
                                .totalAdvanceGiven(java.math.BigDecimal.ZERO)
                                .totalRepaid(java.math.BigDecimal.ZERO)
                                .totalPayrollDeducted(java.math.BigDecimal.ZERO)
                                .outstandingBalance(java.math.BigDecimal.ZERO)
                                .status("ACTIVE")
                                .build();
                        account = mongoTemplate.save(account);
                    }

                    String legacyId = doc.get("_id") != null ? doc.get("_id").toString() : null;
                    org.springframework.data.mongodb.core.query.Query txQuery = new org.springframework.data.mongodb.core.query.Query(
                            org.springframework.data.mongodb.core.query.Criteria.where("employeeId").is(empId)
                                    .and("description").is("Migrated Legacy Advance: " + legacyId)
                    );
                    if (!mongoTemplate.exists(txQuery, com.app.billing.model.AdvanceTransaction.class)) {
                        java.math.BigDecimal prevBal = account.getOutstandingBalance() != null ? account.getOutstandingBalance() : java.math.BigDecimal.ZERO;
                        java.math.BigDecimal newBal = prevBal.add(amount);

                        account.setTotalAdvanceGiven((account.getTotalAdvanceGiven() != null ? account.getTotalAdvanceGiven() : java.math.BigDecimal.ZERO).add(amount));
                        account.setOutstandingBalance(newBal);
                        mongoTemplate.save(account);

                        com.app.billing.model.AdvanceTransaction advanceTx = com.app.billing.model.AdvanceTransaction.builder()
                                .advanceAccountId(account.getId())
                                .employeeId(empId)
                                .employeeCode(account.getEmployeeCode())
                                .employeeName(account.getEmployeeName())
                                .transactionType(com.app.billing.model.AdvanceTransaction.TransactionType.ADVANCE_GIVEN)
                                .amount(amount)
                                .transactionDate(java.time.LocalDate.now())
                                .paymentMode("Other")
                                .otherPaymentModeDetails("Legacy Migration")
                                .description("Migrated Legacy Advance: " + legacyId)
                                .reason(doc.getString("reason"))
                                .previousBalance(prevBal)
                                .resultingBalance(newBal)
                                .status("COMPLETED")
                                .build();
                        advanceTx.setCreatedBy("system");
                        advanceTx.setCreatedAt(java.time.LocalDateTime.now());
                        mongoTemplate.save(advanceTx);
                        migratedCount++;
                    }
                }
            }
            mongoTemplate.dropCollection("salary_advances");
            log.info("Successfully migrated {} legacy salary advance records and dropped salary_advances collection.", migratedCount);
        } catch (Exception e) {
            log.warn("Failed during legacy salary advances migration: {}", e.getMessage(), e);
        }
    }

    private void migrateInvoiceGeneratedFlags() {
        try {
            org.springframework.data.mongodb.core.query.Query q = new org.springframework.data.mongodb.core.query.Query(
                    org.springframework.data.mongodb.core.query.Criteria.where("invoiceNumber").exists(true).ne(null).ne("")
            );
            org.springframework.data.mongodb.core.query.Update u = new org.springframework.data.mongodb.core.query.Update().set("invoiceGenerated", true);
            mongoTemplate.updateMulti(q, u, com.app.billing.model.SmallClientEntryQuotation.class);
            mongoTemplate.updateMulti(q, u, com.app.billing.model.MonthlyCourierQuotation.class);
            log.info("Migrated invoiceGenerated flags for existing quotations with invoice numbers.");
        } catch (Exception e) {
            log.warn("Failed to migrate invoiceGenerated flags: {}", e.getMessage());
        }
    }

    /** Copy permission matrices from legacy Access Control categories to matching Master Data rows. */
    private void migrateLegacyEntitlementsToMasterData() {
        for (MasterData md : masterDataRepository.findByTypeAndActive(
                MasterData.MasterDataType.EMPLOYEE_CATEGORY, true)) {
            if (entitlementRepository.findByCategoryId(md.getId()).isPresent()) {
                continue;
            }
            employeeCategoryRepository.findByNameIgnoreCase(md.getName()).ifPresent(legacy -> {
                entitlementRepository.findByCategoryId(legacy.getId()).ifPresent(oldEnt -> {
                    Entitlement migrated = Entitlement.builder()
                            .categoryId(md.getId())
                            .modulePermissions(oldEnt.getModulePermissions())
                            .build();
                    migrated.setCreatedBy("system");
                    migrated.setUpdatedBy("system");
                    migrated.setLastUpdatedBy("system");
                    entitlementRepository.save(migrated);
                    log.info("Migrated entitlement from legacy category '{}' to master data id {}",
                            md.getName(), md.getId());
                });
            });
        }
    }

    private static final String DEFAULT_ADMIN_LOGIN = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "Minha@123";
    private static final String LEGACY_ADMIN_EMAIL = "admin@billing.com";

    private void seedDefaultAdmin() {
        if (employeeRepository.existsByEmail(DEFAULT_ADMIN_LOGIN)) {
            log.info("Default admin user already exists: {}", DEFAULT_ADMIN_LOGIN);
            return;
        }

        if (employeeRepository.existsByEmail(LEGACY_ADMIN_EMAIL)) {
            employeeRepository.findFirstByEmailOrderByIdAsc(LEGACY_ADMIN_EMAIL).ifPresent(admin -> {
                admin.setEmail(DEFAULT_ADMIN_LOGIN);
                admin.setEmployeeCode(DEFAULT_ADMIN_LOGIN);
                admin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
                employeeRepository.save(admin);
                log.info("Migrated legacy admin account to {} / {}", DEFAULT_ADMIN_LOGIN, DEFAULT_ADMIN_PASSWORD);
            });
            return;
        }

        Employee admin = Employee.builder()
                .employeeCode(DEFAULT_ADMIN_LOGIN)
                .employeeName("Admin User")
                .email(DEFAULT_ADMIN_LOGIN)
                .password(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                .role(Employee.Role.ADMIN)
                .category("Manager")
                .designation("Administrator")
                .gender(Employee.Gender.OTHER)
                .status(Employee.Status.ACTIVE)
                .phone("1234567890")
                .address("Default Address")
                .dateOfJoining(java.time.LocalDate.now())
                .build();
        employeeRepository.save(admin);
        log.info("Default admin user created: {} / {}", DEFAULT_ADMIN_LOGIN, DEFAULT_ADMIN_PASSWORD);
    }

    /**
     * Create a category (if missing) and grant it permissions. When {@code fullAccess} is
     * true the category receives all permissions on all registered modules; otherwise only
     * view/create/edit on the modules listed in {@code limitedModules}.
     *
     * @return the category id
     */
    private String seedCategory(String name, String description, boolean fullAccess, Set<String> limitedModules) {
        MasterData category = masterDataRepository.findByTypeAndActive(
                        MasterData.MasterDataType.EMPLOYEE_CATEGORY, true).stream()
                .filter(md -> md.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    MasterData md = MasterData.builder()
                            .name(name)
                            .description(description)
                            .type(MasterData.MasterDataType.EMPLOYEE_CATEGORY)
                            .active(true)
                            .role("General Staff")
                            .build();
                    md.setCreatedBy("system");
                    md.setUpdatedBy("system");
                    md.setLastUpdatedBy("system");
                    return masterDataRepository.save(md);
                });

        // Only seed the permission matrix once (don't overwrite admin-configured changes).
        if (entitlementRepository.findByCategoryId(category.getId()).isEmpty()) {
            Map<String, ModulePermission> matrix = buildMatrix(fullAccess, limitedModules);
            Entitlement entitlement = Entitlement.builder()
                    .categoryId(category.getId())
                    .modulePermissions(matrix)
                    .build();
            entitlement.setCreatedBy("system");
            entitlement.setUpdatedBy("system");
            entitlement.setLastUpdatedBy("system");
            entitlementRepository.save(entitlement);
            log.info("Seeded entitlement for master-data category '{}' ({} modules)", name, matrix.size());
        }
        return category.getId();
    }

    private Map<String, ModulePermission> buildMatrix(boolean fullAccess, Set<String> limitedModules) {
        Map<String, ModulePermission> matrix = new HashMap<>();
        List<ModuleRegistry> modules = moduleRegistryService.findAll();
        for (ModuleRegistry module : modules) {
            boolean include = fullAccess || (limitedModules != null && limitedModules.contains(module.getModuleKey()));
            if (!include) {
                continue;
            }
            matrix.put(module.getModuleKey(), buildPermission(module, fullAccess));
        }
        return matrix;
    }

    private ModulePermission buildPermission(ModuleRegistry module, boolean fullAccess) {
        List<String> ops = module.getOperations();
        // Limited (party) users get view/create/edit only; full users get every operation.
        boolean view = ops.contains("view");
        boolean create = ops.contains("create");
        boolean edit = ops.contains("edit");
        boolean delete = fullAccess && ops.contains("delete");
        boolean export = fullAccess && ops.contains("export");
        boolean print = fullAccess && ops.contains("print");
        boolean email = fullAccess && ops.contains("email");
        boolean download = fullAccess && ops.contains("download");

        Map<String, Boolean> actions = new HashMap<>();
        if (module.getActions() != null) {
            module.getActions().forEach(a -> actions.put(a.getKey(), fullAccess));
        }
        Map<String, FieldPermission> fields = new HashMap<>();
        if (module.getFields() != null) {
            module.getFields().forEach(f -> fields.put(f.getKey(),
                    FieldPermission.builder().visible(fullAccess).editable(fullAccess).build()));
        }
        Map<String, Boolean> cards = new HashMap<>();
        if (module.getDashboardCards() != null) {
            module.getDashboardCards().forEach(c -> cards.put(c.getKey(), fullAccess || view));
        }

        return ModulePermission.builder()
                .view(view)
                .create(create)
                .edit(edit)
                .delete(delete)
                .export(export)
                .print(print)
                .email(email)
                .download(download)
                .actions(actions)
                .fields(fields)
                .dashboardCards(cards)
                .build();
    }

    private void backfillEmployeeCategories(String billingCategoryId, String partyCategoryId) {
        int updated = 0;
        List<Employee> employees = employeeRepository.findAll();
        for (Employee employee : employees) {
            if (employee.getRole() == Employee.Role.ADMIN) {
                continue; // Admin needs no category (full access by role).
            }

            String resolvedCategoryId = resolveEmployeeCategoryId(employee, billingCategoryId, partyCategoryId);
            if (resolvedCategoryId == null) {
                continue;
            }
            if (resolvedCategoryId.equals(employee.getCategoryId())) {
                continue;
            }
            employee.setCategoryId(resolvedCategoryId);
            employeeRepository.save(employee);
            updated++;
        }
        if (updated > 0) {
            log.info("Backfilled categoryId on {} legacy employee(s)", updated);
        }
    }

    private String resolveEmployeeCategoryId(Employee employee, String billingCategoryId, String partyCategoryId) {
        if (employee.getCategoryId() != null && !employee.getCategoryId().isBlank()) {
            var existing = masterDataRepository.findById(employee.getCategoryId());
            if (existing.isPresent()
                    && existing.get().getType() == MasterData.MasterDataType.EMPLOYEE_CATEGORY
                    && !Boolean.FALSE.equals(existing.get().getActive())) {
                return employee.getCategoryId();
            }
        }
        if (employee.getCategory() != null && !employee.getCategory().isBlank()) {
            var byName = masterDataRepository.findByTypeAndActive(
                            MasterData.MasterDataType.EMPLOYEE_CATEGORY, true).stream()
                    .filter(md -> md.getName().equalsIgnoreCase(employee.getCategory().trim()))
                    .findFirst();
            if (byName.isPresent()) {
                return byName.get().getId();
            }
        }
        return (employee.getRole() == Employee.Role.PARTY_USER) ? partyCategoryId : billingCategoryId;
    }
}

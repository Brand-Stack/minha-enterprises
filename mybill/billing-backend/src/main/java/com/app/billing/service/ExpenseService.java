package com.app.billing.service;

import com.app.billing.dao.ExpenseRepository;
import com.app.billing.dao.MasterDataRepository;
import com.app.billing.dto.ExpenseDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Expense;
import com.app.billing.model.MasterData;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {
    
    private final ExpenseRepository expenseRepository;
    private final MasterDataRepository masterDataRepository;
    private final AuditUtil auditUtil;
    private final MongoTemplate mongoTemplate;
    
    @Transactional
    public ExpenseDto create(ExpenseDto dto) {
        // Validate category from Master
        if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
            validateMasterData(dto.getCategory(), MasterData.MasterDataType.EXPENSE_CATEGORY, "Category");
        }
        
        Expense expense = toEntity(dto);
        
        if (expense.getExpenseDate() == null) {
            expense.setExpenseDate(LocalDate.now());
        }
        
        // Auto-generate expense number if not provided
        if (expense.getExpenseNumber() == null || expense.getExpenseNumber().trim().isEmpty()) {
            expense.setExpenseNumber(generateNextExpenseNumber(expense.getTransactionType()));
        }
        
        // Check if expense already exists for this purchase (prevent duplicates)
        if (dto.getPurchaseBillId() != null && !dto.getPurchaseBillId().trim().isEmpty()) {
            List<Expense> existingExpenses = expenseRepository.findByPurchaseBillId(dto.getPurchaseBillId());
            if (!existingExpenses.isEmpty()) {
                // Expense already exists for this purchase, return existing one
                return toDto(existingExpenses.get(0));
            }
        }
        
        auditUtil.setCreatedBy(expense);
        Expense saved = expenseRepository.save(expense);
        return toDto(saved);
    }
    
    public ExpenseDto findById(String id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
        return toDto(expense);
    }
    
    @Transactional
    public ExpenseDto update(String id, ExpenseDto dto) {
        Expense existing = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
        
        // Validate category from Master
        if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
            validateMasterData(dto.getCategory(), MasterData.MasterDataType.EXPENSE_CATEGORY, "Category");
        }
        
        Expense expense = toEntity(dto);
        expense.setId(id);
        
        auditUtil.setUpdatedBy(expense);
        
        Expense saved = expenseRepository.save(expense);
        return toDto(saved);
    }
    
    public PageResponse<ExpenseDto> findAll(int page, int size, String sortBy, String sortDir, Integer calendarMonth, Integer calendarYear) {
        List<Expense> all = expenseRepository.findAll();
        List<Expense> filtered = filterExpenses(all, calendarMonth, calendarYear);
        return getPageResponse(filtered, page, size, sortBy, sortDir);
    }
    
    public PageResponse<ExpenseDto> findByDateRange(LocalDate startDate, LocalDate endDate, int page, int size, Integer calendarMonth, Integer calendarYear) {
        List<Expense> all = expenseRepository.findByExpenseDateBetween(startDate, endDate);
        List<Expense> filtered = filterExpenses(all, calendarMonth, calendarYear);
        return getPageResponse(filtered, page, size, "expenseDate", "desc");
    }
    
    public List<ExpenseDto> findByCategory(String category, Integer calendarMonth, Integer calendarYear) {
        List<Expense> all = expenseRepository.findByCategory(category);
        List<Expense> filtered = filterExpenses(all, calendarMonth, calendarYear);
        return filtered.stream().map(this::toDto).collect(Collectors.toList());
    }
    
    public PageResponse<ExpenseDto> findByCategoryAndDateRange(String category, LocalDate startDate, LocalDate endDate, int page, int size, Integer calendarMonth, Integer calendarYear) {
        List<Expense> all = expenseRepository.findByCategoryAndExpenseDateBetween(category, startDate, endDate);
        List<Expense> filtered = filterExpenses(all, calendarMonth, calendarYear);
        return getPageResponse(filtered, page, size, "expenseDate", "desc");
    }

    private List<Expense> filterExpenses(List<Expense> list, Integer calendarMonth, Integer calendarYear) {
        if (calendarMonth == null && calendarYear == null) {
            return list;
        }
        return list.stream().filter(e -> {
            if (e.getExpenseDate() == null) {
                return false;
            }
            LocalDate d = e.getExpenseDate();
            if (calendarMonth != null && d.getMonthValue() != calendarMonth) {
                return false;
            }
            if (calendarYear != null && d.getYear() != calendarYear) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    private void sortExpenses(List<Expense> list, final String sortBy, final String sortDir) {
        final String field = (sortBy == null || sortBy.trim().isEmpty()) ? "expenseDate" : sortBy.trim();
        final boolean desc = "desc".equalsIgnoreCase(sortDir);
        list.sort((a, b) -> {
            int comp = 0;
            if ("expenseDate".equals(field)) {
                LocalDate da = a.getExpenseDate();
                LocalDate db = b.getExpenseDate();
                if (da != null && db != null) comp = da.compareTo(db);
                else if (da != null) comp = 1;
                else if (db != null) comp = -1;
            } else if ("expenseNumber".equals(field)) {
                String na = a.getExpenseNumber();
                String nb = b.getExpenseNumber();
                if (na != null && nb != null) comp = na.compareTo(nb);
                else if (na != null) comp = 1;
                else if (nb != null) comp = -1;
            } else if ("amount".equals(field)) {
                BigDecimal aa = a.getAmount();
                BigDecimal ab = b.getAmount();
                if (aa != null && ab != null) comp = aa.compareTo(ab);
                else if (aa != null) comp = 1;
                else if (ab != null) comp = -1;
            } else if ("category".equals(field)) {
                String ca = a.getCategory();
                String cb = b.getCategory();
                if (ca != null && cb != null) comp = ca.compareTo(cb);
                else if (ca != null) comp = 1;
                else if (cb != null) comp = -1;
            }
            return desc ? -comp : comp;
        });
    }

    private PageResponse<ExpenseDto> getPageResponse(List<Expense> list, int page, int size, String sortBy, String sortDir) {
        sortExpenses(list, sortBy, sortDir);
        int total = list.size();
        int from = page * size;
        if (from >= total) {
            return PaginationUtil.toPageResponse(List.of(), page, size, total);
        }
        int to = Math.min(from + size, total);
        List<ExpenseDto> slice = list.subList(from, to).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PaginationUtil.toPageResponse(slice, page, size, total);
    }
    
    public List<String> getDefaultCategories() {
        // Return categories from Master Data instead of hardcoded list
        return masterDataRepository.findByTypeAndActive(MasterData.MasterDataType.EXPENSE_CATEGORY, true)
                .stream()
                .map(MasterData::getName)
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
    
    public Map<String, java.math.BigDecimal> getCategoryTotals() {
        List<Expense> allExpenses = expenseRepository.findAll();
        return allExpenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.reducing(
                                java.math.BigDecimal.ZERO,
                                Expense::getAmount,
                                java.math.BigDecimal::add
                        )
                ));
    }
    
    @Transactional
    public void delete(String id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
        expenseRepository.delete(expense);
    }
    
    private String generateNextExpenseNumber(Expense.TransactionType type) {
        Query q = new Query();
        if (type == Expense.TransactionType.CASH_OUT) {
            q.addCriteria(Criteria.where("transactionType").is(Expense.TransactionType.CASH_OUT));
            long count = mongoTemplate.count(q, Expense.class);
            return "COT-" + String.format("%03d", count + 1);
        } else {
            q.addCriteria(new Criteria().orOperator(
                Criteria.where("transactionType").is(Expense.TransactionType.CASH_IN),
                Criteria.where("transactionType").exists(false)
            ));
            long count = mongoTemplate.count(q, Expense.class);
            return "CIN-" + String.format("%03d", count + 1);
        }
    }
    
    private Expense toEntity(ExpenseDto dto) {
        BigDecimal amt = dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO;
        BigDecimal paidCashAmount = (dto.getPaymentType() == Expense.PaymentType.CASH) ? amt : BigDecimal.ZERO;
        BigDecimal paidOnlineAmount = (dto.getPaymentType() != Expense.PaymentType.CASH) ? amt : BigDecimal.ZERO;
        Expense.TransactionType txType = dto.getTransactionType() != null 
                ? Expense.TransactionType.valueOf(dto.getTransactionType()) 
                : Expense.TransactionType.CASH_IN;
        return Expense.builder()
                .expenseNumber(dto.getExpenseNumber())
                .expenseDate(dto.getExpenseDate())
                .expenseDetails(dto.getExpenseDetails())
                .category(dto.getCategory())
                .partyName(dto.getPartyName())
                .purchaseBillId(dto.getPurchaseBillId())
                .paymentType(dto.getPaymentType())
                .amount(amt)
                .paidCashAmount(paidCashAmount)
                .paidOnlineAmount(paidOnlineAmount)
                .description(dto.getDescription())
                .transactionType(txType)
                .build();
    }
    
    private ExpenseDto toDto(Expense expense) {
        ExpenseDto dto = new ExpenseDto();
        dto.setId(expense.getId());
        dto.setExpenseNumber(expense.getExpenseNumber());
        dto.setExpenseDate(expense.getExpenseDate());
        dto.setExpenseDetails(expense.getExpenseDetails());
        dto.setCategory(expense.getCategory());
        dto.setPartyName(expense.getPartyName());
        dto.setPurchaseBillId(expense.getPurchaseBillId());
        dto.setPaymentType(expense.getPaymentType());
        dto.setAmount(expense.getAmount());
        dto.setPaidCashAmount(expense.getPaidCashAmount());
        dto.setPaidOnlineAmount(expense.getPaidOnlineAmount());
        dto.setDescription(expense.getDescription());
        dto.setLastUpdatedBy(expense.getLastUpdatedBy());
        dto.setTransactionType(expense.getTransactionType() != null ? expense.getTransactionType().name() : Expense.TransactionType.CASH_IN.name());
        dto.setCreatedBy(expense.getCreatedBy());
        return dto;
    }
}

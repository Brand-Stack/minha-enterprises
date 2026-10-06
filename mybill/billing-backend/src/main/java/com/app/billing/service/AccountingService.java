package com.app.billing.service;

import com.app.billing.dao.AccountingEntryRepository;
import com.app.billing.dto.AccountingEntryDto;
import com.app.billing.dto.AccountingSummaryDto;
import com.app.billing.dto.CashFlowDailyDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.util.PaginationUtil;
import com.app.billing.model.AccountingEntry;
import com.app.billing.util.AuditUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AccountingEntryRepository repository;
    private final AuditUtil auditUtil;

    @Transactional
    public AccountingEntryDto create(AccountingEntryDto dto) {
        validateAmountAndType(dto);
        String type = dto.getEntryType() != null ? dto.getEntryType().trim().toUpperCase() : "";
        Optional<AccountingEntry> last = repository.findTopByOrderByEntryDateDescCreatedAtDesc();
        BigDecimal balance = last.map(AccountingEntry::getBalanceAfter).orElse(ZERO);
        BigDecimal amt = dto.getAmount();
        if (AccountingEntry.TYPE_OUT.equals(type)) {
            if (balance.compareTo(ZERO) <= 0) {
                throw new IllegalArgumentException("Please add IN amount first.");
            }
            if (amt.compareTo(balance) > 0) {
                throw new IllegalArgumentException("OUT amount cannot exceed current balance.");
            }
        }
        BigDecimal newBalance = AccountingEntry.TYPE_IN.equals(type) ? balance.add(amt) : balance.subtract(amt);
        if (newBalance.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("Insufficient balance.");
        }
        LocalDate d = dto.getEntryDate() != null ? dto.getEntryDate() : LocalDate.now();
        AccountingEntry e = AccountingEntry.builder()
                .entryDate(d)
                .entryType(type)
                .amount(amt)
                .description(StringUtils.hasText(dto.getDescription()) ? dto.getDescription().trim() : "")
                .balanceAfter(newBalance)
                .build();
        auditUtil.setCreatedBy(e);
        e = repository.save(e);
        return toDto(e);
    }

    @Transactional
    public AccountingEntryDto update(String id, AccountingEntryDto dto) {
        validateAmountAndType(dto);
        AccountingEntry existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Accounting entry not found: " + id));
        existing.setEntryDate(dto.getEntryDate() != null ? dto.getEntryDate() : LocalDate.now());
        existing.setEntryType(dto.getEntryType() != null ? dto.getEntryType().trim().toUpperCase() : "");
        existing.setAmount(dto.getAmount());
        existing.setDescription(StringUtils.hasText(dto.getDescription()) ? dto.getDescription().trim() : "");
        auditUtil.setUpdatedBy(existing);
        repository.save(existing);
        recomputeRunningBalances();
        return toDto(repository.findById(id).orElseThrow());
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Accounting entry not found: " + id);
        }
        repository.deleteById(id);
        recomputeRunningBalances();
    }

    /**
     * Re-applies IN/OUT rules in chronological order and refreshes {@link AccountingEntry#getBalanceAfter()}
     * for every row (used after edit/delete).
     */
    private void recomputeRunningBalances() {
        List<AccountingEntry> ordered = repository.findAllByOrderByEntryDateAscCreatedAtAsc();
        BigDecimal running = ZERO;
        for (AccountingEntry e : ordered) {
            BigDecimal amt = e.getAmount() != null ? e.getAmount() : ZERO;
            String type = e.getEntryType() != null ? e.getEntryType().trim().toUpperCase() : "";
            if (!AccountingEntry.TYPE_IN.equals(type) && !AccountingEntry.TYPE_OUT.equals(type)) {
                throw new IllegalStateException("Invalid entry type in ledger: " + type);
            }
            if (AccountingEntry.TYPE_IN.equals(type)) {
                running = running.add(amt);
            } else {
                if (running.compareTo(amt) < 0) {
                    throw new IllegalArgumentException(
                            "Ledger would become inconsistent: an OUT entry exceeds the running balance at that point in time.");
                }
                running = running.subtract(amt);
            }
            e.setBalanceAfter(running);
            repository.save(e);
        }
    }

    private void validateAmountAndType(AccountingEntryDto dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        String type = dto.getEntryType() != null ? dto.getEntryType().trim().toUpperCase() : "";
        if (!AccountingEntry.TYPE_IN.equals(type) && !AccountingEntry.TYPE_OUT.equals(type)) {
            throw new IllegalArgumentException("Type must be IN or OUT.");
        }
    }

    public List<AccountingEntryDto> search(
            LocalDate dateFrom,
            LocalDate dateTo,
            String entryType,
            Integer calendarMonth,
            Integer calendarYear,
            String search) {
        List<AccountingEntry> filtered = new ArrayList<>(filterEntries(dateFrom, dateTo, entryType, calendarMonth, calendarYear, search));
        sortAccountingEntries(filtered, "entryDate", "desc");
        return filtered.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<AccountingEntryDto> search(
            LocalDate dateFrom,
            LocalDate dateTo,
            String entryType,
            Integer calendarMonth,
            Integer calendarYear) {
        return search(dateFrom, dateTo, entryType, calendarMonth, calendarYear, null);
    }

    public PageResponse<AccountingEntryDto> searchPaged(
            LocalDate dateFrom,
            LocalDate dateTo,
            String entryType,
            Integer calendarMonth,
            Integer calendarYear,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        List<AccountingEntry> entities = new ArrayList<>(filterEntries(dateFrom, dateTo, entryType, calendarMonth, calendarYear, search));
        sortAccountingEntries(entities, sortBy, sortDir);
        long total = entities.size();
        int from = page * size;
        if (from >= total) {
            return PaginationUtil.toPageResponse(List.of(), page, size, total);
        }
        int to = Math.min(from + size, (int) total);
        List<AccountingEntryDto> slice = entities.subList(from, to).stream().map(this::toDto).collect(Collectors.toList());
        return PaginationUtil.toPageResponse(slice, page, size, total);
    }

    public PageResponse<AccountingEntryDto> searchPaged(
            LocalDate dateFrom,
            LocalDate dateTo,
            String entryType,
            Integer calendarMonth,
            Integer calendarYear,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        return searchPaged(dateFrom, dateTo, entryType, calendarMonth, calendarYear, null, page, size, sortBy, sortDir);
    }

    private List<AccountingEntry> filterEntries(
            LocalDate dateFrom,
            LocalDate dateTo,
            String entryType,
            Integer calendarMonth,
            Integer calendarYear,
            String search) {
        List<AccountingEntry> all = repository.findAllByOrderByEntryDateAscCreatedAtAsc();
        String s = StringUtils.hasText(search) ? search.trim().toLowerCase() : null;
        return all.stream()
                .filter(e -> dateFrom == null || (e.getEntryDate() != null && !e.getEntryDate().isBefore(dateFrom)))
                .filter(e -> dateTo == null || (e.getEntryDate() != null && !e.getEntryDate().isAfter(dateTo)))
                .filter(e -> !StringUtils.hasText(entryType) || entryType.equalsIgnoreCase(e.getEntryType()))
                .filter(e -> {
                    if (e.getEntryDate() == null) {
                        return false;
                    }
                    if (calendarYear != null && calendarMonth != null && calendarMonth >= 1 && calendarMonth <= 12) {
                        YearMonth ym = YearMonth.of(calendarYear, calendarMonth);
                        return YearMonth.from(e.getEntryDate()).equals(ym);
                    }
                    if (calendarYear != null) {
                        return e.getEntryDate().getYear() == calendarYear;
                    }
                    if (calendarMonth != null && calendarMonth >= 1 && calendarMonth <= 12) {
                        return e.getEntryDate().getMonthValue() == calendarMonth;
                    }
                    return true;
                })
                .filter(e -> {
                    if (s == null) {
                        return true;
                    }
                    boolean matchDesc = e.getDescription() != null && e.getDescription().toLowerCase().contains(s);
                    boolean matchType = e.getEntryType() != null && e.getEntryType().toLowerCase().contains(s);
                    boolean matchAmount = e.getAmount() != null && e.getAmount().toString().contains(s);
                    boolean matchDate = e.getEntryDate() != null && e.getEntryDate().toString().contains(s);
                    return matchDesc || matchType || matchAmount || matchDate;
                })
                .collect(Collectors.toList());
    }

    public BigDecimal getTotalAmountForFilters(
            LocalDate dateFrom,
            LocalDate dateTo,
            String entryType,
            Integer calendarMonth,
            Integer calendarYear,
            String search) {
        List<AccountingEntry> filtered = filterEntries(dateFrom, dateTo, entryType, calendarMonth, calendarYear, search);
        return filtered.stream()
                .map(e -> e.getAmount() != null ? e.getAmount() : ZERO)
                .reduce(ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalAmountForFilters(
            LocalDate dateFrom,
            LocalDate dateTo,
            String entryType,
            Integer calendarMonth,
            Integer calendarYear) {
        return getTotalAmountForFilters(dateFrom, dateTo, entryType, calendarMonth, calendarYear, null);
    }

    private static void sortAccountingEntries(List<AccountingEntry> list, String sortBy, String sortDir) {
        boolean asc = "asc".equalsIgnoreCase(sortDir);
        Comparator<AccountingEntry> primary;
        String f = sortBy != null ? sortBy.trim() : "";
        switch (f) {
            case "amount":
                primary = Comparator.comparing(e -> e.getAmount() != null ? e.getAmount() : ZERO);
                break;
            case "entryType":
                primary = Comparator.comparing(e -> e.getEntryType() != null ? e.getEntryType() : "",
                        String.CASE_INSENSITIVE_ORDER);
                break;
            case "balanceAfter":
                primary = Comparator.comparing(e -> e.getBalanceAfter() != null ? e.getBalanceAfter() : ZERO);
                break;
            case "description":
                primary = Comparator.comparing(e -> e.getDescription() != null ? e.getDescription() : "",
                        String.CASE_INSENSITIVE_ORDER);
                break;
            default:
                primary = Comparator.comparing(AccountingEntry::getEntryDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(e -> e.getCreatedAt() != null ? e.getCreatedAt()
                                : java.time.LocalDateTime.MIN);
        }
        if (!asc) {
            primary = primary.reversed();
        }
        list.sort(primary);
    }

    public AccountingSummaryDto summary() {
        List<AccountingEntry> all = repository.findAllByOrderByEntryDateAscCreatedAtAsc();
        BigDecimal totalIn = ZERO;
        BigDecimal totalOut = ZERO;
        YearMonth ym = YearMonth.now();
        LocalDate ms = ym.atDay(1);
        LocalDate me = ym.atEndOfMonth();
        BigDecimal monthIn = ZERO;
        BigDecimal monthOut = ZERO;
        for (AccountingEntry e : all) {
            BigDecimal a = e.getAmount() != null ? e.getAmount() : ZERO;
            if (AccountingEntry.TYPE_IN.equalsIgnoreCase(e.getEntryType())) {
                totalIn = totalIn.add(a);
                if (e.getEntryDate() != null && !e.getEntryDate().isBefore(ms) && !e.getEntryDate().isAfter(me)) {
                    monthIn = monthIn.add(a);
                }
            } else if (AccountingEntry.TYPE_OUT.equalsIgnoreCase(e.getEntryType())) {
                totalOut = totalOut.add(a);
                if (e.getEntryDate() != null && !e.getEntryDate().isBefore(ms) && !e.getEntryDate().isAfter(me)) {
                    monthOut = monthOut.add(a);
                }
            }
        }
        BigDecimal current = all.isEmpty() ? ZERO
                : all.get(all.size() - 1).getBalanceAfter() != null ? all.get(all.size() - 1).getBalanceAfter() : ZERO;
        return AccountingSummaryDto.builder()
                .currentBalance(current)
                .totalIn(totalIn)
                .totalOut(totalOut)
                .monthIn(monthIn)
                .monthOut(monthOut)
                .totalTransactions(all.size())
                .build();
    }

    /** IN/OUT totals for an inclusive date range; null from/to means open-ended on that side. */
    public AccountingSummaryDto summaryInRange(LocalDate from, LocalDate to) {
        List<AccountingEntry> all = repository.findAllByOrderByEntryDateAscCreatedAtAsc();
        BigDecimal totalIn = ZERO;
        BigDecimal totalOut = ZERO;
        for (AccountingEntry e : all) {
            if (!isDateInRange(e.getEntryDate(), from, to)) {
                continue;
            }
            BigDecimal a = e.getAmount() != null ? e.getAmount() : ZERO;
            if (AccountingEntry.TYPE_IN.equalsIgnoreCase(e.getEntryType())) {
                totalIn = totalIn.add(a);
            } else if (AccountingEntry.TYPE_OUT.equalsIgnoreCase(e.getEntryType())) {
                totalOut = totalOut.add(a);
            }
        }
        BigDecimal net = totalIn.subtract(totalOut);
        return AccountingSummaryDto.builder()
                .currentBalance(net)
                .totalIn(totalIn)
                .totalOut(totalOut)
                .monthIn(totalIn)
                .monthOut(totalOut)
                .build();
    }

    public List<CashFlowDailyDto> cashFlowTrendInRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return cashFlowTrendLastDays(7);
        }
        LocalDate normalizedStart = from;
        LocalDate normalizedEnd = to;
        if (normalizedStart.isAfter(normalizedEnd)) {
            LocalDate tmp = normalizedStart;
            normalizedStart = normalizedEnd;
            normalizedEnd = tmp;
        }
        long span = java.time.temporal.ChronoUnit.DAYS.between(normalizedStart, normalizedEnd) + 1;
        if (span > 120) {
            normalizedStart = normalizedEnd.minusDays(119);
        }
        final LocalDate rangeStart = normalizedStart;
        final LocalDate rangeEnd = normalizedEnd;
        List<AccountingEntry> all = repository.findAllByOrderByEntryDateAscCreatedAtAsc();
        List<AccountingEntry> window = all.stream()
                .filter(e -> e.getEntryDate() != null
                        && !e.getEntryDate().isBefore(rangeStart)
                        && !e.getEntryDate().isAfter(rangeEnd))
                .collect(Collectors.toList());
        List<CashFlowDailyDto> out = new ArrayList<>();
        for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
            final LocalDate day = d;
            BigDecimal net = ZERO;
            for (AccountingEntry e : window) {
                if (!day.equals(e.getEntryDate())) {
                    continue;
                }
                BigDecimal a = e.getAmount() != null ? e.getAmount() : ZERO;
                if (AccountingEntry.TYPE_IN.equalsIgnoreCase(e.getEntryType())) {
                    net = net.add(a);
                } else {
                    net = net.subtract(a);
                }
            }
            out.add(CashFlowDailyDto.builder().date(day).netAmount(net).build());
        }
        return out;
    }

    private static boolean isDateInRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        if (to != null && date.isAfter(to)) {
            return false;
        }
        return true;
    }

    public List<CashFlowDailyDto> cashFlowTrendLastDays(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1);
        List<AccountingEntry> all = repository.findAllByOrderByEntryDateAscCreatedAtAsc();
        List<AccountingEntry> window = all.stream()
                .filter(e -> e.getEntryDate() != null && !e.getEntryDate().isBefore(start) && !e.getEntryDate().isAfter(end))
                .collect(Collectors.toList());
        List<CashFlowDailyDto> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            BigDecimal net = ZERO;
            for (AccountingEntry e : window) {
                if (!d.equals(e.getEntryDate())) {
                    continue;
                }
                BigDecimal a = e.getAmount() != null ? e.getAmount() : ZERO;
                if (AccountingEntry.TYPE_IN.equalsIgnoreCase(e.getEntryType())) {
                    net = net.add(a);
                } else {
                    net = net.subtract(a);
                }
            }
            out.add(CashFlowDailyDto.builder().date(d).netAmount(net).build());
        }
        return out;
    }

    private AccountingEntryDto toDto(AccountingEntry e) {
        return AccountingEntryDto.builder()
                .id(e.getId())
                .entryDate(e.getEntryDate())
                .entryType(e.getEntryType())
                .amount(e.getAmount())
                .description(e.getDescription())
                .balanceAfter(e.getBalanceAfter())
                .createdBy(e.getCreatedBy())
                .lastUpdatedBy(e.getLastUpdatedBy())
                .build();
    }
}

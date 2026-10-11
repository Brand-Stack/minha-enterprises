package com.app.billing.controller;

import com.app.billing.dto.AwbGlobalSearchHitDto;
import com.app.billing.dto.CourierDashboardDto;
import com.app.billing.dto.DashboardDto;
import com.app.billing.model.ModulePermission;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.AwbGlobalSearchService;
import com.app.billing.service.CourierDashboardService;
import com.app.billing.service.DashboardService;
import com.app.billing.service.PermissionEvaluatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@RequiresPermission(module = Modules.DASHBOARD, action = Modules.VIEW)
@Tag(name = "Dashboard", description = "Dashboard analytics and reports")
public class DashboardController {
    
    private final DashboardService dashboardService;
    private final CourierDashboardService courierDashboardService;
    private final AwbGlobalSearchService awbGlobalSearchService;
    private final PermissionEvaluatorService permissionEvaluatorService;
    
    @GetMapping("/turnover")
    @Operation(summary = "Get turnover data", description = "Get turnover aggregated by day, week, month, or year")
    public ResponseEntity<List<DashboardDto.TurnoverData>> getTurnover(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAY") String groupBy) {
        return ResponseEntity.ok(dashboardService.getTurnoverData(startDate, endDate, groupBy));
    }
    
    @GetMapping("/billing")
    @Operation(summary = "Get billing data", description = "Get billing statistics")
    public ResponseEntity<List<DashboardDto.BillingData>> getBillingData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAY") String groupBy) {
        return ResponseEntity.ok(dashboardService.getBillingData(startDate, endDate, groupBy, null));
    }
    
    @GetMapping("/stock")
    @Operation(summary = "Get stock data", description = "Get stock levels and movements")
    public ResponseEntity<List<DashboardDto.StockData>> getStockData() {
        return ResponseEntity.ok(dashboardService.getStockData());
    }
    
    @GetMapping("/sales")
    @Operation(summary = "Get sales data", description = "Get item-wise sales data")
    public ResponseEntity<List<DashboardDto.SalesData>> getSalesData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(dashboardService.getSalesData(startDate, endDate));
    }
    
    @GetMapping("/summary")
    @Operation(summary = "Get dashboard summary", description = "Get overall dashboard summary")
    public ResponseEntity<DashboardDto.DashboardSummary> getDashboardSummary() {
        return ResponseEntity.ok(dashboardService.getDashboardSummary());
    }

    @GetMapping("/courier")
    @Operation(summary = "Courier management dashboard (shipments, trends, activity)")
    public ResponseEntity<CourierDashboardDto.FullPayload> getCourierDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate revenueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate revenueTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate clientEntryFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate clientEntryTo,
            @RequestParam(required = false) String clientEntryCourier,
            @RequestParam(required = false) String clientEntryStatus,
            @RequestParam(required = false, defaultValue = "false") boolean revenueAll) {
        return ResponseEntity.ok(courierDashboardService.buildFull(
                revenueFrom, revenueTo, clientEntryFrom, clientEntryTo, clientEntryCourier, clientEntryStatus,
                revenueAll, resolveAllowedDashboardCards()));
    }

    /**
     * Resolve the set of dashboard card keys the current user may view.
     * Returns {@code null} for ADMIN (unrestricted) so no filtering is applied downstream.
     */
    private Set<String> resolveAllowedDashboardCards() {
        if (permissionEvaluatorService.isCurrentUserAdmin()) {
            return null;
        }
        ModulePermission perm = permissionEvaluatorService.currentModulePermission(Modules.DASHBOARD);
        if (perm == null || perm.getDashboardCards() == null) {
            return Collections.emptySet();
        }
        return perm.getDashboardCards().entrySet().stream()
                .filter(e -> Boolean.TRUE.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @GetMapping("/awb-search")
    @Operation(summary = "Search AWB across client entry, small client entry, collection center, and cash booking")
    public ResponseEntity<List<AwbGlobalSearchHitDto>> searchAwbAcrossModules(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String courierType) {
        return ResponseEntity.ok(awbGlobalSearchService.search(q, fromDate, toDate, courierType));
    }
}


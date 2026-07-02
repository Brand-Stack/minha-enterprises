package com.app.billing.service;

import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.EntitlementRepository;
import com.app.billing.dao.MasterDataRepository;
import com.app.billing.dto.AuthRequest;
import com.app.billing.dto.AuthResponse;
import com.app.billing.model.Employee;
import com.app.billing.model.Entitlement;
import com.app.billing.model.MasterData;
import com.app.billing.model.ModulePermission;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final EmployeeRepository employeeRepository;
    private final MasterDataRepository masterDataRepository;
    private final EntitlementRepository entitlementRepository;
    private final AuditService auditService;
    
    public AuthResponse authenticate(AuthRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            log.warn("Login failed for user {}: bad credentials", request.getEmail());
            throw new BadCredentialsException("Invalid user ID or password");
        }
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        Employee employee = employeeRepository.findByLoginId(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        
        String roleName = (employee.getRole() != null) ? employee.getRole().name() : "EMPLOYEE";
        if (employee.getRole() == null) {
            log.warn("Employee {} has no role set; using default EMPLOYEE", employee.getEmail());
        }
        boolean isAdmin = employee.getRole() == Employee.Role.ADMIN;
        
        String token = jwtUtil.generateToken(userDetails, roleName, employee.getId());

        String categoryName = employee.getCategory();
        if (employee.getCategoryId() != null) {
            categoryName = masterDataRepository.findById(employee.getCategoryId())
                    .filter(md -> md.getType() == MasterData.MasterDataType.EMPLOYEE_CATEGORY)
                    .map(MasterData::getName)
                    .orElse(categoryName);
        }

        // ADMIN gets an empty matrix here; the frontend treats admin=true as full access.
        Map<String, ModulePermission> permissions = new HashMap<>();
        if (!isAdmin && employee.getCategoryId() != null) {
            permissions = entitlementRepository.findByCategoryId(employee.getCategoryId())
                    .map(Entitlement::getModulePermissions)
                    .orElseGet(HashMap::new);
        }

        auditService.log("LOGIN", null, employee.getId(), "User logged in",
                employee.getId(), employee.getEmail(), roleName, employee.getCategory());

        return AuthResponse.builder()
                .token(token)
                .email(employee.getEmail())
                .role(roleName)
                .employeeId(employee.getId())
                .employeeName(employee.getEmployeeName())
                .categoryId(employee.getCategoryId())
                .categoryName(categoryName)
                .admin(isAdmin)
                .permissions(permissions)
                .build();
    }
}

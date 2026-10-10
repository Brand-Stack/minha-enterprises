package com.app.billing.security;

import com.app.billing.dao.EmployeeRepository;
import com.app.billing.model.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final EmployeeRepository employeeRepository;
    
    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        Employee employee = employeeRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("Employee not found: " + loginId));
        String roleName = (employee.getRole() != null) ? employee.getRole().name() : "BILLING_USER";
        return User.builder()
                .username(employee.getEmail())
                .password(employee.getPassword() != null ? employee.getPassword() : "")
                .authorities("ROLE_" + roleName)
                .build();
    }
}


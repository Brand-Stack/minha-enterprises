package com.app.billing.security;

import com.app.billing.service.PermissionEvaluatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Enforces {@link RequiresPermission} on controller methods. Throws
 * {@link AccessDeniedException} (mapped to HTTP 403 by the global exception handler)
 * when the current user lacks the required entitlement.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionAspect {

    private final PermissionEvaluatorService permissionEvaluatorService;

    private static final Map<String, String> ACTION_MESSAGES = Map.of(
            Modules.VIEW, "You do not have permission to view this module.",
            Modules.CREATE, "You do not have permission to create records.",
            Modules.EDIT, "You do not have permission to edit this record.",
            Modules.DELETE, "You do not have permission to delete this record.",
            Modules.DOWNLOAD, "You do not have permission to download this document.",
            Modules.EXPORT, "You do not have permission to export records.",
            Modules.PRINT, "You do not have permission to print this document.",
            Modules.EMAIL, "You do not have permission to email this document."
    );

    @Around("@annotation(com.app.billing.security.RequiresPermission) "
            + "|| @within(com.app.billing.security.RequiresPermission)")
    public Object enforce(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(RequiresPermission.class);
        }

        if (annotation != null) {
            boolean allowed = permissionEvaluatorService.hasPermission(annotation.module(), annotation.action());
            if (!allowed) {
                log.warn("Access denied: module={} action={} method={}",
                        annotation.module(), annotation.action(), method.getName());
                throw new AccessDeniedException(buildMessage(annotation.action()));
            }
        }
        return joinPoint.proceed();
    }

    private static String buildMessage(String action) {
        return ACTION_MESSAGES.getOrDefault(action, "You do not have permission to perform this action.");
    }
}

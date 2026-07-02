package com.app.billing.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the entitlement required to invoke a controller method. Enforced by
 * {@link PermissionAspect}. ADMIN users bypass the check; all other users must have
 * the given action granted on the given module via their category's entitlement.
 *
 * <p>Example:
 * <pre>{@code @RequiresPermission(module = Modules.CLIENTS, action = Modules.CREATE)}</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** Module key from {@link Modules}. */
    String module();

    /** Action key (standard CRUD-style or a named button action). Defaults to "view". */
    String action() default Modules.VIEW;
}

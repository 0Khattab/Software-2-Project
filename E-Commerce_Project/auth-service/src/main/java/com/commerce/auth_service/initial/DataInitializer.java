package com.commerce.auth_service.initial;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.commerce.auth_service.entity.Permission;
import com.commerce.auth_service.entity.RolePermission;
import com.commerce.auth_service.entity.User;
import com.commerce.auth_service.repository.PermissionRepository;
import com.commerce.auth_service.repository.RolePermissionRepository;

import jakarta.transaction.Transactional;

@Component
public class DataInitializer implements ApplicationRunner {
    @Autowired
    private  PermissionRepository permissionRepository;
    @Autowired
    private  RolePermissionRepository rolePermissionRepository;

    // ── All system permissions ─────────────────────────────────────────────
    private static final Map<String, String> ALL_PERMISSIONS = Map.ofEntries(
        // Product
        Map.entry("product:read",    "View products"),
        Map.entry("product:create",  "Create new products"),
        Map.entry("product:update",  "Update existing products"),
        Map.entry("product:delete",  "Delete products"),
        // Order
        Map.entry("order:read",      "View orders"),
        Map.entry("order:create",    "Create new orders"),
        Map.entry("order:update",    "Update order details"),
        Map.entry("order:cancel",    "Cancel orders"),
        // Payment
        Map.entry("payment:read",    "View payment details"),
        Map.entry("payment:process", "Process payments"),
        Map.entry("payment:refund",  "Issue refunds"),
        // User
        Map.entry("user:read",       "View user profiles"),
        Map.entry("user:manage",     "Block or delete users"),
        // Review
        Map.entry("review:read",     "View reviews"),
        Map.entry("review:create",   "Write reviews"),
        Map.entry("review:delete",   "Delete any review"),
        // Category
        Map.entry("category:read",   "View categories"),
        Map.entry("category:manage", "Create/edit/delete categories"),
        // Admin
        Map.entry("admin:dashboard", "View admin dashboard"),
        Map.entry("admin:reports",   "View sales reports")
    );

    // ── USER role gets these only ──────────────────────────────────────────
    private static final Set<String> USER_PERMISSIONS = Set.of(
        "product:read",
        "order:read",
        "order:create",
        "order:cancel",
        "payment:read",
        "payment:process",
        "review:read",
        "review:create",
        "category:read",
        "user:read"
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // ── Step 1: Insert missing permissions ─────────────────────────────
        // User admin = new User();
        ALL_PERMISSIONS.forEach((name, description) -> {
            if (!permissionRepository.existsByName(name)) {
                Permission permission = Permission.builder()
                        .name(name)
                        .description(description)
                        .build();
                permissionRepository.save(permission);
            }
        });

        // ── Step 2: Assign to ADMIN — all permissions ──────────────────────
        List<Permission> allPerms = permissionRepository.findAll();

        allPerms.forEach(permission -> {
            RolePermission.RolePermissionId adminId =
                new RolePermission.RolePermissionId(
                    "ADMIN", permission.getId());

            if (!rolePermissionRepository.existsById(adminId)) {
                rolePermissionRepository.save(
                    RolePermission.builder()
                        .id(adminId)
                        .role(User.Role.ADMIN)
                        .permission(permission)
                        .build()
                );
            }
        });

        // ── Step 3: Assign to USER — limited set ───────────────────────────
        allPerms.stream()
                .filter(p -> USER_PERMISSIONS.contains(p.getName()))
                .forEach(permission -> {
                    RolePermission.RolePermissionId userId =
                        new RolePermission.RolePermissionId(
                            "USER", permission.getId());

                    if (!rolePermissionRepository.existsById(userId)) {
                        rolePermissionRepository.save(
                            RolePermission.builder()
                                .id(userId)
                                .role(User.Role.USER)
                                .permission(permission)
                                .build()
                        );
                    }
                });
    }

}
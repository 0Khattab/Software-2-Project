package com.commerce.auth_service.initial;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.commerce.auth_service.entity.Permission;
import com.commerce.auth_service.entity.RolePermission;
import com.commerce.auth_service.entity.User;
import com.commerce.auth_service.repository.PermissionRepository;
import com.commerce.auth_service.repository.RolePermissionRepository;
import com.commerce.auth_service.repository.UserRepository;

import jakarta.transaction.Transactional;

@Component
public class DataInitializer implements ApplicationRunner {
    @Autowired
    private  PermissionRepository permissionRepository;
    @Autowired
    private  RolePermissionRepository rolePermissionRepository;
    @Autowired
    private  UserRepository userRepository;
    @Autowired
    private  PasswordEncoder passwordEncoder;

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
        Map.entry("cart:create",     "Create Cart"),
        Map.entry("cart:update",     "Update Cart"),
        Map.entry("cart:delete-item",     "Delete Item from Cart"),
        Map.entry("cart:add-item",     "Add Item to Cart"),
        Map.entry("cart:clear",     "Clear Cart"),
        Map.entry("cart:read", "view Cart"),
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
        Map.entry("admin:reports",   "View sales reports"),
        // Employee 
        Map.entry("employee:create",    "Add new employees"),
        Map.entry("employee:read",      "View employees"),
        Map.entry("employee:update",    "Update employee details"),
        Map.entry("employee:delete",    "Remove employees"),
        Map.entry("employee:permissions","Manage employee permissions")
    );

    private static final Set<String> USER_PERMISSIONS = Set.of(
        "product:read",
        "order:read",
        "order:create",
        "order:cancel",
        "order:update",
        "payment:read",
        "payment:process",
        "review:read",
        "review:create",
        "category:read",
        "user:read",
        "cart:create",
        "cart:update",
        "cart:delete-item",
        "cart:add-item",
        "cart:clear",
        "cart:read"
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ALL_PERMISSIONS.forEach((name, description) -> {
            if (!permissionRepository.existsByName(name)) {
                Permission permission = Permission.builder()
                        .name(name)
                        .description(description)
                        .build();
                permissionRepository.save(permission);
            }
        });

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
        initSuperAdmin();
    }

    private void initSuperAdmin() {
        String superAdminEmail = "superadmin@system.com";

        if (userRepository.existsByEmail(superAdminEmail)) {
            return;
        }

        User superAdmin = User.builder()
                .email(superAdminEmail)
                .passwordHash(passwordEncoder.encode("SuperAdmin@123"))
                .role(User.Role.ADMIN)
                .status(User.Status.ACTIVE)
                .build();

        userRepository.save(superAdmin);
    }

}
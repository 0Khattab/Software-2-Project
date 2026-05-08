package com.commerce.auth_service.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.commerce.auth_service.dto.AddEmployeeRequest;
import com.commerce.auth_service.dto.UserResponse;
import com.commerce.auth_service.entity.*;
import com.commerce.auth_service.exception.*;
import com.commerce.auth_service.interfaces.IEmployeeService;
import com.commerce.auth_service.repository.*;

import jakarta.transaction.Transactional;

@Service
public class EmployeeServiceImp implements IEmployeeService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private UserPermissionRepository userPermissionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;


    @Transactional
    public UserResponse addEmployee(String requesterId,
            AddEmployeeRequest request) {

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));

        validateRequesterCanAddEmployee(requester, request.getRole());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        Set<String> requestedPermissions = request.getPermissions();
        if (requestedPermissions == null || requestedPermissions.isEmpty()) {
            throw new IllegalArgumentException("Permissions must be specified when adding an employee");
        }


        User employee = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.EMPLOYEE)
                .status(User.Status.ACTIVE)
                .build();
        userRepository.save(employee);
        assignPermissionsToEmployee(employee, requestedPermissions);
        return mapToResponse(employee, requestedPermissions);
    }

    @Transactional
    public void updateEmployeePermissions(String requesterId,
            String employeeId,
            Set<String> newPermissions) {

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
        if (requester.getRole() != User.Role.ADMIN) {
    throw new ForbiddenException("Only ADMIN allowed");
}

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        if (employee.getRole() != User.Role.EMPLOYEE) {
            throw new ForbiddenException("Target user is not an employee");
        }


        userPermissionRepository.deleteAllByUserId(employeeId);

        assignPermissionsToEmployee(employee, newPermissions);
    }

    public UserResponse getEmployee(String employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        Set<String> permissions = userPermissionRepository
                .findByUserId(employeeId)
                .stream()
                .filter(UserPermission::isGranted)
                .map(up -> up.getPermission().getName())
                .collect(Collectors.toSet());

        return mapToResponse(employee, permissions);
    }

    public List<UserResponse> getAllEmployees() {
        return userRepository.findByRole(User.Role.EMPLOYEE)
                .stream()
                .map(emp -> getEmployee(emp.getId()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteEmployee(String requesterId, String employeeId) {
        User requester =userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
        if (requester.getRole() != User.Role.ADMIN) {
    throw new ForbiddenException("Only ADMIN allowed");
}

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        if (employee.getRole() != User.Role.EMPLOYEE) {
            throw new ForbiddenException("Target user is not an employee");
        }

        userRepository.delete(employee);
    }


    private void validateRequesterCanAddEmployee(User requester,
            User.Role targetRole) {
        boolean isAdmin = requester.getRole() == User.Role.ADMIN;

        if (!isAdmin) {
            throw new ForbiddenException(
                    "Only ADMIN can add employees");
        }

        if (isAdmin && targetRole != User.Role.EMPLOYEE) {
            throw new ForbiddenException(
                    "ADMIN can only add EMPLOYEE role users");
        }
    }


    private void assignPermissionsToEmployee(User employee,
            Set<String> permissionNames) {
        permissionNames.forEach(name -> {
            permissionRepository.findByName(name).ifPresent(permission -> {
                UserPermission up = UserPermission.builder()
                        .id(new UserPermission.UserPermissionId(
                                employee.getId(), permission.getId()))
                        .user(employee)
                        .permission(permission)
                        .granted(true)
                        .build();
                userPermissionRepository.save(up);
            });
        });
    }

    private UserResponse mapToResponse(User user, Set<String> permissions) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .permissions(permissions)
                .createdAt(user.getCreatedAt())
                .build();
    }

}

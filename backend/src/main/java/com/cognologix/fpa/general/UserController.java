package com.cognologix.fpa.general;

import com.cognologix.fpa.general.dto.ChangePasswordRequest;
import com.cognologix.fpa.general.dto.CreateUserRequest;
import com.cognologix.fpa.general.dto.ResetPasswordRequest;
import com.cognologix.fpa.general.dto.UpdateUserRoleRequest;
import com.cognologix.fpa.general.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Invite, roles, deactivate, password (ADR-042)")
public class UserController {

    private final UserService userService;

    @GetMapping
    @AdminOnly
    @Operation(summary = "List all users")
    public List<UserResponse> listUsers() {
        return userService.listAll().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @AdminOnly
    @Operation(summary = "Create / invite a user — sets must_change_password")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest req,
            Authentication auth) {
        var created = userService.createUser(
                req.email(), req.fullName(), req.role(), req.initialPassword(), actor(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    @PutMapping("/{id}/role")
    @AdminOnly
    @Operation(summary = "Change user role")
    public UserResponse updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateUserRoleRequest req) {
        return UserResponse.from(userService.updateRole(id, req.role()));
    }

    @PutMapping("/{id}/deactivate")
    @AdminOnly
    @Operation(summary = "Deactivate user (cannot deactivate self)")
    public UserResponse deactivate(@PathVariable UUID id, Authentication auth) {
        return UserResponse.from(userService.deactivate(id, actor(auth)));
    }

    @PutMapping("/{id}/reactivate")
    @AdminOnly
    @Operation(summary = "Reactivate a deactivated user")
    public UserResponse reactivate(@PathVariable UUID id) {
        return UserResponse.from(userService.reactivate(id));
    }

    @PutMapping("/{id}/reset-password")
    @AdminOnly
    @Operation(summary = "Admin reset of another user's password — sets must_change_password")
    public UserResponse resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest req) {
        return UserResponse.from(userService.resetPassword(id, req.newPassword()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get own profile")
    public UserResponse me(Authentication auth) {
        return userService.findByEmail(actor(auth))
                .map(UserResponse::from)
                .orElseThrow(() -> new GeneralBadRequestException("User not found"));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change own password (both roles)")
    public UserResponse changeOwnPassword(
            @Valid @RequestBody ChangePasswordRequest req,
            Authentication auth) {
        return UserResponse.from(userService.changeOwnPassword(
                actor(auth), req.currentPassword(), req.newPassword()));
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "system";
    }
}

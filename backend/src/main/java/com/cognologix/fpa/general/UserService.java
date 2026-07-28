package com.cognologix.fpa.general;

import com.cognologix.fpa.general.repository.AppUserRepository;
import com.cognologix.fpa.general.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cognologix.fpa.general.BackupGridHelper.*;

@Service
@RequiredArgsConstructor
public class UserService {

    public static final int LOGIN_RATE_LIMIT = 5;
    public static final int LOGIN_RATE_WINDOW_MINUTES = 15;
    public static final String USERS_BACKUP_FILE = "users.xlsx";

    static final String[] USER_BACKUP_HEADERS = {
            "id", "email", "full_name", "role", "is_active", "must_change_password", "created_at"
    };

    private final AppUserRepository appUserRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<AppUser> findByEmail(String email) {
        return appUserRepository.findByEmailIgnoreCase(email);
    }

    public AppUser requireById(UUID id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new GeneralBadRequestException("User not found: " + id));
    }

    public List<AppUser> listAll() {
        return appUserRepository.findAll();
    }

    public long countRecentLoginAttempts(String email) {
        Instant since = Instant.now().minus(LOGIN_RATE_WINDOW_MINUTES, ChronoUnit.MINUTES);
        return loginAttemptRepository.countByEmailSince(email, since);
    }

    public boolean isLoginRateLimited(String email) {
        return countRecentLoginAttempts(email) >= LOGIN_RATE_LIMIT;
    }

    @Transactional
    public void recordLoginAttempt(String email, boolean success, String ipAddress) {
        var attempt = new LoginAttempt();
        attempt.setEmail(email.trim().toLowerCase());
        attempt.setSuccess(success);
        attempt.setIpAddress(ipAddress);
        attempt.setAttemptedAt(Instant.now());
        loginAttemptRepository.save(attempt);
    }

    @Transactional
    public void markLoginSuccess(AppUser user) {
        user.setLastLoginAt(Instant.now());
        appUserRepository.save(user);
    }

    @Transactional
    public AppUser createUser(String email, String fullName, UserRole role,
                              String initialPassword, String createdBy) {
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new GeneralBadRequestException("A user with this email already exists");
        }
        var user = new AppUser();
        user.setEmail(email.trim().toLowerCase());
        user.setFullName(fullName.trim());
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setMustChangePassword(true);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setCreatedBy(createdBy);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser updateRole(UUID id, UserRole role) {
        var user = requireById(id);
        user.setRole(role);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser deactivate(UUID id, String actorEmail) {
        var user = requireById(id);
        if (user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw new GeneralBadRequestException("You cannot deactivate your own account");
        }
        user.setActive(false);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser reactivate(UUID id) {
        var user = requireById(id);
        user.setActive(true);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser resetPassword(UUID id, String newPassword) {
        var user = requireById(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser changeOwnPassword(String email, String currentPassword, String newPassword) {
        var user = findByEmail(email)
                .orElseThrow(() -> new GeneralBadRequestException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new GeneralBadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        return appUserRepository.save(user);
    }

    // ── Backup / restore (ADR-044 Tier 2) ────────────────────────────────────

    public BackupSheet exportUsersBackupSheet() {
        List<String[]> rows = new ArrayList<>();
        for (AppUser user : appUserRepository.findAll()) {
            rows.add(row(
                    str(user.getId()),
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole().name(),
                    String.valueOf(user.isActive()),
                    String.valueOf(user.isMustChangePassword()),
                    user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""));
        }
        return new BackupSheet(USERS_BACKUP_FILE, USER_BACKUP_HEADERS, rows);
    }

    @Transactional
    public void wipeUsersExcept(String preserveEmail) {
        String normalized = preserveEmail.trim().toLowerCase();
        loginAttemptRepository.deleteAllInBatch();
        for (AppUser user : appUserRepository.findAll()) {
            if (!user.getEmail().equalsIgnoreCase(normalized)) {
                appUserRepository.delete(user);
            }
        }
    }

    @Transactional
    public int restoreUsers(List<String[]> rows, String tempPassword, String preserveEmail) {
        String normalizedPreserve = preserveEmail.trim().toLowerCase();
        Optional<AppUser> preserved = appUserRepository.findByEmailIgnoreCase(normalizedPreserve);
        int count = 0;

        for (String[] row : rows) {
            try {
                String email = requireCell(row, 1, "email").toLowerCase();
                String fullName = requireCell(row, 2, "full_name");
                UserRole role = UserRole.valueOf(requireCell(row, 3, "role"));
                boolean active = parseBoolean(cell(row, 4));

                if (email.equalsIgnoreCase(normalizedPreserve)) {
                    preserved.ifPresent(user -> {
                        user.setFullName(fullName);
                        user.setRole(role);
                        user.setActive(active);
                        appUserRepository.save(user);
                    });
                    count++;
                    continue;
                }

                AppUser user = appUserRepository.findByEmailIgnoreCase(email).orElseGet(AppUser::new);
                user.setEmail(email);
                user.setFullName(fullName);
                user.setRole(role);
                user.setActive(active);
                user.setPasswordHash(passwordEncoder.encode(tempPassword));
                user.setMustChangePassword(true);
                if (user.getCreatedAt() == null) {
                    Instant created = parseInstant(cell(row, 6), "created_at");
                    user.setCreatedAt(created != null ? created : Instant.now());
                }
                user.setCreatedBy("restore");
                appUserRepository.save(user);
                count++;
            } catch (RuntimeException ignored) {
                // skip bad rows
            }
        }
        return count;
    }
}

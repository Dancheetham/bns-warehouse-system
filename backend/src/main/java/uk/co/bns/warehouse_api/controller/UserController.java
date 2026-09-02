package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.AuthUserView;
import uk.co.bns.warehouse_api.dto.ChangePasswordRequest;
import uk.co.bns.warehouse_api.dto.CreateUserRequest;
import uk.co.bns.warehouse_api.entity.User;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.UserRepository;
import uk.co.bns.warehouse_api.service.UserSettingsService;

import java.util.List;
import java.util.Map;

/**
 * Any logged-in user can manage users for now - there's no role/permission
 * model yet (everyone sees everything throughout this app), so this matches
 * the existing pattern rather than inventing admin/staff tiers nobody asked for.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSettingsService userSettingsService;

    @GetMapping
    public List<AuthUserView> getAll() {
        return userRepository.findAll().stream()
                .map(u -> new AuthUserView(u.getId(), u.getName()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserView create(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByNameIgnoreCase(request.name())) {
            throw new ValidationException("A login already exists with that name");
        }
        User user = new User();
        user.setName(request.name());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);
        return new AuthUserView(user.getId(), user.getName());
    }

    @PutMapping("/{id}/password")
    public void changePassword(@PathVariable Long id, @Valid @RequestBody ChangePasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @GetMapping("/me/settings")
    public Map<String, String> getMySettings(Authentication authentication) {
        return userSettingsService.getForUser(authentication.getName());
    }

    @PutMapping("/me/settings")
    public Map<String, String> setMySettings(Authentication authentication, @RequestBody Map<String, String> values) {
        userSettingsService.setAllForUser(authentication.getName(), values);
        return userSettingsService.getForUser(authentication.getName());
    }
}

package com.example.bank.controller;

import com.example.bank.dto.UserResponse;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * The caller's own profile. The ID comes from the verified token, never from the
     * URL, so there is no ID to tamper with to see someone else's profile.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.getUser(user.id());
    }
}

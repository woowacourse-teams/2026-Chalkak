package com.chalkak.backend.user.api.v1.controller;

import com.chalkak.backend.auth.LoginUser;
import com.chalkak.backend.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@LoginUser UUID userId) {
        userService.withdraw(userId);

        return ResponseEntity.noContent().build();
    }
}

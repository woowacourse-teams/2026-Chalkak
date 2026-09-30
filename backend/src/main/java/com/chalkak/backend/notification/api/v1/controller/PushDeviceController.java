package com.chalkak.backend.notification.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.auth.api.support.RequiresExistingUser;
import com.chalkak.backend.notification.api.v1.docs.PushDeviceApiDocs;
import com.chalkak.backend.notification.api.v1.dto.request.PushDeviceRegistrationRequest;
import com.chalkak.backend.notification.service.PushDeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push-devices")
public class PushDeviceController implements PushDeviceApiDocs {

    private final PushDeviceService pushDeviceService;

    @Override
    @RequiresExistingUser
    @PutMapping("/current")
    public ResponseEntity<Void> registerCurrentDevice(
            @Valid @RequestBody PushDeviceRegistrationRequest request,
            @LoginUser AuthenticatedUser loginUser
    ) {
        pushDeviceService.register(loginUser.userId(), loginUser.sessionId(), request.fcmToken());
        return ResponseEntity.noContent().build();
    }
}

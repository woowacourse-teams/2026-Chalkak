package com.chalkak.backend.filter.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.auth.api.support.RequiresUsableUser;
import com.chalkak.backend.filter.api.v1.docs.FilterApiDocs;
import com.chalkak.backend.filter.api.v1.dto.response.FilterDraftResponse;
import com.chalkak.backend.filter.service.FilterService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/filters")
public class FilterController implements FilterApiDocs {

    private final FilterService filterService;

    @Override
    @RequiresUsableUser
    @PostMapping
    public ResponseEntity<FilterDraftResponse> createDraft(@LoginUser AuthenticatedUser loginUser) {
        UUID filterId = filterService.createDraft(loginUser.userId());

        return ResponseEntity.status(HttpStatus.CREATED).body(new FilterDraftResponse(filterId));
    }
}

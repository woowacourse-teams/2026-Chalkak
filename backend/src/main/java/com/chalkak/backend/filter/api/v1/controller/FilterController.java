package com.chalkak.backend.filter.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.auth.api.support.RequiresExistingUser;
import com.chalkak.backend.auth.api.support.RequiresUsableUser;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.filter.api.v1.docs.FilterApiDocs;
import com.chalkak.backend.filter.api.v1.dto.request.AuthorFilterListRequest;
import com.chalkak.backend.filter.api.v1.dto.response.AuthorFilterListResponse;
import com.chalkak.backend.filter.api.v1.dto.response.FilterDetailResponse;
import com.chalkak.backend.filter.api.v1.dto.response.FilterDraftResponse;
import com.chalkak.backend.filter.service.AuthorFilterListResult;
import com.chalkak.backend.filter.service.FilterDetailResult;
import com.chalkak.backend.filter.service.FilterService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class FilterController implements FilterApiDocs {

    private final FilterService filterService;

    @Override
    @RequiresUsableUser
    @PostMapping("/filters")
    public ResponseEntity<FilterDraftResponse> createDraft(@LoginUser AuthenticatedUser loginUser) {
        UUID filterId = filterService.createDraft(loginUser.userId());

        return ResponseEntity.status(HttpStatus.CREATED).body(new FilterDraftResponse(filterId));
    }

    @Override
    @RequiresExistingUser
    @GetMapping("/users/{userId}/filters")
    public ResponseEntity<AuthorFilterListResponse> getAuthorFilters(
            @PathVariable("userId") String artistId,
            @Valid @ModelAttribute AuthorFilterListRequest request,
            @LoginUser AuthenticatedUser loginUser
    ) {
        AuthorFilterListResult result = filterService.getAuthorFilters(
                CanonicalUuidParser.parse(artistId),
                loginUser.userId(),
                request.page(),
                request.pageSize()
        );
        return ResponseEntity.ok(AuthorFilterListResponse.fromResult(result));
    }

    @Override
    @RequiresExistingUser
    @GetMapping("/filters/{filterId}")
    public ResponseEntity<FilterDetailResponse> getFilter(
            @PathVariable String filterId,
            @LoginUser AuthenticatedUser loginUser
    ) {
        FilterDetailResult result = filterService.getFilter(CanonicalUuidParser.parse(filterId),
                loginUser.userId());
        return ResponseEntity.ok(FilterDetailResponse.fromResult(result));
    }
}

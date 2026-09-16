package com.chalkak.backend.feedback.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.auth.api.support.RequiresExistingUser;
import com.chalkak.backend.feedback.api.v1.docs.FeedbackApiDocs;
import com.chalkak.backend.feedback.api.v1.dto.request.FeedbackSubmissionRequest;
import com.chalkak.backend.feedback.api.v1.dto.response.FeedbackSubmissionResponse;
import com.chalkak.backend.feedback.service.FeedbackService;
import com.chalkak.backend.feedback.service.FeedbackSubmissionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/feedbacks")
public class FeedbackController implements FeedbackApiDocs {

    private final FeedbackService feedbackService;

    @Override
    @RequiresExistingUser
    @PostMapping
    public ResponseEntity<FeedbackSubmissionResponse> submitFeedback(
            @Valid @RequestBody FeedbackSubmissionRequest request,
            @LoginUser AuthenticatedUser loginUser
    ) {
        FeedbackSubmissionResult result = feedbackService.submit(
                loginUser.userId(),
                request.content());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FeedbackSubmissionResponse.from(result));
    }
}

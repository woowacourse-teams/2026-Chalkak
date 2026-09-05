package com.chalkak.backend.topic.api.v1.controller;

import com.chalkak.backend.topic.api.v1.docs.TopicApiDocs;
import com.chalkak.backend.topic.api.v1.dto.response.TopicDetailResponse;
import com.chalkak.backend.topic.service.TopicDetail;
import com.chalkak.backend.topic.service.TopicService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/topics")
public class TopicController implements TopicApiDocs {

    private final TopicService topicService;

    @Override
    @GetMapping
    public ResponseEntity<TopicDetailResponse> getTopic(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        TopicDetail detail = topicService.getTopic(date);

        return ResponseEntity.ok(TopicDetailResponse.fromTopicDetail(detail));
    }
}

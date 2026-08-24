package com.chalkak.backend.topic.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TopicRepository topicRepository;

    public TopicDetail getTopic(LocalDate topicDate) {
        validateNotFuture(topicDate);

        Topic topic = topicRepository.findActiveByTopicDate(topicDate)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "주제를 찾을 수 없습니다."
                ));

        return TopicDetail.from(topic, Instant.now());
    }

    private void validateNotFuture(LocalDate topicDate) {
        if (topicDate.isAfter(LocalDate.now(KST))) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "미래의 주제는 조회할 수 없습니다."
            );
        }
    }
}

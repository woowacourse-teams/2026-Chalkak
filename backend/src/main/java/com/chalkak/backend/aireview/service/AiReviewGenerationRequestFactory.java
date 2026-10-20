package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.domain.AiPromptDraft;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 프롬프트 초안과 게시물로 생성 요청을 조립한다. 관리자 미리보기와 워커가 같은 방식으로 원본 사진 주소와 주제 제목을 넣도록 한 곳에
 * 둔다. 게시물은 사진과 주제를 함께 읽어 온 것이어야 한다.
 */
@Component
@RequiredArgsConstructor
public class AiReviewGenerationRequestFactory {

    private final ImageUrlProvider imageUrlProvider;

    public AiReviewGenerationRequest create(AiPromptDraft draft, Post post) {
        return new AiReviewGenerationRequest(
                draft,
                imageUrlProvider.getUrl(post.getPhoto().getOriginalStorageKey()),
                post.getTopic().getTitle()
        );
    }
}

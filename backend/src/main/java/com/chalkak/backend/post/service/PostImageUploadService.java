package com.chalkak.backend.post.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.repository.PostImageUploadIssuer;
import com.chalkak.backend.post.repository.PostImageUploadRepository;
import com.chalkak.backend.post.repository.PresignedPostImageUpload;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시물 이미지 업로드 권한 발급. */
@Service
@RequiredArgsConstructor
public class PostImageUploadService {

    private final UserRepository userRepository;
    private final PostImageUploadRepository postImageUploadRepository;
    private final PostImageUploadIssuer postImageUploadIssuer;

    @Transactional
    public PostImageUploadResult createPostImageUpload(UUID userId) {
        User uploader = userRepository.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "사진을 업로드할 회원을 찾을 수 없습니다."
                ));

        PostImageUpload upload = postImageUploadRepository.save(
                PostImageUpload.createPostImageUpload(uploader, Instant.now())
        );
        PresignedPostImageUpload presigned = postImageUploadIssuer.issue(upload.getId());

        return new PostImageUploadResult(
                upload.getId(),
                presigned.uploadUrl(),
                presigned.expiresInSeconds(),
                presigned.contentType(),
                presigned.maxBytes()
        );
    }
}

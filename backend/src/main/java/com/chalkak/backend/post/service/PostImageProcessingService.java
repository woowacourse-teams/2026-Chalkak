package com.chalkak.backend.post.service;

import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostImageUploadRepository;
import com.chalkak.backend.post.repository.PostRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이미지 처리 Lambda가 보내는 완료·실패 콜백 반영. */
@Service
@RequiredArgsConstructor
public class PostImageProcessingService {

    private final PostRepository postRepository;
    private final PostImageStorage postImageStorage;
    private final PostImageUploadRepository postImageUploadRepository;

    /**
     * 게시물이 아직 없으면 업로드 상태만 바꾸고 끝낸다. 나중에 도착하는 게시물 생성 요청이 READY를 보고
     * 곧바로 공개 상태로 만든다.
     */
    @Transactional
    public void completePostImageProcessing(UUID uploadId, Map<String, Object> imageMetadata) {
        postImageUploadRepository.findByIdForUpdate(uploadId)
                .ifPresent(upload -> completeProcessedUpload(upload, uploadId, imageMetadata));
    }

    @Transactional
    public void failPostImageProcessing(UUID uploadId, String rejectionReason) {
        postImageUploadRepository.findByIdForUpdate(uploadId)
                .ifPresent(upload -> failProcessedUpload(upload, uploadId, rejectionReason));
    }

    private void completeProcessedUpload(
            PostImageUpload upload,
            UUID uploadId,
            Map<String, Object> imageMetadata
    ) {
        upload.completeProcessing(imageMetadata);
        if (!upload.isProcessed()) {
            return;
        }
        findValidatingPost(uploadId).ifPresent(post -> {
            post.getPhoto().completeProcessing(
                    postImageStorage.toThumbnailStorageKey(uploadId),
                    upload.getImageMetadata()
            );
            post.approve(Instant.now());
        });
    }

    private void failProcessedUpload(
            PostImageUpload upload,
            UUID uploadId,
            String rejectionReason
    ) {
        upload.failProcessing(rejectionReason);
        if (!upload.isRejected()) {
            return;
        }
        findValidatingPost(uploadId).ifPresent(post -> post.reject(Instant.now()));
    }

    private Optional<Post> findValidatingPost(UUID uploadId) {
        return postRepository.findValidatingByPostImageUploadId(uploadId);
    }
}

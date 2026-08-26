package com.chalkak.backend.post.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.photo.repository.PhotoRepository;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostImageUploadIssuer;
import com.chalkak.backend.post.repository.PostImageUploadRepository;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PresignedPostImageUpload;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.domain.TopicPhase;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시물과 이미지 업로드의 상태를 바꾸는 흐름. 업로드 권한 발급, 게시물 생성, 이미지 처리 결과 반영이 모두
 * 같은 업로드 행을 잠그고 겨루므로 한자리에 둔다. 읽기 전용 조회는 {@link PostQueryService}가 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PostCommandService {

    private final PostRepository postRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final PostImageStorage postImageStorage;
    private final PostImageUploadRepository postImageUploadRepository;
    private final PostImageUploadIssuer postImageUploadIssuer;
    private final PostProcessingPolicy postProcessingPolicy;

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

    public PostCreationResult createPost(
            UUID userId,
            UUID topicId,
            UUID photoUploadId,
            String title
    ) {
        User author = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 작성할 회원을 찾을 수 없습니다."
                ));
        Topic topic = topicRepository.findActiveById(topicId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 작성할 주제를 찾을 수 없습니다."
                ));
        validateTopicOpen(topic);
        validatePostNotCreated(userId, topicId, Instant.now());

        // S3 확인은 왕복이 길 수 있다. 업로드 행에 비관적 락을 잡은 채 기다리면 같은 업로드의 처리 콜백이
        // 그동안 락 대기에 묶이므로 락 밖에서 먼저 확인한다.
        boolean stagingImageExists = needsStagingImageCheck(userId, photoUploadId)
                && postImageStorage.existsUploadedImage(photoUploadId);

        PostImageUpload upload = getClaimableUpload(userId, photoUploadId);
        upload.claim(Instant.now());
        // 확인한 뒤에 처리가 끝나 staging이 지워졌을 수 있으므로 최종 판정은 락 안에서 읽은 상태로 한다.
        if (!upload.isProcessed() && !stagingImageExists) {
            throw new NotFoundException(
                    ErrorCode.BUSINESS_ERROR,
                    "업로드한 사진을 찾을 수 없습니다."
            );
        }

        String originalStorageKey = postImageStorage.toOriginalStorageKey(photoUploadId);
        validatePhotoNotUsed(originalStorageKey);

        Photo photo = Photo.createPhoto(originalStorageKey);
        Post post = Post.createPost(author, topic, photo, photoUploadId, title);
        if (upload.isProcessed()) {
            photo.completeProcessing(
                    postImageStorage.toThumbnailStorageKey(photoUploadId),
                    upload.getImageMetadata()
            );
            post.approve(Instant.now());
        }
        Post savedPost = postRepository.save(post);

        return new PostCreationResult(savedPost.getId(), savedPost.getModerationStatus());
    }

    /**
     * 이미지 처리 완료 콜백. 게시물이 아직 없으면 업로드 상태만 바꾸고 끝낸다. 나중에 도착하는 게시물 생성
     * 요청이 READY를 보고 곧바로 공개 상태로 만든다.
     */
    public void completePostImageProcessing(UUID uploadId, Map<String, Object> imageMetadata) {
        postImageUploadRepository.findByIdForUpdate(uploadId)
                .ifPresent(upload -> completeProcessedUpload(upload, uploadId, imageMetadata));
    }

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

    private void validateTopicOpen(Topic topic) {
        if (topic.getParticipationPeriod().phaseAt(Instant.now()) != TopicPhase.OPEN) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "현재 게시물을 작성할 수 없는 주제입니다."
            );
        }
    }

    /**
     * 이미지 처리 콜백이 유실되거나 영구 거부되면 게시물이 검수 대기 상태로 남는다. 작성자에게는 보이지도
     * 않으면서 같은 주제 재작성만 막으므로, 처리 대기 시간을 넘긴 게시물은 여기서 거절 처리하고 길을 터 준다.
     */
    private void validatePostNotCreated(UUID userId, UUID topicId, Instant now) {
        Optional<Post> activePost =
                postRepository.findActiveByAuthorIdAndTopicId(userId, topicId);
        if (activePost.isEmpty()) {
            return;
        }

        Post post = activePost.get();
        if (isProcessingTimedOut(post, now)) {
            post.reject(now);
            // 부분 유니크 인덱스가 REJECTED만 제외하므로, 새 게시물 INSERT보다 이 거절이 먼저 반영돼야 한다.
            postRepository.flush();
            return;
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "이미 해당 주제에 게시물을 작성했습니다."
        );
    }

    private boolean isProcessingTimedOut(Post post, Instant now) {
        return post.isValidating()
                && postProcessingPolicy.isProcessingTimedOut(post.getCreatedAt(), now);
    }

    /**
     * 다른 회원의 uploadId는 권한 없음이 아니라 없는 것으로 답한다. 존재 여부를 알려주지 않기 위해서다.
     */
    private PostImageUpload getClaimableUpload(UUID userId, UUID photoUploadId) {
        return postImageUploadRepository.findByIdForUpdate(photoUploadId)
                .filter(upload -> upload.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "업로드한 사진을 찾을 수 없습니다."
                ));
    }

    /**
     * 이미 처리가 끝났으면 staging 객체는 지워진 뒤라 확인할 필요가 없고, 없거나 남의 업로드면 어차피
     * {@link #getClaimableUpload}가 없는 것으로 답하므로 S3까지 갈 이유가 없다.
     */
    private boolean needsStagingImageCheck(UUID userId, UUID photoUploadId) {
        return postImageUploadRepository.findStatusByIdAndUserId(photoUploadId, userId)
                .filter(status -> status != PostImageUploadStatus.READY)
                .isPresent();
    }

    private void validatePhotoNotUsed(String originalStorageKey) {
        if (photoRepository.existsByOriginalStorageKey(originalStorageKey)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미 사용된 사진입니다."
            );
        }
    }
}

package com.chalkak.backend.post.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.photo.repository.PhotoRepository;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostImageUploadIssuer;
import com.chalkak.backend.post.repository.PostImageUploadRepository;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PostSlice;
import com.chalkak.backend.post.repository.PresignedPostImageUpload;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.domain.TopicPhase;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PostRepository postRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final PostImageStorage postImageStorage;
    private final PostImageUploadRepository postImageUploadRepository;
    private final PostImageUploadIssuer postImageUploadIssuer;
    private final ImageUrlProvider imageUrlProvider;
    private final RandomSeedGenerator randomSeedGenerator;

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

    @Transactional
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
        validatePostNotCreated(userId, topicId);

        PostImageUpload upload = getClaimableUpload(userId, photoUploadId);
        upload.claim(Instant.now());
        if (!upload.isProcessed()) {
            validateUploadedImageExists(photoUploadId);
        }

        String originalStorageKey = postImageStorage.toOriginalStorageKey(photoUploadId);
        validatePhotoNotUsed(originalStorageKey);

        Photo photo = Photo.createPhoto(originalStorageKey);
        Post post = Post.createPost(author, topic, photo, title);
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

    public PostDetail getPost(UUID postId) {
        Post post = postRepository.findVisibleById(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."
                ));

        return PostDetail.from(post, imageUrlProvider);
    }

    public PostListResult getPosts(
            LocalDate topicDate,
            PostSort sort,
            String randomSeed,
            int page,
            int pageSize
    ) {
        validateRandomSeedCombination(sort, randomSeed, page);
        validateTopicDate(topicDate);

        Topic topic = topicRepository.findActiveByTopicDate(topicDate)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "해당 날짜의 주제를 찾을 수 없습니다."
                ));
        if (sort == PostSort.RANDOM) {
            String effectiveRandomSeed = Objects.requireNonNullElseGet(
                    randomSeed,
                    randomSeedGenerator::generateRandomSeed
            );
            PostSlice postSlice = postRepository.findVisibleRandomByTopicId(
                    topic.getId(),
                    effectiveRandomSeed,
                    page - 1,
                    pageSize
            );
            return PostListResult.from(
                    postSlice,
                    page,
                    pageSize,
                    effectiveRandomSeed,
                    imageUrlProvider
            );
        }

        PostSlice postSlice = postRepository.findVisibleRecentByTopicId(
                topic.getId(),
                page - 1,
                pageSize
        );

        return PostListResult.from(
                postSlice,
                page,
                pageSize,
                randomSeed,
                imageUrlProvider
        );
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
        return postRepository.findValidatingByOriginalStorageKey(
                postImageStorage.toOriginalStorageKey(uploadId)
        );
    }

    private void validateRandomSeedCombination(
            PostSort sort,
            String randomSeed,
            int page
    ) {
        boolean hasSeedWithRecentSort = sort == PostSort.RECENT && randomSeed != null;
        boolean isSeedMissingAfterFirstRandomPage = sort == PostSort.RANDOM
                && page > 1
                && randomSeed == null;

        if (hasSeedWithRecentSort
                || isSeedMissingAfterFirstRandomPage) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "조회 조건이 올바르지 않습니다."
            );
        }
    }

    private void validateTopicDate(LocalDate topicDate) {
        if (topicDate.isAfter(LocalDate.now(KST))) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "미래 날짜의 게시물은 조회할 수 없습니다."
            );
        }
    }

    private void validateTopicOpen(Topic topic) {
        if (topic.getParticipationPeriod().phaseAt(Instant.now()) != TopicPhase.OPEN) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "현재 게시물을 작성할 수 없는 주제입니다."
            );
        }
    }

    private void validatePostNotCreated(UUID userId, UUID topicId) {
        if (postRepository.existsActiveByAuthorIdAndTopicId(userId, topicId)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미 해당 주제에 게시물을 작성했습니다."
            );
        }
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

    private void validateUploadedImageExists(UUID photoUploadId) {
        if (!postImageStorage.existsUploadedImage(photoUploadId)) {
            throw new NotFoundException(
                    ErrorCode.BUSINESS_ERROR,
                    "업로드한 사진을 찾을 수 없습니다."
            );
        }
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

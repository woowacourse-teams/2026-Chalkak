package com.chalkak.backend.filter.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.filter.domain.Filter;
import com.chalkak.backend.filter.repository.FilterRepository;
import com.chalkak.backend.filter.repository.FilterSlice;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostImageUploadRepository;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FilterService {

    private final UserRepository userRepository;
    private final FilterRepository filterRepository;
    private final PostImageUploadRepository postImageUploadRepository;
    private final PostImageStorage postImageStorage;
    private final ImageUrlProvider imageUrlProvider;

    @Transactional
    public UUID createDraft(UUID userId) {
        User artist = getActiveArtist(userId, "작가만 필터 초안을 만들 수 있습니다.");
        return filterRepository.save(Filter.createDraft(artist, Instant.now())).getId();
    }

    @Transactional
    public void updateDraft(
            UUID filterId,
            UUID userId,
            String name,
            String description,
            List<UUID> uploadIds
    ) {
        getActiveArtist(userId, "작가만 필터 초안을 수정할 수 있습니다.");
        Filter filter = filterRepository.findActiveByIdForUpdate(filterId)
                .orElseThrow(this::filterNotFound);
        validateFilterOwner(filter, userId);
        filter.updateDraft(
                name,
                description,
                Instant.now()
        );
        if (uploadIds != null) {
            replacePhotos(
                    filterId,
                    userId,
                    uploadIds
            );
        }
    }

    @Transactional
    public void deleteDraft(UUID filterId, UUID userId) {
        getActiveArtist(userId, "작가만 필터 초안을 삭제할 수 있습니다.");
        Filter filter = filterRepository.findByIdForUpdate(filterId)
                .orElseThrow(this::filterNotFound);
        validateFilterOwner(filter, userId);
        filter.deleteDraft(Instant.now());
    }

    @Transactional(readOnly = true)
    public AuthorFilterListResult getAuthorFilters(
            UUID artistId,
            UUID viewerId,
            int page,
            int pageSize
    ) {
        FilterSlice slice = filterRepository.findVisibleByArtistId(
                artistId,
                viewerId,
                page,
                pageSize
        );
        return AuthorFilterListResult.fromSlice(
                slice,
                page,
                pageSize
        );
    }

    @Transactional(readOnly = true)
    public FilterDetailResult getFilter(UUID filterId, UUID viewerId) {
        Filter filter = filterRepository.findVisibleById(filterId, viewerId)
                .orElseThrow(this::filterNotFound);
        boolean owner = filter.isOwnedBy(viewerId);
        List<FilterDetailResult.Photo> photos = filterRepository.findPhotoUploads(filterId).stream()
                .filter(upload -> owner || upload.isProcessed())
                .map(this::toPhoto)
                .toList();

        return FilterDetailResult.fromFilter(filter, photos);
    }

    private NotFoundException filterNotFound() {
        return new NotFoundException(ErrorCode.BUSINESS_ERROR, "필터를 찾을 수 없습니다.");
    }

    private void validateFilterOwner(Filter filter, UUID userId) {
        if (!filter.isOwnedBy(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "본인의 필터가 아닙니다.");
        }
    }

    private User getActiveArtist(UUID userId, String notArtistMessage) {
        User artist = userRepository.findActiveByIdForShare(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED,
                        "유효하지 않은 인증 정보입니다."));
        artist.validateAccessible();
        if (!artist.isArtist()) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, notArtistMessage);
        }
        return artist;
    }

    private void replacePhotos(
            UUID filterId,
            UUID userId,
            List<UUID> uploadIds
    ) {
        if (Set.copyOf(uploadIds).size() != uploadIds.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "같은 소개 사진을 중복해서 연결할 수 없습니다.");
        }
        Set<UUID> currentUploadIds = filterRepository.findPhotoUploads(filterId).stream()
                .map(PostImageUpload::getId)
                .collect(Collectors.toSet());

        // 여러 초안에서 같은 업로드를 동시에 연결하더라도 업로드 행을 같은 순서로 잠근다.
        List<UUID> newUploadIds = uploadIds.stream()
                .filter(uploadId -> !currentUploadIds.contains(uploadId))
                .sorted()
                .toList();
        for (UUID uploadId : newUploadIds) {
            PostImageUpload upload = postImageUploadRepository.findByIdForUpdate(uploadId)
                    .filter(candidate -> candidate.isOwnedBy(userId))
                    .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                            "업로드한 사진을 찾을 수 없습니다."));
            upload.claim(Instant.now());
        }
        filterRepository.replacePhotoUploads(filterId, uploadIds);
    }

    private FilterDetailResult.Photo toPhoto(PostImageUpload upload) {
        UUID uploadId = upload.getId();
        String originalImageUrl = null;
        String thumbnailImageUrl = null;
        if (upload.isProcessed()) {
            originalImageUrl = imageUrlProvider
                    .getUrl(postImageStorage.toOriginalStorageKey(uploadId));
            thumbnailImageUrl = imageUrlProvider
                    .getUrl(postImageStorage.toThumbnailStorageKey(uploadId));
        }

        return new FilterDetailResult.Photo(
                uploadId,
                upload.getStatus(),
                originalImageUrl,
                thumbnailImageUrl
        );
    }
}

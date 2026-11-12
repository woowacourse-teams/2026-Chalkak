package com.chalkak.backend.filter.service;

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
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FilterService {

    private final UserRepository userRepository;
    private final FilterRepository filterRepository;
    private final PostImageStorage postImageStorage;
    private final ImageUrlProvider imageUrlProvider;

    @Transactional
    public UUID createDraft(UUID userId) {
        User artist = userRepository.findActiveByIdForShare(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED,
                        "유효하지 않은 인증 정보입니다."));
        artist.validateAccessible();
        if (!artist.isArtist()) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "작가만 필터 초안을 만들 수 있습니다.");
        }

        return filterRepository.save(Filter.createDraft(artist)).getId();
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
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.BUSINESS_ERROR, "필터를 찾을 수 없습니다."));
        boolean owner = filter.isOwnedBy(viewerId);
        List<FilterDetailResult.Photo> photos = filterRepository.findPhotoUploads(filterId).stream()
                .filter(upload -> owner || upload.isProcessed())
                .map(this::toPhoto)
                .toList();

        return FilterDetailResult.fromFilter(filter, photos);
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

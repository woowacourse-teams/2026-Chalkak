package com.chalkak.backend.filter.repository;

import com.chalkak.backend.filter.domain.Filter;
import com.chalkak.backend.post.domain.PostImageUpload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FilterRepository {

    Filter save(Filter filter);

    FilterSlice findVisibleByArtistId(
            UUID artistId,
            UUID viewerId,
            int page,
            int pageSize
    );

    Optional<Filter> findVisibleById(UUID filterId, UUID viewerId);

    List<PostImageUpload> findPhotoUploads(UUID filterId);
}

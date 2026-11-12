package com.chalkak.backend.filter.infrastructure.persistence;

import com.chalkak.backend.filter.domain.Filter;
import com.chalkak.backend.filter.repository.FilterRepository;
import com.chalkak.backend.filter.repository.FilterSlice;
import com.chalkak.backend.post.domain.PostImageUpload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FilterRepositoryImpl implements FilterRepository {

    private final FilterJpaRepository filterJpaRepository;

    @Override
    public Filter save(Filter filter) {
        return filterJpaRepository.save(filter);
    }

    @Override
    public FilterSlice findVisibleByArtistId(
            UUID artistId,
            UUID viewerId,
            int page,
            int pageSize
    ) {
        Slice<Filter> slice = filterJpaRepository.findVisibleByArtistId(
                artistId,
                viewerId,
                PageRequest.of(page - 1, pageSize)
        );
        return new FilterSlice(slice.getContent(), slice.hasNext());
    }

    @Override
    public Optional<Filter> findVisibleById(UUID filterId, UUID viewerId) {
        return filterJpaRepository.findVisibleById(filterId, viewerId);
    }

    @Override
    public List<PostImageUpload> findPhotoUploads(UUID filterId) {
        return filterJpaRepository.findPhotoUploads(filterId);
    }
}

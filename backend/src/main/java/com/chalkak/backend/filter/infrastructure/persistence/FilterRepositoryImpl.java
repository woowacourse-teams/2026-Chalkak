package com.chalkak.backend.filter.infrastructure.persistence;

import com.chalkak.backend.filter.domain.Filter;
import com.chalkak.backend.filter.repository.FilterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FilterRepositoryImpl implements FilterRepository {

    private final FilterJpaRepository filterJpaRepository;

    @Override
    public Filter save(Filter filter) {
        return filterJpaRepository.save(filter);
    }
}

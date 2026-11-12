package com.chalkak.backend.filter.infrastructure.persistence;

import com.chalkak.backend.filter.domain.Filter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilterJpaRepository extends JpaRepository<Filter, UUID> {
}

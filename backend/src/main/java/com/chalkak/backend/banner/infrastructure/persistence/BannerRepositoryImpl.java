package com.chalkak.backend.banner.infrastructure.persistence;

import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerPage;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BannerRepositoryImpl implements BannerRepository {

    private static final String UNIQUE_WEB_VIEW_URL = "ux_banners_web_view_url_digest";

    private final BannerJpaRepository jpaRepository;

    @Override
    public boolean existsByWebViewUrl(String webViewUrl) {
        return jpaRepository.existsByWebViewUrlAndDeletedAtIsNull(webViewUrl);
    }

    @Override
    public Banner save(Banner banner) {
        try {
            return jpaRepository.save(banner);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateWebViewUrl(exception)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "동일한 웹뷰 URL의 배너가 이미 존재합니다.");
            }
            throw exception;
        }
    }

    @Override
    public BannerPage findBanners(int page, int pageSize) {
        PageRequest pageable = PageRequest.of(
                page - 1,
                pageSize,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt",
                        "id"
                )
        );
        Slice<Banner> result = jpaRepository.findByDeletedAtIsNull(pageable);
        return new BannerPage(
                result.getContent(),
                page,
                pageSize,
                result.hasNext()
        );
    }

    @Override
    public Optional<Banner> findByIdForUpdate(UUID bannerId) {
        return jpaRepository.findByIdForUpdate(bannerId);
    }

    private boolean isDuplicateWebViewUrl(DataIntegrityViolationException exception) {
        if (!(exception.getMostSpecificCause() instanceof PSQLException postgresException)) {
            return false;
        }
        ServerErrorMessage error = postgresException.getServerErrorMessage();
        return error != null && UNIQUE_WEB_VIEW_URL.equals(error.getConstraint());
    }
}

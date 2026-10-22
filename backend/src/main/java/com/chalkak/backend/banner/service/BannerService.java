package com.chalkak.backend.banner.service;

import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BannerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BannerRepository bannerRepository;

    @Transactional(readOnly = true)
    public BannerListResult getBanners(int page, int pageSize) {
        validatePagination(page, pageSize);
        return BannerListResult.from(bannerRepository.findBanners(page, pageSize));
    }

    private void validatePagination(int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE
                || offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다.");
        }
    }

}

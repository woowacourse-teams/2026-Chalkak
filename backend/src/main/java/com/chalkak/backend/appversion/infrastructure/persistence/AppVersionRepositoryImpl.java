package com.chalkak.backend.appversion.infrastructure.persistence;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.AppVersion;
import com.chalkak.backend.appversion.repository.AppVersionRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AppVersionRepositoryImpl implements AppVersionRepository {

    private final AppVersionJpaRepository jpaRepository;

    @Override
    public Optional<AppVersion> findByPlatform(AppPlatform platform) {
        return jpaRepository.findByPlatform(platform);
    }

    @Override
    public void lockPlatform(AppPlatform platform) {
        jpaRepository.lockPlatform(platform.name());
    }

    @Override
    public AppVersion saveAndFlush(AppVersion setting) {
        try {
            return jpaRepository.saveAndFlush(setting);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CHANGED,
                    "설정이 변경되었습니다. 다시 조회한 뒤 저장해 주세요.");
        }
    }
}

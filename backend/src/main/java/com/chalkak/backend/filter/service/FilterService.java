package com.chalkak.backend.filter.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.filter.domain.Filter;
import com.chalkak.backend.filter.repository.FilterRepository;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FilterService {

    private final UserRepository userRepository;
    private final FilterRepository filterRepository;

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
}

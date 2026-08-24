package com.chalkak.backend.user.service;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.domain.SignatureImagePolicy;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final SignatureImageStorage signatureImageStorage;
    private final SignatureImagePolicy signatureImagePolicy;

    @Transactional
    public void withdraw(UUID userId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR, "탈퇴할 회원을 찾을 수 없습니다."));

        user.withdraw();
    }

    @Transactional
    public String updateSignature(UUID userId, UUID uploadId) {
        String storageKey = signatureImageStorage.toOriginalStorageKey(uploadId);
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR, "사인을 교체할 회원을 찾을 수 없습니다."));

        // PUT은 멱등해야 하므로 이미 반영된 요청의 재시도는 상태를 건드리지 않고 같은 응답을 돌려준다.
        if (user.hasSignature(storageKey)) {
            return signatureImageStorage.toImageUrl(storageKey);
        }
        if (userRepository.existsBySignatureOriginalStorageKey(storageKey)) {
            throw new NotFoundException(ErrorCode.BUSINESS_ERROR, "업로드한 사인 이미지를 찾을 수 없습니다.");
        }

        StoredImageMetadata image = signatureImageStorage.findUploadedImage(uploadId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR, "업로드한 사인 이미지를 찾을 수 없습니다."));
        signatureImagePolicy.validate(image);
        user.updateSignature(storageKey);

        return signatureImageStorage.toImageUrl(storageKey);
    }
}

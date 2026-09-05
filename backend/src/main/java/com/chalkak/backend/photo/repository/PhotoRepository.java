package com.chalkak.backend.photo.repository;

public interface PhotoRepository {

    /**
     * {@code ux_photos_original_storage_key}가 부분 인덱스가 아니므로 soft-delete된 사진도 원본 키를 계속 점유한다.
     * 사전 검사도 제약과 같은 범위를 봐야 하므로 삭제 여부로 거르지 않는다.
     */
    boolean existsByOriginalStorageKey(String originalStorageKey);
}

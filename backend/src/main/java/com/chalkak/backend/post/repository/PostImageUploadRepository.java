package com.chalkak.backend.post.repository;

import com.chalkak.backend.post.domain.PostImageUpload;

public interface PostImageUploadRepository {

    PostImageUpload save(PostImageUpload postImageUpload);
}

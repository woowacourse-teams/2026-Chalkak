package com.chalkak.backend.post.api.v1.converter;

import com.chalkak.backend.post.service.PostSort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class PostSortConverter implements Converter<String, PostSort> {

    @Override
    public PostSort convert(String source) {
        return PostSort.from(source);
    }
}

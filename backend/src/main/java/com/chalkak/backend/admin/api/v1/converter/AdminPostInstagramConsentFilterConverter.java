package com.chalkak.backend.admin.api.v1.converter;

import com.chalkak.backend.admin.repository.post.AdminPostInstagramConsentFilter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class AdminPostInstagramConsentFilterConverter
        implements Converter<String, AdminPostInstagramConsentFilter> {

    @Override
    public AdminPostInstagramConsentFilter convert(String source) {
        return AdminPostInstagramConsentFilter.from(source);
    }
}

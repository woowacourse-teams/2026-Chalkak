package com.chalkak.backend.admin.api.v1.converter;

import com.chalkak.backend.admin.service.AdminTopicSort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class AdminTopicSortConverter implements Converter<String, AdminTopicSort> {

    @Override
    public AdminTopicSort convert(String source) {
        return AdminTopicSort.from(source);
    }
}

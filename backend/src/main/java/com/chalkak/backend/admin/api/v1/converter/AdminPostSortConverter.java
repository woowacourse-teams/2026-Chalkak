package com.chalkak.backend.admin.api.v1.converter;

import com.chalkak.backend.admin.service.AdminPostSort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class AdminPostSortConverter implements Converter<String, AdminPostSort> {

    @Override
    public AdminPostSort convert(String source) {
        return AdminPostSort.from(source);
    }
}

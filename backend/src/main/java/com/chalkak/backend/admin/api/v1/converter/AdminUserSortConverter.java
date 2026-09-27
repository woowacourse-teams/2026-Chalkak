package com.chalkak.backend.admin.api.v1.converter;

import com.chalkak.backend.admin.repository.user.AdminUserSort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSortConverter implements Converter<String, AdminUserSort> {

    @Override
    public AdminUserSort convert(String source) {
        return AdminUserSort.from(source);
    }
}

package com.chalkak.backend.admin.api.v1.converter;

import com.chalkak.backend.admin.service.AdminAuditLogSort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class AdminAuditLogSortConverter implements Converter<String, AdminAuditLogSort> {

    @Override
    public AdminAuditLogSort convert(String source) {
        return AdminAuditLogSort.from(source);
    }
}

package com.chalkak.backend.admin.api.v1.converter;

import com.chalkak.backend.admin.service.AdminFeedbackSort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class AdminFeedbackSortConverter implements Converter<String, AdminFeedbackSort> {

    @Override
    public AdminFeedbackSort convert(String source) {
        return AdminFeedbackSort.from(source);
    }
}

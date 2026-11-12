package com.chalkak.backend.filter.repository;

import com.chalkak.backend.filter.domain.Filter;
import java.util.List;

public record FilterSlice(List<Filter> filters, boolean hasNext) {
}

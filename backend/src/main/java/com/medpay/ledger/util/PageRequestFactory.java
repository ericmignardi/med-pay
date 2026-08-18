package com.medpay.ledger.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequestFactory {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequestFactory() {
    }

    public static PageRequest of(Integer page, Integer size) {
        return PageRequest.of(normalizePage(page), normalizeSize(size));
    }

    public static PageRequest of(Integer page, Integer size, Sort sort) {
        return PageRequest.of(normalizePage(page), normalizeSize(size), sort);
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}

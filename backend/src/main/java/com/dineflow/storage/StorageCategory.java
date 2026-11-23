package com.dineflow.storage;

public enum StorageCategory {
    MENU_IMAGE("images"),
    INVOICE_PDF("documents/invoices"),
    BUSINESS_DOCUMENT("documents/general");

    private final String keyPrefix;

    StorageCategory(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }
}

package com.dineflow.storage;

import java.time.Instant;

public record SignedUrlResult(
        String objectKey,
        String url,
        Instant expiresAt
) {}

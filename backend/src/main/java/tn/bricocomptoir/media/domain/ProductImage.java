package tn.bricocomptoir.media.domain;

import java.time.Instant;
import java.util.UUID;

public record ProductImage(UUID id, UUID productId, String originalKey, String cardKey, String detailKey,
                           String sourceMime, long sourceBytes, String sourceSha256, int width, int height,
                           int sortOrder, boolean primary, Instant createdAt) { }

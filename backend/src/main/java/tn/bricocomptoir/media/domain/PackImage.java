package tn.bricocomptoir.media.domain;

import java.time.Instant;
import java.util.UUID;

public record PackImage(UUID id, UUID packId, String originalKey, String cardKey, String detailKey,
                           String sourceMime, long sourceBytes, String sourceSha256, int width, int height,
                           int sortOrder, boolean primary, Instant createdAt) { }

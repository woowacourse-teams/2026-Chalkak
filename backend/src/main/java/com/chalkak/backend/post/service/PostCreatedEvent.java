package com.chalkak.backend.post.service;

import java.util.UUID;

public record PostCreatedEvent(UUID postId) {
}

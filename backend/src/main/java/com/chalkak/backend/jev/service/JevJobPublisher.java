package com.chalkak.backend.jev.service;

import java.util.UUID;

public interface JevJobPublisher {

    void publish(UUID jobId);
}

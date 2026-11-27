package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.FeedFeedingSubmissionEvent;

public interface FeedFeedingStagingService {

    boolean persistStaged(FeedFeedingSubmissionEvent event);
}
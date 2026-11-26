package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.FeedProductionSubmissionEvent;

public interface FeedProductionStagingService {

    boolean persistStaged(FeedProductionSubmissionEvent event);
}
package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.FeedFormulationSubmissionEvent;

public interface FeedFormulationStagingService {

    boolean persistStaged(FeedFormulationSubmissionEvent event);
}
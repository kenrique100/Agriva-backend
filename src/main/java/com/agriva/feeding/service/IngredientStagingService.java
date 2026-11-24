package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.FeedIngredientSubmissionEvent;

public interface IngredientStagingService {
    boolean persistStaged(FeedIngredientSubmissionEvent event);
}
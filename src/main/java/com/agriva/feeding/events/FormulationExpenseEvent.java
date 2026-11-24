package com.agriva.feeding.events;

import java.util.UUID;

public record FormulationExpenseEvent(
        UUID formulationId,
        UUID expenseSubmissionId,
        UUID farmId,
        UUID userId,
        String payloadJson,
        boolean reversal
) {}
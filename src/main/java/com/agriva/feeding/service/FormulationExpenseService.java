package com.agriva.feeding.service;

import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FormulationExpenseService {

    void snapshotCpAndBookExpense(Formulation formulation,
                                  List<FormulationLine> lines,
                                  UUID farmId,
                                  UUID userId,
                                  Map<UUID, com.agriva.shared.models.enums.Currency> currencyById);

    void scheduleReversal(Formulation formulation, UUID farmId, UUID userId);
}
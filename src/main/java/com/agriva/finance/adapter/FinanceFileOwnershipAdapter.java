package com.agriva.finance.adapter;

import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.shared.port.FileOwnershipPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FinanceFileOwnershipAdapter implements FileOwnershipPort {

    private final IncomeRepository incomeRepo;
    private final ExpenseRepository expenseRepo;

    @Override
    public boolean isOwnedByFarm(String fileName, UUID farmId) {
        return incomeRepo.existsByFarmIdAndReceipt(farmId, fileName)
                || expenseRepo.existsByFarmIdAndReceipt(farmId, fileName);
    }
}
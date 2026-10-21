package com.agriva.service;

import com.agriva.model.dto.salary.SalaryReceiptResponse;

public interface PdfReceiptService {
    byte[] generate(SalaryReceiptResponse receipt);
}
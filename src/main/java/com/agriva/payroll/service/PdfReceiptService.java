package com.agriva.payroll.service;

import com.agriva.payroll.model.dto.response.SalaryReceiptResponse;

public interface PdfReceiptService {
    byte[] generate(SalaryReceiptResponse receipt);
}
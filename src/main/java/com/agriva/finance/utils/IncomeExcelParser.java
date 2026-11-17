package com.agriva.finance.utils;

import com.agriva.finance.model.dto.request.IncomeRequest;
import com.agriva.finance.model.enums.IncomeCategory;
import com.agriva.shared.utils.ExcelCellReader;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class IncomeExcelParser extends AbstractExcelBatchParser<IncomeRequest> {

    @Override
    protected Class<IncomeRequest> requestType() {
        return IncomeRequest.class;
    }

    @Override
    protected List<String> requiredHeaders() {
        return List.of("reason", "incomedate", "amountreceived");
    }

    @Override
    protected IncomeRequest mapRow(Row row, Map<String, Integer> colMap) {
        IncomeRequest req = new IncomeRequest();

        req.setReason(ExcelCellReader.readString(getCell(row, colMap, "reason")));
        req.setIncomeDate(ExcelCellReader.readDate(
                getCell(row, colMap, "incomedate"), "Income date"));

        String qtyStr = ExcelCellReader.readString(getCell(row, colMap, "quantity"));
        req.setQuantity(qtyStr != null && !qtyStr.isBlank()
                ? Integer.parseInt(qtyStr) : 1);

        req.setAmountReceived(ExcelCellReader.readBigDecimal(
                getCell(row, colMap, "amountreceived"), "Amount received"));
        req.setExpectedAmount(ExcelCellReader.readBigDecimal(
                getCell(row, colMap, "expectedamount"), "Expected amount"));

        req.setReceipt(ExcelCellReader.readString(getCell(row, colMap, "receipt")));

        req.setCurrency(parseCurrency(
                ExcelCellReader.readString(getCell(row, colMap, "currency"))));

        req.setCategory(parseEnum(
                ExcelCellReader.readString(getCell(row, colMap, "category")),
                IncomeCategory.class, IncomeCategory.MISCELLANEOUS));

        return req;
    }
}
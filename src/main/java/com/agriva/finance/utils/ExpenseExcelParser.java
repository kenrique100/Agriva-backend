package com.agriva.finance.utils;

import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.model.enums.ExpenseCategory;
import com.agriva.shared.utils.ExcelCellReader;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ExpenseExcelParser extends AbstractExcelBatchParser<ExpenseRequest> {

    @Override
    protected Class<ExpenseRequest> requestType() {
        return ExpenseRequest.class;
    }

    @Override
    protected List<String> requiredHeaders() {
        return List.of("reason", "expensedate", "amountpaid");
    }

    @Override
    protected ExpenseRequest mapRow(Row row, Map<String, Integer> colMap) {
        ExpenseRequest req = new ExpenseRequest();

        req.setReason(ExcelCellReader.readString(getCell(row, colMap, "reason")));
        req.setExpenseDate(ExcelCellReader.readDate(
                getCell(row, colMap, "expensedate"), "Expense date"));

        String qtyStr = ExcelCellReader.readString(getCell(row, colMap, "qtypurchased"));
        req.setQtyPurchased(qtyStr != null && !qtyStr.isBlank()
                ? Integer.parseInt(qtyStr) : 1);

        req.setAmountPaid(ExcelCellReader.readBigDecimal(
                getCell(row, colMap, "amountpaid"), "Amount paid"));
        req.setExpectedAmount(ExcelCellReader.readBigDecimal(
                getCell(row, colMap, "expectedamount"), "Expected amount"));

        req.setReceipt(ExcelCellReader.readString(getCell(row, colMap, "receipt")));

        req.setCurrency(parseCurrency(
                ExcelCellReader.readString(getCell(row, colMap, "currency"))));

        req.setCategory(parseEnum(
                ExcelCellReader.readString(getCell(row, colMap, "category")),
                ExpenseCategory.class, ExpenseCategory.MISCELLANEOUS));

        return req;
    }
}
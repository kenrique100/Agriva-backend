package com.agriva.service.impl;

import com.agriva.model.dto.salary.SalaryReceiptLineItem;
import com.agriva.model.dto.salary.SalaryReceiptResponse;
import com.agriva.service.PdfReceiptService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class PdfReceiptServiceImpl implements PdfReceiptService {

    private static final float MARGIN = 50;
    private static final float LINE_HEIGHT = 20;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Override
    public byte[] generate(SalaryReceiptResponse r) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {

                // Title
                c.beginText();
                c.setNonStrokingColor(new Color(0, 102, 204));
                c.setFont(PDType1Font.HELVETICA_BOLD, 18);
                c.newLineAtOffset(MARGIN, 760);
                c.showText("SALARY PAYMENT RECEIPT");
                c.endText();

                // Personnel block
                float y = 700;
                c.setNonStrokingColor(Color.BLACK);
                c.beginText();
                c.setFont(PDType1Font.HELVETICA_BOLD, 12);
                c.newLineAtOffset(MARGIN, y);
                c.showText("EMPLOYEE");
                c.endText();
                y -= LINE_HEIGHT;
                c.beginText();
                c.setFont(PDType1Font.HELVETICA, 10);
                c.setLeading(14f);
                c.newLineAtOffset(MARGIN, y);
                c.showText("Name: " + safe(r.getPersonnelName()));
                c.newLine();
                c.showText("National ID: " + safe(r.getPersonnelNationalId()));
                c.newLine();
                c.showText("Department: " + safe(r.getDepartment()));
                c.newLine();
                c.showText("Employment Date: " + (r.getEmploymentDate() != null
                        ? r.getEmploymentDate().format(DATE) : "-"));
                c.endText();

                // Salary block
                y -= 90;
                c.beginText();
                c.setFont(PDType1Font.HELVETICA_BOLD, 12);
                c.newLineAtOffset(MARGIN, y);
                c.showText("PAYMENT");
                c.endText();
                y -= LINE_HEIGHT;
                c.beginText();
                c.setFont(PDType1Font.HELVETICA, 10);
                c.setLeading(14f);
                c.newLineAtOffset(MARGIN, y);
                c.showText("Receipt #: " + safe(r.getReceiptNumber()));
                c.newLine();
                c.showText("Payment Date: " + (r.getPaymentDate() != null
                        ? r.getPaymentDate().format(DATE) : "-"));
                c.newLine();
                c.showText("Reference: " + safe(r.getPaymentReference()));
                c.newLine();
                c.showText("Amount: " + (r.getAmount() != null
                        ? String.format("%,.2f", r.getAmount()) : "0.00"));
                c.newLine();
                c.showText("Status: " + safe(r.getStatus()));
                c.endText();

                // Productivity
                y -= 90;
                c.beginText();
                c.setFont(PDType1Font.HELVETICA_BOLD, 12);
                c.newLineAtOffset(MARGIN, y);
                c.showText("PRODUCTIVITY (last 30 days)");
                c.endText();
                y -= LINE_HEIGHT;
                c.beginText();
                c.setFont(PDType1Font.HELVETICA, 10);
                c.setLeading(14f);
                c.newLineAtOffset(MARGIN, y);
                c.showText("Total Expected Hours: " + safeBig(r.getTotalExpectedHours()));
                c.newLine();
                c.showText("Total Actual Hours: " + safeBig(r.getTotalActualHours()));
                c.newLine();
                c.showText("Productivity: " + (r.getOverallProductivity() != null
                        ? r.getOverallProductivity().setScale(2, RoundingMode.HALF_UP) + "%" : "-"));
                c.newLine();
                c.showText("Working Days: " + (r.getWorkingDays() != null ? r.getWorkingDays() : 0));
                c.endText();

                // Tasks table (compact)
                if (r.getTasks() != null && !r.getTasks().isEmpty()) {
                    y -= 90;
                    c.beginText();
                    c.setFont(PDType1Font.HELVETICA_BOLD, 12);
                    c.newLineAtOffset(MARGIN, y);
                    c.showText("VALIDATED TASKS");
                    c.endText();
                    y -= LINE_HEIGHT;
                    c.beginText();
                    c.setFont(PDType1Font.HELVETICA, 9);
                    c.setLeading(12f);
                    c.newLineAtOffset(MARGIN, y);
                    for (SalaryReceiptLineItem li : r.getTasks()) {
                        if (y < 120) break;
                        c.showText(String.format("• %s  (%.1f / %.1f h  →  %s%%)",
                                truncate(li.getTitle(), 55),
                                li.getActualHours() == null ? 0 : li.getActualHours().doubleValue(),
                                li.getExpectedHours() == null ? 0 : li.getExpectedHours().doubleValue(),
                                li.getCompletionRate() == null ? "0" : li.getCompletionRate().setScale(0, RoundingMode.HALF_UP)));
                        c.newLine();
                        y -= 12;
                    }
                    c.endText();
                }

                // QR code
                try {
                    BitMatrix matrix = new QRCodeWriter().encode(
                            "RECEIPT:" + r.getReceiptNumber(), BarcodeFormat.QR_CODE, 90, 90);
                    BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix);
                    PDImageXObject qrImg = LosslessFactory.createFromImage(doc, qr);
                    c.drawImage(qrImg, MARGIN, 60, 60, 60);
                } catch (WriterException e) {
                    log.warn("QR generation failed: {}", e.getMessage());
                }

                // Footer
                c.beginText();
                c.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
                c.newLineAtOffset(MARGIN, 40);
                c.showText("This is a computer-generated document. No physical signature is required.");
                c.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private static String safe(String s) { return s == null ? "-" : s; }
    private static String safeBig(java.math.BigDecimal b) { return b == null ? "0" : b.toPlainString(); }
    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n - 1) + "…" : s;
    }
}
package com.agriva.payroll.service.impl;

import com.agriva.payroll.model.dto.response.SalaryReceiptLineItem;
import com.agriva.payroll.model.dto.response.SalaryReceiptResponse;
import com.agriva.payroll.service.PdfReceiptService;
import com.google.zxing.BarcodeFormat;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class PdfReceiptServiceImpl implements PdfReceiptService {

    private static final float MARGIN = 50;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Brand Colors
    private static final Color PRIMARY_GREEN = new Color(22, 163, 74); // #16a34a
    private static final Color DARK_TEXT = new Color(30, 41, 59);      // #1e293b
    private static final Color LIGHT_TEXT = new Color(100, 116, 139);  // #64748b
    private static final Color BG_GRAY = new Color(248, 250, 252);     // #f8fafc
    private static final Color LINE_GRAY = new Color(226, 232, 240);   // #e2e8f0

    @Override
    public byte[] generate(SalaryReceiptResponse r) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {

                // 1. Header Banner
                c.setNonStrokingColor(PRIMARY_GREEN);
                c.addRect(0, 792 - 80, 595, 80); // A4 width is 595, height is 842
                c.fill();

                // Logo (Ensure you have /static/images/agriva_emblem.png in your resources)
                try {
                    InputStream logoStream = new ClassPathResource("static/images/agriva_emblem.png").getInputStream();
                    BufferedImage logoImg = javax.imageio.ImageIO.read(logoStream);
                    if (logoImg != null) {
                        PDImageXObject logo = LosslessFactory.createFromImage(doc, logoImg);
                        c.drawImage(logo, MARGIN, 792 - 65, 40, 40);
                    }
                } catch (Exception e) {
                    log.warn("Agriva logo not found, skipping. {}", e.getMessage());
                }

                // Header Text
                c.beginText();
                c.setFont(PDType1Font.HELVETICA_BOLD, 22);
                c.setNonStrokingColor(Color.WHITE);
                c.newLineAtOffset(MARGIN + 50, 792 - 45);
                c.showText("SALARY RECEIPT");
                c.endText();

                c.beginText();
                c.setFont(PDType1Font.HELVETICA, 10);
                c.setNonStrokingColor(new Color(220, 252, 231)); // Light green
                c.newLineAtOffset(MARGIN + 50, 792 - 60);
                c.showText(r.getFarmName());
                c.endText();

                // 2. Meta Info (Top Right)
                c.beginText();
                c.setFont(PDType1Font.HELVETICA_BOLD, 9);
                c.setNonStrokingColor(Color.WHITE);
                c.newLineAtOffset(450, 792 - 30);
                c.showText("Receipt No: " + r.getReceiptNumber());
                c.endText();

                c.beginText();
                c.setFont(PDType1Font.HELVETICA, 9);
                c.setNonStrokingColor(Color.WHITE);
                c.newLineAtOffset(450, 792 - 45);
                c.showText("Date: " + (r.getIssueDate() != null ? r.getIssueDate().format(DATE) : "-"));
                c.endText();

                // 3. Details Section (Two Columns)
                float y = 792 - 120;

                // Left: Employee Details
                drawSectionTitle(c, "EMPLOYEE DETAILS", MARGIN, y);
                y -= 20;
                drawLabelValue(c, "Name:", safe(r.getPersonnelName()), MARGIN, y); y -= 18;
                drawLabelValue(c, "Employee ID:", safe(r.getPersonnelNationalId()), MARGIN, y); y -= 18;
                drawLabelValue(c, "Department:", safe(r.getDepartment()), MARGIN, y); y -= 18;
                drawLabelValue(c, "Employment:", r.getEmploymentDate() != null ? r.getEmploymentDate().format(DATE) : "-", MARGIN, y);

                // Right: Payment Details
                float rightColX = 320;
                float yRight = 792 - 120;
                drawSectionTitle(c, "PAYMENT DETAILS", rightColX, yRight);
                yRight -= 20;
                drawLabelValue(c, "Payment Date:", r.getPaymentDate() != null ? r.getPaymentDate().format(DATE) : "-", rightColX, yRight); yRight -= 18;
                drawLabelValue(c, "Reference:", safe(r.getPaymentReference()), rightColX, yRight); yRight -= 18;
                drawLabelValue(c, "Status:", safe(r.getStatus()), rightColX, yRight); yRight -= 18;

                // Highlighted Amount
                c.beginText();
                c.setFont(PDType1Font.HELVETICA_BOLD, 11);
                c.setNonStrokingColor(DARK_TEXT);
                c.newLineAtOffset(rightColX, yRight);
                c.showText("Amount:  ");
                c.setNonStrokingColor(PRIMARY_GREEN);
                c.setFont(PDType1Font.HELVETICA_BOLD, 14);
                c.showText(String.format("%,.2f %s", r.getAmount(), "XAF")); // Or dynamic currency
                c.endText();

                // 4. Divider
                y -= 40;
                c.setStrokingColor(LINE_GRAY);
                c.setLineWidth(1f);
                c.moveTo(MARGIN, y);
                c.lineTo(595 - MARGIN, y);
                c.stroke();

                // 5. Productivity Summary
                y -= 25;
                drawSectionTitle(c, "PRODUCTIVITY SUMMARY", MARGIN, y);
                y -= 20;

                float boxWidth = (595 - (MARGIN * 2) - 30) / 4;
                drawSummaryBox(c, "Total Hours", safeBig(r.getTotalActualHours()) + " h", MARGIN, y, boxWidth);
                drawSummaryBox(c, "Expected", safeBig(r.getTotalExpectedHours()) + " h", MARGIN + boxWidth + 10, y, boxWidth);
                drawSummaryBox(c, "Productivity", (r.getOverallProductivity() != null ? r.getOverallProductivity().setScale(1, RoundingMode.HALF_UP) + "%" : "-"), MARGIN + (boxWidth * 2) + 20, y, boxWidth);
                drawSummaryBox(c, "Days Worked", String.valueOf(r.getWorkingDays()), MARGIN + (boxWidth * 3) + 30, y, boxWidth);

                // 6. Task Table
                y -= 60;
                drawSectionTitle(c, "VALIDATED TASKS", MARGIN, y);
                y -= 20;

                // Table Header Background
                c.setNonStrokingColor(BG_GRAY);
                c.addRect(MARGIN, y - 5, 595 - (MARGIN * 2), 20);
                c.fill();

                // Table Headers
                c.beginText();
                c.setFont(PDType1Font.HELVETICA_BOLD, 9);
                c.setNonStrokingColor(LIGHT_TEXT);
                c.newLineAtOffset(MARGIN + 5, y);
                c.showText("Task Description");
                c.newLineAtOffset(250, 0);
                c.showText("Expected Hrs");
                c.newLineAtOffset(70, 0);
                c.showText("Actual Hrs");
                c.newLineAtOffset(70, 0);
                c.showText("Completion");
                c.endText();

                y -= 20;
                boolean alternate = false;

                if (r.getTasks() != null && !r.getTasks().isEmpty()) {
                    for (SalaryReceiptLineItem li : r.getTasks()) {
                        if (y < 150) break; // Prevent overlapping footer

                        if (alternate) {
                            c.setNonStrokingColor(BG_GRAY);
                            c.addRect(MARGIN, y - 5, 595 - (MARGIN * 2), 18);
                            c.fill();
                        }

                        c.beginText();
                        c.setFont(PDType1Font.HELVETICA, 9);
                        c.setNonStrokingColor(DARK_TEXT);
                        c.newLineAtOffset(MARGIN + 5, y);
                        c.showText(truncate(li.getTitle(), 45));

                        c.newLineAtOffset(250, 0);
                        c.showText(li.getExpectedHours() != null ? li.getExpectedHours().stripTrailingZeros().toPlainString() : "0");

                        c.newLineAtOffset(70, 0);
                        c.showText(li.getActualHours() != null ? li.getActualHours().stripTrailingZeros().toPlainString() : "0");

                        c.newLineAtOffset(70, 0);
                        c.showText(li.getCompletionRate() != null ? li.getCompletionRate().setScale(1, RoundingMode.HALF_UP) + "%" : "0%");
                        c.endText();

                        // Bottom border for row
                        c.setStrokingColor(LINE_GRAY);
                        c.setLineWidth(0.5f);
                        c.moveTo(MARGIN, y - 5);
                        c.lineTo(595 - MARGIN, y - 5);
                        c.stroke();

                        y -= 18;
                        alternate = !alternate;
                    }
                } else {
                    c.beginText();
                    c.setFont(PDType1Font.HELVETICA_OBLIQUE, 9);
                    c.setNonStrokingColor(LIGHT_TEXT);
                    c.newLineAtOffset(MARGIN + 5, y);
                    c.showText("No validated tasks in this period.");
                    c.endText();
                }

                // 7. Footer (QR Code & Signature)
                float footerY = 80;

                // QR Code
                try {
                    BitMatrix matrix = new QRCodeWriter().encode(
                            "RECEIPT:" + r.getReceiptNumber(), BarcodeFormat.QR_CODE, 80, 80);
                    BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix);
                    PDImageXObject qrImg = LosslessFactory.createFromImage(doc, qr);
                    c.drawImage(qrImg, MARGIN, footerY, 60, 60);
                } catch (Exception e) {
                    log.warn("QR generation failed: {}", e.getMessage());
                }

                c.beginText();
                c.setFont(PDType1Font.HELVETICA, 7);
                c.setNonStrokingColor(LIGHT_TEXT);
                c.newLineAtOffset(MARGIN, footerY - 10);
                c.showText("Scan to verify authenticity");
                c.endText();

                // Signature Area
                float sigX = 350;
                float sigY = footerY + 20;

                // Draw a smooth signature line
                c.setStrokingColor(DARK_TEXT);
                c.setLineWidth(1f);
                c.moveTo(sigX, sigY);
                c.lineTo(595 - MARGIN, sigY);
                c.stroke();

                // Optional: Draw a cursive signature placeholder
                c.beginText();
                c.setFont(PDType1Font.TIMES_ITALIC, 18);
                c.setNonStrokingColor(new Color(15, 23, 42));
                c.newLineAtOffset(sigX + 10, sigY + 5);
                c.showText(r.getManagerName());
                c.endText();

                c.beginText();
                c.setFont(PDType1Font.HELVETICA_BOLD, 9);
                c.setNonStrokingColor(DARK_TEXT);
                c.newLineAtOffset(sigX, sigY - 12);
                c.showText("Authorized Signature");
                c.endText();

                c.beginText();
                c.setFont(PDType1Font.HELVETICA, 8);
                c.setNonStrokingColor(LIGHT_TEXT);
                c.newLineAtOffset(sigX, sigY - 24);
                c.showText("Manager: " + r.getManagerName() + " | " + r.getFarmName());
                c.endText();

                // Footer disclaimer
                c.beginText();
                c.setFont(PDType1Font.HELVETICA_OBLIQUE, 7);
                c.setNonStrokingColor(LIGHT_TEXT);
                c.newLineAtOffset(MARGIN, 30);
                c.showText("This is a computer-generated document. No physical signature is required.");
                c.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed", e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    // --- Helper Methods for UI Layout ---

    private void drawSectionTitle(PDPageContentStream c, String title, float x, float y) throws Exception {
        c.beginText();
        c.setFont(PDType1Font.HELVETICA_BOLD, 10);
        c.setNonStrokingColor(PRIMARY_GREEN);
        c.newLineAtOffset(x, y);
        c.showText(title);
        c.endText();
    }

    private void drawLabelValue(PDPageContentStream c, String label, String value, float x, float y) throws Exception {
        c.beginText();
        c.setFont(PDType1Font.HELVETICA_BOLD, 9);
        c.setNonStrokingColor(LIGHT_TEXT);
        c.newLineAtOffset(x, y);
        c.showText(label);
        c.endText();

        c.beginText();
        c.setFont(PDType1Font.HELVETICA, 9);
        c.setNonStrokingColor(DARK_TEXT);
        c.newLineAtOffset(x + 70, y);
        c.showText(value);
        c.endText();
    }

    private void drawSummaryBox(PDPageContentStream c, String label, String value, float x, float y, float width) throws Exception {
        c.setNonStrokingColor(BG_GRAY);
        c.addRect(x, y - 10, width, 40);
        c.fill();

        c.beginText();
        c.setFont(PDType1Font.HELVETICA, 8);
        c.setNonStrokingColor(LIGHT_TEXT);
        c.newLineAtOffset(x + 10, y + 15);
        c.showText(label);
        c.endText();

        c.beginText();
        c.setFont(PDType1Font.HELVETICA_BOLD, 12);
        c.setNonStrokingColor(DARK_TEXT);
        c.newLineAtOffset(x + 10, y + 2);
        c.showText(value);
        c.endText();
    }

    private static String safe(String s) { return s == null || s.isBlank() ? "-" : s; }

    private static String safeBig(java.math.BigDecimal b) {
        return b == null ? "0" : b.stripTrailingZeros().toPlainString();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n - 3) + "..." : s;
    }
}
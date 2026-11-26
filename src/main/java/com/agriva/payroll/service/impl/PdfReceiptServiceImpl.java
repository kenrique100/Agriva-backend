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
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class PdfReceiptServiceImpl implements PdfReceiptService {

    private static final float PAGE_W = 595f;
    private static final float PAGE_H = 842f;
    private static final float MARGIN = 40f;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;   // 515
    private static final float RIGHT = PAGE_W - MARGIN;           // 555
    private static final Color BRAND_DARK  = new Color(14, 59, 44);
    private static final Color BRAND_GREEN = new Color(31, 143, 95);
    private static final Color ACCENT_GOLD = new Color(232, 163, 61);
    private static final Color GREEN_SOFT  = new Color(230, 244, 236);
    private static final Color TEXT        = new Color(20, 32, 27);
    private static final Color MUTED       = new Color(101, 117, 108);
    private static final Color LINE        = new Color(227, 234, 228);
    private static final Color BG_SOFT     = new Color(244, 247, 243);
    private static final Color MINT        = new Color(191, 227, 208);
    private static final Color DANGER      = new Color(229, 72, 77);
    private static final PDFont REGULAR = PDType1Font.HELVETICA;
    private static final PDFont BOLD    = PDType1Font.HELVETICA_BOLD;
    private static final PDFont OBLIQUE = PDType1Font.HELVETICA_OBLIQUE;
    private static final PDFont SIGN    = PDType1Font.TIMES_ITALIC;
    private static final String FALLBACK_CURRENCY = "XAF";   // only when the DTO lacks one
    private static final float ROW_H = 22f;
    private static final int MAX_FULL_ROWS = 5;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @Override
    public byte[] generate(SalaryReceiptResponse r) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
                drawHeader(doc, c, r);
                drawNetPayCard(c, r);
                drawInfoCard(c, 40f, "EMPLOYEE", new String[][]{
                        {"Name", safe(r.getPersonnelName())},
                        {"Employee ID", safe(r.getPersonnelNationalId())},
                        {"Department", safe(r.getDepartment())},
                        {"Employed since", date(r.getEmploymentDate())}});
                drawInfoCard(c, 305f, "PAYMENT", new String[][]{
                        {"Payment date", date(r.getPaymentDate())},
                        {"Reference", safe(r.getPaymentReference())},
                        {"Status", capitalize(safe(r.getStatus()))},
                        {"Receipt no.", safe(r.getReceiptNumber())}});
                drawProductivitySection(c, r);
                drawTasksTable(c, r);
                drawFooter(doc, c, r);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed", e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private void drawHeader(PDDocument doc, PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        rect(c, 0, 0, PAGE_W, 112, BRAND_DARK);
        rect(c, 0, 112, PAGE_W, 4, ACCENT_GOLD);

        roundRect(c, 40, 32, 48, 48, 10, ACCENT_GOLD, null);
        try (InputStream in = new ClassPathResource("static/images/agriva_emblem.png").getInputStream()) {
            BufferedImage img = javax.imageio.ImageIO.read(in);
            if (img != null) {
                PDImageXObject logo = LosslessFactory.createFromImage(doc, img);
                c.drawImage(logo, 48, PAGE_H - 40 - 32, 32, 32);
            }
        } catch (Exception e) {
            log.warn("Agriva logo not found, skipping. {}", e.getMessage());
        }

        text(c, "SALARY RECEIPT", 104, 34, BOLD, 22, Color.WHITE, 0.6f);
        text(c, safe(r.getFarmName()), 104, 62, REGULAR, 10, MINT, 0);
        text(c, "Official payment receipt", 104, 77, REGULAR, 8, MINT, 0);

        textRight(c, "RECEIPT NO.", RIGHT, 26, BOLD, 7.5f, MINT);
        textRight(c, safe(r.getReceiptNumber()), RIGHT, 38, BOLD, 12, Color.WHITE);
        textRight(c, "ISSUED", RIGHT, 62, BOLD, 7.5f, MINT);
        textRight(c, date(r.getIssueDate()), RIGHT, 74, REGULAR, 10, Color.WHITE);
    }

    private void drawNetPayCard(PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        roundRect(c, 40, 136, CONTENT_W, 84, 12, GREEN_SOFT, LINE);
        text(c, "NET PAY", 60, 152, BOLD, 8, MUTED, 1f);
        String amount = r.getAmount() == null ? "0.00" : String.format(Locale.US, "%,.2f", r.getAmount());

        // D3: use the currency carried by the DTO.
        String currencyLabel = r.getCurrency() != null
                ? r.getCurrency().name()
                : FALLBACK_CURRENCY;

        text(c, currencyLabel + " " + amount, 60, 166, BOLD, 30, BRAND_GREEN, 0);

        String status = safe(r.getStatus()).toUpperCase(Locale.ROOT);
        float pillW = width(BOLD, 8.5f, status) + 24;
        float pillX = 535 - pillW;
        roundRect(c, pillX, 152, pillW, 22, 11, statusColor(status), null);
        text(c, status, pillX + 12, 157, BOLD, 8.5f, Color.WHITE, 0.5f);

        textRight(c, "Paid on " + date(r.getPaymentDate()), 535, 184, BOLD, 9, TEXT);
        textRight(c, "Ref: " + safe(r.getPaymentReference()), 535, 198, REGULAR, 8, MUTED);
    }

    private void drawInfoCard(PDPageContentStream c, float x, String title, String[][] rows) throws IOException {
        roundRect(c, x, 240, 250, 118, 10, Color.WHITE, LINE);
        rect(c, x + 16, 253, 3, 10, BRAND_GREEN);
        text(c, title, x + 26, 253, BOLD, 8.5f, BRAND_GREEN, 1f);
        rect(c, x + 16, 266, 218, 1, LINE);
        for (int i = 0; i < rows.length; i++) {
            float y = 278 + i * 18f;
            text(c, rows[i][0], x + 16, y, BOLD, 8.5f, MUTED, 0);
            text(c, fit(REGULAR, 9, rows[i][1], 138), x + 96, y, REGULAR, 9, TEXT, 0);
        }
    }

    private void drawProductivitySection(PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        rect(c, 40, 368, 3, 10, BRAND_GREEN);
        text(c, "PERFORMANCE & PRODUCTIVITY", 50, 368, BOLD, 8.5f, BRAND_GREEN, 1f);
        textRight(c, "Last 30 days · as of " + date(LocalDate.now()), RIGHT, 368, REGULAR, 8, MUTED);

        String avgLabel = "no work recorded";
        if (r.getWorkingDays() != null && r.getWorkingDays() > 0 && r.getTotalActualHours() != null) {
            double avg = r.getTotalActualHours().doubleValue() / r.getWorkingDays();
            avgLabel = String.format(Locale.US, "%.1f h / day avg", avg);
        }

        String varianceLabel = "no target";
        if (r.getTotalActualHours() != null && r.getTotalExpectedHours() != null) {
            BigDecimal v = r.getTotalActualHours().subtract(r.getTotalExpectedHours());
            double dv = v.doubleValue();
            if (Math.abs(dv) < 0.5) {
                varianceLabel = "matches target";
            } else if (dv > 0) {
                varianceLabel = String.format(Locale.US, "%.1f h above target", dv);
            } else {
                varianceLabel = String.format(Locale.US, "%.1f h below target", Math.abs(dv));
            }
        }

        String tier = productivityTier(r.getOverallProductivity());
        Color tierCol = tierColor(r.getOverallProductivity());

        drawRichKpiCard(c, 40,      "HOURS WORKED",  hours(r.getTotalActualHours()) + " h",
                avgLabel, BRAND_GREEN, false);
        drawRichKpiCard(c, 171.25f, "TARGET HOURS",  hours(r.getTotalExpectedHours()) + " h",
                varianceLabel, TEXT, false);
        drawRichKpiCard(c, 302.5f,  "PRODUCTIVITY",  pct(r.getOverallProductivity()),
                tier, tierCol, true);
        drawRichKpiCard(c, 433.75f, "DAYS WORKED",
                String.valueOf(r.getWorkingDays() == null ? 0 : r.getWorkingDays()),
                "of last 30 days", MUTED, false);

        LocalDate pStart = r.getPeriodStartDate() != null
                ? r.getPeriodStartDate()
                : (r.getPaymentDate() != null ? r.getPaymentDate().minusDays(30) : null);
        LocalDate pEnd = r.getPeriodEndDate() != null
                ? r.getPeriodEndDate()
                : r.getPaymentDate();

        text(c, "REPORTING PERIOD", 40, 468, BOLD, 7.5f, MUTED, 0.5f);
        text(c, (pStart != null ? date(pStart) : "-") + "  to  " + (pEnd != null ? date(pEnd) : "-"),
                40, 480, REGULAR, 9, TEXT, 0);

        float pillW = width(BOLD, 9, tier) + 28;
        float pillX = RIGHT - pillW;
        roundRect(c, pillX, 468, pillW, 24, 12, tierCol, null);
        text(c, tier, pillX + 14, 474, BOLD, 9, Color.WHITE, 0.5f);

        text(c, "Overall productivity", 40, 494, BOLD, 7.5f, MUTED, 0.5f);
        textRight(c, pct(r.getOverallProductivity()), RIGHT, 494, BOLD, 9, tierCol);

        float barY = 500;
        float barH = 10;
        roundRect(c, 40, barY, CONTENT_W, barH, 5, LINE, null);
        double p = ratio(r.getOverallProductivity());
        if (p > 0) {
            roundRect(c, 40, barY, (float) (CONTENT_W * p), barH, 5, tierCol, null);
        }

        String[] scale = {"0%", "25%", "50%", "75%", "100%"};
        for (int i = 0; i < scale.length; i++) {
            float x = 40 + (CONTENT_W * i / 4f);
            float w = width(REGULAR, 7, scale[i]);
            float tx = x - w / 2;
            if (i == 0) tx = x;
            if (i == scale.length - 1) tx = RIGHT - w;
            text(c, scale[i], tx, barY + barH + 4, REGULAR, 7, MUTED, 0);
        }

        TaskBreakdown tb = breakdown(r.getTasks());
        drawBreakdownChip(c, 40,      String.valueOf(tb.total),    "TASKS VALIDATED",       MUTED);
        drawBreakdownChip(c, 171.25f, String.valueOf(tb.onTarget), "ON TARGET · 90-100%",   BRAND_GREEN);
        drawBreakdownChip(c, 302.5f,  String.valueOf(tb.overtime), "OVERTIME · >100%",      ACCENT_GOLD);
        drawBreakdownChip(c, 433.75f, String.valueOf(tb.under),    "UNDER BUDGET · <90%",   DANGER);
    }

    private void drawRichKpiCard(PDPageContentStream c, float x, String label, String value,
                                 String subtitle, Color valueColor, boolean highlight) throws IOException {
        float w = 121.25f;
        float h = 76;
        Color bg = highlight ? GREEN_SOFT : BG_SOFT;
        rect(c, x, 384, w, h, bg);
        rect(c, x, 384, 3, h, highlight ? BRAND_DARK : BRAND_GREEN);

        text(c, label, x + 14, 394, BOLD, 7.5f, MUTED, 0.6f);
        text(c, fit(BOLD, 20, value, w - 24), x + 14, 408, BOLD, 20, valueColor, 0);

        rect(c, x + 14, 436, w - 28, 0.5f, LINE);

        text(c, fit(REGULAR, 7.5f, subtitle, w - 24), x + 14, 444, REGULAR, 7.5f, MUTED, 0);
    }

    private void drawBreakdownChip(PDPageContentStream c, float x, String value,
                                   String label, Color color) throws IOException {
        float w = 121.25f;
        float h = 30;
        rect(c, x, 524, w, h, Color.WHITE);
        c.setStrokingColor(LINE);
        c.setLineWidth(0.5f);
        c.addRect(x, PAGE_H - 524 - h, w, h);
        c.stroke();
        rect(c, x, 524, 3, h, color);

        text(c, value, x + 14, 528, BOLD, 16, color, 0);
        text(c, fit(BOLD, 7, label, w - 20), x + 14, 546, BOLD, 7, MUTED, 0.3f);
    }

    private record TaskBreakdown(int total, int onTarget, int overtime, int under) {}

    private static TaskBreakdown breakdown(List<SalaryReceiptLineItem> tasks) {
        if (tasks == null || tasks.isEmpty()) return new TaskBreakdown(0, 0, 0, 0);
        int on = 0, over = 0, under = 0;
        for (SalaryReceiptLineItem t : tasks) {
            BigDecimal rate = t.getCompletionRate();
            if (rate == null) { under++; continue; }
            double d = rate.doubleValue();
            if (d > 100) over++;
            else if (d >= 90) on++;
            else under++;
        }
        return new TaskBreakdown(tasks.size(), on, over, under);
    }

    private static String productivityTier(BigDecimal pct) {
        if (pct == null) return "NOT RATED";
        double d = pct.doubleValue();
        if (d >= 95) return "OUTSTANDING";
        if (d >= 85) return "EXCELLENT";
        if (d >= 70) return "GOOD";
        if (d >= 50) return "SATISFACTORY";
        return "NEEDS IMPROVEMENT";
    }

    private static Color tierColor(BigDecimal pct) {
        if (pct == null) return MUTED;
        double d = pct.doubleValue();
        if (d >= 95) return BRAND_DARK;
        if (d >= 85) return BRAND_GREEN;
        if (d >= 70) return ACCENT_GOLD;
        if (d >= 50) return MUTED;
        return DANGER;
    }

    private void drawTasksTable(PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        List<SalaryReceiptLineItem> tasks = r.getTasks() == null ? List.of() : r.getTasks();

        rect(c, 40, 562, 3, 10, BRAND_GREEN);
        text(c, "VALIDATED TASKS", 50, 562, BOLD, 8.5f, BRAND_GREEN, 1f);
        textRight(c, tasks.size() + " tasks validated this period", RIGHT, 562, REGULAR, 8, MUTED);

        roundRect(c, 40, 580, CONTENT_W, 24, 4, BRAND_DARK, null);
        text(c, "#", 50, 588, BOLD, 8, Color.WHITE, 0);
        text(c, "TASK DESCRIPTION", 72, 588, BOLD, 8, Color.WHITE, 0.5f);
        textRight(c, "EXPECTED (H)", 395, 588, BOLD, 8, Color.WHITE);
        textRight(c, "ACTUAL (H)", 455, 588, BOLD, 8, Color.WHITE);
        textRight(c, "COMPLETION", 547, 588, BOLD, 8, Color.WHITE);

        float y = 604;
        if (tasks.isEmpty()) {
            text(c, "No validated tasks in this period.", 72, y + 7, OBLIQUE, 9, MUTED, 0);
            y += ROW_H;
        } else {
            boolean overflow = tasks.size() > MAX_FULL_ROWS;
            int shown = overflow ? MAX_FULL_ROWS - 1 : tasks.size();
            for (int i = 0; i < shown; i++) {
                SalaryReceiptLineItem li = tasks.get(i);
                if (i % 2 == 1) rect(c, 40, y, CONTENT_W, ROW_H, BG_SOFT);
                rect(c, 40, y + 21.5f, CONTENT_W, 0.5f, LINE);
                text(c, String.format("%02d", i + 1), 50, y + 7, REGULAR, 9, MUTED, 0);
                text(c, fit(REGULAR, 9, li.getTitle(), 250), 72, y + 7, REGULAR, 9, TEXT, 0);
                textRight(c, hours(li.getExpectedHours()), 395, y + 7, REGULAR, 9, TEXT);
                textRight(c, hours(li.getActualHours()), 455, y + 7, REGULAR, 9, TEXT);

                double done = ratio(li.getCompletionRate());
                Color barCol = completionColor(done);
                roundRect(c, 466, y + 9, 36, 5, 2.5f, LINE, null);
                if (done > 0) roundRect(c, 466, y + 9,
                        (float) (36 * Math.min(1, done)), 5, 2.5f, barCol, null);
                textRight(c, pct(li.getCompletionRate()), 547, y + 7, BOLD, 9, TEXT);
                y += ROW_H;
            }
            if (overflow) {
                text(c, "+ " + (tasks.size() - shown) + " more validated tasks (see payroll record)",
                        72, y + 7, REGULAR, 8.5f, MUTED, 0);
                y += ROW_H;
            }
        }

        rect(c, 40, y, CONTENT_W, 26, GREEN_SOFT);
        rect(c, 40, y, CONTENT_W, 1.5f, BRAND_GREEN);
        text(c, "Total – " + tasks.size() + " validated tasks", 72, y + 8, BOLD, 9, TEXT, 0);
        textRight(c, hours(r.getTotalExpectedHours()), 395, y + 8, BOLD, 9, TEXT);
        textRight(c, hours(r.getTotalActualHours()), 455, y + 8, BOLD, 9, TEXT);
        textRight(c, pct(r.getOverallProductivity()), 547, y + 8, BOLD, 9, BRAND_GREEN);
    }

    private static Color completionColor(double ratio01) {
        if (ratio01 < 0.5) return DANGER;
        if (ratio01 < 0.9) return ACCENT_GOLD;
        if (ratio01 <= 1.0) return BRAND_GREEN;
        return BRAND_DARK;
    }

    private void drawFooter(PDDocument doc, PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        rect(c, 40, 752, CONTENT_W, 0.75f, LINE);

        try {
            BitMatrix m = new QRCodeWriter().encode("RECEIPT:" + safe(r.getReceiptNumber()),
                    BarcodeFormat.QR_CODE, 220, 220);
            PDImageXObject qr = LosslessFactory.createFromImage(doc, MatrixToImageWriter.toBufferedImage(m));
            c.drawImage(qr, 40, PAGE_H - 762 - 54, 54, 54);
        } catch (Exception e) {
            log.warn("QR generation failed: {}", e.getMessage());
        }
        text(c, "Scan to verify authenticity", 104, 774, BOLD, 8.5f, TEXT, 0);
        text(c, "RECEIPT:" + safe(r.getReceiptNumber()), 104, 788, REGULAR, 7.5f, MUTED, 0);

        String manager = safe(r.getManagerName());
        text(c, fit(SIGN, 18, manager, 165), 385, 768, SIGN, 18, BRAND_DARK, 0);
        rect(c, 375, 794, 180, 0.75f, TEXT);
        text(c, "Authorized signature", 375, 799, BOLD, 8.5f, TEXT, 0);
        text(c, fit(REGULAR, 7.5f, "Manager: " + manager + " · " + safe(r.getFarmName()), 180),
                375, 811, REGULAR, 7.5f, MUTED, 0);

        rect(c, 0, 826, PAGE_W, 16, BRAND_DARK);
        text(c, "Computer-generated document. No physical signature is required.", 40, 831, REGULAR, 6.5f, MINT, 0);
        textRight(c, "Page 1 of 1", RIGHT, 831, REGULAR, 6.5f, MINT);
    }

    private void rect(PDPageContentStream c, float x, float yTop, float w, float h, Color fill) throws IOException {
        c.setNonStrokingColor(fill);
        c.addRect(x, PAGE_H - yTop - h, w, h);
        c.fill();
    }

    private void roundRect(PDPageContentStream c, float x, float yTop, float w, float h, float rad,
                           Color fill, Color stroke) throws IOException {
        float y = PAGE_H - yTop - h;
        float r = Math.min(rad, Math.min(w, h) / 2);
        float k = r * 0.5523f;
        c.moveTo(x + r, y);
        c.lineTo(x + w - r, y);
        c.curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
        c.lineTo(x + w, y + h - r);
        c.curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h);
        c.lineTo(x + r, y + h);
        c.curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r);
        c.lineTo(x, y + r);
        c.curveTo(x, y + r - k, x + r - k, y, x + r, y);
        c.closePath();
        if (fill != null && stroke != null) {
            c.setNonStrokingColor(fill); c.setStrokingColor(stroke); c.setLineWidth(0.75f); c.fillAndStroke();
        } else if (fill != null) {
            c.setNonStrokingColor(fill); c.fill();
        } else if (stroke != null) {
            c.setStrokingColor(stroke); c.setLineWidth(0.75f); c.stroke();
        } else {
            c.closePath();
        }
    }

    private void text(PDPageContentStream c, String s, float x, float yTop, PDFont font, float size,
                      Color color, float letterSpacing) throws IOException {
        c.beginText();
        c.setFont(font, size);
        c.setNonStrokingColor(color);
        c.setCharacterSpacing(letterSpacing);
        c.newLineAtOffset(x, PAGE_H - yTop - size * 0.85f);
        c.showText(clean(font, s));
        c.endText();
        c.setCharacterSpacing(0);
    }

    private void textRight(PDPageContentStream c, String s, float rightX, float yTop, PDFont font,
                           float size, Color color) throws IOException {
        text(c, s, rightX - width(font, size, s), yTop, font, size, color, 0);
    }

    private float width(PDFont font, float size, String s) throws IOException {
        return font.getStringWidth(clean(font, s)) / 1000f * size;
    }

    private String fit(PDFont font, float size, String s, float maxWidth) throws IOException {
        String t = clean(font, s == null ? "" : s);
        if (width(font, size, t) <= maxWidth) return t;
        while (t.length() > 1 && width(font, size, t + "...") > maxWidth) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    private String clean(PDFont font, String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            try { font.encode(String.valueOf(ch)); sb.append(ch); } catch (Exception e) { sb.append('?'); }
        }
        return sb.toString();
    }

    private static Color statusColor(String status) {
        switch (status) {
            case "PROCESSED": return BRAND_GREEN;
            case "PENDING":   return ACCENT_GOLD;
            case "FAILED":    return DANGER;
            default:          return MUTED;
        }
    }

    private static String safe(String s) { return s == null || s.isBlank() ? "-" : s; }

    private static String date(java.time.temporal.TemporalAccessor d) { return d == null ? "-" : DATE.format(d); }

    private static String hours(BigDecimal b) { return b == null ? "0" : b.stripTrailingZeros().toPlainString(); }

    private static String pct(BigDecimal b) {
        return b == null ? "-" : b.setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private static double ratio(BigDecimal percent) {
        if (percent == null) return 0;
        return Math.max(0, Math.min(1, percent.doubleValue() / 100.0));
    }

    private static String capitalize(String s) {
        if (s == null || s.length() < 2) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
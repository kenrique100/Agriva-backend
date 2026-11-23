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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Salary receipt (A4). Layout follows the Figma frame "Salary receipt – A4 (595×842 pt)".
 * <p>
 * Coordinates below are written exactly as in Figma (origin top-left, y grows DOWN).
 * The helpers convert them to PDFBox space (origin bottom-left): pdfY = PAGE_H - yTop - height.
 */
@Service
@Slf4j
public class PdfReceiptServiceImpl implements PdfReceiptService {

    // ---- Page ----------------------------------------------------------------------------
    private static final float PAGE_W = 595f;
    private static final float PAGE_H = 842f;          // A4 (the old code used 792 = US Letter)
    private static final float MARGIN = 40f;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;   // 515
    private static final float RIGHT = PAGE_W - MARGIN;           // 555

    // ---- Colours (same palette as the Agriva app) ------------------------------------------
    private static final Color BRAND_DARK  = new Color(14, 59, 44);     // #0E3B2C
    private static final Color BRAND_GREEN = new Color(31, 143, 95);    // #1F8F5F
    private static final Color ACCENT_GOLD = new Color(232, 163, 61);   // #E8A33D
    private static final Color GREEN_SOFT  = new Color(230, 244, 236);  // #E6F4EC
    private static final Color TEXT        = new Color(20, 32, 27);     // #14201B
    private static final Color MUTED       = new Color(101, 117, 108);  // #65756C
    private static final Color LINE        = new Color(227, 234, 228);  // #E3EAE4
    private static final Color BG_SOFT     = new Color(244, 247, 243);  // #F4F7F3
    private static final Color MINT        = new Color(191, 227, 208);  // #BFE3D0
    private static final Color DANGER      = new Color(229, 72, 77);    // #E5484D

    // ---- Fonts (standard PDF fonts: no font files to ship) -----------------------------------
    private static final PDFont REGULAR = PDType1Font.HELVETICA;
    private static final PDFont BOLD    = PDType1Font.HELVETICA_BOLD;
    private static final PDFont OBLIQUE = PDType1Font.HELVETICA_OBLIQUE;
    private static final PDFont SIGN    = PDType1Font.TIMES_ITALIC;

    private static final String CURRENCY = "XAF";
    private static final float ROW_H = 22f;
    private static final int MAX_FULL_ROWS = 8;
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
                drawKpis(c, r);
                drawProductivityBar(c, r);
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

    /** Header band 0,0 595x112 + gold stripe 112..116. */
    private void drawHeader(PDDocument doc, PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        rect(c, 0, 0, PAGE_W, 112, BRAND_DARK);
        rect(c, 0, 112, PAGE_W, 4, ACCENT_GOLD);

        roundRect(c, 40, 32, 48, 48, 10, ACCENT_GOLD, null);          // logo tile
        try (InputStream in = new ClassPathResource("static/images/agriva_emblem.png").getInputStream()) {
            BufferedImage img = javax.imageio.ImageIO.read(in);
            if (img != null) {
                PDImageXObject logo = LosslessFactory.createFromImage(doc, img);
                c.drawImage(logo, 48, PAGE_H - 40 - 32, 32, 32);       // 32x32 inside the tile
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

    /** Net pay card 40,136 515x84 + status pill. */
    private void drawNetPayCard(PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        roundRect(c, 40, 136, CONTENT_W, 84, 12, GREEN_SOFT, LINE);
        text(c, "NET PAY", 60, 152, BOLD, 8, MUTED, 1f);
        String amount = r.getAmount() == null ? "0.00" : String.format(Locale.US, "%,.2f", r.getAmount());
        text(c, CURRENCY + " " + amount, 60, 166, BOLD, 30, BRAND_GREEN, 0);

        String status = safe(r.getStatus()).toUpperCase(Locale.ROOT);
        float pillW = width(BOLD, 8.5f, status) + 24;
        float pillX = 535 - pillW;
        roundRect(c, pillX, 152, pillW, 22, 11, statusColor(status), null);
        text(c, status, pillX + 12, 157, BOLD, 8.5f, Color.WHITE, 0.5f);

        textRight(c, "Paid on " + date(r.getPaymentDate()), 535, 184, BOLD, 9, TEXT);
        textRight(c, "Ref: " + safe(r.getPaymentReference()), 535, 198, REGULAR, 8, MUTED);
    }

    /** Info card at (x,240) 250x118. */
    private void drawInfoCard(PDPageContentStream c, float x, String title, String[][] rows) throws IOException {
        roundRect(c, x, 240, 250, 118, 10, Color.WHITE, LINE);
        rect(c, x + 16, 253, 3, 10, BRAND_GREEN);                      // title marker
        text(c, title, x + 26, 253, BOLD, 8.5f, BRAND_GREEN, 1f);
        rect(c, x + 16, 266, 218, 1, LINE);
        for (int i = 0; i < rows.length; i++) {
            float y = 278 + i * 18f;
            text(c, rows[i][0], x + 16, y, BOLD, 8.5f, MUTED, 0);
            text(c, fit(REGULAR, 9, rows[i][1], 138), x + 96, y, REGULAR, 9, TEXT, 0);
        }
    }

    /** Four KPI boxes at y=378, 121.25x56, gap 10. */
    private void drawKpis(PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        String[] labels = {"TOTAL HOURS", "EXPECTED", "PRODUCTIVITY", "DAYS WORKED"};
        String[] values = {hours(r.getTotalActualHours()) + " h", hours(r.getTotalExpectedHours()) + " h",
                pct(r.getOverallProductivity()), String.valueOf(r.getWorkingDays())};
        for (int i = 0; i < 4; i++) {
            float x = 40 + i * 131.25f;
            rect(c, x, 378, 121.25f, 56, BG_SOFT);
            rect(c, x, 378, 3, 56, BRAND_GREEN);                       // accent bar
            text(c, labels[i], x + 14, 390, BOLD, 7.5f, MUTED, 0.6f);
            text(c, values[i], x + 14, 404, BOLD, 16, i == 2 ? BRAND_GREEN : TEXT, 0);
        }
    }

    /** Overall productivity progress bar: track 40,464 515x8. */
    private void drawProductivityBar(PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        text(c, "Overall productivity", 40, 448, BOLD, 8.5f, MUTED, 0);
        textRight(c, pct(r.getOverallProductivity()), RIGHT, 448, BOLD, 8.5f, BRAND_GREEN);
        roundRect(c, 40, 464, CONTENT_W, 8, 4, LINE, null);
        double p = ratio(r.getOverallProductivity());
        if (p > 0) roundRect(c, 40, 464, (float) (CONTENT_W * p), 8, 4, BRAND_GREEN, null);
    }

    /** Validated tasks table: header y=510 (h24), rows from y=534 (h22), totals band after the rows. */
    private void drawTasksTable(PDPageContentStream c, SalaryReceiptResponse r) throws IOException {
        List<SalaryReceiptLineItem> tasks = r.getTasks() == null ? List.of() : r.getTasks();

        rect(c, 40, 492, 3, 10, BRAND_GREEN);
        text(c, "VALIDATED TASKS", 50, 492, BOLD, 8.5f, BRAND_GREEN, 1f);
        textRight(c, tasks.size() + " tasks validated this period", RIGHT, 492, REGULAR, 8, MUTED);

        roundRect(c, 40, 510, CONTENT_W, 24, 4, BRAND_DARK, null);
        text(c, "#", 50, 518, BOLD, 8, Color.WHITE, 0);
        text(c, "TASK DESCRIPTION", 72, 518, BOLD, 8, Color.WHITE, 0.5f);
        textRight(c, "EXPECTED (H)", 395, 518, BOLD, 8, Color.WHITE);
        textRight(c, "ACTUAL (H)", 455, 518, BOLD, 8, Color.WHITE);
        textRight(c, "COMPLETION", 547, 518, BOLD, 8, Color.WHITE);

        float y = 534;
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
                roundRect(c, 466, y + 9, 36, 5, 2.5f, LINE, null);
                if (done > 0) roundRect(c, 466, y + 9, (float) (36 * Math.min(1, done)), 5, 2.5f,
                        done >= 0.9 ? BRAND_GREEN : ACCENT_GOLD, null);
                textRight(c, pct(li.getCompletionRate()), 547, y + 7, BOLD, 9, TEXT);
                y += ROW_H;
            }
            if (overflow) {
                text(c, "+ " + (tasks.size() - shown) + " more validated tasks (see payroll record)",
                        72, y + 7, REGULAR, 8.5f, MUTED, 0);
                y += ROW_H;
            }
        }

        // Totals band
        rect(c, 40, y, CONTENT_W, 26, GREEN_SOFT);
        rect(c, 40, y, CONTENT_W, 1.5f, BRAND_GREEN);
        text(c, "Total – " + tasks.size() + " validated tasks", 72, y + 8, BOLD, 9, TEXT, 0);
        textRight(c, hours(r.getTotalExpectedHours()), 395, y + 8, BOLD, 9, TEXT);
        textRight(c, hours(r.getTotalActualHours()), 455, y + 8, BOLD, 9, TEXT);
        textRight(c, pct(r.getOverallProductivity()), 547, y + 8, BOLD, 9, BRAND_GREEN);
    }

    /** Footer: divider y=752, QR 54x54 at (40,762), signature block right, band at y=826. */
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

    // =========================================================================================
    // Drawing helpers (all take Figma-style top-left coordinates)
    // =========================================================================================

    private void rect(PDPageContentStream c, float x, float yTop, float w, float h, Color fill) throws IOException {
        c.setNonStrokingColor(fill);
        c.addRect(x, PAGE_H - yTop - h, w, h);
        c.fill();
    }

    /** Rounded rectangle; pass null for fill or stroke to skip it. */
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

    /** yTop is the top of the text box; baseline is about 0.85 x font size below it. */
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

    private void textRight(PDPageContentStream c, String s, float rightX, float yTop, PDFont font, float size,
                           Color color) throws IOException {
        text(c, s, rightX - width(font, size, s), yTop, font, size, color, 0);
    }

    private float width(PDFont font, float size, String s) throws IOException {
        return font.getStringWidth(clean(font, s)) / 1000f * size;
    }

    /** Cuts the text with "..." so it never runs past maxWidth. */
    private String fit(PDFont font, float size, String s, float maxWidth) throws IOException {
        String t = clean(font, s == null ? "" : s);
        if (width(font, size, t) <= maxWidth) return t;
        while (t.length() > 1 && width(font, size, t + "...") > maxWidth) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    /** Standard PDF fonts only support WinAnsi: replace anything else with '?'. */
    private String clean(PDFont font, String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            try { font.encode(String.valueOf(ch)); sb.append(ch); } catch (Exception e) { sb.append('?'); }
        }
        return sb.toString();
    }

    // =========================================================================================
    // Formatting helpers
    // =========================================================================================

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

    /** 95.0 (percent) -> 0.95, clamped to 0..1. */
    private static double ratio(BigDecimal percent) {
        if (percent == null) return 0;
        return Math.max(0, Math.min(1, percent.doubleValue() / 100.0));
    }

    private static String capitalize(String s) {
        if (s == null || s.length() < 2) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
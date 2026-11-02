package com.obms.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.obms.common.ApiException;
import com.obms.config.AppProperties;
import com.obms.domain.Account;
import com.obms.domain.Enums;
import com.obms.domain.Transaction;
import com.obms.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates PDF account statements (FR-7).
 *
 * <p>The opening balance is derived rather than stored: it is the first row's
 * balance-after minus that row's own effect. That keeps the statement
 * arithmetically consistent with the ledger instead of relying on a separate
 * running total that could drift out of step.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter STAMP_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final int MAX_RANGE_DAYS = 400;

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final AuditService auditService;
    private final AppProperties properties;

    /**
     * Builds a statement PDF for an account and date range.
     *
     * @param from inclusive start date
     * @param to   inclusive end date
     * @return the PDF bytes
     */
    @Transactional(readOnly = true)
    public StatementDocument generate(Long accountId, Long customerId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("DATE_RANGE_REQUIRED", "Provide both a start and end date.");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "End date must be on or after start date.");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw ApiException.badRequest("RANGE_TOO_LARGE",
                    "Statements cover at most " + MAX_RANGE_DAYS + " days. Narrow the range.");
        }

        Account account = accountService.requireOwned(accountId, customerId);

        List<Transaction> rows = transactionRepository.findStatementRows(
                accountId,
                from.atStartOfDay(ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(ZONE).toInstant());

        Totals totals = Totals.from(rows);
        byte[] pdf = render(account, from, to, rows, totals);

        auditService.record("statement.generated", "Account", account.getAccountNumber(),
                "Statement " + from + " to " + to + " with " + rows.size() + " rows");

        String filename = "OBMS_Statement_" + account.getAccountNumber() + "_"
                + from + "_to_" + to + ".pdf";
        return new StatementDocument(filename, pdf, rows.size(), totals.openingBalance(),
                totals.closingBalance(), totals.totalDebits(), totals.totalCredits());
    }

    private byte[] render(Account account, LocalDate from, LocalDate to,
                          List<Transaction> rows, Totals totals) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 42, 42);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(14, 35, 76));
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

            Paragraph header = new Paragraph(properties.getBank().getName(), titleFont);
            header.setSpacingAfter(2f);
            document.add(header);

            Paragraph subtitle = new Paragraph(
                    properties.getBank().getBranch() + "  |  IFSC " + properties.getBank().getIfsc(),
                    labelFont);
            subtitle.setSpacingAfter(14f);
            document.add(subtitle);

            Paragraph statementTitle = new Paragraph("Account Statement",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK));
            statementTitle.setSpacingAfter(8f);
            document.add(statementTitle);

            document.add(summaryTable(account, from, to, totals, labelFont, bodyFont));

            Paragraph spacer = new Paragraph(" ");
            spacer.setSpacingAfter(6f);
            document.add(spacer);

            document.add(rows.isEmpty()
                    ? new Paragraph("No transactions in this period.", bodyFont)
                    : transactionTable(rows, headFont, bodyFont));

            Paragraph footer = new Paragraph(
                    "\nGenerated " + STAMP_FMT.format(java.time.LocalDateTime.now(ZONE))
                            + ". This is a simulated banking environment created for an academic "
                            + "project. It is not a legal financial document and no real funds are "
                            + "involved.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7.5f, Color.GRAY));
            document.add(footer);

            document.close();
        } catch (DocumentException ex) {
            log.error("Failed to render statement PDF", ex);
            throw ApiException.serviceUnavailable("Could not generate the statement PDF.");
        }
        return out.toByteArray();
    }

    private PdfPTable summaryTable(Account account, LocalDate from, LocalDate to,
                                   Totals totals, Font labelFont, Font bodyFont) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);

        addPair(table, "Account Number", account.getAccountNumber(), labelFont, bodyFont);
        addPair(table, "Account Type", account.getAccountType().name(), labelFont, bodyFont);
        addPair(table, "Account Holder", account.getCustomer().fullName(), labelFont, bodyFont);
        addPair(table, "Customer Number", account.getCustomer().getCustomerNumber(), labelFont, bodyFont);
        addPair(table, "Period", DATE_FMT.format(from) + " to " + DATE_FMT.format(to),
                labelFont, bodyFont);
        addPair(table, "Currency", account.getCurrency(), labelFont, bodyFont);
        addPair(table, "Opening Balance", money(totals.openingBalance()), labelFont, bodyFont);
        addPair(table, "Closing Balance", money(totals.closingBalance()), labelFont, bodyFont);
        addPair(table, "Total Debits", money(totals.totalDebits()), labelFont, bodyFont);
        addPair(table, "Total Credits", money(totals.totalCredits()), labelFont, bodyFont);

        return table;
    }

    private void addPair(PdfPTable table, String label, String value, Font labelFont, Font bodyFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorderWidth(0.4f);
        labelCell.setBorderColor(new Color(220, 224, 230));
        labelCell.setPadding(5f);
        labelCell.setBackgroundColor(new Color(246, 248, 251));

        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "-" : value, bodyFont));
        valueCell.setBorderWidth(0.4f);
        valueCell.setBorderColor(new Color(220, 224, 230));
        valueCell.setPadding(5f);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private PdfPTable transactionTable(List<Transaction> rows, Font headFont, Font bodyFont) {
        PdfPTable table = new PdfPTable(new float[]{2.0f, 2.3f, 3.6f, 1.6f, 1.6f, 1.9f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        for (String heading : List.of("Date", "Reference", "Description", "Debit", "Credit", "Balance")) {
            PdfPCell cell = new PdfPCell(new Phrase(heading, headFont));
            cell.setBackgroundColor(new Color(14, 35, 76));
            cell.setPadding(5f);
            cell.setBorderWidth(0.3f);
            table.addCell(cell);
        }

        boolean alternate = false;
        for (Transaction row : rows) {
            Color background = alternate ? new Color(249, 250, 252) : Color.WHITE;
            alternate = !alternate;

            boolean debit = row.getDirection() == Enums.TransactionDirection.DEBIT;
            addBodyCell(table, DATE_FMT.format(row.getBookedAt().atZone(ZONE).toLocalDate()),
                    bodyFont, background, Element.ALIGN_LEFT);
            addBodyCell(table, row.getReference(), bodyFont, background, Element.ALIGN_LEFT);
            addBodyCell(table, describe(row), bodyFont, background, Element.ALIGN_LEFT);
            addBodyCell(table, debit ? money(row.getAmount()) : "", bodyFont, background,
                    Element.ALIGN_RIGHT);
            addBodyCell(table, debit ? "" : money(row.getAmount()), bodyFont, background,
                    Element.ALIGN_RIGHT);
            addBodyCell(table, money(row.getBalanceAfter()), bodyFont, background,
                    Element.ALIGN_RIGHT);
        }
        return table;
    }

    private void addBodyCell(PdfPTable table, String text, Font font, Color background, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4.5f);
        cell.setBorderWidth(0.3f);
        cell.setBorderColor(new Color(226, 230, 236));
        cell.setBackgroundColor(background);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private String describe(Transaction row) {
        StringBuilder sb = new StringBuilder();
        if (row.getDescription() != null && !row.getDescription().isBlank()) {
            sb.append(row.getDescription());
        } else {
            sb.append(row.getTransactionType().name().replace('_', ' '));
        }
        if (row.getCounterpartyName() != null && !row.getCounterpartyName().isBlank()) {
            sb.append(" (").append(row.getCounterpartyName()).append(')');
        }
        return sb.toString();
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "" : amount.toPlainString();
    }

    /**
     * A rendered statement.
     *
     * @param filename       suggested download name
     * @param content        PDF bytes
     * @param rowCount       number of ledger rows included
     * @param openingBalance balance before the first row in the period
     * @param closingBalance balance after the last row in the period
     */
    public record StatementDocument(
            String filename,
            byte[] content,
            int rowCount,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal totalDebits,
            BigDecimal totalCredits
    ) {
        /** Defensive copy: records expose arrays by reference by default. */
        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    /**
     * Period aggregates computed from the ledger rows themselves.
     */
    record Totals(BigDecimal openingBalance, BigDecimal closingBalance,
                  BigDecimal totalDebits, BigDecimal totalCredits) {

        static Totals from(List<Transaction> rows) {
            if (rows.isEmpty()) {
                return new Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            BigDecimal debits = BigDecimal.ZERO;
            BigDecimal credits = BigDecimal.ZERO;
            for (Transaction row : rows) {
                if (row.getDirection() == Enums.TransactionDirection.DEBIT) {
                    debits = debits.add(row.getAmount());
                } else {
                    credits = credits.add(row.getAmount());
                }
            }

            Transaction first = rows.get(0);
            Transaction last = rows.get(rows.size() - 1);

            // Reverse the first row's own effect to recover the opening balance.
            BigDecimal opening = first.getBalanceAfter() == null
                    ? BigDecimal.ZERO
                    : first.getDirection() == Enums.TransactionDirection.DEBIT
                            ? first.getBalanceAfter().add(first.getAmount())
                            : first.getBalanceAfter().subtract(first.getAmount());

            BigDecimal closing = last.getBalanceAfter() == null ? opening : last.getBalanceAfter();
            return new Totals(opening, closing, debits, credits);
        }
    }
}

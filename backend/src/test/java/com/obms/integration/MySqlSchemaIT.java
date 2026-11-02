package com.obms.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the application against a real MySQL 8 server and checks the schema
 * Hibernate produces from the JPA mappings.
 *
 * <p>Named with an {@code IT} suffix so Surefire's default includes skip it:
 * the ordinary {@code mvn test} run stays on in-memory H2 and needs no
 * database server. CI runs this class explicitly, with a MySQL service
 * container, and then dumps the resulting schema to
 * {@code database/schema.sql} - which is why that file is generated rather
 * than written by hand and able to drift from the entities.</p>
 *
 * <p>The checks below are deliberately about structure rather than data. Two
 * things would otherwise go unverified by the rest of the suite, because every
 * other test runs on H2:</p>
 * <ul>
 *   <li>that the mappings are valid against MySQL and its dialect at all, and</li>
 *   <li>that monetary columns land as DECIMAL. If a {@code BigDecimal} field
 *       were ever mapped to a floating-point column, every balance in the
 *       system would be subject to rounding error, and no H2 test would
 *       notice.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("mysqlschema")
class MySqlSchemaIT {

    /** Tables the Phase-1 data layer and the Phase-2 modules require. */
    private static final List<String> EXPECTED_TABLES = List.of(
            "customers", "users", "roles", "user_roles", "kyc_records",
            "accounts", "transactions", "beneficiaries", "billers",
            "bill_payments", "pending_transfers", "otp_tokens",
            "notifications", "savings_goals", "fraud_alerts",
            "financial_score_snapshots", "copilot_conversations",
            "copilot_messages", "idempotency_records", "audit_logs");

    /** Columns that hold money and must therefore never be floating point. */
    private static final List<String> MONEY_COLUMNS = List.of(
            "accounts.balance",
            "accounts.minimum_balance",
            "accounts.daily_transfer_limit",
            "transactions.amount",
            "transactions.balance_after",
            "bill_payments.amount",
            "pending_transfers.amount",
            "savings_goals.target_amount",
            "savings_goals.saved_amount",
            "fraud_alerts.amount");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("the application context starts against MySQL")
    void contextStartsAgainstMySql() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            assertThat(metaData.getDatabaseProductName())
                    .as("this test is meaningless unless it really ran on MySQL")
                    .containsIgnoringCase("mysql");
        }
    }

    @Test
    @DisplayName("Hibernate creates every table the modules need")
    void schemaContainsExpectedTables() throws SQLException {
        Set<String> actual = readTableNames();
        assertThat(actual).containsAll(EXPECTED_TABLES);
    }

    @Test
    @DisplayName("every monetary column is DECIMAL, never a floating-point type")
    void monetaryColumnsAreDecimal() throws SQLException {
        List<String> offenders = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String qualified : MONEY_COLUMNS) {
                String[] parts = qualified.split("\\.", 2);
                String table = parts[0];
                String column = parts[1];

                String type = null;
                try (ResultSet columns = metaData.getColumns(
                        connection.getCatalog(), null, table, column)) {
                    if (columns.next()) {
                        type = columns.getString("TYPE_NAME");
                    }
                }

                if (type == null) {
                    offenders.add(qualified + " (column not found)");
                } else if (!type.toUpperCase(Locale.ROOT).contains("DECIMAL")) {
                    offenders.add(qualified + " is " + type);
                }
            }
        }

        assertThat(offenders)
                .as("monetary columns must be DECIMAL so balances cannot drift")
                .isEmpty();
    }

    @Test
    @DisplayName("reference data is seeded with the four roles")
    void referenceRolesAreSeeded() throws SQLException {
        Set<String> roles = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.createStatement()
                     .executeQuery("select name from roles")) {
            while (rs.next()) {
                roles.add(rs.getString(1));
            }
        }
        assertThat(roles)
                .contains("CUSTOMER", "BANK_STAFF", "ADMIN", "FRAUD_ANALYST");
    }

    private Set<String> readTableNames() throws SQLException {
        Set<String> names = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(
                    connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return names;
    }
}

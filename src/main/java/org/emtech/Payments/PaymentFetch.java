package org.emtech.Payments;

import lombok.extern.slf4j.Slf4j;
import org.emtech.Entity.Props;
import org.emtech.Tools.DatabaseConnection;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class PaymentFetch {

    private final DatabaseConnection databaseConnection;
    private final PaymentService paymentService;

    public PaymentFetch(DatabaseConnection databaseConnection, PaymentService paymentService) {
        this.databaseConnection = databaseConnection;
        this.paymentService = paymentService;
    }
    public class SqlLoader {
        public static String load(String resourcePath) {
            try (InputStream is = SqlLoader.class.getClassLoader()
                    .getResourceAsStream(resourcePath)) {
                if (is == null) throw new IllegalArgumentException("SQL file not found: " + resourcePath);
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load SQL: " + resourcePath, e);
            }
        }
    }

    @Scheduled(fixedDelay = 35000)
    public void fetchAndSavePayments() {
        try (Connection conn = databaseConnection.dbConnection()) {
            String sqlFetch = PaymentQueries.getFetchPaymentsSql();
            List<Props> paymentsList = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sqlFetch);
                 ResultSet rs = ps.executeQuery()) {

                int count = 0;

                while (rs.next()) {
                    count++;

                    Props p = new Props();
                    p.setTransRefCode(rs.getString("TRANS_REF_CODE"));
                    p.setCustomerName(rs.getString("CUSTOMER_NAME"));
                    p.setCurrency(rs.getString("CRNCY"));
                    p.setAmount(rs.getString("AMT"));
                    p.setTimestamp(rs.getString("TIMESTAMP"));
                    p.setSpotExchangerate(rs.getString("SPOT_RATE"));
                    p.setCross(rs.getString("CROSS_RATE"));
                    p.setUsdEquivalent(rs.getString("USD_EQUIVALENT"));
                    p.setSector(rs.getString("SECTOR"));
                    p.setSectorDescription(rs.getString("SECTORDESCRIPTION"));

                    paymentsList.add(p);
                }

                System.out.println("Total fetched for payments++++++++++++: " + count);
            }

            if (paymentsList.isEmpty()) {
                log.info("No new payments to insert.");
                return;
            }
            Set<String> existingKeys = new HashSet<>();

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT TRANSREFCODE || '_' || TIMESTAMP AS key FROM custom.forex_payments WHERE FOREXTYPE = 'P'");
                 ResultSet rsCheck = ps.executeQuery()) {

                while (rsCheck.next()) {
                    existingKeys.add(rsCheck.getString("key"));
                }
            }
            String insertSql = """
    INSERT INTO custom.FOREX_PAYMENTS (
        TRANSREFCODE, CUSTOMERNAME, CURRENCY, AMOUNT, TIMESTAMP,
        SPOTEXCHANGERATE, CROSSRATE, USDEQUIVALENT, SECTORCODE, SECTORDESCRIPTION,FOREXTYPE, POSTED_FLAG
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?,?, ?, 'P', 'N')
""";

            int insertedCount = 0;

            try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {

                for (Props p : paymentsList) {

                    String key = p.getTransRefCode() + "_" + p.getTimestamp();

                    if (existingKeys.contains(key)) {
                        continue;
                    }

                    psInsert.setString(1, p.getTransRefCode());
                    psInsert.setString(2, p.getCustomerName());
                    psInsert.setString(3, p.getCurrency());
                    psInsert.setString(4, p.getAmount());
                    psInsert.setString(5, p.getTimestamp());
                    psInsert.setString(6, p.getSpotExchangerate());
                    psInsert.setString(7, p.getCross());
                    psInsert.setString(8, p.getUsdEquivalent());
                    psInsert.setString(9, p.getSector());
                    psInsert.setString(10, p.getSectorDescription());

                    psInsert.addBatch();
                    insertedCount++;
                    if (insertedCount % 500 == 0) {
                        psInsert.executeBatch();
                    }

                    existingKeys.add(key);
                }


                psInsert.executeBatch();
            }

            if (insertedCount == 0) {
                log.info("No new payments to insert (all duplicates).");
            } else {
                log.info("Successfully inserted {} new payments++++++++++++++++++++.", insertedCount);
            }

        } catch (Exception e) {
            log.error("Error in fetchAndSavePayments", e);
        }
    }
}

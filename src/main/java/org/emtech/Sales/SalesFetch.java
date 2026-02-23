package org.emtech.Sales;

import org.emtech.Tools.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SalesFetch {

    private static final Logger log = LoggerFactory.getLogger(SalesFetch.class);
    private final DatabaseConnection databaseConnection;

    public SalesFetch(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    @Scheduled(fixedDelay = 15000)
    public void fetchAndSaveSales() {

        String selectQuery = """
        
                select htd.tran_id TRANS_REF_CODE,
        htd.TRAN_DATE TIMESTAMP,\s
        gam.acct_name CUSTOMER_NAME,
          cvm.LOCALE_VALUE SECTOR,
          gam.acct_crncy_code CURRENCY,
          pst.CRNCY_SOLD,
          pst.buy_or_sell,
        pst.AMT_HOME_CRNCY,
        htd.tran_amt AMOUNT,
        htd.rate as SPOT_EXCHANGE_RATE,
        CASE\s
                WHEN gam.acct_crncy_code = 'KES' and  pst.CRNCY_SOLD <> 'KES'
                or gam.acct_crncy_code <> 'KES' and  pst.CRNCY_SOLD = 'KES'
                THEN\s
                    'K2' \s
                ELSE\s
                    'N2'  \s
            END AS INTERBANK_CODES,
          CASE\s
            WHEN TRIM(gam.ACCT_CRNCY_CODE) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(gam.ACCT_CRNCY_CODE), 'USD',  'CBK',htd.TRAN_DATE )
        END AS CROSS_RATE,
        CASE\s
            WHEN gam.ACCT_CRNCY_CODE = 'USD'\s
                 THEN htd.TRAN_AMT
            ELSE ROUND(  htd.TRAN_AMT * custom.GetConvRate(  TRIM(gam.ACCT_CRNCY_CODE),  'USD', 'CBK', htd.TRAN_DATE ), 2)\s
        END AS USD_EQUIVALENT
        
        from tbaadm.htd, tbaadm.pst,tbaadm.gam ,CRMUSER.ACCOUNTS crm,tbaadm.cvm
        where htd.tran_id = pst.tran_id\s
        and  htd.acid = gam.acid\s
        and htd.TRAN_DATE=pst.TRAN_DATE\s
        and htd.PART_TRAN_SRL_NUM = pst.PART_TRAN_SRL_NUM\s
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        and  BUY_OR_SELL in ('S')
        and htd.tran_date = '31-jan-2025'\s
        """;

        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {

            ResultSet rs = selectStmt.executeQuery();

            Set<String> existingKeys = new HashSet<>();
            String checkSql =
                    "SELECT TRANSREFCODE || '_' || TIMESTAMP AS key FROM custom.forex_sales WHERE FOREXTYPE = 'S'";

            try (PreparedStatement ps = conn.prepareStatement(checkSql);
                 ResultSet rsCheck = ps.executeQuery()) {
                while (rsCheck.next()) {
                    existingKeys.add(rsCheck.getString("key"));
                }
            }

            List<String> insertStatements = new ArrayList<>();
            int batchSize = 0;

            while (rs.next()) {

                String transRefCode = rs.getString("TRANS_REF_CODE");
                String customerName = rs.getString("CUSTOMER_NAME");
                String currency = rs.getString("CURRENCY");
                String amount = rs.getString("AMOUNT");
                String timestamp = rs.getString("TIMESTAMP");
                String spotRate = rs.getString("SPOT_EXCHANGE_RATE");
                String crossRate = rs.getString("CROSS_RATE");
                String usdEquivalent = rs.getString("USD_EQUIVALENT");
                String sectorCode = rs.getString("SECTOR");
                String interbank = rs.getString("INTERBANK_CODES");

                String key = transRefCode + "_" + timestamp;
                if (existingKeys.contains(key)) {
                    continue;
                }

                String insertSql = String.format("""
                    INSERT INTO custom.forex_sales (
                        TRANSREFCODE, CUSTOMERNAME, CURRENCY, AMOUNT, TIMESTAMP,
                        SPOTEXCHANGERATE, CROSSRATE, USDEQUIVALENT,
                        SECTORCODE, INTERBANK_CODES, FOREXTYPE, POSTED_FLAG
                    )
                    VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','S','N')
                    """,
                        transRefCode, customerName, currency, amount, timestamp,
                        spotRate, crossRate, usdEquivalent, sectorCode, interbank
                );

                insertStatements.add(insertSql);
                existingKeys.add(key);
                batchSize++;

                if (batchSize % 500 == 0) {
                    try (Statement stmt = conn.createStatement()) {
                        for (String sql : insertStatements) {
                            stmt.addBatch(sql);
                        }
                        stmt.executeBatch();
                        insertStatements.clear();
                    }
                }
            }

            if (!insertStatements.isEmpty()) {
                try (Statement stmt = conn.createStatement()) {
                    for (String sql : insertStatements) {
                        stmt.addBatch(sql);
                    }
                    stmt.executeBatch();
                }
            }

            if (batchSize == 0) {
                log.info("No new sales to insert.");
            } else {
                log.info("Sales saved into custom.forex successfully .");
            }

        } catch (Exception e) {
            log.error("Error saving Sales into custom.forex", e);
        }
    }
}


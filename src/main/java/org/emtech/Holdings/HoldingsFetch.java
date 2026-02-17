package org.emtech.Holdings;

import lombok.extern.slf4j.Slf4j;
import org.emtech.Tools.DatabaseConnection;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

@Slf4j
@Service
public class HoldingsFetch {

    private final DatabaseConnection databaseConnection;

    public HoldingsFetch(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    @Scheduled(fixedDelay = 15000)
    public void fetchAndSaveHoldings() {

        String selectQuery = """
      
                SELECT
          UNIQUE(GAM.FORACID) ACCOUNT_REF,
          GAM.ACCT_NAME       CUSTOMER_NAME,
          GAM.ACCT_CRNCY_CODE CURRENCY,
          CASE     WHEN GAM.SCHM_TYPE = 'CAA' THEN 'CA'     WHEN GAM.SCHM_TYPE = 'SBA' THEN 'SA'     WHEN GAM.SCHM_TYPE = 'TDA' THEN 'FD'     ELSE 'CA' END AS ACCOUNT_TYPE,\s
          ACCOUNTS.ORGKEY     CIF,
          TBAADM.eab_Bal(GAM.ACID, TO_DATE('02-JAN-26','DD-MON-RR') ) AMOUNT,
          TBAADM.ConvertAmount(GAM.ACCT_CRNCY_CODE, 'KES', 'CBK',
          TBAADM.eab_Bal(GAM.ACID, TO_DATE('02-JAN-26','DD-MON-RR') ), TO_DATE('02-JAN-26','DD-MON-RR') ) AMOUNT_KES,
      
          (SELECT LOCALE_VALUE FROM TBAADM.CVM
           WHERE SEGMENTATION_CLASS = CATEGORY_VALUE
             AND CRM_CATEGORY_TYPE = 'SEGMENTATION_CLASS') SECTOR,
          (SELECT LOCALE_VALUE FROM TBAADM.CVM
           WHERE SUBSEGMENT = CATEGORY_VALUE
             AND CRM_CATEGORY_TYPE = 'CORP_SUB_SEGMENT') SUB_SECTOR, \s
          CASE
              WHEN GAM.ACCT_CRNCY_CODE = 'USD' THEN 1
              ELSE (
                  SELECT RTH.VAR_CRNCY_UNITS
                  FROM tbaadm.rth RTH
                  WHERE RTH.RTLIST_DATE = TO_DATE('02-JAN-26','DD-MON-RR')\s
                    AND RTH.FXD_CRNCY_CODE = GAM.ACCT_CRNCY_CODE
                    AND RTH.VAR_CRNCY_CODE = 'USD'
                    AND RTH.RATECODE = 'CBK'
                  FETCH FIRST 1 ROW ONLY
              )
          END AS CROSSRATE
      
      FROM TBAADM.GAM
      JOIN CRMUSER.ACCOUNTS ON ACCOUNTS.ORGKEY = GAM.CIF_ID
      --JOIN TBAADM.GAC        ON GAM.ACID      = GAC.ACID
      --JOIN tbaadm.eit        ON EIT.ENTITY_ID = GAM.ACID
      
      WHERE GAM.DEL_FLG = 'N'
        AND GAM.ACCT_CLS_FLG = 'N'
        AND TBAADM.eab_Bal(GAM.ACID, TO_DATE('02-JAN-26','DD-MON-RR') ) >= 0
        AND ACCOUNTS.CORP_ID IS NULL
        AND GAM.ACCT_CRNCY_CODE != 'KES'
      
      UNION ALL
      
      -- Corporate part
      SELECT
          UNIQUE(GAM.FORACID) ACCOUNT_REF,
          GAM.ACCT_NAME       CUSTOMER_NAME,
          GAM.ACCT_CRNCY_CODE ,
          CASE     WHEN GAM.SCHM_TYPE = 'CAA' THEN 'CA'     WHEN GAM.SCHM_TYPE = 'SBA' THEN 'SA'     WHEN GAM.SCHM_TYPE = 'TDA' THEN 'FD'     ELSE 'CA' END AS ACCOUNT_TYPE,\s
          ACCOUNTS.ORGKEY     CIF,
          TBAADM.eab_Bal(GAM.ACID, TO_DATE('02-JAN-26','DD-MON-RR') ) AMOUNT,
          TBAADM.ConvertAmount(GAM.ACCT_CRNCY_CODE, 'KES', 'CBK',
          TBAADM.eab_Bal(GAM.ACID, TO_DATE('02-JAN-26','DD-MON-RR') ), TO_DATE('02-JAN-26','DD-MON-RR') ) AMOUNT_KES,
          (SELECT LOCALE_VALUE FROM TBAADM.CVM
           WHERE SEGMENTATION_CLASS = CATEGORY_VALUE
             AND CRM_CATEGORY_TYPE = 'CORP_SEGMENTATION_CLASS') SECTOR,
          (SELECT LOCALE_VALUE FROM TBAADM.CVM
           WHERE SUBSEGMENT = CATEGORY_VALUE
             AND CRM_CATEGORY_TYPE = 'SUB_SEGMENT') SUB_SECTOR,
          CASE
              WHEN GAM.ACCT_CRNCY_CODE = 'USD' THEN 1
              ELSE (
                  SELECT RTH.VAR_CRNCY_UNITS
                  FROM tbaadm.rth RTH
                  WHERE RTH.RTLIST_DATE = TO_DATE('02-JAN-26','DD-MON-RR')\s
                    AND RTH.FXD_CRNCY_CODE = GAM.ACCT_CRNCY_CODE
                    AND RTH.VAR_CRNCY_CODE = 'USD'
                    AND RTH.RATECODE = 'CBK'
                  FETCH FIRST 1 ROW ONLY
              )
          END AS CROSSRATE
      
      FROM TBAADM.GAM
      JOIN CRMUSER.ACCOUNTS ON ACCOUNTS.ORGKEY = GAM.CIF_ID
      --JOIN TBAADM.GAC       ON GAM.ACID      = GAC.ACID
      --JOIN tbaadm.eit       ON EIT.ENTITY_ID = GAM.ACID
      
      WHERE GAM.DEL_FLG = 'N'
        AND GAM.ACCT_CLS_FLG = 'N'
        AND TBAADM.eab_Bal(GAM.ACID, TO_DATE('02-JAN-26','DD-MON-RR') ) >= 0
        AND ACCOUNTS.CORP_ID IS NOT NULL
        AND GAM.ACCT_CRNCY_CODE != 'KES'
        """;

        String insertSql = """
        INSERT INTO custom.forex_holdings (
            ACCOUNTREF, CUSTOMERNAME, ACCOUNTTYPE,
            CURRENCY, AMOUNT, CROSSRATE, SECTORCODE,
            FOREXTYPE, POSTED_FLAG
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'H', 'N')
    """;

        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectQuery);
             ResultSet rs = selectStmt.executeQuery();
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            Set<String> existingKeys = new HashSet<>();
            String checkSql =
                    "SELECT ACCOUNTREF || '_' || CURRENCY " +
                            "FROM custom.forex_holdings " +
                            "WHERE FOREXTYPE = 'H' " +
                            "AND POSTED_FLAG IN ('N','Y')";

            try (Statement st = conn.createStatement(); ResultSet rsCheck = st.executeQuery(checkSql)) {
                while (rsCheck.next()) { existingKeys.add(rsCheck.getString(1)); }
            }

            int count = 0;
            while (rs.next()) {
                String key = rs.getString("ACCOUNT_REF") + "_" + rs.getString("CURRENCY");
                if (existingKeys.contains(key)) continue;

                // Use setString/setObject to safely handle data and nulls
                insertStmt.setString(1, rs.getString("ACCOUNT_REF"));
                insertStmt.setString(2, rs.getString("CUSTOMER_NAME"));
                insertStmt.setString(3, rs.getString("ACCOUNT_TYPE"));
                insertStmt.setString(4, rs.getString("CURRENCY"));
                insertStmt.setString(5, rs.getString("AMOUNT"));
                insertStmt.setString(6, rs.getString("CROSSRATE"));
                insertStmt.setString(7, rs.getString("SECTOR"));

                insertStmt.addBatch();
                existingKeys.add(key);
                count++;

                if (count % 500 == 0) {
                    insertStmt.executeBatch();
                }
            }

            if (count % 500 != 0) {
                insertStmt.executeBatch();
            }

            log.info(count == 0 ? "No new holdings to insert." : "Saved {} holdings successfully.", count);

        } catch (Exception e) {
            log.error("Error saving holdings", e);
        }
    }}


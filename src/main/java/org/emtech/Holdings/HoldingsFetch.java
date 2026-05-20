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

  //  @Scheduled(cron = "0 0 2 1 * ?")
  @Scheduled(cron = "0 30 13 * * ?")
    public void fetchAndSaveHoldings() {

        String selectQuery = """
         select   UNIQUE(GAM.FORACID) ACCOUNT_REF,
                                   GAM.ACCT_NAME       CUSTOMER_NAME,
                                   GAM.ACCT_CRNCY_CODE CURRENCY,
                                   CASE     WHEN GAM.SCHM_TYPE = 'CAA' THEN 'CA'     WHEN GAM.SCHM_TYPE = 'SBA' THEN 'SA'     WHEN GAM.SCHM_TYPE = 'TDA' THEN 'FD'     ELSE 'CA' END AS ACCOUNT_TYPE,
                                   ACCOUNTS.ORGKEY     CIF,
                                   TBAADM.eab_Bal(GAM.ACID, LAST_DAY(ADD_MONTHS(SYSDATE, -1)) ) AMOUNT,
                                   TBAADM.ConvertAmount(GAM.ACCT_CRNCY_CODE, 'KES', 'CBK',
                                   TBAADM.eab_Bal(GAM.ACID, LAST_DAY(ADD_MONTHS(SYSDATE, -1)) ), LAST_DAY(ADD_MONTHS(SYSDATE, -1)) ) AMOUNT_KES,
                                   CASE REPLACE(
                                 (SELECT LOCALE_VALUE
                                  FROM TBAADM.CVM
                                  WHERE SEGMENTATION_CLASS = CATEGORY_VALUE
                                    AND CRM_CATEGORY_TYPE = 'SEGMENTATION_CLASS'),
                                 '&', 'AND'
                              )
                           WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'HG01'
         WHEN 'ANY OTHER ACTIVITIES'                               THEN 'HS07'
         WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'HG08'
         WHEN 'BUSINESS SERVICES'                                  THEN 'HS07'
         WHEN 'ELECTRICITY AND WATER'                              THEN 'HG11'
         WHEN 'FINANCE AND INSURANCE'                              THEN 'HS03'
         WHEN 'FOREIGN TRADE'                                      THEN 'HT03'
         WHEN 'MANUFACTURING'                                      THEN 'HG06'
         WHEN 'MINING AND QUARRYING'                               THEN 'HG03'
         WHEN 'OTHER ENTERPRISES'                                  THEN 'HS07'
         WHEN 'REAL ESTATE'                                        THEN 'HS07'
         WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'HS02'
         WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'HS01'
         WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'HG14'
         ELSE 'HS07'
                         END AS SECTOR,
                           REPLACE(
                         (SELECT LOCALE_VALUE FROM TBAADM.CVM
                          WHERE SEGMENTATION_CLASS = CATEGORY_VALUE
                            AND CRM_CATEGORY_TYPE = 'SEGMENTATION_CLASS'),
                         '&', 'AND'
                     ) AS SECTORDESCRIPTION,
                                   (SELECT LOCALE_VALUE FROM TBAADM.CVM
                                    WHERE SUBSEGMENT = CATEGORY_VALUE
                                      AND CRM_CATEGORY_TYPE = 'CORP_SUB_SEGMENT') SUB_SECTOR,
                                        CASE
                                       WHEN GAM.ACCT_CRNCY_CODE = 'USD' THEN 1
                                      ELSE custom.GetConvRate( TRIM(GAM.ACCT_CRNCY_CODE), 'USD',  'CBK',SYSDATE )
         
                                   END AS CROSSRATE
                               FROM TBAADM.GAM
                               JOIN CRMUSER.ACCOUNTS ON ACCOUNTS.ORGKEY = GAM.CIF_ID
                               --JOIN TBAADM.GAC        ON GAM.ACID      = GAC.ACID
                               --JOIN tbaadm.eit        ON EIT.ENTITY_ID = GAM.ACID
                               WHERE GAM.DEL_FLG = 'N'
                                 AND GAM.ACCT_CLS_FLG = 'N'
                                 AND TBAADM.eab_Bal(GAM.ACID, LAST_DAY(ADD_MONTHS(SYSDATE, -1)) ) >= 0
                                 AND ACCOUNTS.CORP_ID IS NULL
                                 AND GAM.ACCT_CRNCY_CODE != 'KES'
                               UNION ALL
                               -- Corporate part
                               SELECT
                                   UNIQUE(GAM.FORACID) ACCOUNT_REF,
                                   GAM.ACCT_NAME       CUSTOMER_NAME,
                                   GAM.ACCT_CRNCY_CODE ,
                                   CASE     WHEN GAM.SCHM_TYPE = 'CAA' THEN 'CA'     WHEN GAM.SCHM_TYPE = 'SBA' THEN 'SA'     WHEN GAM.SCHM_TYPE = 'TDA' THEN 'FD'     ELSE 'CA' END AS ACCOUNT_TYPE,
                                   ACCOUNTS.ORGKEY     CIF,
                                   TBAADM.eab_Bal(GAM.ACID, LAST_DAY(ADD_MONTHS(SYSDATE, -1)) ) AMOUNT,
                                   TBAADM.ConvertAmount(GAM.ACCT_CRNCY_CODE, 'KES', 'CBK',
                                   TBAADM.eab_Bal(GAM.ACID, LAST_DAY(ADD_MONTHS(SYSDATE, -1)) ), LAST_DAY(ADD_MONTHS(SYSDATE, -1)) ) AMOUNT_KES,
                                   CASE REPLACE(
                                 (SELECT LOCALE_VALUE FROM TBAADM.CVM
                                  WHERE SEGMENTATION_CLASS = CATEGORY_VALUE
                                    AND crm_category_type = 'CORP_SEGMENTATION_CLASS'),
                                 '&', 'AND'
                              )
                           WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'HG01'
         WHEN 'ANY OTHER ACTIVITIES'                               THEN 'HS07'
         WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'HG08'
         WHEN 'BUSINESS SERVICES'                                  THEN 'HS07'
         WHEN 'ELECTRICITY AND WATER'                              THEN 'HG11'
         WHEN 'FINANCE AND INSURANCE'                              THEN 'HS03'
         WHEN 'FOREIGN TRADE'                                      THEN 'HT03'
         WHEN 'MANUFACTURING'                                      THEN 'HG06'
         WHEN 'MINING AND QUARRYING'                               THEN 'HG03'
         WHEN 'OTHER ENTERPRISES'                                  THEN 'HS07'
         WHEN 'REAL ESTATE'                                        THEN 'HS07'
         WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'HS02'
         WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'HS01'
         WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'HG14'
         ELSE 'HS07'
                         END AS SECTOR,
                           REPLACE(
                         (SELECT LOCALE_VALUE FROM TBAADM.CVM
                          WHERE SEGMENTATION_CLASS = CATEGORY_VALUE
                                    AND crm_category_type = 'CORP_SEGMENTATION_CLASS'),
                         '&', 'AND'
                     ) AS SECTORDESCRIPTION,
                                   (SELECT LOCALE_VALUE FROM TBAADM.CVM
                                    WHERE SUBSEGMENT = CATEGORY_VALUE
                                      AND CRM_CATEGORY_TYPE = 'CORP_SUB_SEGMENT') SUB_SECTOR,
                                   CASE
                                       WHEN GAM.ACCT_CRNCY_CODE = 'USD' THEN 1
                                      ELSE custom.GetConvRate( TRIM(GAM.ACCT_CRNCY_CODE), 'USD',  'CBK',SYSDATE )
         
                                   END AS CROSSRATE
                               FROM TBAADM.GAM
                               JOIN CRMUSER.ACCOUNTS ON ACCOUNTS.ORGKEY = GAM.CIF_ID
                               --JOIN TBAADM.GAC       ON GAM.ACID      = GAC.ACID
                               --JOIN tbaadm.eit       ON EIT.ENTITY_ID = GAM.ACID
                               WHERE GAM.DEL_FLG = 'N'
                                 AND GAM.ACCT_CLS_FLG = 'N'
                                 AND TBAADM.eab_Bal(GAM.ACID, LAST_DAY(ADD_MONTHS(SYSDATE, -1)) ) >= 0
                                 AND ACCOUNTS.CORP_ID IS NOT NULL
                                 AND GAM.ACCT_CRNCY_CODE != 'KES'
        """;

        String insertSql = """
        INSERT INTO custom.forex_holdings (
            ACCOUNTREF, CUSTOMERNAME, ACCOUNTTYPE,
            CURRENCY, AMOUNT, CROSSRATE, SECTORCODE,SECTORDESCRIPTION,
            FOREXTYPE, POSTED_FLAG
        ) VALUES (?, ?, ?, ?, ?, ?, ?,? ,'H', 'N')
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


                insertStmt.setString(1, rs.getString("ACCOUNT_REF"));
                insertStmt.setString(2, rs.getString("CUSTOMER_NAME"));
                insertStmt.setString(3, rs.getString("ACCOUNT_TYPE"));
                insertStmt.setString(4, rs.getString("CURRENCY"));
                insertStmt.setString(5, rs.getString("AMOUNT"));
                insertStmt.setString(6, rs.getString("CROSSRATE"));
                insertStmt.setString(7, rs.getString("SECTOR"));
                insertStmt.setString(8, rs.getString("SECTORDESCRIPTION"));

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


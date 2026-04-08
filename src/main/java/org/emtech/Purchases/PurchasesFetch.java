package org.emtech.Purchases;

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
public class PurchasesFetch {

    private static final Logger log = LoggerFactory.getLogger(PurchasesFetch.class);
    private final DatabaseConnection databaseConnection;

    public PurchasesFetch(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    @Scheduled(fixedDelay = 20000)
    public void fetchAndSavePurchases() {

        String selectQuery = """
        
                select dtd.tran_id TRANS_REF_CODE,
        dtd.TRAN_DATE TIMESTAMP,\s
        gam.acct_name CUSTOMER_NAME,
        CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'RG01'
            WHEN 'ANY OTHER ACTIVITIES'                               THEN 'RS08'
            WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'RG08'
            WHEN 'BUSINESS SERVICES'                                  THEN 'RS08'
            WHEN 'ELECTRICITY AND WATER'                              THEN 'RG11'
            WHEN 'FINANCE AND INSURANCE'                              THEN 'RS03'
            WHEN 'FOREIGN TRADE'                                      THEN 'RTO'
            WHEN 'MANUFACTURING'                                      THEN 'RG06'
            WHEN 'MINING AND QUARRYING'                               THEN 'RG03'
            WHEN 'OTHER ENTERPRISES'                                  THEN 'RS08'
            WHEN 'REAL ESTATE'                                        THEN 'RS08'
            WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'RS02'
            WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'RS01'
            WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'RG14'
            ELSE 'RS08'
        END AS SECTOR,
          gam.acct_crncy_code CURRENCY,
          pst.CRNCY_SOLD,
          pst.buy_or_sell,
        --pst.AMT_HOME_CRNCY,
        dtd.tran_amt AMOUNT,
        dtd.rate as SPOT_EXCHANGE_RATE,
        CASE\s
                WHEN gam.acct_crncy_code = 'KES' and  pst.CRNCY_SOLD <> 'KES'
                or gam.acct_crncy_code <> 'KES' and  pst.CRNCY_SOLD = 'KES'
                THEN\s
                    'K2'   -- K category (KES involved) – refine to K1/K2/K3 later
                ELSE\s
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
          CASE\s
            WHEN TRIM(gam.ACCT_CRNCY_CODE) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(gam.ACCT_CRNCY_CODE), 'USD',  'CBK',dtd.TRAN_DATE )
        END AS CROSS_RATE,
        CASE\s
            WHEN gam.ACCT_CRNCY_CODE = 'USD'\s
                 THEN dtd.TRAN_AMT
            ELSE ROUND(  dtd.TRAN_AMT * custom.GetConvRate(  TRIM(gam.ACCT_CRNCY_CODE),  'USD', 'CBK', dtd.TRAN_DATE ), 2)\s
        END AS USD_EQUIVALENT
        -- ,dtd.TRAN_PARTICULAR, dtd.TRAN_RMKS
        -- dtd.*\s
        from tbaadm.dtd, tbaadm.pst,tbaadm.gam ,CRMUSER.ACCOUNTS crm,tbaadm.cvm
        where dtd.tran_id = pst.tran_id\s
        and  dtd.acid = gam.acid\s
        and dtd.TRAN_DATE=pst.TRAN_DATE\s
        and dtd.PART_TRAN_SRL_NUM = pst.PART_TRAN_SRL_NUM\s
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        and  BUY_OR_SELL in ('B')
        and dtd.tran_date = trunc(sysdate)
        
        UNION ALL
        
        select
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,\s
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'RS08' SECTOR,
        
        
        B.CCY_ONE_AMOUNT_CCY CURRENCY,
        B.CCY_TWO_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
               ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_SPOT_RATE spotrate,
                       CASE\s
                WHEN B.CCY_ONE_AMOUNT_CCY = 'KES' and  B.CCY_TWO_AMOUNT_CCY <> 'KES'
                or B.CCY_ONE_AMOUNT_CCY <> 'KES' and  B.CCY_TWO_AMOUNT_CCY = 'KES'
                  THEN\s
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE\s
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE\s
            WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_ONE_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        CASE\s
            WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'\s
                 THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)\s
        END AS USD_EQUIVALENT
        
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLOU'
        AND B.CCY_TWO_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'S'
        
        UNION ALL
        
        select\s
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,\s
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'RS08' SECTOR,
        
        B.CCY_TWO_AMOUNT_CCY CURRENCY,\s
        B.CCY_ONE_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
               ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_SPOT_RATE spotrate,
                       CASE\s
                WHEN B.CCY_TWO_AMOUNT_CCY = 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                or B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY = 'KES'
                  THEN\s
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE\s
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE\s
            WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_TWO_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        CASE\s
            WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'\s
                 THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)\s
        END AS USD_EQUIVALENT
        
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLOU'
        AND B.CCY_ONE_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'B'
        
        UNION ALL
        
        select\s
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,\s
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'RS08' SECTOR,
        
        B.CCY_ONE_AMOUNT_CCY CURRENCY,
        B.CCY_TWO_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
               ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_FWD_RATE spotrate,
                       CASE\s
                WHEN B.CCY_ONE_AMOUNT_CCY = 'KES' and  B.CCY_TWO_AMOUNT_CCY <> 'KES'
                or B.CCY_ONE_AMOUNT_CCY <> 'KES' and  B.CCY_TWO_AMOUNT_CCY = 'KES'
                  THEN\s
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE\s
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE\s
            WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_ONE_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        
        CASE\s
            WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'\s
                 THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)\s
        END AS USD_EQUIVALENT
        
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='NELG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLSW'
        AND B.CCY_TWO_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'S'
        
        UNION ALL
        
        select\s
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,\s
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'RS08' SECTOR,
        
        B.CCY_TWO_AMOUNT_CCY CURRENCY,
        B.CCY_one_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
               ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_FWD_RATE spotrate,
                       CASE\s
                WHEN B.CCY_TWO_AMOUNT_CCY = 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                or B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY = 'KES'
                  THEN\s
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE\s
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE\s
            WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_TWO_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        CASE\s
            WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'\s
                 THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)\s
        END AS USD_EQUIVALENT
        
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='NELG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLSW'
        AND B.CCY_ONE_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'B'
        
        
        """;

        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectQuery);
             ResultSet rs = selectStmt.executeQuery()) {

            // ================= EXISTING KEYS =================
            Set<String> existingKeys = new HashSet<>();

            String checkSql =
                    "SELECT TRANSREFCODE || '_' || TIMESTAMP AS key FROM custom.forex_purchases WHERE FOREXTYPE = 'P'";

            try (PreparedStatement ps = conn.prepareStatement(checkSql);
                 ResultSet rsCheck = ps.executeQuery()) {

                while (rsCheck.next()) {
                    existingKeys.add(rsCheck.getString("key"));
                }
            }

            // ================= INSERT PREP =================
            String insertSql = """
        INSERT INTO custom.forex_purchases (
            TRANSREFCODE, CUSTOMERNAME, CURRENCY, AMOUNT, TIMESTAMP,
            SPOTEXCHANGERATE, CROSSRATE, USDEQUIVALENT,
            SECTORCODE, INTERBANK_CODES, FOREXTYPE, POSTED_FLAG
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'P', 'N')
    """;

            int insertedCount = 0;
            int fetchedCount = 0;

            try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {

                // ================= MAIN LOOP =================
                while (rs.next()) {
                    fetchedCount++;

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
                        System.out.println("SKIPPED DUPLICATE: " + key);
                        continue;
                    }

                    psInsert.setString(1, transRefCode);
                    psInsert.setString(2, customerName);
                    psInsert.setString(3, currency);
                    psInsert.setString(4, amount);
                    psInsert.setString(5, timestamp);
                    psInsert.setString(6, spotRate);
                    psInsert.setString(7, crossRate);
                    psInsert.setString(8, usdEquivalent);
                    psInsert.setString(9, sectorCode);
                    psInsert.setString(10, interbank);

                    psInsert.addBatch();
                    insertedCount++;

                    if (insertedCount % 500 == 0) {
                        psInsert.executeBatch();
                    }

                    existingKeys.add(key);
                }

                // final flush
                psInsert.executeBatch();
            }

            System.out.println("TOTAL PURCHASES FETCHED: " + fetchedCount);

            if (insertedCount == 0) {
                log.info("No new purchases to insert.");
            } else {
                log.info("Inserted {} purchases successfully.", insertedCount);
            }

        } catch (Exception e) {
            log.error("Error saving purchases into custom.forex_purchases", e);
        }
    }
}

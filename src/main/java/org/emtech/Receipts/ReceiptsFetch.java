package org.emtech.Receipts;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import org.emtech.Tools.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReceiptsFetch {

    private static final Logger log = LoggerFactory.getLogger(ReceiptsFetch.class);

    private final DatabaseConnection databaseConnection;

    public ReceiptsFetch(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }
    @Scheduled(fixedDelay = 45000)
    public void fetchAndSaveReceipts() {

        String selectQuery = """
        SELECT  
         chd.tran_id TRANS_REF_CODE,
        -- gam.foracid ,
         gam.ACCT_NAME CUSTOMER_NAME,
        -- gam.cif_id,
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
        
          CGM.LODG_DATE TIMESTAMP,
                 TRIM(CGM.COLLECTION_CRNCY) CURRENCY,
                 CGM.COLLECTION_AMT AMOUNT,
        --         TRIM(CGM.PURPOSE_OF_REM) PURPOSE,
        --         CGM.party_name PARTY,
        --         CPM.IN_OUT_IND INOUT,
        --         'R' RTYPE,
                 chd.EVENT_RATE SPOT_EXCHANGE_RATE,
        --         0 CROSSRATE,
         CASE
            WHEN TRIM(CGM.COLLECTION_CRNCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(CGM.COLLECTION_CRNCY), 'USD',  'CBK',CGM.LODG_DATE )
         END AS CROSS_RATE,
         CASE
            WHEN CGM.COLLECTION_CRNCY = 'USD'
                 THEN CGM.COLLECTION_AMT
            ELSE ROUND(  CGM.COLLECTION_AMT * custom.GetConvRate(  TRIM(CGM.COLLECTION_CRNCY),  'USD', 'CBK', CGM.LODG_DATE ), 2)
         END AS USD_EQUIVALENT,
        --         'X' OTHERCCY,
                 CGM.OTHER_PARTY_NAME DESCRIPTION,
                 CGM.COLLECTION_ID BILLID
         FROM   \s
                 TBAADM.CGM,TBAADM.CPM,tbaadm.chd,tbaadm.gam,CRMUSER.ACCOUNTS crm,tbaadm.cvm
         WHERE CGM.COLLECTION_CODE = CPM.COLL_CODE
        and cgm.collection_id=chd.collection_id
        and cgm.oper_acid=gam.acid
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        and  chd.tran_id is not null
         AND     CGM.TRAN_SOL_ID IN (SELECT SOL_ID FROM TBAADM.SST WHERE SST.SET_ID = UPPER('ALL'))
        -- AND     CGM.LODG_DATE BETWEEN = TRUNC(SYSDATE)
         AND     CGM.LODG_DATE = TRUNC(SYSDATE)
         AND     CGM.DEL_FLG = 'N'
         AND     CGM.ENTITY_CRE_FLG = 'Y'
         AND     CGM.COLLECTION_CRNCY <> 'KES'
         AND     CPM.IN_OUT_IND = 'I'
         AND     NVL(TRIM(CGM.PURPOSE_OF_REM),' ') <> 'RTGS'
        
        union all
        
        SELECT 
            fae.TRAN_ID TRANS_REF_CODE,
        --    gam.foracid,
            gam.ACCT_NAME CUSTOMER_NAME,
        --    gam.cif_id,
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
            fae.tran_date TIMESTAMP,
            TRIM(FBM.BILL_CRNCY_CODE) CURRENCY,
            FBM.BILL_AMT AMOUNT,
        --    TRIM(FBM.PURPOSE_OF_REM) PURPOSE,
        --    FBM.PARTY_NAME PARTY,
        --    FBPT.INWARD_OUTWARD_IND INOUT,
        --    'B' RTYPE,
            fbm.NOTL_CONV_RATE spot_rate,
        --    0 CROSSRATE,
          CASE
            WHEN TRIM(FBM.BILL_CRNCY_CODE) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(FBM.BILL_CRNCY_CODE), 'USD',  'CBK',fbm.DATE_OF_REMIT )
         END AS CROSS_RATE,
         CASE
            WHEN FBM.BILL_CRNCY_CODE = 'USD'
                 THEN FBM.BILL_AMT
            ELSE ROUND(  FBM.BILL_AMT * custom.GetConvRate(  TRIM(FBM.BILL_CRNCY_CODE),  'USD', 'CBK',  fbm.DATE_OF_REMIT ), 2)
         END AS USD_EQUIVALENT,
        --    'X' OTHERCCY,
            FBM.OTHER_PARTY_NAME DESCRIPTION,
            FBM.BILL_ID BILLID
        FROM TBAADM.FBM FBM
        JOIN TBAADM.FBPT FBPT
             ON FBM.BILL_PARAM_TYPE = FBPT.BILL_PARAM_TYPE
        JOIN TBAADM.GAM GAM
             ON FBM.OPER_ACID = GAM.ACID
        JOIN (
                SELECT *
                FROM (SELECT FAE.*,  ROW_NUMBER() OVER (PARTITION BY BILL_ID ORDER BY TRAN_ID) rn
                      FROM TBAADM.FAE FAE )  WHERE rn = 1 ) FAE
             ON FBM.BILL_ID = FAE.BILL_ID
        join CRMUSER.ACCOUNTS crm
        on gam.cif_id = crm.orgkey
        join tbaadm.cvm
        on  crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        WHERE FBM.SOL_ID IN ( SELECT SOL_ID   FROM TBAADM.SST WHERE SST.SET_ID = UPPER('ALL'))
        AND FBM.DATE_OF_REMIT = TRUNC(SYSDATE)
        AND FBM.DEL_FLG = 'N'
        AND FBM.ENTITY_CRE_FLG = 'Y'
        AND FBM.BILL_CRNCY_CODE <> 'KES'
        AND FBPT.INWARD_OUTWARD_IND = 'I'
        AND NVL(TRIM(FBM.PURPOSE_OF_REM),' ') <> 'RTGS'
        AND FBM.BILL_FUNC_CODE = 'R'
        
        union all
        
        select 
        tut.tran_id TRANS_REF_CODE ,
        --gam.foracid,
        gam.ACCT_NAME,
        --gam.cif_id,
        cvm.LOCALE_VALUE,
        A.DEAL_DATE,
         B.CCY_TWO_AMOUNT_CCY CRNCY,
               ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       C.SHORT_NAME_1 PARTY,
        --       'O' INOUT,
        --       'T' RTYPE,
        
               B.MARKET_SPOT_RATE spot_RATE,
                 CASE
            WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
         END AS CROSS_RATE,
         CASE
            WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
         END AS USD_EQUIVALENT,
        --       B.CCY_ONE_AMOUNT_CCY OTHERCCY,
               'X' DESCRIPTION,
               'X' BILLID
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C, TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        and A.deal_num=tut.deal_number
        and tut.foracid = gam.foracid
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        AND A.DEAL_DATE  = TRUNC(SYSDATE)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLSP'
        AND B.CCY_TWO_AMOUNT_CCY <>'KES'
        AND FX_BUY_SELL = 'S'
        
        union all
        
        select 
        tut.tran_id TRANS_REF_CODE,
        --gam.foracid,
        gam.ACCT_NAME,
        --gam.cif_id,
        cvm.LOCALE_VALUE,
        A.DEAL_DATE,
        B.CCY_ONE_AMOUNT_CCY CRNCY,
               ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       C.SHORT_NAME_1 PARTY,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_SPOT_RATE spot_RATE,
                 CASE
            WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
         END AS CROSS_RATE,
         CASE
            WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
         END AS USD_EQUIVALENT,
        --       B.CCY_ONE_AMOUNT_CCY OTHERCCY,
               'X' DESCRIPTION,
               'X' BILLID
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        and A.deal_num=tut.deal_number
        and tut.foracid = gam.foracid
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        AND A.DEAL_DATE = TRUNC(SYSDATE)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLSP'
        AND B.CCY_ONE_AMOUNT_CCY <>'KES'
        AND FX_BUY_SELL = 'B'
        --AND GAM.SCHM_TYPE != 'OAB'
        UNION ALL
        
        select 
        tut.tran_id TRANS_REF_CODE,
        --gam.foracid,
        gam.ACCT_NAME,
        --gam.cif_id,
        cvm.LOCALE_VALUE,
        A.DEAL_DATE,
        B.CCY_TWO_AMOUNT_CCY CRNCY,
               ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       C.SHORT_NAME_1 PARTY,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_FWD_RATE spot_RATE,
                 CASE
            WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
         END AS CROSS_RATE,
         CASE
            WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
         END AS USD_EQUIVALENT,
        --       B.CCY_ONE_AMOUNT_CCY OTHERCCY,
               'X' DESCRIPTION,
               'X' BILLID
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C, TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        and A.deal_num=tut.deal_number
        and tut.foracid = gam.foracid
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        AND A.DEAL_DATE = TRUNC(SYSDATE)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLOU'
        AND B.CCY_TWO_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'S'
        --AND GAM.SCHM_TYPE != 'OAB'
        
        UNION ALL
        
        select 
        tut.tran_id TRANS_REF_CODE,
        --gam.foracid,
        gam.ACCT_NAME,
        --gam.cif_id,
        cvm.LOCALE_VALUE,
        A.DEAL_DATE,
        B.CCY_ONE_AMOUNT_CCY CRNCY,
               ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       C.SHORT_NAME_1 PARTY,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_FWD_RATE spot_RATE,
                 CASE
            WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
         END AS CROSS_RATE,
         CASE
            WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
         END AS USD_EQUIVALENT,
        --       B.CCY_ONE_AMOUNT_CCY OTHERCCY,
               'X' DESCRIPTION,
               'X' BILLID
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C, TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        and A.deal_num=tut.deal_number
        and tut.foracid = gam.foracid
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        AND A.DEAL_DATE = TRUNC(SYSDATE)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLOU'
        AND B.CCY_ONE_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'B'
        --AND GAM.SCHM_TYPE != 'OAB'
        
        UNION ALL
        
        select 
        tut.tran_id TRANS_REF_CODE,
        --gam.foracid,
        gam.ACCT_NAME,
        --gam.cif_id,
        cvm.LOCALE_VALUE,
        A.DEAL_DATE,
        B.CCY_TWO_AMOUNT_CCY CRNCY,
               ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       C.SHORT_NAME_1 PARTY,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_FWD_RATE spot_RATE,
                 CASE
            WHEN TRIM(CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate(  TRIM(CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
         END AS CROSS_RATE,
         CASE
            WHEN CCY_TWO_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
         END AS USD_EQUIVALENT,
        --       B.CCY_ONE_AMOUNT_CCY OTHERCCY,
               'X' DESCRIPTION,
               'X' BILLID
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C, TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        and A.deal_num=tut.deal_number
        and tut.foracid = gam.foracid
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        AND A.DEAL_DATE = TRUNC(SYSDATE)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='NELG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLSW'
        AND B.CCY_TWO_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'S'
        --AND GAM.SCHM_TYPE != 'OAB'
        
        UNION ALL
        
        select 
        tut.tran_id TRANS_REF_CODE,
        --gam.foracid,
        gam.ACCT_NAME,
        --gam.cif_id,
        cvm.LOCALE_VALUE,
        A.DEAL_DATE,
        B.CCY_ONE_AMOUNT_CCY CRNCY,
               ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       C.SHORT_NAME_1 PARTY,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_FWD_RATE spot_RATE,
                 CASE
            WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
         END AS CROSS_RATE,
         CASE
            WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
         END AS USD_EQUIVALENT,
        --       B.CCY_TWO_AMOUNT_CCY OTHERCCY,
               'X' DESCRIPTION,
               'X' BILLID
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C, TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        and A.deal_num=tut.deal_number
        and tut.foracid = gam.foracid
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        AND A.DEAL_DATE = TRUNC(SYSDATE)
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

            String checkSql = "SELECT TRANSREFCODE || '_' || TIMESTAMP AS key FROM custom.forex WHERE FOREXTYPE = 'R'";
            try (PreparedStatement ps = conn.prepareStatement(checkSql);
                 ResultSet rsCheck = ps.executeQuery()) {

                while (rsCheck.next()) {
                    existingKeys.add(rsCheck.getString("key"));
                }
            }

            // ================= INSERT PREP =================
            String insertSql = """
        INSERT INTO custom.forex (
            TRANSREFCODE, CUSTOMERNAME, CURRENCY, AMOUNT, TIMESTAMP,
            SPOTEXCHANGERATE, CROSSRATE, USDEQUIVALENT, SECTORCODE, FOREXTYPE, POSTED_FLAG
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'R', 'N')
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

                    psInsert.addBatch();
                    insertedCount++;

                    if (insertedCount % 500 == 0) {
                        psInsert.executeBatch();
                    }

                    existingKeys.add(key);
                }

                psInsert.executeBatch();
            }

            System.out.println("TOTAL RECEIPTS FETCHED: " + fetchedCount);

            if (insertedCount == 0) {
                log.info("No new receipts to insert.");
            } else {
                log.info("Inserted {} receipts successfully.", insertedCount);
            }

        } catch (Exception e) {
            log.error("Error saving receipts into custom.forex", e);
        }
    }}
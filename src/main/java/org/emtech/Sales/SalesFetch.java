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

    @Scheduled(fixedDelay = 25000)
    public void fetchAndSaveSales() {

        String selectQuery = """
        
                select dtd.tran_id TRANS_REF_CODE,
        dtd.TRAN_DATE TIMESTAMP,
        gam.acct_name CUSTOMER_NAME,
          CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
            WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
            WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
            WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
            WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
            WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
            WHEN 'FOREIGN TRADE'                                      THEN 'PTO'
            WHEN 'MANUFACTURING'                                      THEN 'PG06'
            WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
            WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
            WHEN 'REAL ESTATE'                                        THEN 'PS07'
            WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
            WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
            WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
            ELSE 'PS07'
        END AS SECTOR,
          gam.acct_crncy_code CURRENCY,
          pst.CRNCY_SOLD,
          pst.buy_or_sell,
        --pst.AMT_HOME_CRNCY,
        dtd.tran_amt AMOUNT,
        dtd.rate as SPOT_EXCHANGE_RATE,
        CASE
                WHEN gam.acct_crncy_code = 'KES' and  pst.CRNCY_SOLD <> 'KES'
                or gam.acct_crncy_code <> 'KES' and  pst.CRNCY_SOLD = 'KES'
                THEN
                    'K2'   -- K category (KES involved) – refine to K1/K2/K3 later
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
          CASE
            WHEN TRIM(gam.ACCT_CRNCY_CODE) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(gam.ACCT_CRNCY_CODE), 'USD',  'CBK',dtd.TRAN_DATE )
        END AS CROSS_RATE,
        CASE
            WHEN gam.ACCT_CRNCY_CODE = 'USD'
                 THEN dtd.TRAN_AMT
            ELSE ROUND(  dtd.TRAN_AMT * custom.GetConvRate(  TRIM(gam.ACCT_CRNCY_CODE),  'USD', 'CBK', dtd.TRAN_DATE ), 2)
        END AS USD_EQUIVALENT
        from tbaadm.dtd, tbaadm.pst,tbaadm.gam ,CRMUSER.ACCOUNTS crm,tbaadm.cvm
        where dtd.tran_id = pst.tran_id
        and  dtd.acid = gam.acid
        and dtd.TRAN_DATE=pst.TRAN_DATE
        and dtd.PART_TRAN_SRL_NUM = pst.PART_TRAN_SRL_NUM
        and gam.cif_id = crm.orgkey
        and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
        and  BUY_OR_SELL in ('S')
        and dtd.tran_date = trunc(sysdate)
        
        UNION ALL
        SELECT 
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,
        B.SHORT_NAME_1 CUSTOMER_NAME,
        'PS07' SECTOR,
        A.CCY_TWO_AMOUNT_CCY CURRENCY,
        A.CCY_ONE_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
                 ABS(A.CCY_TWO_AMOUNT_VALUE) AMT,
                 A.CONTRACT_RATE spotrate,
                 CASE
                WHEN A.CCY_TWO_AMOUNT_CCY = 'KES' and  A.CCY_ONE_AMOUNT_CCY <> 'KES'
                or A.CCY_TWO_AMOUNT_CCY <> 'KES' and  A.CCY_ONE_AMOUNT_CCY = 'KES'
                THEN
                    'K2'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   A.CCY_TWO_AMOUNT_CCY <> 'KES' and  A.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
        
            CASE
            WHEN TRIM(A.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(A.CCY_TWO_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        CASE
            WHEN A.CCY_TWO_AMOUNT_CCY = 'USD'
                 THEN ABS(A.CCY_TWO_AMOUNT_VALUE)
            ELSE ROUND(  ABS(A.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(A.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)
        END AS USD_EQUIVALENT
        
        FROM    VCBPRD.TT_FX@FCFTLINK A,VCBPRD.SD_CPTY@FCFTLINK B
        WHERE   A.CPTY_FBO_ID_NUM = B.FBO_ID_NUM
        AND     DEAL_DATE  = trunc(sysdate)
        AND     DEAL_STATE NOT IN ('DLTD')
        AND     A.CCY_TWO_AMOUNT_CCY <> 'KES'
        AND     A.DEAL_NUM < 8000000 AND A.DEAL_NUM > 0
        AND     A.DEAL_TYPE LIKE 'FX%'
        AND     A.ACCOUNTING_CODE = 'INTBNK'
        AND     A.CCY_TWO_AMOUNT_VALUE < 0
        AND     A.DEAL_TYPE != 'FXOUTS'
        
        UNION ALL
        
        SELECT
           to_char(a.deal_num) TRANS_REF_CODE,
          a.DEAL_DATE TIMESTAMP,
          B.SHORT_NAME_1 CUSTOMER_NAME,
        'PS07' SECTOR,
        
          A.CCY_TWO_SWAP_AMOUNT_CCY CURRENCY,
          A.CCY_ONE_SWAP_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
                 ABS(A.CCY_TWO_SWAP_AMOUNT_VALUE) AMT,
                 A.CONTRACT_RATE spotrate,
                         CASE
                WHEN A.CCY_TWO_SWAP_AMOUNT_CCY = 'KES' and  A.CCY_ONE_SWAP_AMOUNT_CCY <> 'KES'
                or A.CCY_TWO_SWAP_AMOUNT_CCY <> 'KES' and  A.CCY_ONE_SWAP_AMOUNT_CCY = 'KES'
                THEN
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   A.CCY_TWO_SWAP_AMOUNT_CCY <> 'KES' and  A.CCY_ONE_SWAP_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE
            WHEN TRIM(A.CCY_TWO_SWAP_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(A.CCY_TWO_SWAP_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        CASE
            WHEN A.CCY_TWO_SWAP_AMOUNT_CCY = 'USD'
                 THEN ABS(A.CCY_TWO_SWAP_AMOUNT_VALUE)
            ELSE ROUND(  ABS(A.CCY_TWO_SWAP_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(A.CCY_TWO_SWAP_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)
        END AS USD_EQUIVALENT
        
        
        FROM    VCBPRD.TT_FX@FCFTLINK A,VCBPRD.SD_CPTY@FCFTLINK B
        WHERE   A.CPTY_FBO_ID_NUM = B.FBO_ID_NUM
        AND     DEAL_DATE  = trunc(sysdate)
        AND     DEAL_STATE NOT IN ('DLTD')
        AND     A.CCY_TWO_SWAP_AMOUNT_CCY <> 'KES'
        AND     A.DEAL_NUM < 8000000 AND A.DEAL_NUM > 0
        AND     A.DEAL_TYPE LIKE 'FX%'
        AND     A.ACCOUNTING_CODE = 'INTBNK'
        AND     A.CCY_TWO_SWAP_AMOUNT_VALUE < 0
        AND     A.DEAL_TYPE = 'FXOUTS'
        
        UNION ALL
        
          SELECT
            to_char(a.deal_num) TRANS_REF_CODE,
           a.DEAL_DATE TIMESTAMP,
           B.SHORT_NAME_1 CUSTOMER_NAME,
        'PS07' SECTOR,
        
        
        
           A.CCY_ONE_AMOUNT_CCY CURRENCY,
            A.CCY_TWO_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
                 ABS(A.CCY_ONE_AMOUNT_VALUE) AMT,
        --         ' ' PURPOSE,
        --         'O' INOUT,
        --         'T' RTYPE,
                 A.CONTRACT_RATE spotrate,
                         CASE
                WHEN A.CCY_ONE_AMOUNT_CCY = 'KES' and  A.CCY_TWO_AMOUNT_CCY <> 'KES'
                or A.CCY_ONE_AMOUNT_CCY <> 'KES' and  A.CCY_TWO_AMOUNT_CCY = 'KES'
                  THEN
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   A.CCY_TWO_AMOUNT_CCY <> 'KES' and  A.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE
            WHEN TRIM(A.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(A.CCY_ONE_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        CASE
            WHEN A.CCY_ONE_AMOUNT_CCY = 'USD'
                 THEN ABS(A.CCY_ONE_AMOUNT_VALUE)
            ELSE ROUND(  ABS(A.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(A.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)
        END AS USD_EQUIVALENT
        
        FROM    VCBPRD.TT_FX@FCFTLINK A,VCBPRD.SD_CPTY@FCFTLINK B
        WHERE   A.CPTY_FBO_ID_NUM = B.FBO_ID_NUM
        AND     DEAL_DATE  = trunc(sysdate)
        AND     DEAL_STATE NOT IN ('DLTD')
        AND     A.CCY_ONE_AMOUNT_CCY <> 'KES'
        AND     A.DEAL_NUM < 8000000 AND A.DEAL_NUM > 0
        AND     A.DEAL_TYPE LIKE 'FX%'
        AND     A.ACCOUNTING_CODE = 'INTBNK'
        AND     A.CCY_ONE_AMOUNT_VALUE < 0
        AND     A.DEAL_TYPE != 'FXOUTS'
        
        union all
        select
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'PS07' SECTOR,
        
        B.CCY_ONE_AMOUNT_CCY CURRENCY,
        B.CCY_TWO_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
               ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
        
               B.MARKET_SPOT_RATE spotrate,
                       CASE
                WHEN B.CCY_ONE_AMOUNT_CCY = 'KES' and  B.CCY_TWO_AMOUNT_CCY <> 'KES'
                or B.CCY_ONE_AMOUNT_CCY <> 'KES' and  B.CCY_TWO_AMOUNT_CCY = 'KES'
                  THEN
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE
            WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_ONE_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        CASE
            WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)
        END AS USD_EQUIVALENT
        
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE   = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLSP'
        AND B.CCY_ONE_AMOUNT_CCY <>'KES'
        AND FX_BUY_SELL = 'S'
        
        UNION ALL
        select
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'PS07' SECTOR,
        
        B.CCY_TWO_AMOUNT_CCY CURRENCY,
        B.CCY_ONE_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
               ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_SPOT_RATE spotrate,
                       CASE
                WHEN B.CCY_TWO_AMOUNT_CCY = 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                or B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY = 'KES'
                  THEN
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE
            WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_TWO_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        CASE
            WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)
        END AS USD_EQUIVALENT
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE  = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLSP'
        AND B.CCY_TWO_AMOUNT_CCY <>'KES'
        AND FX_BUY_SELL = 'B'
        UNION ALL
        
        select
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'PS07' SECTOR,
        B.CCY_ONE_AMOUNT_CCY CURRENCY,
        B.CCY_TWO_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
               ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
               B.MARKET_FWD_RATE spotrate,
                       CASE
                WHEN B.CCY_ONE_AMOUNT_CCY = 'KES' and  B.CCY_TWO_AMOUNT_CCY <> 'KES'
                or B.CCY_ONE_AMOUNT_CCY <> 'KES' and  B.CCY_TWO_AMOUNT_CCY = 'KES'
                  THEN
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE
            WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_ONE_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        CASE
            WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)
        END AS USD_EQUIVALENT
        
        
        
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE  = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLOU'
        AND B.CCY_ONE_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'S'
        
        UNION ALL
        select
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'PS07' SECTOR,
        
        
        
        B.CCY_TWO_AMOUNT_CCY CURRENCY,
        B.CCY_one_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
               ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
        
               B.MARKET_FWD_RATE spotrate,
        
                       CASE
                WHEN B.CCY_TWO_AMOUNT_CCY = 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                or B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY = 'KES'
                  THEN
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE
            WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_TWO_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        
        
        CASE
            WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)
        END AS USD_EQUIVALENT
        
        
        
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE  = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLOU'
        AND B.CCY_TWO_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'B'
        
        UNION ALL
        select
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'PS07' SECTOR,
        B.CCY_ONE_AMOUNT_CCY CURRENCY,
        B.CCY_TWO_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
        
        
               ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
        --       ' ' PURPOSE,
        --       'O' INOUT,
        --       'T' RTYPE,
               B.MARKET_FWD_RATE SPOTRATE,
                       CASE
                WHEN B.CCY_ONE_AMOUNT_CCY = 'KES' and  B.CCY_TWO_AMOUNT_CCY <> 'KES'
                or B.CCY_ONE_AMOUNT_CCY <> 'KES' and  B.CCY_TWO_AMOUNT_CCY = 'KES'
                  THEN
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE
            WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_ONE_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        
        CASE
            WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)
        END AS USD_EQUIVALENT
        
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE  = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='NELG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLSW'
        AND B.CCY_ONE_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'S'
        
        UNION ALL
        
        select
          to_char(a.deal_num) TRANS_REF_CODE,
        a.DEAL_DATE TIMESTAMP,
        C.SHORT_NAME_1 CUSTOMER_NAME,
        'PS07' SECTOR,
        
        B.CCY_TWO_AMOUNT_CCY CURRENCY,
        B.CCY_one_AMOUNT_CCY OTHERCCY,
        FX_BUY_SELL,
        
               ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
               B.MARKET_FWD_RATE SPOTRATE,
                       CASE
                WHEN B.CCY_TWO_AMOUNT_CCY = 'KES' and  B.CCY_one_AMOUNT_CCY <> 'KES'
                or B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_one_AMOUNT_CCY = 'KES'
                  THEN
                    'K1'   -- K category (KES involved) – refine to K1/K2/K3 later
                 WHEN   B.CCY_TWO_AMOUNT_CCY <> 'KES' and  B.CCY_ONE_AMOUNT_CCY <> 'KES'
                 THEN
                    'N1'
                ELSE
                    'N2'   -- N category (no KES)
            END AS INTERBANK_CODES,
            CASE
            WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
            ELSE custom.GetConvRate( TRIM(B.CCY_TWO_AMOUNT_CCY), 'USD',  'CBK',DEAL_DATE )
        END AS CROSS_RATE,
        
        CASE
            WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'
                 THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
            ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK', DEAL_DATE ), 2)
        END AS USD_EQUIVALENT
        from VCBPRD.TT_FXP_DEAL@FCFTLINK A, VCBPRD.TT_FXP_LEG@FCFTLINK B, VCBPRD.SD_CPTY@FCFTLINK C
        where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
        AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
        AND A.DEAL_DATE  = trunc(sysdate)
        AND A.ACCOUNTING_CODE='INTBNK'
        AND A.DEAL_STATE NOT IN ('DLTD')
        AND B.LEG_IDENTIFIER='NELG' and B.parent_fbo_id_ver=999999
        AND A.DEAL_TYPE='FXPLSW'
        AND B.CCY_TWO_AMOUNT_CCY<>'KES'
        AND FX_BUY_SELL = 'B'
        """;

        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectQuery);
             ResultSet rs = selectStmt.executeQuery()) {

            // ================= EXISTING KEYS =================
            Set<String> existingKeys = new HashSet<>();

            String checkSql = "SELECT TRANSREFCODE || '_' || TIMESTAMP AS key FROM custom.forex_sales WHERE FOREXTYPE = 'S'";
            try (PreparedStatement ps = conn.prepareStatement(checkSql);
                 ResultSet rsCheck = ps.executeQuery()) {

                while (rsCheck.next()) {
                    existingKeys.add(rsCheck.getString("key"));
                }
            }

            // ================= INSERT PREP =================
            String insertSql = """
        INSERT INTO custom.forex_sales (
            TRANSREFCODE, CUSTOMERNAME, CURRENCY, AMOUNT, TIMESTAMP,
            SPOTEXCHANGERATE, CROSSRATE, USDEQUIVALENT,
            SECTORCODE, INTERBANK_CODES, FOREXTYPE, POSTED_FLAG
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'S', 'N')
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

            System.out.println("TOTAL SALES FETCHED: " + fetchedCount);

            if (insertedCount == 0) {
                log.info("No new sales to insert.");
            } else {
                log.info("Inserted {} sales successfully.", insertedCount);
            }

        } catch (Exception e) {
            log.error("Error saving Sales into custom.forex_sales", e);
        }
    }
}


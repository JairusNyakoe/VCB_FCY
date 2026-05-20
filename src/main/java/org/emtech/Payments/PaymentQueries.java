package org.emtech.Payments;

public class PaymentQueries {

    private PaymentQueries() {}

    public static final String FETCH_PAYMENTS_SQL = """
            SELECT 
                            CHD.tran_id TRANS_REF_CODE,
                             GAM.ACCT_NAME CUSTOMER_NAME,
                              CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                     REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                              CGM.RCRE_TIME  TIMESTAMP,
                            TRIM(CGM.COLLECTION_CRNCY) CRNCY,
                                     CGM.COLLECTION_AMT AMT,
                                     --TRIM(CGM.PURPOSE_OF_REM) PURPOSE,
                                   --  CGM.OPER_ACID PARTY,
                                  --   CPM.IN_OUT_IND INOUT,
                                  --   'R' RTYPE,
                                  CHD.EVENT_RATE Spot_rate,
                                   --  0 CROSSRATE,
                                    CASE
                                WHEN TRIM(CGM.COLLECTION_CRNCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate( TRIM(CGM.COLLECTION_CRNCY), 'USD',  'CBK',CGM.LODG_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN CGM.COLLECTION_CRNCY = 'USD'
                                     THEN CGM.COLLECTION_AMT
                                ELSE ROUND(  CGM.COLLECTION_AMT * custom.GetConvRate(  TRIM(CGM.COLLECTION_CRNCY),  'USD', 'CBK', CGM.LODG_DATE ), 2)
                             END AS USD_EQUIVALENT,
                                   --  'X' OTHERCCY,
            
                                     CGM.OTHER_PARTY_NAME DESCRIPTION,
                                     CGM.COLLECTION_ID BILLID
                             FROM    TBAADM.CGM,TBAADM.CPM,TBAADM.CHD,TBAADM.CVM,CRMUSER.ACCOUNTS CRM,TBAADM.GAM
                             WHERE   CGM.COLLECTION_CODE = CPM.COLL_CODE
                             and CGM.collection_id=CHD.collection_id
                            and CGM.oper_acid=GAM.acid
                            and GAM.cif_id = CRM.orgkey
                            and CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE
                            and  CHD.tran_id is not null
                             AND     CGM.TRAN_SOL_ID IN (SELECT SOL_ID FROM TBAADM.SST WHERE SST.SET_ID = 'ALL')
                             AND     CGM.LODG_DATE = trunc(sysdate)
                             AND     CGM.DEL_FLG = 'N'
                             AND     CGM.ENTITY_CRE_FLG = 'Y'
                             and     gam.cif_id is not null
                             AND     CGM.COLLECTION_CRNCY <> ALL ('KES')
                              AND     CGM.COLLECTION_CODE != 'OUTMULTICC'
                             AND     CPM.IN_OUT_IND = 'O'
                             AND     NVL(TRIM(CGM.PURPOSE_OF_REM),' ') <> 'RTGS'
                            -------------------------------------------------------------------------
                             UNION ALL
            
                             SELECT 
            
                             CHD.tran_id TRANS_REF_CODE,
                             GAM.ACCT_NAME CUSTOMER_NAME,
                              CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(( SELECT MAX(CVM.LOCALE_VALUE) FROM CRMUSER.ACCOUNTS ACC JOIN TBAADM.CVM CVM ON ACC.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE WHERE ACC.ORGKEY = GAM.CIF_ID),'&','AND')AS SECTORDESCRIPTION,
                              CGM.RCRE_TIME TIMESTAMP,
                             TRIM(CGM.COLLECTION_CRNCY) CRNCY,
                                     CGM.COLLECTION_AMT AMT,
                                    -- TRIM(CGM.PURPOSE_OF_REM) PURPOSE,
                             --        CGM.OPER_ACID PARTY,
                              --       CPM.IN_OUT_IND INOUT,
                                --     'R' RTYPE,
                                CHD.EVENT_RATE Spot_rate,
                                 --    0 CROSSRATE,
                                         CASE
                                WHEN TRIM(CGM.COLLECTION_CRNCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate( TRIM(CGM.COLLECTION_CRNCY), 'USD',  'CBK',CGM.LODG_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN CGM.COLLECTION_CRNCY = 'USD'
                                     THEN CGM.COLLECTION_AMT
                                ELSE ROUND(  CGM.COLLECTION_AMT * custom.GetConvRate(  TRIM(CGM.COLLECTION_CRNCY),  'USD', 'CBK', CGM.LODG_DATE ), 2)
                             END AS USD_EQUIVALENT,
                                  --   'X' OTHERCCY,
                                     CGM.OTHER_PARTY_NAME DESCRIPTION,
                                     CGM.COLLECTION_ID BILLID
                             FROM    TBAADM.CGM,TBAADM.CPM,TBAADM.CHD,TBAADM.CVM,CRMUSER.ACCOUNTS CRM,TBAADM.GAM
                             WHERE   CGM.COLLECTION_CODE = CPM.COLL_CODE
                            and CGM.collection_id=CHD.collection_id
                            and CGM.oper_acid=GAM.acid
                            and GAM.cif_id = CRM.orgkey
                            and CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE
                            and  CHD.tran_id is not null
                             AND     CGM.TRAN_SOL_ID IN (SELECT SOL_ID FROM TBAADM.SST WHERE SST.SET_ID = 'ALL')
                             AND     CGM.LODG_DATE = trunc(sysdate)
                             AND     CGM.DEL_FLG = 'N'
                             and     gam.cif_id is not null
                             AND     CGM.ENTITY_CRE_FLG = 'Y'
                             AND     CGM.COLLECTION_CODE = 'OUTMULTICC'
                             AND     CPM.IN_OUT_IND = 'O'
                             AND     NVL(TRIM(CGM.PURPOSE_OF_REM),' ') <> 'RTGS'
                             and CGM.COLLECTION_CRNCY <> 'KES'
      
                             UNION ALL
            
                             SELECT 
                            (SELECT DISTINCT tran_id from tbaadm.dtd dtd where APZTB_TRANSACTION_REQUEST.BULK_REF_NO= dtd.tran_rmks and rownum=1) TRANS_REF_CODE,
                             GAM.ACCT_NAME CUSTOMER_NAME,
                            -- ( SELECT MAX(CVM.LOCALE_VALUE) FROM CRMUSER.ACCOUNTS ACC JOIN TBAADM.CVM CVM ON ACC.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE WHERE ACC.ORGKEY = GAM.CIF_ID) SECTOR,
                             CASE REPLACE(( SELECT MAX(CVM.LOCALE_VALUE) FROM CRMUSER.ACCOUNTS ACC JOIN TBAADM.CVM CVM ON ACC.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE WHERE ACC.ORGKEY = GAM.CIF_ID), '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                           REPLACE(( SELECT MAX(CVM.LOCALE_VALUE) FROM CRMUSER.ACCOUNTS ACC JOIN TBAADM.CVM CVM ON ACC.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE WHERE ACC.ORGKEY = GAM.CIF_ID),'&','AND')AS SECTORDESCRIPTION,
                              CHECKER_DT TIMESTAMP,
                                    TRN_CURRENCY CRNCY,
                                    to_number(APZTB_TRANSACTION_REQUEST.AMOUNT) AMT,
                                    --ADDL_COL29 Purpose,
                                 -- ACID,
                                -- 'O',
                                  --'R',
                              (SELECT DISTINCT rate from tbaadm.dtd dtd where APZTB_TRANSACTION_REQUEST.BULK_REF_NO= dtd.tran_rmks and rownum=1) Spot_rate,
                              --0,
                                            CASE
                                WHEN TRN_CURRENCY = 'USD' THEN 1
                                ELSE custom.GetConvRate( TRN_CURRENCY, 'USD','CBK',APZTB_TRANSACTION_REQUEST.CHECKER_DT )
                            END AS CROSS_RATE,
                            CASE
                                WHEN TRN_CURRENCY = 'USD'
                                     THEN  to_number(APZTB_TRANSACTION_REQUEST.AMOUNT)
                                ELSE ROUND( to_number(APZTB_TRANSACTION_REQUEST.AMOUNT) * custom.GetConvRate(TRN_CURRENCY,  'USD', 'CBK', APZTB_TRANSACTION_REQUEST.CHECKER_DT ), 2)
                            END AS USD_EQUIVALENT,
                            --        'X',
                                    APZTB_TRANSACTION_REQUEST.BENEFICIARY_NAME DESCRIPTION,
                                    TB_MB_TRANS_LOG.BULK_REF_NO BILLID
                            FROM VCBPROD.TB_MB_TRANS_LOG@fcixlink,TBAADM.GAM,
                            VCBPROD.APZTB_TRANSACTION_REQUEST@fcixlink
                            WHERE TB_MB_TRANS_LOG.TRANSACTION_TYPE IN ('INTTRANSFER')
                            AND DESCRIPTION = 'AUTHORIZE'
                            AND APZTB_TRANSACTION_REQUEST.FROM_ACC = FORACID
                            AND ERROR_MSG = 'success'
                            AND TRN_CURRENCY != 'KES'
                            --AND SOL.SOL_ID = '001'
                            and TB_MB_TRANS_LOG.BULK_REF_NO = APZTB_TRANSACTION_REQUEST.BULK_REF_NO
                            --AND SOL_CLS_DATE = TRUNC(APZTB_TRANSACTION_REQUEST.CHECKER_DT)
                            And TRUNC(APZTB_TRANSACTION_REQUEST.CHECKER_DT)= trunc(sysdate)
                            and APZTB_TRANSACTION_REQUEST.BULK_REF_NO IN (SELECT DISTINCT tran_rmks from tbaadm.dtd DTD where APZTB_TRANSACTION_REQUEST.BULK_REF_NO= dtd.tran_rmks )
            
                            UNION ALL
            
                             SELECT 
                                 fae.TRAN_ID TRANS_REF_CODE,
                            --    gam.foracid,
                                gam.ACCT_NAME CUSTOMER_NAME,
                            --    gam.cif_id,
                                CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                                 FAE.LCHG_TIME  TIMESTAMP,
                                   --FBM.BILL_ID,
                                    TRIM(FBM.BILL_CRNCY_CODE) CRNCY,
                                   -- FBM.BILL_AMT AMT,
                                    (select SUM(EVENT_AMT) from tbaadm.fbh, TBAADM.GCT  where bill_id=fbm.bill_id
                                    and bill_func='R'
                                    and ENTITY_CRE_FLG='Y' AND FBH.DEL_FLG='N'  AND  VFD_BOD_DATE= trunc(sysdate)
                                    --AND FBH.DUE_DATE<=DB_STAT_DATE
                                    ) AMT,
                                    fbm.NOTL_CONV_RATE spot_rate,
                                   -- TRIM(FBM.PURPOSE_OF_REM) PURPOSE,
                                   -- OPER_ACID,
                                   -- FBM.PARTY_NAME PARTY,
                                    --FBPT.INWARD_OUTWARD_IND INOUT,
                                    --'B' RTYPE,
                                    --10 CROSSRATE,
                                      CASE
                                WHEN TRIM(FBM.BILL_CRNCY_CODE) = 'USD' THEN 1
                                ELSE custom.GetConvRate( TRIM(FBM.BILL_CRNCY_CODE), 'USD',  'CBK',fbm.DATE_OF_REMIT )
                             END AS CROSS_RATE,
                             CASE
                                WHEN FBM.BILL_CRNCY_CODE = 'USD'
                                     THEN FBM.BILL_AMT
                                ELSE ROUND(  FBM.BILL_AMT * custom.GetConvRate(  TRIM(FBM.BILL_CRNCY_CODE),  'USD', 'CBK',  fbm.DATE_OF_REMIT ), 2)
                             END AS USD_EQUIVALENT,
                                    --'X' OTHERCCY,
                                    FBM.OTHER_PARTY_NAME DESCRIPTION,
                                    FBM.BILL_ID BILLID
                             FROM   TBAADM.FBM,TBAADM.FBPT,TBAADM.GAM,TBAADM.FAE, CRMUSER.ACCOUNTS crm,TBAADM.CVM
                             WHERE  FBM.BILL_PARAM_TYPE = FBPT.BILL_PARAM_TYPE
                             AND FBM.BILL_ID in(select bill_id from tbaadm.fbh, TBAADM.GCT where bill_id=fbm.bill_id
                             and bill_func='R'
                             and ENTITY_CRE_FLG='Y' AND  FBH.DEL_FLG='N'
                             and fae.tran_date= trunc(sysdate)
                            -- AND FBH.DUE_DATE<=DB_STAT_DATE
                             )
                            AND GAM.cif_id = CRM.orgkey
                            AND CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE
                            AND FBM.OPER_ACID = GAM.ACID
                            AND FBM.BILL_ID = FAE.BILL_ID
                            and     gam.cif_id is not null
                            --and FBM.BILL_B2K_ID in(select B2K_ID from tbaadm.smh where B2K_ID=fbm.BILL_B2K_ID and MT_NO=202)
                             AND    FBM.SOL_ID IN (SELECT SOL_ID FROM TBAADM.SST WHERE SST.SET_ID =  'ALL')
                             AND    FBM.DEL_FLG = 'N'
                             AND    FBM.ENTITY_CRE_FLG = 'Y'
                             AND    FBM.BILL_CRNCY_CODE <> 'KES'
                             AND    FBPT.INWARD_OUTWARD_IND = 'O'
                             AND    NVL(TRIM(FBM.PURPOSE_OF_REM),' ') <> 'RTGS'
                             AND    FBM.BILL_FUNC_CODE = 'R'
                            and BILL_CNTRY_CODE !='KE'
                            --AND FBPT.BILL_TENOR !='S'
                            AND (FBPT.BILL_TENOR !='S' or  (FBPT.BILL_TENOR ='S' and FBM.BILL_FUNC_CODE = 'R'))
            
                             UNION ALL
            
                             SELECT
                               tut.tran_id TRANS_REF_CODE,
                            --gam.foracid,
                            gam.ACCT_NAME CUSTOMER_NAME,
                            --gam.cif_id,
                            CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                            A.CAPTURE_TIMESTAMP TIMESTAMP,
                             A.CCY_TWO_AMOUNT_CCY CRNCY,
                                     ABS(A.CCY_TWO_AMOUNT_VALUE) AMT,
                                     --' ' PURPOSE,
                                    -- B.SHORT_NAME_1 PARTY,
                                     --'O' INOUT,
                                     --'T' RTYPE,
                                     A.CONTRACT_RATE Spot_rate,
                                    -- A.CCY_ONE_AMOUNT_CCY OTHERCCY,
                                                      CASE
                                WHEN TRIM(A.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate(  TRIM(A.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN A.CCY_TWO_AMOUNT_CCY = 'USD'
                                     THEN ABS(A.CCY_TWO_AMOUNT_VALUE)
                                ELSE ROUND(  ABS(A.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(A.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
                             END AS USD_EQUIVALENT,
                                     'X' DESCRIPTION,
                                     'X' BILLID
                             FROM    vcbprd.TT_FX@FCFTLINK A,vcbprd.SD_CPTY@FCFTLINK B ,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
                             WHERE   A.CPTY_FBO_ID_NUM = B.FBO_ID_NUM
                             and A.deal_num=tut.deal_number
                            and tut.foracid = gam.foracid
                            and gam.cif_id = crm.orgkey
                            and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
                             AND     DEAL_DATE = trunc(sysdate)
                             AND     DEAL_STATE NOT IN ('DLTD')
                             AND     A.CCY_TWO_AMOUNT_CCY <> 'KES'
                             AND     A.DEAL_NUM < 8000000 AND A.DEAL_NUM > 0
                             AND     A.DEAL_TYPE LIKE 'FX%'
                             and     gam.cif_id is not null
                             AND     A.ACCOUNTING_CODE = 'INTBNK'
                             AND     A.CCY_TWO_AMOUNT_VALUE < 0
                             AND     A.DEAL_TYPE != 'FXOUTS'
            
                             UNION ALL
            
                             SELECT
                               tut.tran_id TRANS_REF_CODE,
                            --gam.foracid,
                            gam.ACCT_NAME CUSTOMER_NAME,
                            --gam.cif_id,
                            CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                            A.CAPTURE_TIMESTAMP TIMESTAMP,
                             A.CCY_TWO_SWAP_AMOUNT_CCY CRNCY,
                                     ABS(A.CCY_TWO_SWAP_AMOUNT_VALUE) AMT,
                                     --' ' PURPOSE,
                                     --B.SHORT_NAME_1 PARTY,
                                     --'O' INOUT,
                                     --'T' RTYPE,
                                     A.CONTRACT_RATE Spot_rate,
                                                       CASE
                                WHEN TRIM( A.CCY_TWO_SWAP_AMOUNT_CCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate(  TRIM( A.CCY_TWO_SWAP_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN A.CCY_ONE_AMOUNT_CCY = 'USD'
                                     THEN ABS(A.CCY_TWO_SWAP_AMOUNT_VALUE)
                                ELSE ROUND(  ABS(A.CCY_TWO_SWAP_AMOUNT_VALUE) * custom.GetConvRate(  TRIM( A.CCY_TWO_SWAP_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
                             END AS USD_EQUIVALENT,
                                    -- A.CCY_ONE_SWAP_AMOUNT_CCY OTHERCCY,
                                     'X' DESCRIPTION,
                                     'X' BILLID
                             FROM    vcbprd.TT_FX@FCFTLINK A,vcbprd.SD_CPTY@FCFTLINK B,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
                             WHERE   A.CPTY_FBO_ID_NUM = B.FBO_ID_NUM
                             and A.deal_num=tut.deal_number
                            and tut.foracid = gam.foracid
                            and gam.cif_id = crm.orgkey
                            and     gam.cif_id is not null
                            and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
                             AND     DEAL_DATE = trunc(sysdate)
                             AND     DEAL_STATE NOT IN ('DLTD')
                             AND     A.CCY_TWO_SWAP_AMOUNT_CCY <> 'KES'
                             AND     A.DEAL_NUM < 8000000 AND A.DEAL_NUM > 0
                             AND     A.DEAL_TYPE LIKE 'FX%'
                             AND     A.ACCOUNTING_CODE = 'INTBNK'
                             AND     A.CCY_TWO_SWAP_AMOUNT_VALUE < 0
                             AND     A.DEAL_TYPE = 'FXOUTS'
            
                             UNION ALL
            
                            SELECT
                              tut.tran_id TRANS_REF_CODE,
                            --gam.foracid,
                            gam.ACCT_NAME CUSTOMER_NAME,
                            --gam.cif_id,
                            CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                            A.CAPTURE_TIMESTAMP TIMESTAMP,
                              A.CCY_ONE_AMOUNT_CCY CRNCY,
                                     ABS(A.CCY_ONE_AMOUNT_VALUE) AMT,
                                     --' ' PURPOSE,
                                     --B.SHORT_NAME_1 PARTY,
                                     --'O' INOUT,
                                     --'T' RTYPE,
                                     A.CONTRACT_RATE Spot_rate,
                                     --A.CCY_TWO_AMOUNT_CCY OTHERCCY,
                                              CASE
                                WHEN TRIM(A.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate(  TRIM(A.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN A.CCY_ONE_AMOUNT_CCY = 'USD'
                                     THEN ABS(A.CCY_ONE_AMOUNT_VALUE)
                                ELSE ROUND(  ABS(A.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(A.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
                             END AS USD_EQUIVALENT,
                                     'X' DESCRIPTION,
                                     'X' BILLID
                             FROM    vcbprd.TT_FX@FCFTLINK A,vcbprd.SD_CPTY@FCFTLINK B,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
                             WHERE   A.CPTY_FBO_ID_NUM = B.FBO_ID_NUM
                             and A.deal_num=tut.deal_number
                            and tut.foracid = gam.foracid
                            and gam.cif_id = crm.orgkey
                            and     gam.cif_id is not null
                            and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
                             AND     DEAL_DATE = trunc(sysdate)
                             AND     DEAL_STATE NOT IN ('DLTD')
                             AND     A.CCY_ONE_AMOUNT_CCY <> 'KES'
                             AND     A.DEAL_NUM < 8000000 AND A.DEAL_NUM > 0
                             AND     A.DEAL_TYPE LIKE 'FX%'
                             AND     A.ACCOUNTING_CODE = 'INTBNK'
                             AND     A.CCY_ONE_AMOUNT_VALUE < 0
                             AND     A.DEAL_TYPE != 'FXOUTS'
       
            
                             UNION ALL
            
                             SELECT
                                   CHD.tran_id TRANS_REF_CODE,
                                  GAM.ACCT_NAME CUSTOMER_NAME,
                                  CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                                  CGM.RCRE_TIME TIMESTAMP,
                                    TRN_CURRENCY CRNCY,
                                    to_number(APZTB_TRANSACTION_REQUEST.AMOUNT) AMT,
                                    --ADDL_COL29 PURPOSE,
                                    --ACID,
                                    --'O',
                                     CHD.EVENT_RATE Spot_rate,
                                    --'E',
                                   -- TO_NUMBER(APZTB_TRANSACTION_REQUEST.ADDL_COL6),
                                             CASE
                                WHEN TRIM(CGM.COLLECTION_CRNCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate( TRIM(CGM.COLLECTION_CRNCY), 'USD','CBK',CGM.LODG_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN CGM.COLLECTION_CRNCY = 'USD'
                                     THEN CGM.COLLECTION_AMT
                                ELSE ROUND(CGM.COLLECTION_AMT * custom.GetConvRate(TRIM(CGM.COLLECTION_CRNCY),  'USD', 'CBK', CGM.LODG_DATE ), 2)
                             END AS USD_EQUIVALENT,
                                    --'X',
                                    APZTB_TRANSACTION_REQUEST.BENEFICIARY_NAME DESCRIPTION,
                                    TB_MB_TRANS_LOG.BULK_REF_NO BILLID
                            FROM vcbprod.TB_MB_TRANS_LOG@fcixlink,TBAADM.GAM,CRMUSER.ACCOUNTS crm,TBAADM.CVM,TBAADM.CGM,TBAADM.CHD,
                            vcbprod.APZTB_TRANSACTION_REQUEST@fcixlink
                            WHERE TB_MB_TRANS_LOG.TRANSACTION_TYPE IN ('INTTRANSFER')
                            AND DESCRIPTION = 'AUTHORIZE'
                            AND APZTB_TRANSACTION_REQUEST.FROM_ACC = FORACID
                            AND ERROR_MSG = 'success'
                            AND TRN_CURRENCY != 'KES'
                            and CGM.collection_id=CHD.collection_id
                            and CGM.oper_acid=GAM.acid
                            and GAM.cif_id = CRM.orgkey
                            and     gam.cif_id is not null
                            and CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE
                            and  CHD.tran_id is not null
                            and gam.cif_id = APZTB_TRANSACTION_REQUEST.cif_no
                            and TB_MB_TRANS_LOG.BULK_REF_NO = APZTB_TRANSACTION_REQUEST.BULK_REF_NO
                            and APZTB_TRANSACTION_REQUEST.BULK_REF_NO = (select tran_rmks from tbaadm.dtd where APZTB_TRANSACTION_REQUEST.BULK_REF_NO= dtd.tran_rmks )
                            And TRUNC(APZTB_TRANSACTION_REQUEST.CHECKER_DT)= trunc(sysdate)
                            AND APZTB_TRANSACTION_REQUEST.ADDL_COL6 IS NOT NULL
            
                             UNION ALL
            
                            select
                            tut.tran_id TRANS_REF_CODE,
                            --gam.foracid,
                            gam.ACCT_NAME CUSTOMER_NAME,
                            --gam.cif_id,
                            CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                            A.CAPTURE_TIMESTAMP TIMESTAMP,
                            B.CCY_ONE_AMOUNT_CCY CRNCY,
                                   ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
                                 --  ' ' PURPOSE,
                                  -- C.SHORT_NAME_1 PARTY,
                                  -- 'O' INOUT,
                                  -- 'T' RTYPE,
                                   B.MARKET_SPOT_RATE Spot_rate,
                                     CASE
                                WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'
                                     THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
                                ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
                             END AS USD_EQUIVALENT,     
                                   --B.CCY_TWO_AMOUNT_CCY OTHERCCY,
                                   'X' DESCRIPTION,
                                   'X' BILLID
                            from vcbprd.TT_FXP_DEAL@FCFTLINK A, vcbprd.TT_FXP_LEG@FCFTLINK B, vcbprd.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
                            where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
                            AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
                            and A.deal_num=tut.deal_number
                            and tut.foracid = gam.foracid
                            and gam.cif_id = crm.orgkey
                            and     gam.cif_id is not null
                            and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
                            AND A.DEAL_DATE =  trunc(sysdate)
                            AND A.ACCOUNTING_CODE='INTBNK'
                            AND A.DEAL_STATE NOT IN ('DLTD')
                            AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
                            AND A.DEAL_TYPE='FXPLSP'
                            AND B.CCY_ONE_AMOUNT_CCY <>'KES'
                            AND FX_BUY_SELL = 'S'
            
                            UNION ALL
            
                            select
                            tut.tran_id TRANS_REF_CODE ,
                            --gam.foracid,
                            gam.ACCT_NAME CUSTOMER_NAME,
                            --gam.cif_id,
                            CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                            A.CAPTURE_TIMESTAMP TIMESTAMP,
                            B.CCY_TWO_AMOUNT_CCY CRNCY,
                                   ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
                                  -- ' ' PURPOSE,
                                  -- C.SHORT_NAME_1 PARTY,
                                  -- 'O' INOUT,
                                  -- 'T' RTYPE,
                                   B.MARKET_SPOT_RATE Spot_rate,
                                     CASE
                                WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'
                                     THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
                                ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
                             END AS USD_EQUIVALENT,     
                                  -- B.CCY_ONE_AMOUNT_CCY OTHERCCY,
                                   'X' DESCRIPTION,
                                   'X' BILLID
                            from vcbprd.TT_FXP_DEAL@FCFTLINK A, vcbprd.TT_FXP_LEG@FCFTLINK B, vcbprd.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
                            where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
                            AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
                            and A.deal_num=tut.deal_number
                            and tut.foracid = gam.foracid
                            and gam.cif_id = crm.orgkey
                            and     gam.cif_id is not null
                            and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
                            AND A.DEAL_DATE =  trunc(sysdate)
                            AND A.ACCOUNTING_CODE='INTBNK'
                            AND A.DEAL_STATE NOT IN ('DLTD')
                            AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
                            AND A.DEAL_TYPE='FXPLSP'
                            AND B.CCY_TWO_AMOUNT_CCY <>'KES'
                            AND FX_BUY_SELL = 'B'
                            UNION ALL
                            select
                            tut.tran_id TRANS_REF_CODE ,
                            --gam.foracid,
                            gam.ACCT_NAME CUSTOMER_NAME,
                            --gam.cif_id,
                            CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                            A.CAPTURE_TIMESTAMP TIMESTAMP,
                            B.CCY_ONE_AMOUNT_CCY CRNCY,
                                   ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
                                   --' ' PURPOSE,
                                   --C.SHORT_NAME_1 PARTY,
                                   --'O' INOUT,
                                   --'T' RTYPE,
                                   B.MARKET_FWD_RATE spot_rate,
                                            CASE
                                WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'
                                     THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
                                ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
                             END AS USD_EQUIVALENT, 
                                   --B.CCY_TWO_AMOUNT_CCY OTHERCCY,
                                   'X' DESCRIPTION,
                                   'X' BILLID
                            from vcbprd.TT_FXP_DEAL@FCFTLINK A, vcbprd.TT_FXP_LEG@FCFTLINK B, vcbprd.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
                            where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
                            AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
                            and A.deal_num=tut.deal_number
                            and tut.foracid = gam.foracid
                            and gam.cif_id = crm.orgkey
                            and     gam.cif_id is not null
                            and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
                            AND A.DEAL_DATE = trunc(sysdate)
                            AND A.ACCOUNTING_CODE='INTBNK'
                            AND A.DEAL_STATE NOT IN ('DLTD')
                            AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
                            AND A.DEAL_TYPE='FXPLOU'
                            AND B.CCY_ONE_AMOUNT_CCY<>'KES'
                            AND FX_BUY_SELL = 'S'
            
                            UNION ALL
            
                            select
                            tut.tran_id TRANS_REF_CODE ,
                            --gam.foracid,
                            gam.ACCT_NAME CUSTOMER_NAME,
                            --gam.cif_id,
                            CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                            A.CAPTURE_TIMESTAMP TIMESTAMP,
                            B.CCY_TWO_AMOUNT_CCY CRNCY,
                                   ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
                                   --' ' PURPOSE,
                                   --C.SHORT_NAME_1 PARTY,
                                   --'O' INOUT,
                                   --'T' RTYPE,
                                   B.MARKET_FWD_RATE spot_rate,
                                     CASE
                                WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'
                                     THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
                                ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
                             END AS USD_EQUIVALENT,       
                             --      B.CCY_TWO_AMOUNT_CCY OTHERCCY,
                                   'X' DESCRIPTION,
                                   'X' BILLID
                            from vcbprd.TT_FXP_DEAL@FCFTLINK A, vcbprd.TT_FXP_LEG@FCFTLINK B, vcbprd.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
                            where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
                            AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
                            and A.deal_num=tut.deal_number
                            and tut.foracid = gam.foracid
                            and gam.cif_id = crm.orgkey
                            and     gam.cif_id is not null
                            and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
                            AND A.DEAL_DATE = trunc(sysdate)
                            AND A.ACCOUNTING_CODE='INTBNK'
                            AND A.DEAL_STATE NOT IN ('DLTD')
                            AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999
                            AND A.DEAL_TYPE='FXPLOU'
                            AND B.CCY_TWO_AMOUNT_CCY<>'KES'
                            AND FX_BUY_SELL = 'B'
            
                            UNION ALL
            
                            select
                            tut.tran_id TRANS_REF_CODE ,
                            --gam.foracid,
                            gam.ACCT_NAME CUSTOMER_NAME,
                            --gam.cif_id,
                            CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                            A.CAPTURE_TIMESTAMP TIMESTAMP,
                            B.CCY_ONE_AMOUNT_CCY CRNCY,
                                   ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,
                                   --' ' PURPOSE,
                                   --C.SHORT_NAME_1 PARTY,
                                   --'O' INOUT,
                                   --'T' RTYPE,
                                   B.MARKET_FWD_RATE spot_rate,
                                            CASE
                                WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN B.CCY_ONE_AMOUNT_CCY = 'USD'
                                     THEN ABS(B.CCY_ONE_AMOUNT_VALUE)
                                ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
                             END AS USD_EQUIVALENT, 
                                   --B.CCY_TWO_AMOUNT_CCY OTHERCCY,
                                   'X' DESCRIPTION,
                                   'X' BILLID
                            from vcbprd.TT_FXP_DEAL@FCFTLINK A, vcbprd.TT_FXP_LEG@FCFTLINK B, vcbprd.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
                            where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
                            AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
                            and A.deal_num=tut.deal_number
                            and tut.foracid = gam.foracid
                            and gam.cif_id = crm.orgkey
                            and     gam.cif_id is not null
                            and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
                            AND A.DEAL_DATE = trunc(sysdate)
                            AND A.ACCOUNTING_CODE='INTBNK'
                            AND A.DEAL_STATE NOT IN ('DLTD')
                            AND B.LEG_IDENTIFIER='NELG' and B.parent_fbo_id_ver=999999
                            AND A.DEAL_TYPE='FXPLSW'
                            AND B.CCY_ONE_AMOUNT_CCY<>'KES'
                            AND FX_BUY_SELL = 'S'
            
                            UNION ALL
            
                            select
                            tut.tran_id TRANS_REF_CODE ,
                            --gam.foracid,
                            gam.ACCT_NAME CUSTOMER_NAME,
                            --gam.cif_id,
                            CASE REPLACE(cvm.LOCALE_VALUE, '&', 'AND')
                            WHEN 'AGRICULTURE, HUNTING, FISHING AND FORESTRY'        THEN 'PG01'
                    WHEN 'ANY OTHER ACTIVITIES'                               THEN 'PS07'
                    WHEN 'BUILDING AND CONSTRUCTION'                          THEN 'PG08'
                    WHEN 'BUSINESS SERVICES'                                  THEN 'PS07'
                    WHEN 'ELECTRICITY AND WATER'                              THEN 'PG11'
                    WHEN 'FINANCE AND INSURANCE'                              THEN 'PS03'
                    WHEN 'FOREIGN TRADE'                                      THEN 'PT0'
                    WHEN 'MANUFACTURING'                                      THEN 'PG06'
                    WHEN 'MINING AND QUARRYING'                               THEN 'PG03'
                    WHEN 'OTHER ENTERPRISES'                                  THEN 'PS07'
                    WHEN 'REAL ESTATE'                                        THEN 'PS07'
                    WHEN 'SOCIAL, COMMUNITY AND PERSONAL SERVICES'            THEN 'PS02'
                    WHEN 'TRANSPORT AND COMMUNICATION'                        THEN 'PS01'
                    WHEN 'WHOLESALE AND RETAIL TRADE, RESTAURANTS AND HOTELS' THEN 'PG14'
                    ELSE 'PS07'
                            END AS SECTOR,
                            REPLACE(cvm.LOCALE_VALUE,'&','AND')AS SECTORDESCRIPTION,
                            A.CAPTURE_TIMESTAMP TIMESTAMP,
                            B.CCY_TWO_AMOUNT_CCY CRNCY,
                                   ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,
                                   --' ' PURPOSE,
                                   --C.SHORT_NAME_1 PARTY,
                                   --'O' INOUT,
                                   --'T' RTYPE,
                                   B.MARKET_FWD_RATE spot_rate,
                              CASE
                                WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1
                                ELSE custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )
                             END AS CROSS_RATE,
                             CASE
                                WHEN B.CCY_TWO_AMOUNT_CCY = 'USD'
                                     THEN ABS(B.CCY_TWO_AMOUNT_VALUE)
                                ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2)
                             END AS USD_EQUIVALENT, 
                                   --B.CCY_TWO_AMOUNT_CCY OTHERCCY,
                                   'X' DESCRIPTION,
                                   'X' BILLID
                            from vcbprd.TT_FXP_DEAL@FCFTLINK A, vcbprd.TT_FXP_LEG@FCFTLINK B, vcbprd.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm
                            where A.DEAL_NUM = B.PARENT_FBO_ID_NUM
                            AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM
                            and A.deal_num=tut.deal_number
                            and tut.foracid = gam.foracid
                            and gam.cif_id = crm.orgkey
                            and     gam.cif_id is not null
                            and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE
                            AND A.DEAL_DATE = trunc(sysdate)
                            AND A.ACCOUNTING_CODE='INTBNK'
                            AND A.DEAL_STATE NOT IN ('DLTD')
                            AND B.LEG_IDENTIFIER='NELG' and B.parent_fbo_id_ver=999999
                            AND A.DEAL_TYPE='FXPLSW'
                            AND B.CCY_TWO_AMOUNT_CCY<>'KES'
                            AND FX_BUY_SELL = 'B'
        """;

    public static String getFetchPaymentsSql() {
        return FETCH_PAYMENTS_SQL;
    }
}
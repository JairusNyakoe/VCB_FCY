package org.emtech.Payments;

import lombok.extern.slf4j.Slf4j;
import org.emtech.Entity.Props;
import org.emtech.Tools.DatabaseConnection;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    @Scheduled(fixedDelay = 15000)
    public void fetchAndSavePayments() {
        try (Connection conn = databaseConnection.dbConnection()) {

            String sqlFetch = "-------------------------------New Query-------------------------------------\n" +
                    "SELECT \n" +
                    "CHD.tran_id TRANS_REF_CODE,\n" +
                    " GAM.ACCT_NAME CUSTOMER_NAME,\n" +
                    "  CVM.LOCALE_VALUE SECTOR,\n" +
                    "   CGM.LODG_DATE TIMESTAMP,\n" +
                    "TRIM(CGM.COLLECTION_CRNCY) CRNCY,\n" +
                    "         CGM.COLLECTION_AMT AMT,\n" +
                    "         --TRIM(CGM.PURPOSE_OF_REM) PURPOSE,\n" +
                    "       --  CGM.OPER_ACID PARTY,\n" +
                    "      --   CPM.IN_OUT_IND INOUT,\n" +
                    "      --   'R' RTYPE,\n" +
                    "      CHD.EVENT_RATE Spot_rate,\n" +
                    "       --  0 CROSSRATE,\n" +
                    "        CASE \n" +
                    "    WHEN TRIM(CGM.COLLECTION_CRNCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate( TRIM(CGM.COLLECTION_CRNCY), 'USD',  'CBK',CGM.LODG_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN CGM.COLLECTION_CRNCY = 'USD' \n" +
                    "         THEN CGM.COLLECTION_AMT\n" +
                    "    ELSE ROUND(  CGM.COLLECTION_AMT * custom.GetConvRate(  TRIM(CGM.COLLECTION_CRNCY),  'USD', 'CBK', CGM.LODG_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,\n" +
                    "       --  'X' OTHERCCY,\n" +
                    "       \n" +
                    "         CGM.OTHER_PARTY_NAME DESCRIPTION,\n" +
                    "         CGM.COLLECTION_ID BILLID\n" +
                    " FROM    TBAADM.CGM,TBAADM.CPM,TBAADM.CHD,TBAADM.CVM,CRMUSER.ACCOUNTS CRM,TBAADM.GAM\n" +
                    " WHERE   CGM.COLLECTION_CODE = CPM.COLL_CODE\n" +
                    " and CGM.collection_id=CHD.collection_id\n" +
                    "and CGM.oper_acid=GAM.acid\n" +
                    "and GAM.cif_id = CRM.orgkey\n" +
                    "and CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE\n" +
                    "and  CHD.tran_id is not null\n" +
                    " AND     CGM.TRAN_SOL_ID IN (SELECT SOL_ID FROM TBAADM.SST WHERE SST.SET_ID = 'ALL')\n" +
                    " AND     CGM.LODG_DATE = '28-JAN-26'\n" +
                    " AND     CGM.DEL_FLG = 'N'\n" +
                    " AND     CGM.ENTITY_CRE_FLG = 'Y'\n" +
                    " and     gam.cif_id is not null\n" +
                    " AND     CGM.COLLECTION_CRNCY <> ALL ('KES')\n" +
                    "  AND     CGM.COLLECTION_CODE != 'OUTMULTICC'\n" +
                    " AND     CPM.IN_OUT_IND = 'O'\n" +
                    " AND     NVL(TRIM(CGM.PURPOSE_OF_REM),' ') <> 'RTGS'\n" +
                    "-------------------------------------------------------------------------\n" +
                    " UNION ALL\n" +
                    "\n" +
                    " SELECT\n" +
                    " \n" +
                    " CHD.tran_id TRANS_REF_CODE,\n" +
                    " GAM.ACCT_NAME CUSTOMER_NAME,\n" +
                    "  CVM.LOCALE_VALUE SECTOR,\n" +
                    "   CGM.LODG_DATE TIMESTAMP,\n" +
                    " TRIM(CGM.COLLECTION_CRNCY) CRNCY,\n" +
                    "         CGM.COLLECTION_AMT AMT,\n" +
                    "        -- TRIM(CGM.PURPOSE_OF_REM) PURPOSE,\n" +
                    " --        CGM.OPER_ACID PARTY,\n" +
                    "  --       CPM.IN_OUT_IND INOUT,\n" +
                    "    --     'R' RTYPE,\n" +
                    "    CHD.EVENT_RATE Spot_rate,\n" +
                    "     --    0 CROSSRATE,\n" +
                    "             CASE \n" +
                    "    WHEN TRIM(CGM.COLLECTION_CRNCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate( TRIM(CGM.COLLECTION_CRNCY), 'USD',  'CBK',CGM.LODG_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN CGM.COLLECTION_CRNCY = 'USD' \n" +
                    "         THEN CGM.COLLECTION_AMT\n" +
                    "    ELSE ROUND(  CGM.COLLECTION_AMT * custom.GetConvRate(  TRIM(CGM.COLLECTION_CRNCY),  'USD', 'CBK', CGM.LODG_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,\n" +
                    "      --   'X' OTHERCCY,\n" +
                    "         CGM.OTHER_PARTY_NAME DESCRIPTION,\n" +
                    "         CGM.COLLECTION_ID BILLID\n" +
                    " FROM    TBAADM.CGM,TBAADM.CPM,TBAADM.CHD,TBAADM.CVM,CRMUSER.ACCOUNTS CRM,TBAADM.GAM\n" +
                    " WHERE   CGM.COLLECTION_CODE = CPM.COLL_CODE\n" +
                    "and CGM.collection_id=CHD.collection_id\n" +
                    "and CGM.oper_acid=GAM.acid\n" +
                    "and GAM.cif_id = CRM.orgkey\n" +
                    "and CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE\n" +
                    "and  CHD.tran_id is not null\n" +
                    " AND     CGM.TRAN_SOL_ID IN (SELECT SOL_ID FROM TBAADM.SST WHERE SST.SET_ID = 'ALL')\n" +
                    " AND     CGM.LODG_DATE = '28-JAN-26'\n" +
                    " AND     CGM.DEL_FLG = 'N'\n" +
                    " and     gam.cif_id is not null\n" +
                    " AND     CGM.ENTITY_CRE_FLG = 'Y'\n" +
                    " AND     CGM.COLLECTION_CODE = 'OUTMULTICC'\n" +
                    " AND     CPM.IN_OUT_IND = 'O'\n" +
                    " AND     NVL(TRIM(CGM.PURPOSE_OF_REM),' ') <> 'RTGS'\n" +
                    "\n" +
                    " UNION ALL\n" +
                    "\n" +
                    " SELECT \n" +
                    "  CHD.tran_id TRANS_REF_CODE,\n" +
                    " GAM.ACCT_NAME CUSTOMER_NAME,\n" +
                    "  CVM.LOCALE_VALUE SECTOR,\n" +
                    "   CGM.LODG_DATE TIMESTAMP,\n" +
                    "        TRN_CURRENCY CRNCY,\n" +
                    "        to_number(APZTB_TRANSACTION_REQUEST.AMOUNT) AMT,\n" +
                    "        --ADDL_COL29 Purpose,\n" +
                    "     -- ACID,\n" +
                    "    -- 'O',\n" +
                    "      --'R',\n" +
                    "      CHD.EVENT_RATE Spot_rate,\n" +
                    "        --0,\n" +
                    "    CASE \n" +
                    "    WHEN TRIM(CGM.COLLECTION_CRNCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate( TRIM(CGM.COLLECTION_CRNCY), 'USD','CBK',CGM.LODG_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN CGM.COLLECTION_CRNCY = 'USD' \n" +
                    "         THEN CGM.COLLECTION_AMT\n" +
                    "    ELSE ROUND(CGM.COLLECTION_AMT * custom.GetConvRate(TRIM(CGM.COLLECTION_CRNCY),  'USD', 'CBK', CGM.LODG_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,\n" +
                    "        --'X',\n" +
                    "        APZTB_TRANSACTION_REQUEST.BENEFICIARY_NAME DESCRIPTION,\n" +
                    "        TB_MB_TRANS_LOG.BULK_REF_NO BILLID\n" +
                    "FROM VCBCORP.TB_MB_TRANS_LOG@fcixlink,TBAADM.GAM,TBAADM.SOL,TBAADM.CHD,TBAADM.CGM,TBAADM.CVM,CRMUSER.ACCOUNTS CRM,\n" +
                    "VCBCORP.APZTB_TRANSACTION_REQUEST@fcixlink\n" +
                    "WHERE TB_MB_TRANS_LOG.TRANSACTION_TYPE IN ('INTTRANSFER')\n" +
                    "AND DESCRIPTION = 'AUTHORIZE'\n" +
                    "AND APZTB_TRANSACTION_REQUEST.FROM_ACC = FORACID\n" +
                    "AND ERROR_MSG = 'success'\n" +
                    "AND TRN_CURRENCY != 'KES'\n" +
                    "AND SOL.SOL_ID = '001'\n" +
                    "and CGM.collection_id=CHD.collection_id\n" +
                    "and CGM.oper_acid=GAM.acid\n" +
                    "and GAM.cif_id = CRM.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE\n" +
                    "and  CHD.tran_id is not null\n" +
                    "and gam.cif_id = APZTB_TRANSACTION_REQUEST.cif_no\n" +
                    "and TB_MB_TRANS_LOG.BULK_REF_NO = APZTB_TRANSACTION_REQUEST.BULK_REF_NO\n" +
                    "AND SOL_CLS_DATE = TRUNC(APZTB_TRANSACTION_REQUEST.CHECKER_DT)\n" +
                    "AND APZTB_TRANSACTION_REQUEST.CHECKER_DT > SOL.LCHG_TIME\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    " SELECT \n" +
                    "     fae.TRAN_ID TRANS_REF_CODE,\n" +
                    "--    gam.foracid,\n" +
                    "    gam.ACCT_NAME CUSTOMER_NAME,\n" +
                    "--    gam.cif_id,\n" +
                    "    cvm.LOCALE_VALUE SECTOR,\n" +
                    "    fae.tran_date TIMESTAMP,\n" +
                    "       --FBM.BILL_ID,\n" +
                    "        TRIM(FBM.BILL_CRNCY_CODE) CRNCY,\n" +
                    "       -- FBM.BILL_AMT AMT,\n" +
                    "        (select SUM(EVENT_AMT) from tbaadm.fbh, TBAADM.GCT  where bill_id=fbm.bill_id \n" +
                    "        and bill_func='R' \n" +
                    "        and ENTITY_CRE_FLG='Y' AND FBH.DEL_FLG='N'  AND  VFD_BOD_DATE='28-JAN-26' \n" +
                    "        --AND FBH.DUE_DATE<=DB_STAT_DATE\n" +
                    "        ) AMT,\n" +
                    "        fbm.NOTL_CONV_RATE spot_rate,\n" +
                    "       -- TRIM(FBM.PURPOSE_OF_REM) PURPOSE,\n" +
                    "       -- OPER_ACID,\n" +
                    "       -- FBM.PARTY_NAME PARTY,\n" +
                    "        --FBPT.INWARD_OUTWARD_IND INOUT,\n" +
                    "        --'B' RTYPE,\n" +
                    "        --10 CROSSRATE,\n" +
                    "          CASE \n" +
                    "    WHEN TRIM(FBM.BILL_CRNCY_CODE) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate( TRIM(FBM.BILL_CRNCY_CODE), 'USD',  'CBK',fbm.DATE_OF_REMIT )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN FBM.BILL_CRNCY_CODE = 'USD' \n" +
                    "         THEN FBM.BILL_AMT\n" +
                    "    ELSE ROUND(  FBM.BILL_AMT * custom.GetConvRate(  TRIM(FBM.BILL_CRNCY_CODE),  'USD', 'CBK',  fbm.DATE_OF_REMIT ), 2) \n" +
                    " END AS USD_EQUIVALENT,\n" +
                    "        --'X' OTHERCCY,\n" +
                    "        FBM.OTHER_PARTY_NAME DESCRIPTION,\n" +
                    "        FBM.BILL_ID BILLID\n" +
                    " FROM   TBAADM.FBM,TBAADM.FBPT,TBAADM.GAM,TBAADM.FAE, CRMUSER.ACCOUNTS crm,TBAADM.CVM\n" +
                    " WHERE  FBM.BILL_PARAM_TYPE = FBPT.BILL_PARAM_TYPE\n" +
                    " AND FBM.BILL_ID in(select bill_id from tbaadm.fbh, TBAADM.GCT where bill_id=fbm.bill_id \n" +
                    " and bill_func='R'\n" +
                    " and ENTITY_CRE_FLG='Y' AND  FBH.DEL_FLG='N' \n" +
                    " and fae.tran_date='28-JAN-26' \n" +
                    "-- AND FBH.DUE_DATE<=DB_STAT_DATE\n" +
                    " ) \n" +
                    "AND GAM.cif_id = CRM.orgkey\n" +
                    "AND CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE\n" +
                    "AND FBM.OPER_ACID = GAM.ACID\n" +
                    "AND FBM.BILL_ID = FAE.BILL_ID\n" +
                    "and     gam.cif_id is not null\n" +
                    "--and FBM.BILL_B2K_ID in(select B2K_ID from tbaadm.smh where B2K_ID=fbm.BILL_B2K_ID and MT_NO=202)\n" +
                    " AND    FBM.SOL_ID IN (SELECT SOL_ID FROM TBAADM.SST WHERE SST.SET_ID =  'ALL')\n" +
                    " AND    FBM.DEL_FLG = 'N'\n" +
                    " AND    FBM.ENTITY_CRE_FLG = 'Y'\n" +
                    " AND    FBM.BILL_CRNCY_CODE <> 'KES'\n" +
                    " AND    FBPT.INWARD_OUTWARD_IND = 'O'\n" +
                    " AND    NVL(TRIM(FBM.PURPOSE_OF_REM),' ') <> 'RTGS'\n" +
                    " AND    FBM.BILL_FUNC_CODE = 'R'\n" +
                    "and BILL_CNTRY_CODE !='KE'\n" +
                    "--AND FBPT.BILL_TENOR !='S'\n" +
                    "AND (FBPT.BILL_TENOR !='S' or  (FBPT.BILL_TENOR ='S' and FBM.BILL_FUNC_CODE = 'R'))\n" +
                    "\n" +
                    " UNION ALL\n" +
                    "\n" +
                    " SELECT \n" +
                    "   tut.tran_id TRANS_REF_CODE,\n" +
                    "--gam.foracid,\n" +
                    "gam.ACCT_NAME CUSTOMER_NAME,\n" +
                    "--gam.cif_id, \n" +
                    "cvm.LOCALE_VALUE SECTOR,\n" +
                    "A.DEAL_DATE TIMESTAMP,\n" +
                    " A.CCY_TWO_AMOUNT_CCY CRNCY,\n" +
                    "         ABS(A.CCY_TWO_AMOUNT_VALUE) AMT,\n" +
                    "         --' ' PURPOSE,\n" +
                    "        -- B.SHORT_NAME_1 PARTY,\n" +
                    "         --'O' INOUT,\n" +
                    "         --'T' RTYPE,\n" +
                    "         A.CONTRACT_RATE Spot_rate,\n" +
                    "        -- A.CCY_ONE_AMOUNT_CCY OTHERCCY,\n" +
                    "                          CASE \n" +
                    "    WHEN TRIM(A.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate(  TRIM(A.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN A.CCY_TWO_AMOUNT_CCY = 'USD' \n" +
                    "         THEN ABS(A.CCY_TWO_AMOUNT_VALUE)\n" +
                    "    ELSE ROUND(  ABS(A.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(A.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,\n" +
                    "         'X' DESCRIPTION,\n" +
                    "         'X' BILLID\n" +
                    " FROM    VCBMIG.TT_FX@FCFTLINK A,VCBMIG.SD_CPTY@FCFTLINK B ,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm\n" +
                    " WHERE   A.CPTY_FBO_ID_NUM = B.FBO_ID_NUM\n" +
                    " and A.deal_num=tut.deal_number \n" +
                    "and tut.foracid = gam.foracid\n" +
                    "and gam.cif_id = crm.orgkey\n" +
                    "and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE\n" +
                    " AND     DEAL_DATE = '28-JAN-26'\n" +
                    " AND     DEAL_STATE NOT IN ('DLTD')\n" +
                    " AND     A.CCY_TWO_AMOUNT_CCY <> 'KES'\n" +
                    " AND     A.DEAL_NUM < 8000000 AND A.DEAL_NUM > 0 \n" +
                    " AND     A.DEAL_TYPE LIKE 'FX%'\n" +
                    " and     gam.cif_id is not null\n" +
                    " AND     A.ACCOUNTING_CODE = 'INTBNK'\n" +
                    " AND     A.CCY_TWO_AMOUNT_VALUE < 0\n" +
                    " AND     A.DEAL_TYPE != 'FXOUTS'\n" +
                    "\n" +
                    " UNION ALL\n" +
                    "\n" +
                    " SELECT\n" +
                    "   tut.tran_id TRANS_REF_CODE,\n" +
                    "--gam.foracid,\n" +
                    "gam.ACCT_NAME CUSTOMER_NAME,\n" +
                    "--gam.cif_id, \n" +
                    "cvm.LOCALE_VALUE SECTOR,\n" +
                    "A.DEAL_DATE TIMESTAMP,\n" +
                    " A.CCY_TWO_SWAP_AMOUNT_CCY CRNCY,\n" +
                    "         ABS(A.CCY_TWO_SWAP_AMOUNT_VALUE) AMT,\n" +
                    "         --' ' PURPOSE,\n" +
                    "         --B.SHORT_NAME_1 PARTY,\n" +
                    "         --'O' INOUT,\n" +
                    "         --'T' RTYPE,\n" +
                    "         A.CONTRACT_RATE Spot_rate,\n" +
                    "                           CASE \n" +
                    "    WHEN TRIM( A.CCY_TWO_SWAP_AMOUNT_CCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate(  TRIM( A.CCY_TWO_SWAP_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN A.CCY_ONE_AMOUNT_CCY = 'USD' \n" +
                    "         THEN ABS(A.CCY_TWO_SWAP_AMOUNT_VALUE)\n" +
                    "    ELSE ROUND(  ABS(A.CCY_TWO_SWAP_AMOUNT_VALUE) * custom.GetConvRate(  TRIM( A.CCY_TWO_SWAP_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,\n" +
                    "        -- A.CCY_ONE_SWAP_AMOUNT_CCY OTHERCCY,\n" +
                    "         'X' DESCRIPTION,\n" +
                    "         'X' BILLID\n" +
                    " FROM    VCBMIG.TT_FX@FCFTLINK A,VCBMIG.SD_CPTY@FCFTLINK B,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm\n" +
                    " WHERE   A.CPTY_FBO_ID_NUM = B.FBO_ID_NUM\n" +
                    " and A.deal_num=tut.deal_number \n" +
                    "and tut.foracid = gam.foracid\n" +
                    "and gam.cif_id = crm.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE\n" +
                    " AND     DEAL_DATE = '28-JAN-26'\n" +
                    " AND     DEAL_STATE NOT IN ('DLTD')\n" +
                    " AND     A.CCY_TWO_SWAP_AMOUNT_CCY <> 'KES'\n" +
                    " AND     A.DEAL_NUM < 8000000 AND A.DEAL_NUM > 0 \n" +
                    " AND     A.DEAL_TYPE LIKE 'FX%' \n" +
                    " AND     A.ACCOUNTING_CODE = 'INTBNK'\n" +
                    " AND     A.CCY_TWO_SWAP_AMOUNT_VALUE < 0\n" +
                    " AND     A.DEAL_TYPE = 'FXOUTS'\n" +
                    "\n" +
                    " UNION ALL\n" +
                    "\n" +
                    "SELECT\n" +
                    "  tut.tran_id TRANS_REF_CODE,\n" +
                    "--gam.foracid,\n" +
                    "gam.ACCT_NAME CUSTOMER_NAME,\n" +
                    "--gam.cif_id, \n" +
                    "cvm.LOCALE_VALUE SECTOR,\n" +
                    "A.DEAL_DATE TIMESTAMP,\n" +
                    "  A.CCY_ONE_AMOUNT_CCY CRNCY,\n" +
                    "         ABS(A.CCY_ONE_AMOUNT_VALUE) AMT,\n" +
                    "         --' ' PURPOSE,\n" +
                    "         --B.SHORT_NAME_1 PARTY,\n" +
                    "         --'O' INOUT,\n" +
                    "         --'T' RTYPE,\n" +
                    "         A.CONTRACT_RATE Spot_rate,\n" +
                    "         --A.CCY_TWO_AMOUNT_CCY OTHERCCY,\n" +
                    "                  CASE \n" +
                    "    WHEN TRIM(A.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate(  TRIM(A.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN A.CCY_ONE_AMOUNT_CCY = 'USD' \n" +
                    "         THEN ABS(A.CCY_ONE_AMOUNT_VALUE)\n" +
                    "    ELSE ROUND(  ABS(A.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(A.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,\n" +
                    "         'X' DESCRIPTION,\n" +
                    "         'X' BILLID\n" +
                    " FROM    VCBMIG.TT_FX@FCFTLINK A,VCBMIG.SD_CPTY@FCFTLINK B,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm\n" +
                    " WHERE   A.CPTY_FBO_ID_NUM = B.FBO_ID_NUM\n" +
                    " and A.deal_num=tut.deal_number \n" +
                    "and tut.foracid = gam.foracid\n" +
                    "and gam.cif_id = crm.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE\n" +
                    " AND     DEAL_DATE = '28-JAN-26'\n" +
                    " AND     DEAL_STATE NOT IN ('DLTD')\n" +
                    " AND     A.CCY_ONE_AMOUNT_CCY <> 'KES'\n" +
                    " AND     A.DEAL_NUM < 8000000 AND A.DEAL_NUM > 0 \n" +
                    " AND     A.DEAL_TYPE LIKE 'FX%' \n" +
                    " AND     A.ACCOUNTING_CODE = 'INTBNK'\n" +
                    " AND     A.CCY_ONE_AMOUNT_VALUE < 0\n" +
                    " AND     A.DEAL_TYPE != 'FXOUTS'\n" +
                    "\n" +
                    "Union All\n" +
                    "\n" +
                    "SELECT \n" +
                    "      CHD.tran_id TRANS_REF_CODE,\n" +
                    "      GAM.ACCT_NAME CUSTOMER_NAME,\n" +
                    "      CVM.LOCALE_VALUE SECTOR,\n" +
                    "      CGM.LODG_DATE TIMESTAMP,\n" +
                    "        TRN_CURRENCY CRNCY,\n" +
                    "        to_number(APZTB_TRANSACTION_REQUEST.AMOUNT) AMT,\n" +
                    "        --ADDL_COL29 PURPOSE,\n" +
                    "--        ACID,\n" +
                    "        --'O',\n" +
                    "         CHD.EVENT_RATE Spot_rate,\n" +
                    "        --'R',\n" +
                    "        --0,\n" +
                    "         CASE \n" +
                    "    WHEN TRIM(CGM.COLLECTION_CRNCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate( TRIM(CGM.COLLECTION_CRNCY), 'USD','CBK',CGM.LODG_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN CGM.COLLECTION_CRNCY = 'USD' \n" +
                    "         THEN CGM.COLLECTION_AMT\n" +
                    "    ELSE ROUND(CGM.COLLECTION_AMT * custom.GetConvRate(TRIM(CGM.COLLECTION_CRNCY),  'USD', 'CBK', CGM.LODG_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,\n" +
                    "        --'X',\n" +
                    "        APZTB_TRANSACTION_REQUEST.BENEFICIARY_NAME DESCRIPTION,\n" +
                    "        TB_MB_TRANS_LOG.BULK_REF_NO BILLID\n" +
                    "FROM VCBCORP.TB_MB_TRANS_LOG@fcixlink,TBAADM.GAM, CRMUSER.ACCOUNTS crm,TBAADM.CGM,TBAADM.CVM,TBAADM.CHD,\n" +
                    "VCBCORP.APZTB_TRANSACTION_REQUEST@fcixlink\n" +
                    "WHERE TB_MB_TRANS_LOG.TRANSACTION_TYPE IN ('INTTRANSFER')\n" +
                    "AND DESCRIPTION = 'AUTHORIZE'\n" +
                    "AND APZTB_TRANSACTION_REQUEST.FROM_ACC = FORACID\n" +
                    "AND ERROR_MSG = 'success'\n" +
                    "AND TRN_CURRENCY != 'KES'\n" +
                    "and CGM.collection_id=CHD.collection_id\n" +
                    "and CGM.oper_acid=GAM.acid\n" +
                    "and     gam.cif_id is not null\n" +
                    "and GAM.cif_id = CRM.orgkey\n" +
                    "and CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE\n" +
                    "and  CHD.tran_id is not null\n" +
                    "and gam.cif_id = APZTB_TRANSACTION_REQUEST.cif_no\n" +
                    "and TB_MB_TRANS_LOG.BULK_REF_NO = APZTB_TRANSACTION_REQUEST.BULK_REF_NO\n" +
                    "And TRUNC(APZTB_TRANSACTION_REQUEST.CHECKER_DT)= '28-JAN-26'\n" +
                    "AND APZTB_TRANSACTION_REQUEST.ADDL_COL6 IS NULL\n" +
                    "\n" +
                    " UNION ALL\n" +
                    "\n" +
                    " SELECT \n" +
                    "       CHD.tran_id TRANS_REF_CODE,\n" +
                    "      GAM.ACCT_NAME CUSTOMER_NAME,\n" +
                    "      CVM.LOCALE_VALUE SECTOR,\n" +
                    "      CGM.LODG_DATE TIMESTAMP,\n" +
                    "        TRN_CURRENCY CRNCY,\n" +
                    "        to_number(APZTB_TRANSACTION_REQUEST.AMOUNT) AMT,\n" +
                    "        --ADDL_COL29 PURPOSE,\n" +
                    "        --ACID,\n" +
                    "        --'O',\n" +
                    "         CHD.EVENT_RATE Spot_rate,\n" +
                    "        --'E',\n" +
                    "       -- TO_NUMBER(APZTB_TRANSACTION_REQUEST.ADDL_COL6),\n" +
                    "                 CASE \n" +
                    "    WHEN TRIM(CGM.COLLECTION_CRNCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate( TRIM(CGM.COLLECTION_CRNCY), 'USD','CBK',CGM.LODG_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN CGM.COLLECTION_CRNCY = 'USD' \n" +
                    "         THEN CGM.COLLECTION_AMT\n" +
                    "    ELSE ROUND(CGM.COLLECTION_AMT * custom.GetConvRate(TRIM(CGM.COLLECTION_CRNCY),  'USD', 'CBK', CGM.LODG_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,\n" +
                    "        --'X',\n" +
                    "        APZTB_TRANSACTION_REQUEST.BENEFICIARY_NAME DESCRIPTION,\n" +
                    "        TB_MB_TRANS_LOG.BULK_REF_NO BILLID\n" +
                    "FROM VCBCORP.TB_MB_TRANS_LOG@fcixlink,TBAADM.GAM,CRMUSER.ACCOUNTS crm,TBAADM.CVM,TBAADM.CGM,TBAADM.CHD,\n" +
                    "VCBCORP.APZTB_TRANSACTION_REQUEST@fcixlink\n" +
                    "WHERE TB_MB_TRANS_LOG.TRANSACTION_TYPE IN ('INTTRANSFER')\n" +
                    "AND DESCRIPTION = 'AUTHORIZE'\n" +
                    "AND APZTB_TRANSACTION_REQUEST.FROM_ACC = FORACID\n" +
                    "AND ERROR_MSG = 'success'\n" +
                    "AND TRN_CURRENCY != 'KES'\n" +
                    "and CGM.collection_id=CHD.collection_id\n" +
                    "and CGM.oper_acid=GAM.acid\n" +
                    "and GAM.cif_id = CRM.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and CRM.SEGMENTATION_CLASS = CVM.CATEGORY_VALUE\n" +
                    "and  CHD.tran_id is not null\n" +
                    "and gam.cif_id = APZTB_TRANSACTION_REQUEST.cif_no\n" +
                    "and TB_MB_TRANS_LOG.BULK_REF_NO = APZTB_TRANSACTION_REQUEST.BULK_REF_NO\n" +
                    "And TRUNC(APZTB_TRANSACTION_REQUEST.CHECKER_DT)= '28-JAN-26'\n" +
                    "AND APZTB_TRANSACTION_REQUEST.ADDL_COL6 IS NOT NULL\n" +
                    "\n" +
                    " UNION ALL\n" +
                    "\n" +
                    "select\n" +
                    "tut.tran_id TRANS_REF_CODE,\n" +
                    "--gam.foracid,\n" +
                    "gam.ACCT_NAME CUSTOMER_NAME,\n" +
                    "--gam.cif_id, \n" +
                    "cvm.LOCALE_VALUE SECTOR,\n" +
                    "A.DEAL_DATE TIMESTAMP,\n" +
                    "B.CCY_ONE_AMOUNT_CCY CRNCY,\n" +
                    "       ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,\n" +
                    "     --  ' ' PURPOSE,\n" +
                    "      -- C.SHORT_NAME_1 PARTY,\n" +
                    "      -- 'O' INOUT,\n" +
                    "      -- 'T' RTYPE,\n" +
                    "       B.MARKET_SPOT_RATE Spot_rate,\n" +
                    "         CASE \n" +
                    "    WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN B.CCY_ONE_AMOUNT_CCY = 'USD' \n" +
                    "         THEN ABS(B.CCY_ONE_AMOUNT_VALUE)\n" +
                    "    ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,       \n" +
                    "       --B.CCY_TWO_AMOUNT_CCY OTHERCCY,\n" +
                    "       'X' DESCRIPTION,\n" +
                    "       'X' BILLID\n" +
                    "from VCBMIG.TT_FXP_DEAL@FCFTLINK A, VCBMIG.TT_FXP_LEG@FCFTLINK B, VCBMIG.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm\n" +
                    "where A.DEAL_NUM = B.PARENT_FBO_ID_NUM\n" +
                    "AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM\n" +
                    "and A.deal_num=tut.deal_number \n" +
                    "and tut.foracid = gam.foracid\n" +
                    "and gam.cif_id = crm.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE\n" +
                    "AND A.DEAL_DATE =  '28-JAN-26'\n" +
                    "AND A.ACCOUNTING_CODE='INTBNK'\n" +
                    "AND A.DEAL_STATE NOT IN ('DLTD')\n" +
                    "AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999\n" +
                    "AND A.DEAL_TYPE='FXPLSP'\n" +
                    "AND B.CCY_ONE_AMOUNT_CCY <>'KES'\n" +
                    "AND FX_BUY_SELL = 'S'\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "select\n" +
                    "tut.tran_id TRANS_REF_CODE ,\n" +
                    "--gam.foracid,\n" +
                    "gam.ACCT_NAME CUSTOMER_NAME, \n" +
                    "--gam.cif_id,\n" +
                    "cvm.LOCALE_VALUE SECTOR, \n" +
                    "A.DEAL_DATE TIMESTAMP,\n" +
                    "B.CCY_TWO_AMOUNT_CCY CRNCY,\n" +
                    "       ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,\n" +
                    "      -- ' ' PURPOSE,\n" +
                    "      -- C.SHORT_NAME_1 PARTY,\n" +
                    "      -- 'O' INOUT,\n" +
                    "      -- 'T' RTYPE,\n" +
                    "       B.MARKET_SPOT_RATE Spot_rate,\n" +
                    "         CASE \n" +
                    "    WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN B.CCY_TWO_AMOUNT_CCY = 'USD' \n" +
                    "         THEN ABS(B.CCY_TWO_AMOUNT_VALUE)\n" +
                    "    ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,       \n" +
                    "      -- B.CCY_ONE_AMOUNT_CCY OTHERCCY,\n" +
                    "       'X' DESCRIPTION,\n" +
                    "       'X' BILLID\n" +
                    "from VCBMIG.TT_FXP_DEAL@FCFTLINK A, VCBMIG.TT_FXP_LEG@FCFTLINK B, VCBMIG.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm\n" +
                    "where A.DEAL_NUM = B.PARENT_FBO_ID_NUM\n" +
                    "AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM\n" +
                    "and A.deal_num=tut.deal_number \n" +
                    "and tut.foracid = gam.foracid\n" +
                    "and gam.cif_id = crm.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE\n" +
                    "AND A.DEAL_DATE =  '28-JAN-26'\n" +
                    "AND A.ACCOUNTING_CODE='INTBNK'\n" +
                    "AND A.DEAL_STATE NOT IN ('DLTD')\n" +
                    "AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999\n" +
                    "AND A.DEAL_TYPE='FXPLSP'\n" +
                    "AND B.CCY_TWO_AMOUNT_CCY <>'KES'\n" +
                    "AND FX_BUY_SELL = 'B'\n" +
                    "UNION ALL\n" +
                    "select\n" +
                    "tut.tran_id TRANS_REF_CODE ,\n" +
                    "--gam.foracid,\n" +
                    "gam.ACCT_NAME CUSTOMER_NAME, \n" +
                    "--gam.cif_id,\n" +
                    "cvm.LOCALE_VALUE SECTOR, \n" +
                    "A.DEAL_DATE TIMESTAMP,\n" +
                    "B.CCY_ONE_AMOUNT_CCY CRNCY,\n" +
                    "       ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,\n" +
                    "       --' ' PURPOSE,\n" +
                    "       --C.SHORT_NAME_1 PARTY,\n" +
                    "       --'O' INOUT,\n" +
                    "       --'T' RTYPE,\n" +
                    "       B.MARKET_FWD_RATE spot_rate,\n" +
                    "                CASE \n" +
                    "    WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN B.CCY_ONE_AMOUNT_CCY = 'USD' \n" +
                    "         THEN ABS(B.CCY_ONE_AMOUNT_VALUE)\n" +
                    "    ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,  \n" +
                    "       --B.CCY_TWO_AMOUNT_CCY OTHERCCY,\n" +
                    "       'X' DESCRIPTION,\n" +
                    "       'X' BILLID\n" +
                    "from VCBMIG.TT_FXP_DEAL@FCFTLINK A, VCBMIG.TT_FXP_LEG@FCFTLINK B, VCBMIG.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm\n" +
                    "where A.DEAL_NUM = B.PARENT_FBO_ID_NUM\n" +
                    "AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM\n" +
                    "and A.deal_num=tut.deal_number \n" +
                    "and tut.foracid = gam.foracid\n" +
                    "and gam.cif_id = crm.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE\n" +
                    "AND A.DEAL_DATE = '28-JAN-26'\n" +
                    "AND A.ACCOUNTING_CODE='INTBNK'\n" +
                    "AND A.DEAL_STATE NOT IN ('DLTD')\n" +
                    "AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999\n" +
                    "AND A.DEAL_TYPE='FXPLOU'\n" +
                    "AND B.CCY_ONE_AMOUNT_CCY<>'KES'\n" +
                    "AND FX_BUY_SELL = 'S'\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "select \n" +
                    "tut.tran_id TRANS_REF_CODE ,\n" +
                    "--gam.foracid,\n" +
                    "gam.ACCT_NAME CUSTOMER_NAME, \n" +
                    "--gam.cif_id,\n" +
                    "cvm.LOCALE_VALUE SECTOR, \n" +
                    "A.DEAL_DATE TIMESTAMP,\n" +
                    "B.CCY_TWO_AMOUNT_CCY CRNCY,\n" +
                    "       ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,\n" +
                    "       --' ' PURPOSE,\n" +
                    "       --C.SHORT_NAME_1 PARTY,\n" +
                    "       --'O' INOUT,\n" +
                    "       --'T' RTYPE,\n" +
                    "       B.MARKET_FWD_RATE spot_rate,\n" +
                    "         CASE \n" +
                    "    WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN B.CCY_TWO_AMOUNT_CCY = 'USD' \n" +
                    "         THEN ABS(B.CCY_TWO_AMOUNT_VALUE)\n" +
                    "    ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,         \n" +
                    " --      B.CCY_TWO_AMOUNT_CCY OTHERCCY,\n" +
                    "       'X' DESCRIPTION,\n" +
                    "       'X' BILLID\n" +
                    "from VCBMIG.TT_FXP_DEAL@FCFTLINK A, VCBMIG.TT_FXP_LEG@FCFTLINK B, VCBMIG.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm\n" +
                    "where A.DEAL_NUM = B.PARENT_FBO_ID_NUM\n" +
                    "AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM\n" +
                    "and A.deal_num=tut.deal_number \n" +
                    "and tut.foracid = gam.foracid\n" +
                    "and gam.cif_id = crm.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE\n" +
                    "AND A.DEAL_DATE = '28-JAN-26'\n" +
                    "AND A.ACCOUNTING_CODE='INTBNK'\n" +
                    "AND A.DEAL_STATE NOT IN ('DLTD')\n" +
                    "AND B.LEG_IDENTIFIER='MNLG' and B.parent_fbo_id_ver=999999\n" +
                    "AND A.DEAL_TYPE='FXPLOU'\n" +
                    "AND B.CCY_TWO_AMOUNT_CCY<>'KES'\n" +
                    "AND FX_BUY_SELL = 'B'\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "select\n" +
                    "tut.tran_id TRANS_REF_CODE ,\n" +
                    "--gam.foracid,\n" +
                    "gam.ACCT_NAME CUSTOMER_NAME, \n" +
                    "--gam.cif_id,\n" +
                    "cvm.LOCALE_VALUE SECTOR, \n" +
                    "A.DEAL_DATE TIMESTAMP,\n" +
                    "B.CCY_ONE_AMOUNT_CCY CRNCY,\n" +
                    "       ABS(B.CCY_ONE_AMOUNT_VALUE) AMT,\n" +
                    "       --' ' PURPOSE,\n" +
                    "       --C.SHORT_NAME_1 PARTY,\n" +
                    "       --'O' INOUT,\n" +
                    "       --'T' RTYPE,\n" +
                    "       B.MARKET_FWD_RATE spot_rate,\n" +
                    "                CASE \n" +
                    "    WHEN TRIM(B.CCY_ONE_AMOUNT_CCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN B.CCY_ONE_AMOUNT_CCY = 'USD' \n" +
                    "         THEN ABS(B.CCY_ONE_AMOUNT_VALUE)\n" +
                    "    ELSE ROUND(  ABS(B.CCY_ONE_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_ONE_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,  \n" +
                    "       --B.CCY_TWO_AMOUNT_CCY OTHERCCY,\n" +
                    "       'X' DESCRIPTION,\n" +
                    "       'X' BILLID\n" +
                    "from VCBMIG.TT_FXP_DEAL@FCFTLINK A, VCBMIG.TT_FXP_LEG@FCFTLINK B, VCBMIG.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm\n" +
                    "where A.DEAL_NUM = B.PARENT_FBO_ID_NUM\n" +
                    "AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM\n" +
                    "and A.deal_num=tut.deal_number \n" +
                    "and tut.foracid = gam.foracid\n" +
                    "and gam.cif_id = crm.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE\n" +
                    "AND A.DEAL_DATE = '28-JAN-26'\n" +
                    "AND A.ACCOUNTING_CODE='INTBNK'\n" +
                    "AND A.DEAL_STATE NOT IN ('DLTD')\n" +
                    "AND B.LEG_IDENTIFIER='NELG' and B.parent_fbo_id_ver=999999\n" +
                    "AND A.DEAL_TYPE='FXPLSW'\n" +
                    "AND B.CCY_ONE_AMOUNT_CCY<>'KES'\n" +
                    "AND FX_BUY_SELL = 'S'\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "select\n" +
                    "tut.tran_id TRANS_REF_CODE ,\n" +
                    "--gam.foracid,\n" +
                    "gam.ACCT_NAME CUSTOMER_NAME, \n" +
                    "--gam.cif_id,\n" +
                    "cvm.LOCALE_VALUE SECTOR, \n" +
                    "A.DEAL_DATE TIMESTAMP,\n" +
                    "B.CCY_TWO_AMOUNT_CCY CRNCY,\n" +
                    "       ABS(B.CCY_TWO_AMOUNT_VALUE) AMT,\n" +
                    "       --' ' PURPOSE,\n" +
                    "       --C.SHORT_NAME_1 PARTY,\n" +
                    "       --'O' INOUT,\n" +
                    "       --'T' RTYPE,\n" +
                    "       B.MARKET_FWD_RATE spot_rate,\n" +
                    "  CASE \n" +
                    "    WHEN TRIM(B.CCY_TWO_AMOUNT_CCY) = 'USD' THEN 1\n" +
                    "    ELSE custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE )\n" +
                    " END AS CROSS_RATE,\n" +
                    " CASE \n" +
                    "    WHEN B.CCY_TWO_AMOUNT_CCY = 'USD' \n" +
                    "         THEN ABS(B.CCY_TWO_AMOUNT_VALUE)\n" +
                    "    ELSE ROUND(  ABS(B.CCY_TWO_AMOUNT_VALUE) * custom.GetConvRate(  TRIM(B.CCY_TWO_AMOUNT_CCY),  'USD', 'CBK',  A.DEAL_DATE ), 2) \n" +
                    " END AS USD_EQUIVALENT,  \n" +
                    "       --B.CCY_TWO_AMOUNT_CCY OTHERCCY,\n" +
                    "       'X' DESCRIPTION,\n" +
                    "       'X' BILLID\n" +
                    "from VCBMIG.TT_FXP_DEAL@FCFTLINK A, VCBMIG.TT_FXP_LEG@FCFTLINK B, VCBMIG.SD_CPTY@FCFTLINK C,TBAADM.tut,TBAADM.GAM,CRMUSER.ACCOUNTS crm,tbaadm.cvm\n" +
                    "where A.DEAL_NUM = B.PARENT_FBO_ID_NUM\n" +
                    "AND A.CPTY_FBO_ID_NUM = C.FBO_ID_NUM\n" +
                    "and A.deal_num=tut.deal_number \n" +
                    "and tut.foracid = gam.foracid\n" +
                    "and gam.cif_id = crm.orgkey\n" +
                    "and     gam.cif_id is not null\n" +
                    "and crm.SEGMENTATION_CLASS = cvm.CATEGORY_VALUE\n" +
                    "AND A.DEAL_DATE = '28-JAN-26'\n" +
                    "AND A.ACCOUNTING_CODE='INTBNK'\n" +
                    "AND A.DEAL_STATE NOT IN ('DLTD')\n" +
                    "AND B.LEG_IDENTIFIER='NELG' and B.parent_fbo_id_ver=999999\n" +
                    "AND A.DEAL_TYPE='FXPLSW'\n" +
                    "AND B.CCY_TWO_AMOUNT_CCY<>'KES'\n" +
                    "AND FX_BUY_SELL = 'B' ";
            List<Props> paymentsList = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sqlFetch);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
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
                    paymentsList.add(p);
                }
            }

            if (paymentsList.isEmpty()) {
                log.info("No new payments to insert.");
                return;
            }

            // Fetch existing keys for deduplication
            Set<String> existingKeys = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT TRANSREFCODE || '_' || TIMESTAMP AS key FROM custom.forex_payments WHERE FOREXTYPE = 'P'");
                 ResultSet rsCheck = ps.executeQuery()) {
                while (rsCheck.next()) {
                    existingKeys.add(rsCheck.getString("key"));
                }
            }

            List<String> insertStatements = new ArrayList<>();
            for (Props p : paymentsList) {
                String key = p.getTransRefCode() + "_" + p.getTimestamp();
                if (existingKeys.contains(key)) continue;

                String insertSql = String.format("""
                        INSERT INTO custom.FOREX_PAYMENTS (
                            TRANSREFCODE, CUSTOMERNAME, CURRENCY, AMOUNT, TIMESTAMP,
                            SPOTEXCHANGERATE, CROSSRATE, USDEQUIVALENT, SECTORCODE, FOREXTYPE, POSTED_FLAG
                        )
                        VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s','P','N')
                        """,
                        p.getTransRefCode(), p.getCustomerName(), p.getCurrency(), p.getAmount(), p.getTimestamp(),
                        p.getSpotExchangerate(), p.getCross(), p.getUsdEquivalent(), p.getSector()
                );

                insertStatements.add(insertSql);
                existingKeys.add(key);
            }

            if (!insertStatements.isEmpty()) {
                try (Statement stmt = conn.createStatement()) {
                    for (String sql : insertStatements) stmt.addBatch(sql);
                    stmt.executeBatch();
                    log.info("Inserted {} new payments into custom.forex_payments", insertStatements.size());
                }
            }

        } catch (Exception e) {
            log.error("Error in fetchAndSavePayments", e);
        }
    }
}

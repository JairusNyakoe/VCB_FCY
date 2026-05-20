package org.emtech.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Props {

    private String AccountRef;
    private String CustomerName;
    private String AccountType;
    private String TransRefCode;
    private String Currency;
    private String Amount;
    private String Sector;
    private String SpotExchangerate;
    private String Cross;
    private  String Timestamp;
    private  String InterBankCodes;
    private  String UsdEquivalent;
    private String exchangeRate;
    private String crossRate;
    private String priceRate;
    private String derivativeCode;
    private String derivativeType;
    private String sectorCode;
    private String sectorDescription;
    private String contractId;
    private String notionalPrincipalAmount;
    private String position;
    private String contractStartDate;
    private String contractEndDate;
    private String valuationDate;
    private String settlementDate;
}

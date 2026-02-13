package org.emtech.Derivatives;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.emtech.Entity.Props;
import org.emtech.Tools.Configurations;
import org.emtech.Tools.DatabaseConnection;
import org.emtech.Tools.LogIn;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class DerivativesService {

    private final WebClient webClient;
    private final Configurations configurations;
    private final LogIn logIn;
    private final DatabaseConnection databaseConnection;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00");

    public DerivativesService(WebClient webClient,
                              Configurations configurations,
                              LogIn logIn,
                              DatabaseConnection databaseConnection) {
        this.webClient = webClient;
        this.configurations = configurations;
        this.logIn = logIn;
        this.databaseConnection = databaseConnection;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Submits forex derivatives to CBK and returns only filename and status.
     */
    public Map<String, Object> submitDerivatives(LocalDate start, LocalDate end) {

        Properties prop = configurations.getProperties();
        String urlTemplate = prop.getProperty("submissionUrl");
        String returnKey = prop.getProperty("returnKey_Derivatives");
        String version = prop.getProperty("version");
        String instCode = prop.getProperty("InstCode");

        int finYear = LocalDate.now().getYear();
        String startDate = start.format(dateFormatter);
        String endDate = end.format(dateFormatter);

        // Fetch derivatives records from DB
        List<Props> propsList = new ArrayList<>();
        String sql = prop.getProperty("sqlFetchDerivatives");
        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Props p = new Props();
                p.setTransRefCode(rs.getString("trans_ref_code"));
                p.setCustomerName(rs.getString("customer_name"));
                p.setContractId(rs.getString("contract_id"));
                p.setCurrency(rs.getString("currency"));
                p.setNotionalPrincipalAmount(rs.getString("notional_principal_amount"));
                p.setTimestamp(rs.getString("timestamp"));
                p.setPosition(rs.getString("position"));
                p.setContractStartDate(rs.getString("contract_start_date"));
                p.setContractEndDate(rs.getString("contract_end_date"));
                p.setValuationDate(rs.getString("valuation_date"));
                p.setSettlementDate(rs.getString("settlement_date"));
                p.setPriceRate(rs.getString("price_rate"));
                p.setExchangeRate(rs.getString("exchange_rate"));
                p.setCrossRate(rs.getString("cross_rate"));
                p.setUsdEquivalent(rs.getString("usd_equivalent"));
                p.setDerivativeCode(rs.getString("derivative_code"));
                p.setDerivativeType(rs.getString("derivative_type"));
                p.setSector(rs.getString("sector_code"));

                propsList.add(p);
            }

        } catch (Exception e) {
            log.error("Error fetching forex derivatives from DB: {}", e.getMessage(), e);
        }

        // Build CBK payload (Area 63)
        List<Map<String, Object>> dynamicItemsList = new ArrayList<>();
        for (Props p : propsList) {

            List<Map<String, Object>> dynamicItems = new ArrayList<>();
            dynamicItems.add(Map.of("Code", "1.1", "Value", p.getTransRefCode()));
            dynamicItems.add(Map.of("Code", "1.2", "Value", p.getCustomerName()));
            dynamicItems.add(Map.of("Code", "1.3", "Value", p.getContractId()));
            dynamicItems.add(Map.of("Code", "1.4", "Value", p.getCurrency()));
            dynamicItems.add(Map.of("Code", "1.5", "Value", p.getNotionalPrincipalAmount()));
            dynamicItems.add(Map.of("Code", "1.6", "Value", p.getTimestamp()));
            dynamicItems.add(Map.of("Code", "1.7", "Value", p.getPosition()));
            dynamicItems.add(Map.of("Code", "1.10", "Value", p.getContractStartDate()));
            dynamicItems.add(Map.of("Code", "1.11", "Value", p.getContractEndDate()));
            dynamicItems.add(Map.of("Code", "1.12", "Value", p.getValuationDate()));
            dynamicItems.add(Map.of("Code", "1.13", "Value", p.getSettlementDate()));
            dynamicItems.add(Map.of("Code", "1.15", "Value", p.getPriceRate()));
            dynamicItems.add(Map.of("Code", "1.18", "Value", p.getExchangeRate()));
            dynamicItems.add(Map.of("Code", "1.19", "Value", p.getCrossRate()));
            dynamicItems.add(Map.of("Code", "1.20", "Value", p.getUsdEquivalent()));
            dynamicItems.add(Map.of("Code", "1.21", "Value", p.getDerivativeCode()));
            dynamicItems.add(Map.of("Code", "1.22", "Value", p.getDerivativeType()));
            dynamicItems.add(Map.of("Code", "1.24", "Value", p.getSector()));

            Map<String, Object> areaMap = new HashMap<>();
            areaMap.put("Area", 63);
            areaMap.put("_areaName", "Forex Derivatives Area");
            areaMap.put("DynamicItems", dynamicItems);

            dynamicItemsList.add(areaMap);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ReturnKey", returnKey);
        requestBody.put("InstCode", instCode);
        requestBody.put("FinYear", finYear);
        requestBody.put("StartDate", startDate);
        requestBody.put("EndDate", endDate);
        requestBody.put("ReturnItemsList", new ArrayList<>());
        requestBody.put("DynamicItemsList", dynamicItemsList);

        String url = String.format(urlTemplate, returnKey, version);
        log.info("Submitting derivatives to CBK: {}", url);

        Map response = null;
        try {
            response = webClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + logIn.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("Error submitting derivatives to CBK: {}", e.getMessage(), e);
        }

        Map<String, Object> result = new HashMap<>();
        if (response != null) {
            result.put("filename", response.get("filename"));
            result.put("status", response.get("status"));
        }

        return result;
    }
}

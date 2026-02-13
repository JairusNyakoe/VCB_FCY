package org.emtech.Holdings;

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
public class HoldingService {

    private final WebClient webClient;
    private final Configurations configurations;
    private final LogIn logIn;
    private final DatabaseConnection databaseConnection;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00");

    public HoldingService(WebClient webClient,
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


     // Submits forex holdings to CBK and returns only filename and status.

    public Map<String, Object> submitHoldings(LocalDate start, LocalDate end) {

        Properties prop = configurations.getProperties();
        String urlTemplate = prop.getProperty("submissionUrl");
        String returnKey = prop.getProperty("returnKey_Holdings");
        String version = prop.getProperty("version");
        String instCode = prop.getProperty("InstCode");

        int finYear = LocalDate.now().getYear();
        String startDate = start.format(dateFormatter);
        String endDate = end.format(dateFormatter);

        // Fetch records from DB
        List<Props> propsList = new ArrayList<>();
        String sql= prop.getProperty("sqlFetchHoldings");
        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Props p = new Props();
                p.setAccountRef(rs.getString("account_ref"));
                p.setCustomerName(rs.getString("account_name"));
                p.setAccountType(rs.getString("account_type"));
                p.setCurrency(rs.getString("currency"));
                p.setAmount(rs.getString("amount"));
                p.setSector(rs.getString("sector_code"));
                p.setCross(rs.getString("cross_rate"));


                propsList.add(p);
            }

        } catch (Exception e) {
            log.error("Error fetching forex holdings from DB: {}", e.getMessage(), e);
        }

        // Build CBK payload
        List<Map<String, Object>> dynamicItemsList = new ArrayList<>();
        for (Props p : propsList) {

            List<Map<String, Object>> dynamicItems = new ArrayList<>();
            dynamicItems.add(Map.of("Code", "1.1", "Value", p.getAccountRef()));
            dynamicItems.add(Map.of("Code", "1.2", "Value", p.getCustomerName()));
            dynamicItems.add(Map.of("Code", "1.3", "Value", p.getCurrency()));
            dynamicItems.add(Map.of("Code", "1.4", "Value", p.getAmount()));
            dynamicItems.add(Map.of("Code", "1.5", "Value", p.getCross()));
            dynamicItems.add(Map.of("Code", "1.6", "Value", p.getAccountType()));
            dynamicItems.add(Map.of("Code", "1.7", "Value", p.getSector()));

            Map<String, Object> areaMap = new HashMap<>();
            areaMap.put("Area", 64);
            areaMap.put("_areaName", "FCY Holdings Area");
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
        log.info("Submitting holdings to CBK: {}", url);

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
            log.error("Error submitting holdings to CBK: {}", e.getMessage(), e);
        }

        // Extract only filename and status
        Map<String, Object> result = new HashMap<>();
        if (response != null) {
            result.put("filename", response.get("filename"));
            result.put("status", response.get("status"));
        }

        return result;
    }
}

package org.emtech.Sales;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.emtech.Entity.Props;
import org.emtech.Tools.Configurations;
import org.emtech.Tools.LogIn;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class SalesService {

    private final WebClient webClient;
    private final Configurations configurations;
    private final LogIn logIn;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00");

    public SalesService(WebClient webClient,
                        Configurations configurations,
                        LogIn logIn) {
        this.webClient = webClient;
        this.configurations = configurations;
        this.logIn = logIn;
        this.objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Submits forex sales to CBK and returns only filename and status.
     */
    public Map<String, Object> submitForex(List<Props> propsList) {

        Properties prop = configurations.getProperties();
        String urlTemplate = prop.getProperty("submissionUrl");
        String returnKey = prop.getProperty("returnKey_Sales");
        String version = prop.getProperty("version");
        String instCode = prop.getProperty("InstCode");

        int finYear = LocalDate.now().getYear();
        String today = LocalDate.now().format(dateFormatter);

        // Build dynamic items
        List<Map<String, Object>> dynamicItems = new ArrayList<>();
        for (Props p : propsList) {
            dynamicItems.add(Map.of("Code", "1.1", "Value", p.getTransRefCode()));
            dynamicItems.add(Map.of("Code", "1.2", "Value", p.getAccountRef()));
            dynamicItems.add(Map.of("Code", "1.3", "Value", p.getCurrency()));
            dynamicItems.add(Map.of("Code", "1.4", "Value", p.getAmount()));
            dynamicItems.add(Map.of("Code", "1.5", "Value", p.getTimestamp() != null ? p.getTimestamp() : today));
            dynamicItems.add(Map.of("Code", "1.6", "Value", p.getSpotExchangerate()));
            dynamicItems.add(Map.of("Code", "1.7", "Value", p.getCross()));
            dynamicItems.add(Map.of("Code", "1.8", "Value", p.getUsdEquivalent()));
            dynamicItems.add(Map.of("Code", "1.9", "Value", p.getInterBankCodes()));
            dynamicItems.add(Map.of("Code", "1.10", "Value", p.getSector()));
        }

        Map<String, Object> areaMap = new HashMap<>();
        areaMap.put("Area", 62);
        areaMap.put("_areaName", "Forex Sales Area");
        areaMap.put("DynamicItems", dynamicItems);

        List<Map<String, Object>> dynamicItemsList = List.of(areaMap);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ReturnKey", returnKey);
        requestBody.put("InstCode", instCode);
        requestBody.put("FinYear", finYear);
        requestBody.put("StartDate", today);
        requestBody.put("EndDate", today);
        requestBody.put("ReturnItemsList", new ArrayList<>());
        requestBody.put("DynamicItemsList", dynamicItemsList);

        String url = String.format(urlTemplate, returnKey, version);
        System.out.println("Submitting to CBK: " + url);

        // Send request and block for response
        Map response = webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + logIn.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        // Extract only filename and status
        Map<String, Object> result = new HashMap<>();
        if (response != null) {
            result.put("filename", response.get("filename"));
            System.out.println("this is the filename____________________________________"+response.get("filename"));
            result.put("status", response.get("status"));
        }

        return result;
    }
}

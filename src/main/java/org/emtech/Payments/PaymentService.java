package org.emtech.Payments;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.emtech.Entity.Props;
import org.emtech.Tools.Configurations;
import org.emtech.Tools.DatabaseConnection;
import org.emtech.Tools.LogIn;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PaymentService {

    private final WebClient webClient;
    private final Configurations configurations;
    private final LogIn logIn;
    private final DatabaseConnection databaseConnection;
    private final ObjectMapper objectMapper;

    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00");

    private final Map<String, Map<String, Object>> postedFiles = new ConcurrentHashMap<>();

    public PaymentService(WebClient webClient,
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

    @Scheduled(fixedDelay = 20000)
    public void scheduledPaymentsSubmission() {
        try {
            LocalDate today = LocalDate.now();
            log.info("Starting scheduled CBK Payments submission...");

            Map<String, Object> response = submitPayments(today, today);

            if (response != null
                    && response.containsKey("filename")
                    && "N".equalsIgnoreCase(String.valueOf(response.get("status")))) {

                String fileName = String.valueOf(response.get("filename"));

                log.info("Payment submitted successfully → fileName: {}", fileName);

                scheduleStatusCheck(fileName);
            }

        } catch (Exception e) {
            log.error("Scheduled Payments submission failed", e);
        }
    }


    public Map<String, Object> submitPayments(LocalDate start, LocalDate end) {

        Properties prop = configurations.getProperties();

        String urlTemplate = prop.getProperty("submissionUrl");
        String returnKey = prop.getProperty("returnKey_Payments");
        String version = prop.getProperty("version");
        String instCode = prop.getProperty("InstCode");
        String sql = prop.getProperty("sqlFetchPayments");

        int finYear = LocalDate.now().getYear();
        String startDate = start.format(dateFormatter);
        String endDate = end.format(dateFormatter);

        List<Props> propsList = new ArrayList<>();

        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Props p = new Props();
                p.setTransRefCode(rs.getString("TRANSREFCODE"));
                p.setCustomerName(rs.getString("CUSTOMERNAME"));
                p.setCurrency(rs.getString("CURRENCY"));
                p.setAmount(rs.getString("AMOUNT"));
                p.setTimestamp(rs.getString("TIMESTAMP"));
                p.setSpotExchangerate(rs.getString("SPOTEXCHANGERATE"));
                p.setCross(rs.getString("CROSSRATE"));
                p.setUsdEquivalent(rs.getString("USDEQUIVALENT"));
                p.setSector(rs.getString("SECTORCODE"));
                propsList.add(p);
            }

        } catch (Exception e) {
            log.error("Error fetching payments from DB", e);
            return null;
        }

        if (propsList.isEmpty()) {
            log.info("No unposted payments found.");
            return null;
        }

        List<Map<String, Object>> dynamicItems = new ArrayList<>();
        int rowNumber = 1;

        for (Props p : propsList) {
            dynamicItems.add(Map.of("Code", rowNumber + ".1", "Value", p.getTransRefCode()));
            dynamicItems.add(Map.of("Code", rowNumber + ".2", "Value", p.getCustomerName()));
            dynamicItems.add(Map.of("Code", rowNumber + ".3", "Value", p.getCurrency()));
            dynamicItems.add(Map.of("Code", rowNumber + ".4", "Value", p.getAmount()));
            dynamicItems.add(Map.of("Code", rowNumber + ".5", "Value", p.getTimestamp()));
            dynamicItems.add(Map.of("Code", rowNumber + ".6", "Value", p.getSpotExchangerate()));
            dynamicItems.add(Map.of("Code", rowNumber + ".7", "Value", p.getCross()));
            dynamicItems.add(Map.of("Code", rowNumber + ".8", "Value", p.getUsdEquivalent()));
            dynamicItems.add(Map.of("Code", rowNumber + ".9", "Value", p.getSector()));
            rowNumber++;
        }

        Map<String, Object> areaMap = new HashMap<>();
        areaMap.put("Area", 60);
        areaMap.put("_areaName", "Forex Payments Area");
        areaMap.put("DynamicItems", dynamicItems);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ReturnKey", returnKey);
        requestBody.put("InstCode", instCode);
        requestBody.put("FinYear", finYear);
        requestBody.put("StartDate", startDate);
        requestBody.put("EndDate", endDate);
        requestBody.put("ReturnItemsList", new ArrayList<>());
        requestBody.put("DynamicItemsList", List.of(areaMap));

        String url = String.format(urlTemplate, returnKey, version);

        Map response;
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
            log.error("Error submitting payments to CBK", e);
            return null;
        }

        if (response == null) return null;

        response.put("returnKey", returnKey);
        response.put("area", 60);

        String status = String.valueOf(response.get("status"));
        String fileName = String.valueOf(response.get("filename"));

        if (!"N".equalsIgnoreCase(status)) {
            log.warn("Payment submission failed.");
            return response;
        }

        String updateSql = """
                UPDATE custom.forex_payments
                SET POSTED_FLAG = 'Y',
                    CBK_FILENAME = ?,
                    CBK_STATUS = ?,
                    POSTED_DATE = SYSDATE
                WHERE TRANSREFCODE = ?
                AND FOREXTYPE = 'P'
                AND POSTED_FLAG = 'N'
                """;

        try (Connection conn = databaseConnection.dbConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                for (Props p : propsList) {
                    ps.setString(1, fileName);
                    ps.setString(2, status);
                    ps.setString(3, p.getTransRefCode());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) {
            log.error("Error updating posted payment records", e);
        }

        return response;
    }
    private void scheduleStatusCheck(String fileName) {
        Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> checkStatus(fileName), 30, TimeUnit.SECONDS);
    }

    private void checkStatus(String fileName) {

        Properties prop = configurations.getProperties();
        String version = prop.getProperty("version");
        String statusUrlTemplate = prop.getProperty("statusUrl");

        String statusUrl = String.format(statusUrlTemplate, fileName, version);

        try {
            Map response = webClient.get()
                    .uri(statusUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + logIn.getAccessToken())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response != null && response.containsKey("filename")) {

                String status = String.valueOf(response.get("status"));

                String updateSql = """
                    UPDATE custom.forex_payments
                    SET CBK_STATUS = ?
                    WHERE CBK_FILENAME = ?
                    """;

                try (Connection conn = databaseConnection.dbConnection();
                     PreparedStatement ps = conn.prepareStatement(updateSql)) {

                    ps.setString(1, status);
                    ps.setString(2, fileName);
                    ps.executeUpdate();

                    log.info("Updated CBK payment status for file {} → {}", fileName, status);
                }
            } else {
                log.warn("CBK response is null OR missing 'filename' key for file {}", fileName);
            }

        } catch (Exception e) {
            log.error("Error checking CBK payment status", e);
        }
    }

}

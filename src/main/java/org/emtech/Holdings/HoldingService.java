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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    @Scheduled(fixedDelay = 20000)
    public void scheduledHoldingsSubmission() {
        try {
            LocalDate today = LocalDate.now();
            Map<String, Object> response = submitHoldings(today, today);

            if (response != null && response.containsKey("filename")) {
                String fileName = String.valueOf(response.get("filename"));
                scheduleStatusCheck(fileName);
            }

        } catch (Exception e) {
            log.error("Scheduled Holdings submission failed", e);
        }
    }

    public Map<String, Object> submitHoldings(LocalDate start, LocalDate end) {

        Properties prop = configurations.getProperties();
        String urlTemplate = prop.getProperty("submissionUrl");
        String returnKey = prop.getProperty("returnKey_Holdings");
        String version = prop.getProperty("version");
        String instCode = prop.getProperty("InstCode");

        LocalDate firstDayOfPrevMonth = start.minusMonths(1).withDayOfMonth(1);
        LocalDate lastDayOfPrevMonth = start.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());

        String startDate = firstDayOfPrevMonth.atStartOfDay().format(dateFormatter);
        String endDate = lastDayOfPrevMonth.atStartOfDay().format(dateFormatter);

        int finYear = firstDayOfPrevMonth.getYear();

        int batchSize = 50;
        int offset = 0;
        boolean hasMoreRecords = true;

        while (hasMoreRecords) {
            List<Props> batch = new ArrayList<>();

            // Fetch next batch using OFFSET/FETCH NEXT
            String sql = """
            SELECT *
            FROM custom.forex_holdings
            WHERE POSTED_FLAG = 'N' AND FOREXTYPE = 'H'
            ORDER BY ACCOUNTREF
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """;

            try (Connection conn = databaseConnection.dbConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, offset);
                ps.setInt(2, batchSize);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Props p = new Props();
                        p.setAccountRef(rs.getString("ACCOUNTREF"));
                        p.setCustomerName(rs.getString("CUSTOMERNAME"));
                        p.setAccountType(rs.getString("ACCOUNTTYPE"));
                        p.setCurrency(rs.getString("CURRENCY"));
                        p.setAmount(rs.getString("AMOUNT"));
                        p.setCross(rs.getString("CROSSRATE"));
                        p.setSector(rs.getString("SECTORCODE"));
                        batch.add(p);
                    }
                }

            } catch (Exception e) {
                log.error("Error fetching holdings from DB", e);
                return null;
            }

            if (batch.isEmpty()) {
                hasMoreRecords = false;
                log.info("All holdings have been submitted.");
                break;
            }

            // Build dynamic items for this batch
            List<Map<String, Object>> dynamicItems = new ArrayList<>();
            int rowNumber = 1;
            for (Props p : batch) {
                dynamicItems.add(Map.of("Code", rowNumber + ".1", "Value", p.getAccountRef()));
                dynamicItems.add(Map.of("Code", rowNumber + ".2", "Value", p.getCustomerName()));
                dynamicItems.add(Map.of("Code", rowNumber + ".3", "Value", p.getCurrency()));
                dynamicItems.add(Map.of("Code", rowNumber + ".4", "Value", p.getAmount()));
                dynamicItems.add(Map.of("Code", rowNumber + ".5", "Value", p.getCross()));
                dynamicItems.add(Map.of("Code", rowNumber + ".6", "Value", p.getAccountType()));
                dynamicItems.add(Map.of("Code", rowNumber + ".7", "Value", p.getSector()));
                rowNumber++;
            }

            Map<String, Object> areaMap = new HashMap<>();
            areaMap.put("Area", 64);
            areaMap.put("_areaName", "FCY Holdings Area");
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

            try {
                Map response = webClient.post()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logIn.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (response != null) {
                    String status = String.valueOf(response.get("status"));
                    String fileName = String.valueOf(response.get("filename"));

                    if ("N".equalsIgnoreCase(status)) {

                        updatePostedRecords(batch, fileName, status);
                        // Schedule status check
                        scheduleStatusCheck(fileName);
                    } else {
                        log.warn("CBK returned status '{}' for batch starting with record {}", status, batch.get(0).getAccountRef());
                    }

                } else {
                    log.warn("CBK returned null response for batch starting with record {}", batch.get(0).getAccountRef());
                }

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                log.error("Batch starting with {} failed with {}: {}", batch.get(0).getAccountRef(), e.getRawStatusCode(), e.getResponseBodyAsString());
                System.out.println("Failed request body: " + requestBody);
            } catch (Exception e) {
                log.error("Unexpected error submitting batch starting with {}", batch.get(0).getAccountRef(), e);
            }

            // Increment offset for next batch
            offset += batch.size();
        }
        return null;
    }
    private void updatePostedRecords(List<Props> batch, String fileName, String status) {
        if (batch.isEmpty()) return;

        // Build a list of placeholders for prepared statement
        String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));

        String sql = """
        UPDATE custom.forex_holdings
        SET posted_flag = 'Y',
            cbk_filename = ?,
            cbk_status = ?,
            posted_date = SYSDATE
        WHERE FOREXTYPE = 'H'
          AND POSTED_FLAG = 'N'
          AND ACCOUNTREF IN (%s)
    """.formatted(placeholders);

        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int index = 1;
            ps.setString(index++, fileName);  // First parameter: fileName
            ps.setString(index++, status);    // Second parameter: status

            // Fill in the placeholders with account references
            for (Props p : batch) {
                ps.setString(index++, p.getAccountRef());
            }

            int updatedRows = ps.executeUpdate();
            log.info("Updated {} records as posted for batch starting with {}", updatedRows, batch.get(0).getAccountRef());

        } catch (Exception e) {
            log.error("Error updating posted records for batch starting with {}", batch.get(0).getAccountRef(), e);
        }
    }




    private void scheduleStatusCheck(String fileName) {
        Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> checkStatus(fileName), 30, TimeUnit.SECONDS);
    }

    private void checkStatus(String fileName) {
        try {
            Properties prop = configurations.getProperties();
            String version = prop.getProperty("version");
            String statusUrlTemplate = prop.getProperty("statusUrl");

            String statusUrl = String.format(statusUrlTemplate, fileName, version);

            Map response = webClient.get()
                    .uri(statusUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + logIn.getAccessToken())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                String status = String.valueOf(response.get("status"));

                try (Connection conn = databaseConnection.dbConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE custom.forex_holdings SET cbk_status = ? WHERE cbk_filename = ?")) {

                    ps.setString(1, status);
                    ps.setString(2, fileName);
                    ps.executeUpdate();
                }
            }

        } catch (Exception e) {
            log.error("Error checking holdings status", e);
        }
    }
}

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
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.UnknownHostException;
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

    @Scheduled(fixedDelay = 30000)
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

        String sql = """
        SELECT *
        FROM custom.forex_holdings
        WHERE POSTED_FLAG = 'N'
        AND FOREXTYPE = 'H'
    """;

        LocalDate firstDayOfPrevMonth = start.minusMonths(1).withDayOfMonth(1);
        LocalDate lastDayOfPrevMonth  = start.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());

        String
                startDate = firstDayOfPrevMonth.atStartOfDay().format(dateFormatter);
        String endDate   = lastDayOfPrevMonth.atStartOfDay().format(dateFormatter);
        int finYear      = firstDayOfPrevMonth.getYear();

        List<Props> allRecords = new ArrayList<>();

        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Props p = new Props();
                p.setAccountRef(rs.getString("ACCOUNTREF"));
                p.setCustomerName(rs.getString("CUSTOMERNAME"));
                p.setAccountType(rs.getString("ACCOUNTTYPE"));
                p.setCurrency(rs.getString("CURRENCY"));
                p.setAmount(rs.getString("AMOUNT"));
                p.setCross(rs.getString("CROSSRATE"));

                String sectorCode = rs.getString("SECTORCODE");
                p.setSector(sectorCode);
                p.setSectorDescription(SECTOR_MAPH.getOrDefault(sectorCode, "UNKNOWN"));

                allRecords.add(p);
            }

        } catch (Exception e) {
            log.error("Error fetching holdings from DB", e);
            return null;
        }

        if (allRecords.isEmpty()) {
            log.info("No unposted holdings found.");
            return null;
        }

      //  log.info("Total holdings to submit: ++++++++++++++++++++++++++++++++++++++++++++++++++++{}", allRecords.size());


        int batchSize = 50;
        Map<String, Object> lastResponse = null;

        for (int i = 0; i < allRecords.size(); i += batchSize) {

            List<Props> batch = allRecords.subList(i, Math.min(i + batchSize, allRecords.size()));
            int batchNumber = (i / batchSize) + 1;
           // log.info("Submitting batch________________________________________________________ {} ({} records)", batchNumber, batch.size());

            List<Map<String, Object>> dynamicItems = new ArrayList<>();
            int row = 1;

            for (Props p : batch) {
                addIfNotNull(dynamicItems, row + ".1",  p.getAccountRef());
                addIfNotNull(dynamicItems, row + ".2",  p.getCustomerName());
                addIfNotNull(dynamicItems, row + ".3",  p.getCurrency());
                addIfNotNull(dynamicItems, row + ".4",  p.getAmount());
                addIfNotNull(dynamicItems, row + ".5",  p.getCross());
                addIfNotNull(dynamicItems, row + ".7",  p.getAccountType());
                addIfNotNull(dynamicItems, row + ".10", p.getSector());
                addIfNotNull(dynamicItems, row + ".11", p.getSectorDescription());
                row++;
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
            try {
                String payloadJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(requestBody);

                //  log.info("Batch {} Payload:_________________________________________________________________________________________\n{}", batchNumber, payloadJson);

            } catch (Exception e) {
             //   log.error("Batch {} Failed to print payload", batchNumber, e);
            }

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

            } catch (WebClientRequestException e) {
                if (e.getCause() instanceof UnknownHostException) {
                    log.error("Batch {}: No connection to CBK (DNS failure).", batchNumber);
                } else {
                    log.error("Batch {}: Network error: {}", batchNumber, e.getMessage());
                }
                continue; // try next batch

            } catch (Exception e) {
               // log.error("Batch {}: Error submitting to CBK", batchNumber, e);
                continue;
            }

            if (response == null) {
                log.warn("Batch {}: CBK returned null response", batchNumber);
                continue;
            }

            response.put("returnKey", returnKey);
            response.put("area", 64);

            String status   = String.valueOf(response.get("status"));
            String fileName = String.valueOf(response.get("filename"));

           // log.info("Batch {}: status={}, fileName={}", batchNumber, status, fileName);

            if (!"N".equalsIgnoreCase(status)) {
              //  log.warn("Batch {}: submission failed. Status: {}", batchNumber, status);
                continue;
            }

            // Update only this batch's records
            String updateSql = """
            UPDATE custom.forex_holdings
            SET POSTED_FLAG = 'Y',
                CBK_FILENAME = ?,
                CBK_STATUS = ?,
                POSTED_DATE = SYSDATE
            WHERE ACCOUNTREF = ?
            AND FOREXTYPE = 'H'
            AND POSTED_FLAG = 'N'
            """;

            try (Connection conn = databaseConnection.dbConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    for (Props p : batch) {
                        ps.setString(1, fileName);
                        ps.setString(2, status);
                        ps.setString(3, p.getAccountRef());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    conn.commit();
              //      log.info("Batch {}: {} records marked as posted.", batchNumber, batch.size());
                }
            } catch (Exception e) {
                log.error("Batch {}: Error updating posted records", batchNumber, e);
            }

            if (fileName != null && !"null".equalsIgnoreCase(fileName)) {
                scheduleStatusCheck(fileName);
            }

            lastResponse = response;
        }

        return lastResponse;
    }

    private void scheduleStatusCheck(String fileName) {
        Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> checkStatus(fileName), 5, TimeUnit.MINUTES);
    }
    private void addIfNotNull(List<Map<String, Object>> list, String code, Object value) {
        if (value != null && !value.toString().trim().isEmpty()) {
            Map<String, Object> map = new HashMap<>();
            map.put("Code", code);
            map.put("Value", value);
            list.add(map);
        }
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
    private static final Map<String, String> SECTOR_MAPH = Map.ofEntries(
            Map.entry("HG01", "Livestock and Animal Products"),
            Map.entry("HS07", "Other services"),
            Map.entry("HG08", "Construction Materials"),
            Map.entry("HG11", "Electronics and ICT"),
            Map.entry("HS03", "Financial services"),
            Map.entry("HT03",  "Transfers"),
            Map.entry("HG06", "Manufactured goods"),
            Map.entry("HG03", "Oil and Allied"),
            Map.entry("HS02", "Travel and tourism services"),
            Map.entry("HS01", "Transport Services"),
            Map.entry("HG14", "Food and Beverages")
    );
}

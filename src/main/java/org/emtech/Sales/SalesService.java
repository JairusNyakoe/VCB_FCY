
package org.emtech.Sales;

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
        import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SalesService {

    private final WebClient webClient;
    private final Configurations configurations;
    private final LogIn logIn;
    private final DatabaseConnection databaseConnection;
    private final ObjectMapper objectMapper;

    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00");

    public SalesService(WebClient webClient,
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

    // =============================
    // Scheduled submission
    // =============================
    @Scheduled(fixedDelay = 20000)
    public void scheduledSalesSubmission() {
        try {
            LocalDate today = LocalDate.now();
            Map<String, Object> response = submitSales(today, today);

            if (response != null && response.containsKey("filename")) {
                scheduleStatusCheck(String.valueOf(response.get("filename")));
            }

        } catch (Exception e) {
            log.error("Scheduled sales submission failed", e);
        }
    }

    public Map<String, Object> submitSales(LocalDate start, LocalDate end) {

        Properties prop = configurations.getProperties();

        String urlTemplate = prop.getProperty("submissionUrl");
        String returnKey = prop.getProperty("returnKey_Sales");
        String version = prop.getProperty("version");
        String instCode = prop.getProperty("InstCode");
        String sql = prop.getProperty("sqlFetchSales");

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
                p.setInterBankCodes(rs.getString("INTERBANK_CODES"));
                p.setSector(rs.getString("SECTORCODE"));
                propsList.add(p);
            }

        } catch (Exception e) {
            log.error("Error fetching sales", e);
            return null;
        }

        if (propsList.isEmpty()) {
            log.info("No sales found.");
            return null;
        }

        // Build payload
        List<Map<String, Object>> dynamicItems = new ArrayList<>();
        int row = 1;

        for (Props p : propsList) {
            dynamicItems.add(Map.of("Code", row + ".1", "Value", p.getTransRefCode()));
            dynamicItems.add(Map.of("Code", row + ".2", "Value", p.getCustomerName()));
            dynamicItems.add(Map.of("Code", row + ".3", "Value", p.getCurrency()));
            dynamicItems.add(Map.of("Code", row + ".4", "Value", p.getAmount()));
            dynamicItems.add(Map.of("Code", row + ".5", "Value", p.getTimestamp()));
            dynamicItems.add(Map.of("Code", row + ".6", "Value", p.getSpotExchangerate()));
            dynamicItems.add(Map.of("Code", row + ".7", "Value", p.getCross()));
            dynamicItems.add(Map.of("Code", row + ".8", "Value", p.getUsdEquivalent()));
            dynamicItems.add(Map.of("Code", row + ".9", "Value", p.getInterBankCodes()));
            dynamicItems.add(Map.of("Code", row + ".10", "Value", p.getSector()));
            row++;
        }

        Map<String, Object> area = new HashMap<>();
        area.put("Area", 62);
        area.put("_areaName", "Forex Sales Area");
        area.put("DynamicItems", dynamicItems);

        Map<String, Object> body = new HashMap<>();
        body.put("ReturnKey", returnKey);
        body.put("InstCode", instCode);
        body.put("FinYear", finYear);
        body.put("StartDate", startDate);
        body.put("EndDate", endDate);
        body.put("ReturnItemsList", new ArrayList<>());
        body.put("DynamicItemsList", List.of(area));

        String url = String.format(urlTemplate, returnKey, version);
        Map response = webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + logIn.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) return null;

        String status = String.valueOf(response.get("status"));
        String fileName = String.valueOf(response.get("filename"));

        System.out.println("the status sales filne+++++++++++++++++++++++++++++ "+response.get("filename"));

        if ("N".equalsIgnoreCase(status)) {
            updatePostedRecords(fileName, status, propsList);
        }

        return response;
    }

    private void updatePostedRecords(String fileName, String status, List<Props> list) {
        String sql = """
            UPDATE custom.forex_sales
            SET posted_flag='Y', cbk_filename=?, cbk_status=?, posted_date=SYSDATE
            WHERE TRANSREFCODE=? AND posted_flag='N'
            """;

        try (Connection conn = databaseConnection.dbConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (Props p : list) {
                ps.setString(1, fileName);
                ps.setString(2, status);
                ps.setString(3, p.getTransRefCode());
                ps.addBatch();
            }
            ps.executeBatch();

        } catch (Exception e) {
            log.error("Error updating posted sales", e);
        }
    }

    private void scheduleStatusCheck(String fileName) {
        Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> checkStatus(fileName), 30, TimeUnit.SECONDS);
    }

    private void checkStatus(String fileName) {
        try {
            Properties prop = configurations.getProperties();
            String url = String.format(prop.getProperty("statusUrl"), fileName, prop.getProperty("version"));

            Map response = webClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + logIn.getAccessToken())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                String status = String.valueOf(response.get("status"));
                System.out.println("this is the final status++++++++++++++++++++++++++++++++++++++="+ response.get("status"));
                try (Connection conn = databaseConnection.dbConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE custom.forex_sales SET cbk_status=? WHERE cbk_filename=?")) {
                    ps.setString(1, status);
                    ps.setString(2, fileName);
                    ps.executeUpdate();
                }
            }

        } catch (Exception e) {
            log.error("Status check failed", e);
        }
    }
}

package com.matbao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matbao.model.WeatherData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gọi Python FastAPI AI service để lấy dự đoán mức cảnh báo.
 * Nếu AI service không chạy, fallback về rule-based.
 */
@Service
public class AiPredictionService {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionService.class);

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gọi AI Python để dự đoán mức cảnh báo cho 1 trạm.
     * Trả về Map chứa alert_level, alert_label, confidence, message.
     */
    public Map<String, Object> predict(String stationId, WeatherData.CurrentWeather current) {
        try {
            // Build request body
            Map<String, Object> body = buildRequestBody(stationId, current);
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceUrl + "/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                JsonNode result = objectMapper.readTree(response.body());
                log.debug("AI prediction cho {}: level={}, confidence={}",
                        stationId,
                        result.get("alert_level").asInt(),
                        result.get("confidence").asDouble());
                return parseAiResponse(result);
            } else {
                log.warn("AI service trả lỗi {}, dùng rule-based fallback", response.statusCode());
                return ruleBasedFallback(current);
            }

        } catch (Exception e) {
            log.warn("AI service không kết nối được ({}), dùng rule-based fallback", e.getMessage());
            return ruleBasedFallback(current);
        }
    }

    /**
     * Kiểm tra AI service còn sống không.
     */
    public boolean isAiServiceAlive() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceUrl + "/health"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // =============================================
    // Helpers
    // =============================================
    private Map<String, Object> buildRequestBody(String stationId, WeatherData.CurrentWeather c) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> body = new HashMap<>();
        body.put("station_id",   stationId);
        body.put("temperature",  c.getTemperature());
        body.put("humidity",     c.getHumidity());
        body.put("precipitation",c.getPrecipitation());
        body.put("wind_speed",   c.getWindSpeed());
        body.put("wind_direction",c.getWindDirection());
        body.put("pressure",     c.getPressure());
        body.put("wind_gusts",   c.getWindSpeed() * 1.3); // Ước tính nếu không có dữ liệu
        body.put("cloud_cover",  70.0);
        body.put("hour",         now.getHour());
        body.put("month",        now.getMonthValue());
        // Rolling features — Spring Boot chưa tính nên để 0, AI sẽ bù bằng features khác
        body.put("precipitation_3h_avg", c.getPrecipitation());
        body.put("precipitation_3h_max", c.getPrecipitation());
        body.put("wind_speed_3h_avg",    c.getWindSpeed());
        body.put("wind_speed_3h_max",    c.getWindSpeed());
        return body;
    }

    private Map<String, Object> parseAiResponse(JsonNode node) {
        Map<String, Object> result = new HashMap<>();
        result.put("alert_level",    node.get("alert_level").asInt());
        result.put("alert_label",    node.get("alert_label").asText());
        result.put("alert_color",    node.get("alert_color").asText());
        result.put("confidence",     node.get("confidence").asDouble());
        result.put("message",        node.get("message").asText());
        result.put("model_name",     node.get("model_name").asText());
        result.put("model_accuracy", node.get("model_accuracy").asDouble());
        result.put("source",         "AI_MODEL");

        // Probabilities
        JsonNode probs = node.get("probabilities");
        if (probs != null) {
            Map<String, Double> probMap = new HashMap<>();
            probMap.put("binh_thuong", probs.get("binh_thuong").asDouble());
            probMap.put("canh_bao",    probs.get("canh_bao").asDouble());
            probMap.put("nguy_hiem",   probs.get("nguy_hiem").asDouble());
            result.put("probabilities", probMap);
        }
        return result;
    }

    /**
     * Fallback khi AI service không chạy — dùng rule-based đơn giản.
     */
    private Map<String, Object> ruleBasedFallback(WeatherData.CurrentWeather current) {
        Map<String, Object> result = new HashMap<>();
        int level = 0;
        String label = "Bình thường";
        String color = "green";
        String message = "Thời tiết bình thường.";

        if (current.getPrecipitation() >= 50 || current.getWindSpeed() >= 90) {
            level = 2; label = "Nguy hiểm"; color = "red";
            message = "NGUY HIỂM — Sơ tán ngay nếu ở vùng trũng thấp!";
        } else if (current.getPrecipitation() >= 25 || current.getWindSpeed() >= 60
                || (current.getHumidity() >= 90 && current.getPrecipitation() >= 15)) {
            level = 1; label = "Cảnh báo"; color = "yellow";
            message = "CẢNH BÁO — Theo dõi chặt chẽ, chuẩn bị ứng phó!";
        }

        result.put("alert_level",    level);
        result.put("alert_label",    label);
        result.put("alert_color",    color);
        result.put("confidence",     1.0);
        result.put("message",        message);
        result.put("model_name",     "Rule-Based Fallback");
        result.put("model_accuracy", 0.0);
        result.put("source",         "RULE_BASED");
        return result;
    }
}

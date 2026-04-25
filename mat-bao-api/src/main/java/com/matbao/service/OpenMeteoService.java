package com.matbao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matbao.model.WeatherData;
import com.matbao.model.AlertLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpenMeteoService {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoService.class);

    @Value("${openmeteo.base-url}")
    private String baseUrl;

    @Value("${openmeteo.hourly-params}")
    private String hourlyParams;

    @Value("${openmeteo.daily-params}")
    private String dailyParams;

    @Value("${alert.rain.warning}")
    private double rainWarningThreshold;

    @Value("${alert.rain.danger}")
    private double rainDangerThreshold;

    @Value("${alert.wind.warning}")
    private double windWarningThreshold;

    @Value("${alert.wind.danger}")
    private double windDangerThreshold;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // =============================================
    // Lấy dữ liệu thời tiết theo tọa độ
    // Cache 1h để không spam API
    // =============================================
    @Cacheable(value = "weatherCache", key = "#lat + '_' + #lon")
    public WeatherData fetchWeatherData(double lat, double lon, String locationName) {
        try {
            String url = buildUrl(lat, lon);
            log.info("Đang gọi Open-Meteo API: lat={}, lon={}, location={}", lat, lon, locationName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Open-Meteo trả lỗi: {}", response.statusCode());
                throw new RuntimeException("API lỗi: " + response.statusCode());
            }

            return parseResponse(response.body(), lat, lon, locationName);

        } catch (Exception e) {
            log.error("Lỗi khi gọi Open-Meteo: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể lấy dữ liệu thời tiết", e);
        }
    }

    // =============================================
    // Build URL cho Open-Meteo API
    // =============================================
    private String buildUrl(double lat, double lon) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam("hourly", hourlyParams)
                .queryParam("daily", dailyParams)
                .queryParam("timezone", "Asia/Ho_Chi_Minh")
                .queryParam("forecast_days", 7)
                .queryParam("wind_speed_unit", "kmh")
                .build()
                .toUriString();
    }

    // =============================================
    // Parse JSON từ Open-Meteo → WeatherData
    // =============================================
    private WeatherData parseResponse(String json, double lat, double lon, String locationName) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode hourlyNode = root.get("hourly");
        JsonNode dailyNode = root.get("daily");

        // Lấy index hiện tại (giờ gần nhất)
        int currentIndex = getCurrentHourIndex(hourlyNode);

        // --- Current weather ---
        WeatherData.CurrentWeather current = WeatherData.CurrentWeather.builder()
                .temperature(getDouble(hourlyNode, "temperature_2m", currentIndex))
                .humidity(getDouble(hourlyNode, "relative_humidity_2m", currentIndex))
                .precipitation(getDouble(hourlyNode, "precipitation", currentIndex))
                .windSpeed(getDouble(hourlyNode, "wind_speed_10m", currentIndex))
                .windDirection(getDouble(hourlyNode, "wind_direction_10m", currentIndex))
                .pressure(getDouble(hourlyNode, "surface_pressure", currentIndex))
                .condition(describeCondition(
                        getDouble(hourlyNode, "precipitation", currentIndex),
                        getDouble(hourlyNode, "wind_speed_10m", currentIndex)))
                .build();

        // --- Hourly (24 giờ gần nhất cho biểu đồ) ---
        List<WeatherData.HourlyWeather> hourlyList = new ArrayList<>();
        JsonNode times = hourlyNode.get("time");
        int start = Math.max(0, currentIndex - 23);
        for (int i = start; i <= currentIndex; i++) {
            hourlyList.add(WeatherData.HourlyWeather.builder()
                    .time(formatTime(times.get(i).asText()))
                    .temperature(getDouble(hourlyNode, "temperature_2m", i))
                    .humidity(getDouble(hourlyNode, "relative_humidity_2m", i))
                    .precipitation(getDouble(hourlyNode, "precipitation", i))
                    .windSpeed(getDouble(hourlyNode, "wind_speed_10m", i))
                    .build());
        }

        // --- Daily (7 ngày tới) ---
        List<WeatherData.DailyForecast> dailyList = new ArrayList<>();
        JsonNode dailyTimes = dailyNode.get("time");
        for (int i = 0; i < dailyTimes.size(); i++) {
            dailyList.add(WeatherData.DailyForecast.builder()
                    .date(dailyTimes.get(i).asText())
                    .maxTemp(getDouble(dailyNode, "temperature_2m_max", i))
                    .minTemp(getDouble(dailyNode, "temperature_2m_min", i))
                    .totalPrecipitation(getDouble(dailyNode, "precipitation_sum", i))
                    .maxWindSpeed(getDouble(dailyNode, "wind_speed_10m_max", i))
                    .build());
        }

        // --- Alert ---
        WeatherData.AlertInfo alert = calculateAlert(current);

        return WeatherData.builder()
                .latitude(lat)
                .longitude(lon)
                .locationName(locationName)
                .fetchedAt(LocalDateTime.now())
                .current(current)
                .hourly(hourlyList)
                .daily(dailyList)
                .alert(alert)
                .build();
    }

    // =============================================
    // Tính mức cảnh báo dựa trên ngưỡng cấu hình
    // =============================================
    private WeatherData.AlertInfo calculateAlert(WeatherData.CurrentWeather current) {
        AlertLevel level = AlertLevel.NORMAL;
        List<String> reasons = new ArrayList<>();

        // Kiểm tra lượng mưa
        if (current.getPrecipitation() >= rainDangerThreshold) {
            level = AlertLevel.DANGER;
            reasons.add(String.format("Mưa cực lớn: %.1f mm/h (ngưỡng nguy hiểm: %.0f mm/h)",
                    current.getPrecipitation(), rainDangerThreshold));
        } else if (current.getPrecipitation() >= rainWarningThreshold) {
            if (level.getSeverity() < AlertLevel.WARNING.getSeverity()) level = AlertLevel.WARNING;
            reasons.add(String.format("Mưa to: %.1f mm/h", current.getPrecipitation()));
        }

        // Kiểm tra tốc độ gió
        if (current.getWindSpeed() >= windDangerThreshold) {
            level = AlertLevel.DANGER;
            reasons.add(String.format("Gió rất mạnh: %.1f km/h (cấp bão)", current.getWindSpeed()));
        } else if (current.getWindSpeed() >= windWarningThreshold) {
            if (level.getSeverity() < AlertLevel.WARNING.getSeverity()) level = AlertLevel.WARNING;
            reasons.add(String.format("Gió mạnh: %.1f km/h (cấp 7-8)", current.getWindSpeed()));
        }

        // Kiểm tra độ ẩm cao + mưa (nguy cơ lũ quét)
        if (current.getHumidity() > 90 && current.getPrecipitation() > 20) {
            if (level.getSeverity() < AlertLevel.WARNING.getSeverity()) level = AlertLevel.WARNING;
            reasons.add("Độ ẩm bão hòa kết hợp mưa lớn — nguy cơ lũ quét");
        }

        if (reasons.isEmpty()) {
            reasons.add("Điều kiện thời tiết bình thường");
        }

        String message = switch (level) {
            case DANGER -> "NGUY HIỂM — Sơ tán ngay nếu ở vùng trũng thấp!";
            case WARNING -> "CẢNH BÁO — Theo dõi chặt chẽ, chuẩn bị ứng phó!";
            case NORMAL -> "Thời tiết bình thường, không có cảnh báo.";
        };

        return WeatherData.AlertInfo.builder()
                .level(level)
                .message(message)
                .color(level.getColor())
                .reasons(reasons)
                .triggeredAt(LocalDateTime.now())
                .build();
    }

    // =============================================
    // Helper methods
    // =============================================
    private double getDouble(JsonNode node, String field, int index) {
        try {
            JsonNode arr = node.get(field);
            if (arr != null && arr.size() > index && !arr.get(index).isNull()) {
                return arr.get(index).asDouble();
            }
        } catch (Exception e) {
            log.warn("Không đọc được field {}: {}", field, e.getMessage());
        }
        return 0.0;
    }

    private int getCurrentHourIndex(JsonNode hourlyNode) {
        JsonNode times = hourlyNode.get("time");
        if (times == null) return 0;

        String nowStr = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"));

        for (int i = 0; i < times.size(); i++) {
            if (times.get(i).asText().equals(nowStr)) return i;
        }
        // Fallback: trả về index giữa nếu không tìm được
        return Math.min(12, times.size() - 1);
    }

    private String formatTime(String isoTime) {
        // "2025-04-25T14:00" → "14:00"
        if (isoTime != null && isoTime.contains("T")) {
            return isoTime.split("T")[1];
        }
        return isoTime;
    }

    private String describeCondition(double rain, double wind) {
        if (rain >= 50 || wind >= 90) return "Bão mạnh";
        if (rain >= 30 || wind >= 60) return "Mưa to / Gió mạnh";
        if (rain >= 10) return "Mưa vừa";
        if (rain > 0) return "Mưa nhỏ";
        return "Trời quang";
    }
}

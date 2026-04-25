package com.matbao.controller;

import com.matbao.model.Station;
import com.matbao.model.WeatherData;
import com.matbao.service.OpenMeteoService;
import com.matbao.service.StationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class WeatherController {

    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);

    private final OpenMeteoService openMeteoService;
    private final StationService stationService;

    public WeatherController(OpenMeteoService openMeteoService, StationService stationService) {
        this.openMeteoService = openMeteoService;
        this.stationService = stationService;
    }

    // =============================================
    // GET /api/v1/weather
    // Lấy thời tiết theo tọa độ tùy chỉnh
    // Ví dụ: /api/v1/weather?lat=21.0245&lon=105.8412&name=Hà+Nội
    // =============================================
    @GetMapping("/weather")
    public ResponseEntity<WeatherData> getWeather(
            @RequestParam(defaultValue = "17.4833") double lat,
            @RequestParam(defaultValue = "106.6000") double lon,
            @RequestParam(defaultValue = "Quảng Bình") String name) {

        log.info("GET /weather — lat={}, lon={}, name={}", lat, lon, name);
        WeatherData data = openMeteoService.fetchWeatherData(lat, lon, name);
        return ResponseEntity.ok(data);
    }

    // =============================================
    // GET /api/v1/dashboard
    // Endpoint chính cho dashboard — trả về overview tất cả trạm
    // =============================================
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        log.info("GET /dashboard");

        List<Station> stations = stationService.getAllStationsWithData();

        // Tính summary toàn hệ thống
        long dangerCount = stations.stream()
                .filter(s -> s.getLatestData() != null &&
                        s.getLatestData().getAlert() != null &&
                        s.getLatestData().getAlert().getLevel().name().equals("DANGER"))
                .count();

        long warningCount = stations.stream()
                .filter(s -> s.getLatestData() != null &&
                        s.getLatestData().getAlert() != null &&
                        s.getLatestData().getAlert().getLevel().name().equals("WARNING"))
                .count();

        long onlineCount = stations.stream()
                .filter(s -> s.getStatus() != Station.StationStatus.OFFLINE)
                .count();

        Map<String, Object> response = new HashMap<>();
        response.put("totalStations", stations.size());
        response.put("onlineStations", onlineCount);
        response.put("dangerZones", dangerCount);
        response.put("warningZones", warningCount);
        response.put("stations", stations);
        response.put("systemStatus", dangerCount > 0 ? "DANGER" : warningCount > 0 ? "WARNING" : "NORMAL");

        return ResponseEntity.ok(response);
    }

    // =============================================
    // GET /api/v1/stations
    // Danh sách tất cả trạm IoT
    // =============================================
    @GetMapping("/stations")
    public ResponseEntity<List<Station>> getAllStations() {
        return ResponseEntity.ok(stationService.getAllStationsWithData());
    }

    // =============================================
    // GET /api/v1/stations/{id}
    // Chi tiết 1 trạm (dùng khi click vào trạm trên map)
    // =============================================
    @GetMapping("/stations/{id}")
    public ResponseEntity<?> getStation(@PathVariable String id) {
        Station station = stationService.getStationById(id);
        if (station == null) {
            return ResponseEntity.notFound().build();
        }
        // Refresh data
        if (station.getStatus() != Station.StationStatus.OFFLINE) {
            WeatherData fresh = openMeteoService.fetchWeatherData(
                    station.getLatitude(), station.getLongitude(), station.getName());
            station.setLatestData(fresh);
        }
        return ResponseEntity.ok(station);
    }

    // =============================================
    // GET /api/v1/alerts
    // Chỉ trả về các trạm đang có cảnh báo
    // =============================================
    @GetMapping("/alerts")
    public ResponseEntity<List<Station>> getActiveAlerts() {
        List<Station> alertStations = stationService.getAllStationsWithData().stream()
                .filter(s -> s.getLatestData() != null &&
                        s.getLatestData().getAlert() != null &&
                        !s.getLatestData().getAlert().getLevel().name().equals("NORMAL"))
                .toList();
        return ResponseEntity.ok(alertStations);
    }

    // =============================================
    // GET /api/v1/health
    // Kiểm tra API còn sống không
    // =============================================
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Mat Bao API",
                "version", "1.0.0"
        ));
    }
}

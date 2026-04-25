package com.matbao.service;

import com.matbao.model.Station;
import com.matbao.model.WeatherData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StationService {

    private static final Logger log = LoggerFactory.getLogger(StationService.class);

    private final OpenMeteoService openMeteoService;
    private final AiPredictionService aiPredictionService;

    public StationService(OpenMeteoService openMeteoService, AiPredictionService aiPredictionService) {
        this.openMeteoService = openMeteoService;
        this.aiPredictionService = aiPredictionService;
    }

    // Lưu dữ liệu các trạm trong memory (production nên dùng DB)
    private final Map<String, Station> stationMap = new ConcurrentHashMap<>();

    // =============================================
    // Danh sách trạm cố định (mô phỏng IoT thật)
    // Production: đọc từ DB
    // =============================================
    @PostConstruct
    public void initStations() {
        List<Station> stations = List.of(
            Station.builder().id("TH-01").name("Trạm Tân Hóa").district("Quảng Bình")
                .latitude(17.7833).longitude(106.0167).status(Station.StationStatus.ONLINE).build(),
            Station.builder().id("MH-03").name("Trạm Minh Hóa").district("Quảng Bình")
                .latitude(17.8167).longitude(105.9833).status(Station.StationStatus.ONLINE).build(),
            Station.builder().id("TH-07").name("Trạm Đồng Lê").district("Tuyên Hóa")
                .latitude(17.8500).longitude(105.8833).status(Station.StationStatus.ONLINE).build(),
            Station.builder().id("QB-12").name("Trạm Ba Đồn").district("Quảng Trạch")
                .latitude(17.7500).longitude(106.4167).status(Station.StationStatus.ONLINE).build(),
            Station.builder().id("QB-09").name("Trạm Lệ Thủy").district("Lệ Thủy")
                .latitude(17.1000).longitude(106.8000).status(Station.StationStatus.OFFLINE).build(),
            Station.builder().id("QB-15").name("Trạm Bố Trạch").district("Bố Trạch")
                .latitude(17.5833).longitude(106.3667).status(Station.StationStatus.ONLINE).build()
        );

        stations.forEach(s -> stationMap.put(s.getId(), s));
        log.info("Đã khởi tạo {} trạm IoT", stationMap.size());
    }

    // =============================================
    // Lấy tất cả trạm kèm dữ liệu thời tiết
    // =============================================
    public List<Station> getAllStationsWithData() {
        List<Station> result = new ArrayList<>();
        for (Station station : stationMap.values()) {
            if (station.getStatus() == Station.StationStatus.OFFLINE) {
                result.add(station); // Trả về nhưng không có data
                continue;
            }
            try {
                WeatherData data = openMeteoService.fetchWeatherData(
                        station.getLatitude(),
                        station.getLongitude(),
                        station.getName()
                );
                // Cập nhật status dựa trên mức cảnh báo
                if (data.getAlert() != null) {
                    switch (data.getAlert().getLevel()) {
                        case DANGER, WARNING -> station.setStatus(Station.StationStatus.WARNING);
                        case NORMAL -> station.setStatus(Station.StationStatus.ONLINE);
                    }
                }
                // Gọi AI để dự đoán
                Map<String, Object> aiResult = aiPredictionService.predict(station.getId(), data.getCurrent());
                if (data.getAlert() != null && aiResult.containsKey("model_name")) {
                    data.getAlert().setModelName((String) aiResult.get("model_name"));
                    data.getAlert().setConfidence((Double) aiResult.get("confidence"));
                }
                station.setLatestData(data);
                stationMap.put(station.getId(), station);
                result.add(station);
            } catch (Exception e) {
                log.error("Lỗi lấy data trạm {}: {}", station.getId(), e.getMessage());
                station.setStatus(Station.StationStatus.OFFLINE);
                result.add(station);
            }
        }
        return result;
    }

    public Station getStationById(String id) {
        return stationMap.get(id);
    }

    public Map<String, Station> getAllStations() {
        return stationMap;
    }
}

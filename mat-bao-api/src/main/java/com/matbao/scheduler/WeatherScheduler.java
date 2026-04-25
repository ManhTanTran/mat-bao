package com.matbao.scheduler;

import com.matbao.service.StationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class WeatherScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeatherScheduler.class);

    private final StationService stationService;

    public WeatherScheduler(StationService stationService) {
        this.stationService = stationService;
    }

    // =============================================
    // Tự động fetch dữ liệu mỗi 2 tiếng
    // Đúng với chu kỳ thu thập dữ liệu của dự án
    // cron = "0 0 */2 * * *" → mỗi 2 giờ đúng
    // =============================================
    @Scheduled(cron = "0 0 */2 * * *")
    @CacheEvict(value = "weatherCache", allEntries = true)
    public void refreshAllStations() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        log.info("=== [{}] Bắt đầu cập nhật dữ liệu tất cả trạm ===", now);

        try {
            var stations = stationService.getAllStationsWithData();
            long online = stations.stream()
                    .filter(s -> s.getStatus() != com.matbao.model.Station.StationStatus.OFFLINE)
                    .count();
            log.info("=== Cập nhật xong: {}/{} trạm online ===", online, stations.size());
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật định kỳ: {}", e.getMessage(), e);
        }
    }

    // =============================================
    // Xóa cache mỗi giờ để dữ liệu luôn mới
    // =============================================
    @Scheduled(cron = "0 0 * * * *")
    @CacheEvict(value = "weatherCache", allEntries = true)
    public void clearCache() {
        log.debug("Cache thời tiết đã được xóa lúc {}", LocalDateTime.now());
    }
}

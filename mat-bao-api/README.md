# Mắt Bão — Backend API

Hệ thống thu thập và xử lý dữ liệu thời tiết thật từ Open-Meteo API cho dashboard cảnh báo thiên tai.

## Yêu cầu

- Java 17+
- Maven 3.8+
- Không cần database (dùng in-memory cache)
- Không cần API key (Open-Meteo miễn phí)

## Chạy nhanh

```bash
# Clone về
git clone <repo-url>
cd mat-bao-api

# Build
mvn clean package -DskipTests

# Chạy
java -jar target/mat-bao-api-1.0.0.jar

# Hoặc chạy trực tiếp với Maven
mvn spring-boot:run
```

Server sẽ chạy tại: `http://localhost:8080`

---

## API Endpoints

### 1. Health check
```
GET /api/v1/health
```
```json
{ "status": "UP", "service": "Mat Bao API", "version": "1.0.0" }
```

### 2. Dashboard overview (endpoint chính cho dashboard)
```
GET /api/v1/dashboard
```
Trả về tổng hợp tất cả trạm, số lượng cảnh báo, trạng thái hệ thống.

### 3. Thời tiết theo tọa độ tùy chỉnh
```
GET /api/v1/weather?lat=21.0245&lon=105.8412&name=Hà+Nội
```

### 4. Danh sách trạm IoT
```
GET /api/v1/stations
```

### 5. Chi tiết 1 trạm
```
GET /api/v1/stations/TH-01
```

### 6. Các trạm đang có cảnh báo
```
GET /api/v1/alerts
```

---

## Tích hợp với Frontend Dashboard

Thêm đoạn này vào JavaScript của dashboard để lấy dữ liệu thật:

```javascript
// Lấy dữ liệu dashboard
async function loadDashboard() {
  const res = await fetch('http://localhost:8080/api/v1/dashboard');
  const data = await res.json();

  // Cập nhật metric cards
  document.getElementById('rain-value').textContent =
    data.stations[0]?.latestData?.current?.precipitation?.toFixed(1) + ' mm/h';

  document.getElementById('wind-value').textContent =
    data.stations[0]?.latestData?.current?.windSpeed?.toFixed(1) + ' km/h';

  // Cập nhật biểu đồ
  const hourly = data.stations[0]?.latestData?.hourly || [];
  const labels = hourly.map(h => h.time);
  const rainData = hourly.map(h => h.precipitation);
  updateChart(labels, rainData);
}

// Gọi mỗi 30 phút
loadDashboard();
setInterval(loadDashboard, 30 * 60 * 1000);
```

---

## Cấu trúc project

```
mat-bao-api/
├── src/main/java/com/matbao/
│   ├── MatBaoApplication.java      ← Entry point
│   ├── controller/
│   │   └── WeatherController.java  ← REST API endpoints
│   ├── service/
│   │   ├── OpenMeteoService.java   ← Gọi Open-Meteo API
│   │   └── StationService.java     ← Quản lý trạm IoT
│   ├── model/
│   │   ├── WeatherData.java        ← Data models
│   │   ├── AlertLevel.java         ← Enum mức cảnh báo
│   │   └── Station.java            ← Model trạm IoT
│   ├── scheduler/
│   │   └── WeatherScheduler.java   ← Tự động fetch mỗi 2h
│   └── config/
│       └── CorsConfig.java         ← CORS cho frontend
└── src/main/resources/
    └── application.properties      ← Cấu hình & ngưỡng cảnh báo
```

---

## Ngưỡng cảnh báo (chỉnh trong application.properties)

| Thông số | Cảnh báo | Nguy hiểm |
|---|---|---|
| Lượng mưa | 30 mm/h | 50 mm/h |
| Tốc độ gió | 60 km/h | 90 km/h |

---

## Mở rộng trong tương lai

- Thêm database (PostgreSQL) để lưu lịch sử
- Tích hợp module AI Python qua REST call
- Thêm WebSocket để push real-time cho dashboard
- Thêm endpoint gửi SMS/Zalo khi có cảnh báo đỏ

# Mắt Bão — AI Module (Python)

Module AI dự đoán mức cảnh báo thời tiết bằng Machine Learning.

## Yêu cầu
- Python 3.10+
- pip

## Cài đặt

```bash
cd mat-bao-ai
pip install -r requirements.txt
```

---

## Quy trình chạy (3 bước)

### Bước 1: Tải dữ liệu lịch sử
```bash
python fetch_data.py
```
Sẽ tải ~17,000 bản ghi thời tiết thật 2 năm từ Open-Meteo.
Kết quả lưu tại: `data/weather_history.csv`
Thời gian: ~30-60 giây

### Bước 2: Train model
```bash
python train_model.py
```
So sánh XGBoost, Random Forest, Gradient Boosting.
Lưu model tốt nhất tại: `models/alert_model.pkl`
Thời gian: ~2-5 phút

Kết quả mẫu:
```
XGBoost          Acc=91.3%  F1=0.8924  ← BEST
Random Forest    Acc=89.7%  F1=0.8801
Gradient Boosting Acc=88.4% F1=0.8712
```

### Bước 3: Chạy API server
```bash
uvicorn api.main:app --port 8000 --reload
```
API sẽ chạy tại: http://localhost:8000

---

## API Endpoints

| Endpoint | Method | Mô tả |
|---|---|---|
| `/` | GET | Thông tin model |
| `/predict` | POST | Dự đoán 1 trạm |
| `/predict/batch` | POST | Dự đoán nhiều trạm |
| `/model/info` | GET | Chi tiết model |
| `/model/feature-importance` | GET | Top features |
| `/health` | GET | Kiểm tra server |

### Ví dụ gọi `/predict`
```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "station_id": "TH-01",
    "temperature": 28.5,
    "humidity": 92.0,
    "precipitation": 38.2,
    "wind_speed": 62.0,
    "wind_direction": 180,
    "pressure": 998.0,
    "wind_gusts": 85.0,
    "cloud_cover": 90.0,
    "hour": 14,
    "month": 10,
    "precipitation_3h_avg": 35.0,
    "precipitation_3h_max": 42.0,
    "wind_speed_3h_avg": 58.0,
    "wind_speed_3h_max": 65.0
  }'
```

### Response mẫu
```json
{
  "station_id": "TH-01",
  "alert_level": 2,
  "alert_label": "Nguy hiểm",
  "alert_color": "red",
  "confidence": 0.9134,
  "probabilities": {
    "binh_thuong": 0.0421,
    "canh_bao": 0.0445,
    "nguy_hiem": 0.9134
  },
  "message": "NGUY HIỂM — Sơ tán ngay nếu ở vùng trũng thấp!",
  "model_name": "XGBoost",
  "model_accuracy": 0.913
}
```

---

## Kết nối với Spring Boot

Thêm vào `application.properties`:
```properties
ai.service.url=http://localhost:8000
```

Spring Boot tự động gọi AI khi có dữ liệu mới.
Nếu AI service chưa chạy, tự động fallback về rule-based.

---

## Chạy toàn bộ hệ thống

```bash
# Terminal 1 — AI Python server
cd mat-bao-ai
uvicorn api.main:app --port 8000

# Terminal 2 — Spring Boot backend + Frontend
cd mat-bao-api
java -jar target/mat-bao-api-1.0.0.jar

# Mở browser
http://localhost:8080
```

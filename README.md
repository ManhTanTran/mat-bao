# 🌀 Mắt Bão — Hệ thống IoT & AI Cảnh báo Thiên tai Sớm

## Giới thiệu
Hệ thống thu thập dữ liệu thời tiết thực tế từ 6 trạm tại Quảng Bình,
phân tích bằng AI và đưa ra cảnh báo sớm mưa lớn, lũ lụt.

## Công nghệ
- **Backend:** Java 17 + Spring Boot 3.2
- **AI:** Python + FastAPI + XGBoost / Random Forest
- **Dữ liệu:** Open-Meteo API (thực tế, miễn phí)
- **Frontend:** HTML/CSS/JS + Chart.js

## Chạy hệ thống

### 1. AI Module
```bash
cd mat-bao-ai
pip install -r requirements.txt
python fetch_data.py
python train_model.py
python -m uvicorn api.main:app --port 8000
```

### 2. Backend + Dashboard
```bash
cd mat-bao-api
mvn clean package -DskipTests
java -jar target/mat-bao-api-1.0.0.jar
```

### 3. Mở browser

http://localhost:8080

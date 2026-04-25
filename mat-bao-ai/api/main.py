"""
api/main.py
FastAPI server — nhận dữ liệu thời tiết hiện tại từ Spring Boot,
trả về mức cảnh báo dự đoán từ model AI đã train.

Chạy: uvicorn api.main:app --port 8000 --reload
Spring Boot gọi: POST http://localhost:8000/predict
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import Optional
import joblib
import json
import numpy as np
import os
import sys

# Đảm bảo import đúng path khi chạy từ thư mục gốc
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

app = FastAPI(
    title="Mắt Bão — AI Prediction API",
    description="Dự đoán mức cảnh báo thời tiết bằng Machine Learning",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# =============================================
# Load model khi khởi động
# =============================================
MODEL_PATH    = "models/alert_model.pkl"
SCALER_PATH   = "models/scaler.pkl"
METADATA_PATH = "models/metadata.json"

model    = None
scaler   = None
metadata = None

@app.on_event("startup")
def load_model():
    global model, scaler, metadata
    try:
        model  = joblib.load(MODEL_PATH)
        scaler = joblib.load(SCALER_PATH)
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            metadata = json.load(f)
        print(f"✓ Model loaded: {metadata['model_name']} (Acc={metadata['accuracy']*100:.1f}%)")
    except FileNotFoundError:
        print("⚠ Model chưa được train! Chạy: python fetch_data.py && python train_model.py")

# =============================================
# Request / Response schemas
# =============================================
class WeatherInput(BaseModel):
    """Dữ liệu thời tiết hiện tại từ 1 trạm IoT."""
    station_id:   str   = Field(..., example="TH-01")
    temperature:  float = Field(..., example=28.5,  description="Nhiệt độ (°C)")
    humidity:     float = Field(..., example=92.0,  description="Độ ẩm (%)")
    precipitation:float = Field(..., example=38.2,  description="Lượng mưa (mm/h)")
    wind_speed:   float = Field(..., example=62.0,  description="Tốc độ gió (km/h)")
    wind_direction:float= Field(0.0, example=180.0, description="Hướng gió (độ)")
    pressure:     float = Field(1013.0, example=998.0, description="Áp suất (hPa)")
    wind_gusts:   float = Field(0.0,  example=85.0, description="Gió giật (km/h)")
    cloud_cover:  float = Field(50.0, example=90.0, description="Mây che phủ (%)")
    hour:         int   = Field(12, ge=0, le=23)
    month:        int   = Field(9,  ge=1, le=12)

    # Rolling features (Spring Boot tính từ lịch sử 3h)
    precipitation_3h_avg: float = Field(0.0)
    precipitation_3h_max: float = Field(0.0)
    wind_speed_3h_avg:    float = Field(0.0)
    wind_speed_3h_max:    float = Field(0.0)

class PredictionResult(BaseModel):
    station_id:    str
    alert_level:   int
    alert_label:   str
    alert_color:   str
    confidence:    float
    probabilities: dict
    message:       str
    model_name:    str
    model_accuracy:float

LABEL_MAP = {
    0: {"label": "Bình thường", "color": "green",
        "message": "Thời tiết bình thường, không có nguy hiểm."},
    1: {"label": "Cảnh báo",   "color": "yellow",
        "message": "Cảnh báo — theo dõi chặt chẽ, chuẩn bị ứng phó!"},
    2: {"label": "Nguy hiểm",  "color": "red",
        "message": "NGUY HIỂM — Sơ tán ngay nếu ở vùng trũng thấp!"},
}

FEATURE_ORDER = [
    "temperature", "humidity", "precipitation", "wind_speed",
    "wind_direction", "pressure", "wind_gusts", "cloud_cover",
    "hour", "month", "is_storm_season",
    "precipitation_3h_avg", "precipitation_3h_max",
    "wind_speed_3h_avg", "wind_speed_3h_max",
]

# =============================================
# ENDPOINTS
# =============================================
@app.get("/")
def root():
    if metadata:
        return {
            "service": "Mắt Bão AI API",
            "status": "ready",
            "model": metadata["model_name"],
            "accuracy": f"{metadata['accuracy']*100:.1f}%",
            "trained_at": metadata["trained_at"],
        }
    return {"service": "Mắt Bão AI API", "status": "model_not_loaded"}


@app.post("/predict", response_model=PredictionResult)
def predict(data: WeatherInput):
    """
    Dự đoán mức cảnh báo từ dữ liệu thời tiết 1 trạm.
    Spring Boot gọi endpoint này sau mỗi lần lấy dữ liệu.
    """
    if model is None:
        raise HTTPException(503, "Model chưa được load. Chạy train trước!")

    is_storm = 1 if data.month in [8, 9, 10, 11] else 0

    features = np.array([[
        data.temperature,
        data.humidity,
        data.precipitation,
        data.wind_speed,
        data.wind_direction,
        data.pressure,
        data.wind_gusts,
        data.cloud_cover,
        data.hour,
        data.month,
        is_storm,
        data.precipitation_3h_avg,
        data.precipitation_3h_max,
        data.wind_speed_3h_avg,
        data.wind_speed_3h_max,
    ]])

    # Scale nếu model cần (Random Forest, GBM)
    needs_scaling = metadata.get("needs_scaling", False)
    if needs_scaling and scaler:
        features = scaler.transform(features)

    # Dự đoán
    predicted_class = int(model.predict(features)[0])
    probabilities   = model.predict_proba(features)[0]
    confidence      = float(probabilities[predicted_class])

    label_info = LABEL_MAP[predicted_class]

    return PredictionResult(
        station_id    = data.station_id,
        alert_level   = predicted_class,
        alert_label   = label_info["label"],
        alert_color   = label_info["color"],
        confidence    = round(confidence, 4),
        probabilities = {
            "binh_thuong": round(float(probabilities[0]), 4),
            "canh_bao":    round(float(probabilities[1]), 4),
            "nguy_hiem":   round(float(probabilities[2]), 4) if len(probabilities) > 2 else 0.0,
        },
        message        = label_info["message"],
        model_name     = metadata["model_name"],
        model_accuracy = round(metadata["accuracy"], 4),
    )


@app.post("/predict/batch")
def predict_batch(stations: list[WeatherInput]):
    """Dự đoán cho nhiều trạm cùng lúc."""
    return [predict(s) for s in stations]


@app.get("/model/info")
def model_info():
    """Thông tin chi tiết về model đã train."""
    if not metadata:
        raise HTTPException(503, "Model chưa load")
    return metadata


@app.get("/model/feature-importance")
def feature_importance():
    """Top features quan trọng nhất."""
    if not metadata:
        raise HTTPException(503, "Model chưa load")
    fi = metadata.get("feature_importance", {})
    sorted_fi = sorted(fi.items(), key=lambda x: x[1], reverse=True)
    return {"features": [{"name": k, "importance": round(v, 4)} for k, v in sorted_fi]}


@app.get("/health")
def health():
    return {
        "status": "UP" if model else "MODEL_NOT_LOADED",
        "model_loaded": model is not None,
    }

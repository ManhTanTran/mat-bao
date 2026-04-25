"""
fetch_data.py
Tải dữ liệu thời tiết lịch sử 2 năm từ Open-Meteo API
cho các trạm tại Quảng Bình và vùng lân cận.
Chạy 1 lần để tạo dataset training.
"""

import requests
import pandas as pd
import numpy as np
import json
import os
from datetime import datetime, timedelta

# =============================================
# Danh sách trạm — tọa độ thực tế
# =============================================
STATIONS = [
    {"id": "TH-01", "name": "Tân Hóa",   "lat": 17.7833, "lon": 106.0167},
    {"id": "MH-03", "name": "Minh Hóa",  "lat": 17.8167, "lon": 105.9833},
    {"id": "TH-07", "name": "Đồng Lê",   "lat": 17.8500, "lon": 105.8833},
    {"id": "QB-12", "name": "Ba Đồn",    "lat": 17.7500, "lon": 106.4167},
    {"id": "QB-15", "name": "Bố Trạch",  "lat": 17.5833, "lon": 106.3667},
    {"id": "QB-09", "name": "Lệ Thủy",   "lat": 17.1000, "lon": 106.8000},
]

# Lấy 2 năm dữ liệu để có đủ mùa bão
END_DATE   = datetime.now().strftime("%Y-%m-%d")
START_DATE = (datetime.now() - timedelta(days=730)).strftime("%Y-%m-%d")

HOURLY_VARS = [
    "temperature_2m",
    "relative_humidity_2m",
    "precipitation",
    "wind_speed_10m",
    "wind_direction_10m",
    "surface_pressure",
    "wind_gusts_10m",
    "cloud_cover",
]

def fetch_station(station: dict) -> pd.DataFrame:
    """Tải dữ liệu lịch sử cho 1 trạm."""
    url = "https://archive-api.open-meteo.com/v1/archive"
    params = {
        "latitude":  station["lat"],
        "longitude": station["lon"],
        "start_date": START_DATE,
        "end_date":   END_DATE,
        "hourly":     ",".join(HOURLY_VARS),
        "timezone":   "Asia/Ho_Chi_Minh",
        "wind_speed_unit": "kmh",
    }

    print(f"  → Đang tải {station['name']} ({START_DATE} → {END_DATE})...")
    resp = requests.get(url, params=params, timeout=60)
    resp.raise_for_status()
    data = resp.json()

    hourly = data["hourly"]
    df = pd.DataFrame(hourly)
    df["time"] = pd.to_datetime(df["time"])
    df["station_id"]   = station["id"]
    df["station_name"] = station["name"]
    df["latitude"]     = station["lat"]
    df["longitude"]    = station["lon"]

    # Đổi tên cột cho dễ dùng
    df.rename(columns={
        "temperature_2m":        "temperature",
        "relative_humidity_2m":  "humidity",
        "wind_speed_10m":        "wind_speed",
        "wind_direction_10m":    "wind_direction",
        "surface_pressure":      "pressure",
        "wind_gusts_10m":        "wind_gusts",
        "cloud_cover":           "cloud_cover",
    }, inplace=True)

    return df

def label_alert(row) -> int:
    """
    Gán nhãn cảnh báo dựa trên tiêu chuẩn VNMHA:
      0 = Bình thường
      1 = Cảnh báo (mưa to / gió mạnh)
      2 = Nguy hiểm (mưa rất to / bão)
    """
    rain = row["precipitation"]
    wind = row["wind_speed"]
    gusts = row.get("wind_gusts", 0) or 0
    hum = row["humidity"]

    # Mức NGUY HIỂM
    if rain >= 50 or wind >= 90 or gusts >= 100:
        return 2
    # Mức CẢNH BÁO
    if rain >= 25 or wind >= 60 or (hum >= 90 and rain >= 15):
        return 1
    return 0

def main():
    os.makedirs("data", exist_ok=True)
    all_dfs = []

    for station in STATIONS:
        try:
            df = fetch_station(station)
            all_dfs.append(df)
            print(f"    ✓ {len(df)} bản ghi")
        except Exception as e:
            print(f"    ✗ Lỗi {station['name']}: {e}")

    if not all_dfs:
        print("Không tải được dữ liệu nào!")
        return

    combined = pd.concat(all_dfs, ignore_index=True)

    # Xóa hàng thiếu dữ liệu quan trọng
    combined.dropna(subset=["temperature", "humidity", "precipitation", "wind_speed"], inplace=True)
    combined.fillna(0, inplace=True)

    # Gán nhãn
    combined["alert_level"] = combined.apply(label_alert, axis=1)

    # Thêm features thời gian (mùa bão VN là T9-T11)
    combined["hour"]  = combined["time"].dt.hour
    combined["month"] = combined["time"].dt.month
    combined["is_storm_season"] = combined["month"].isin([8, 9, 10, 11]).astype(int)

    # Thêm rolling features (xu hướng 3 giờ qua)
    combined.sort_values(["station_id", "time"], inplace=True)
    for col in ["precipitation", "wind_speed"]:
        combined[f"{col}_3h_avg"] = (
            combined.groupby("station_id")[col]
            .transform(lambda x: x.rolling(3, min_periods=1).mean())
        )
        combined[f"{col}_3h_max"] = (
            combined.groupby("station_id")[col]
            .transform(lambda x: x.rolling(3, min_periods=1).max())
        )

    print(f"\nTổng: {len(combined):,} bản ghi")
    print("Phân bố nhãn:")
    print(combined["alert_level"].value_counts().sort_index())
    print(f"  0=Bình thường, 1=Cảnh báo, 2=Nguy hiểm")

    out_path = "data/weather_history.csv"
    combined.to_csv(out_path, index=False)
    print(f"\n✓ Đã lưu: {out_path}")

if __name__ == "__main__":
    main()

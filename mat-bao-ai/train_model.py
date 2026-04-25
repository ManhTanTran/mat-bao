"""
train_model.py
Train mô hình AI phân loại mức cảnh báo thời tiết.

Pipeline:
  1. Load CSV từ fetch_data.py
  2. Feature engineering
  3. Train XGBoost + Random Forest
  4. So sánh, chọn model tốt nhất
  5. Lưu model + scaler + metadata
"""

import pandas as pd
import numpy as np
import joblib
import json
import os
from datetime import datetime

from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.metrics import (
    classification_report, confusion_matrix,
    accuracy_score, f1_score
)
from xgboost import XGBClassifier

# =============================================
# FEATURES dùng để train
# =============================================
FEATURE_COLS = [
    "temperature",
    "humidity",
    "precipitation",
    "wind_speed",
    "wind_direction",
    "pressure",
    "wind_gusts",
    "cloud_cover",
    "hour",
    "month",
    "is_storm_season",
    "precipitation_3h_avg",
    "precipitation_3h_max",
    "wind_speed_3h_avg",
    "wind_speed_3h_max",
]

TARGET_COL = "alert_level"

LABEL_NAMES = {0: "Bình thường", 1: "Cảnh báo", 2: "Nguy hiểm"}

def load_data(path: str = "data/weather_history.csv") -> pd.DataFrame:
    print(f"Đang load dữ liệu từ {path}...")
    df = pd.read_csv(path)
    print(f"  → {len(df):,} bản ghi, {df['alert_level'].value_counts().to_dict()}")
    return df

def prepare_features(df: pd.DataFrame):
    """Chuẩn bị X, y — xử lý missing values."""
    available = [c for c in FEATURE_COLS if c in df.columns]
    missing = [c for c in FEATURE_COLS if c not in df.columns]
    if missing:
        print(f"  ⚠ Thiếu features: {missing} — sẽ dùng 0")
        for c in missing:
            df[c] = 0

    X = df[FEATURE_COLS].fillna(0).values
    y = df[TARGET_COL].values
    return X, y

def train_and_evaluate(name: str, model, X_train, X_test, y_train, y_test) -> dict:
    """Train 1 model và trả về metrics."""
    print(f"\n{'='*50}")
    print(f"Training: {name}")
    print(f"{'='*50}")

    model.fit(X_train, y_train)
    y_pred = model.predict(X_test)

    acc = accuracy_score(y_test, y_pred)
    f1  = f1_score(y_test, y_pred, average="weighted")

    print(f"Accuracy : {acc:.4f} ({acc*100:.1f}%)")
    print(f"F1-score : {f1:.4f}")
    print("\nClassification Report:")
    unique_labels = sorted(np.unique(np.concatenate([y_train, y_test])))
    print(classification_report(y_test, y_pred,
                                labels=unique_labels,
                                target_names=[LABEL_NAMES[i] for i in unique_labels]))

    # Confusion matrix
    cm = confusion_matrix(y_test, y_pred)
    print("Confusion Matrix:")
    print(cm)

    return {
        "name": name,
        "model": model,
        "accuracy": float(acc),
        "f1_score": float(f1),
        "confusion_matrix": cm.tolist(),
        "classification_report": classification_report(
            y_test, y_pred,
            labels=unique_labels,
            target_names=[LABEL_NAMES[i] for i in unique_labels],
            output_dict=True
        )
    }

def main():
    os.makedirs("models", exist_ok=True)

    # =============================================
    # 1. Load & chuẩn bị dữ liệu
    # =============================================
    df = load_data()
    X, y = prepare_features(df)

    print(f"\nPhân bố nhãn:")
    unique, counts = np.unique(y, return_counts=True)
    for u, c in zip(unique, counts):
        print(f"  {LABEL_NAMES[u]}: {c:,} ({c/len(y)*100:.1f}%)")

    # =============================================
    # 2. Train/test split (stratified để giữ tỉ lệ)
    # =============================================
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # Scale features (dùng cho RF, không bắt buộc với XGB)
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled  = scaler.transform(X_test)

    print(f"\nTrain: {len(X_train):,} | Test: {len(X_test):,}")

    # =============================================
    # 3. Định nghĩa các models
    # =============================================
    models = {
        "XGBoost": XGBClassifier(
            n_estimators=300,
            max_depth=6,
            learning_rate=0.1,
            subsample=0.8,
            colsample_bytree=0.8,
            use_label_encoder=False,
            eval_metric="mlogloss",
            random_state=42,
            n_jobs=-1,
        ),
        "Random Forest": RandomForestClassifier(
            n_estimators=200,
            max_depth=10,
            min_samples_split=5,
            class_weight="balanced",  # Xử lý imbalanced (ít mẫu nguy hiểm)
            random_state=42,
            n_jobs=-1,
        ),
        "Gradient Boosting": GradientBoostingClassifier(
            n_estimators=200,
            max_depth=5,
            learning_rate=0.1,
            random_state=42,
        ),
    }

    # =============================================
    # 4. Train & đánh giá từng model
    # =============================================
    results = []
    for name, model in models.items():
        # XGBoost không cần scale
        if name == "XGBoost":
            result = train_and_evaluate(name, model, X_train, X_test, y_train, y_test)
        else:
            result = train_and_evaluate(name, model, X_train_scaled, X_test_scaled, y_train, y_test)
        results.append(result)

    # =============================================
    # 5. Chọn model tốt nhất theo F1-score
    # =============================================
    best = max(results, key=lambda r: r["f1_score"])
    print(f"\n{'='*50}")
    print(f"Model tốt nhất: {best['name']}")
    print(f"  Accuracy : {best['accuracy']*100:.1f}%")
    print(f"  F1-score : {best['f1_score']:.4f}")
    print(f"{'='*50}")

    # =============================================
    # 6. Lưu model + scaler + metadata
    # =============================================
    joblib.dump(best["model"], "models/alert_model.pkl")
    joblib.dump(scaler, "models/scaler.pkl")

    # Feature importance (nếu là tree-based)
    feature_importance = {}
    try:
        importances = best["model"].feature_importances_
        feature_importance = dict(zip(FEATURE_COLS, importances.tolist()))
        fi_sorted = sorted(feature_importance.items(), key=lambda x: x[1], reverse=True)
        print("\nTop 10 features quan trọng nhất:")
        for feat, imp in fi_sorted[:10]:
            bar = "█" * int(imp * 50)
            print(f"  {feat:<30} {bar} {imp:.4f}")
    except AttributeError:
        pass

    metadata = {
        "model_name":      best["name"],
        "trained_at":      datetime.now().isoformat(),
        "accuracy":        best["accuracy"],
        "f1_score":        best["f1_score"],
        "feature_cols":    FEATURE_COLS,
        "label_names":     LABEL_NAMES,
        "train_samples":   len(X_train),
        "test_samples":    len(X_test),
        "needs_scaling":   best["name"] != "XGBoost",
        "feature_importance": feature_importance,
        "confusion_matrix": best["confusion_matrix"],
        "all_models": [
            {"name": r["name"], "accuracy": r["accuracy"], "f1": r["f1_score"]}
            for r in results
        ]
    }

    with open("models/metadata.json", "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print(f"\n✓ Đã lưu:")
    print(f"  models/alert_model.pkl")
    print(f"  models/scaler.pkl")
    print(f"  models/metadata.json")
    print(f"\nSo sánh tất cả models:")
    for r in sorted(results, key=lambda x: x["f1_score"], reverse=True):
        marker = " ← BEST" if r["name"] == best["name"] else ""
        print(f"  {r['name']:<25} Acc={r['accuracy']*100:.1f}%  F1={r['f1_score']:.4f}{marker}")

if __name__ == "__main__":
    main()

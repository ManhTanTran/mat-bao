package com.matbao.model;

import java.time.LocalDateTime;
import java.util.List;

public class WeatherData {

    private double latitude;
    private double longitude;
    private String locationName;
    private LocalDateTime fetchedAt;
    private CurrentWeather current;
    private List<HourlyWeather> hourly;
    private List<DailyForecast> daily;
    private AlertInfo alert;

    public WeatherData() {}

    // Getters & Setters
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
    public CurrentWeather getCurrent() { return current; }
    public void setCurrent(CurrentWeather current) { this.current = current; }
    public List<HourlyWeather> getHourly() { return hourly; }
    public void setHourly(List<HourlyWeather> hourly) { this.hourly = hourly; }
    public List<DailyForecast> getDaily() { return daily; }
    public void setDaily(List<DailyForecast> daily) { this.daily = daily; }
    public AlertInfo getAlert() { return alert; }
    public void setAlert(AlertInfo alert) { this.alert = alert; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final WeatherData obj = new WeatherData();
        public Builder latitude(double v) { obj.latitude = v; return this; }
        public Builder longitude(double v) { obj.longitude = v; return this; }
        public Builder locationName(String v) { obj.locationName = v; return this; }
        public Builder fetchedAt(LocalDateTime v) { obj.fetchedAt = v; return this; }
        public Builder current(CurrentWeather v) { obj.current = v; return this; }
        public Builder hourly(List<HourlyWeather> v) { obj.hourly = v; return this; }
        public Builder daily(List<DailyForecast> v) { obj.daily = v; return this; }
        public Builder alert(AlertInfo v) { obj.alert = v; return this; }
        public WeatherData build() { return obj; }
    }

    // =========================================================
    public static class CurrentWeather {
        private double temperature;
        private double humidity;
        private double precipitation;
        private double windSpeed;
        private double windDirection;
        private double pressure;
        private String condition;

        public CurrentWeather() {}

        public double getTemperature() { return temperature; }
        public double getHumidity() { return humidity; }
        public double getPrecipitation() { return precipitation; }
        public double getWindSpeed() { return windSpeed; }
        public double getWindDirection() { return windDirection; }
        public double getPressure() { return pressure; }
        public String getCondition() { return condition; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final CurrentWeather obj = new CurrentWeather();
            public Builder temperature(double v) { obj.temperature = v; return this; }
            public Builder humidity(double v) { obj.humidity = v; return this; }
            public Builder precipitation(double v) { obj.precipitation = v; return this; }
            public Builder windSpeed(double v) { obj.windSpeed = v; return this; }
            public Builder windDirection(double v) { obj.windDirection = v; return this; }
            public Builder pressure(double v) { obj.pressure = v; return this; }
            public Builder condition(String v) { obj.condition = v; return this; }
            public CurrentWeather build() { return obj; }
        }
    }

    // =========================================================
    public static class HourlyWeather {
        private String time;
        private double temperature;
        private double humidity;
        private double precipitation;
        private double windSpeed;

        public HourlyWeather() {}

        public String getTime() { return time; }
        public double getTemperature() { return temperature; }
        public double getHumidity() { return humidity; }
        public double getPrecipitation() { return precipitation; }
        public double getWindSpeed() { return windSpeed; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final HourlyWeather obj = new HourlyWeather();
            public Builder time(String v) { obj.time = v; return this; }
            public Builder temperature(double v) { obj.temperature = v; return this; }
            public Builder humidity(double v) { obj.humidity = v; return this; }
            public Builder precipitation(double v) { obj.precipitation = v; return this; }
            public Builder windSpeed(double v) { obj.windSpeed = v; return this; }
            public HourlyWeather build() { return obj; }
        }
    }

    // =========================================================
    public static class DailyForecast {
        private String date;
        private double maxTemp;
        private double minTemp;
        private double totalPrecipitation;
        private double maxWindSpeed;

        public DailyForecast() {}

        public String getDate() { return date; }
        public double getMaxTemp() { return maxTemp; }
        public double getMinTemp() { return minTemp; }
        public double getTotalPrecipitation() { return totalPrecipitation; }
        public double getMaxWindSpeed() { return maxWindSpeed; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final DailyForecast obj = new DailyForecast();
            public Builder date(String v) { obj.date = v; return this; }
            public Builder maxTemp(double v) { obj.maxTemp = v; return this; }
            public Builder minTemp(double v) { obj.minTemp = v; return this; }
            public Builder totalPrecipitation(double v) { obj.totalPrecipitation = v; return this; }
            public Builder maxWindSpeed(double v) { obj.maxWindSpeed = v; return this; }
            public DailyForecast build() { return obj; }
        }
    }

    // =========================================================
    public static class AlertInfo {
        private AlertLevel level;
        private String message;
        private String color;
        private List<String> reasons;
        private LocalDateTime triggeredAt;
        private String modelName;
        private double confidence;

        public AlertInfo() {}

        public AlertLevel getLevel() { return level; }
        public String getMessage() { return message; }
        public String getColor() { return color; }
        public List<String> getReasons() { return reasons; }
        public LocalDateTime getTriggeredAt() { return triggeredAt; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final AlertInfo obj = new AlertInfo();
            public Builder level(AlertLevel v) { obj.level = v; return this; }
            public Builder message(String v) { obj.message = v; return this; }
            public Builder color(String v) { obj.color = v; return this; }
            public Builder reasons(List<String> v) { obj.reasons = v; return this; }
            public Builder triggeredAt(LocalDateTime v) { obj.triggeredAt = v; return this; }
            public AlertInfo build() { return obj; }
        }
    }
}

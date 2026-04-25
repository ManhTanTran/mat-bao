package com.matbao.model;

public class Station {

    private String id;
    private String name;
    private String district;
    private double latitude;
    private double longitude;
    private StationStatus status;
    private WeatherData latestData;

    public enum StationStatus {
        ONLINE, OFFLINE, WARNING
    }

    public Station() {}

    public Station(String id, String name, String district, double latitude, double longitude,
                   StationStatus status, WeatherData latestData) {
        this.id = id;
        this.name = name;
        this.district = district;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.latestData = latestData;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDistrict() { return district; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public StationStatus getStatus() { return status; }
    public WeatherData getLatestData() { return latestData; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDistrict(String district) { this.district = district; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setStatus(StationStatus status) { this.status = status; }
    public void setLatestData(WeatherData latestData) { this.latestData = latestData; }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String name;
        private String district;
        private double latitude;
        private double longitude;
        private StationStatus status;
        private WeatherData latestData;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder district(String district) { this.district = district; return this; }
        public Builder latitude(double latitude) { this.latitude = latitude; return this; }
        public Builder longitude(double longitude) { this.longitude = longitude; return this; }
        public Builder status(StationStatus status) { this.status = status; return this; }
        public Builder latestData(WeatherData latestData) { this.latestData = latestData; return this; }

        public Station build() {
            return new Station(id, name, district, latitude, longitude, status, latestData);
        }
    }
}

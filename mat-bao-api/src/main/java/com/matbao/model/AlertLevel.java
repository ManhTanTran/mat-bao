package com.matbao.model;

public enum AlertLevel {
    NORMAL("Bình thường", "green", 0),
    WARNING("Cảnh báo", "yellow", 1),
    DANGER("Nguy hiểm", "red", 2);

    private final String label;
    private final String color;
    private final int severity;

    AlertLevel(String label, String color, int severity) {
        this.label = label;
        this.color = color;
        this.severity = severity;
    }

    public String getLabel() { return label; }
    public String getColor() { return color; }
    public int getSeverity() { return severity; }
}

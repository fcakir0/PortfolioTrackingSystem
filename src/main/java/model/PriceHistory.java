package model;

import java.time.LocalDateTime;

public class PriceHistory {
    private int id;
    private int assetId;
    private double price;
    private LocalDateTime recordedAt;

    public PriceHistory() {}

    public PriceHistory(int id, int assetId, double price, LocalDateTime recordedAt) {
        this.id = id;
        this.assetId = assetId;
        this.price = price;
        this.recordedAt = recordedAt;
    }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAssetId() { return assetId; }
    public void setAssetId(int assetId) { this.assetId = assetId; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}

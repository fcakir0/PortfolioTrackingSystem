package model;

import java.time.LocalDateTime;

public class Trade {
    private int id;
    private int userId;
    private int assetId;
    private TradeType tradeType;
    private double quantity;
    private double price;
    private LocalDateTime tradeDate;

    public Trade() {}

    public Trade(int id, int userId, int assetId, TradeType tradeType,
                 double quantity, double price, LocalDateTime tradeDate) {
        this.id = id;
        this.userId = userId;
        this.assetId = assetId;
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.price = price;
        this.tradeDate = tradeDate;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getAssetId() { return assetId; }
    public void setAssetId(int assetId) { this.assetId = assetId; }

    public TradeType getTradeType() { return tradeType; }
    public void setTradeType(TradeType tradeType) { this.tradeType = tradeType; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public LocalDateTime getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDateTime tradeDate) { this.tradeDate = tradeDate; }
}

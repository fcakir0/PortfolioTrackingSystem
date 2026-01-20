package model;

public class PortfolioRow {
    private int assetId;
    private String symbol;
    private MarketType marketType;
    private double quantity;
    private double avgCost;
    private double currentPrice;
    private double profitLoss;

    public PortfolioRow(int assetId, String symbol, MarketType marketType, double quantity,
                        double avgCost, double currentPrice, double profitLoss) {
        this.assetId = assetId;
        this.symbol = symbol;
        this.marketType = marketType;
        this.quantity = quantity;
        this.avgCost = avgCost;
        this.currentPrice = currentPrice;
        this.profitLoss = profitLoss;
    }

    public int getAssetId() { return assetId; }
    public String getSymbol() { return symbol; }
    public MarketType getMarketType() { return marketType; }
    public double getQuantity() { return quantity; }
    public double getAvgCost() { return avgCost; }
    public double getCurrentPrice() { return currentPrice; }
    public double getProfitLoss() { return profitLoss; }
}

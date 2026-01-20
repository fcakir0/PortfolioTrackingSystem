package model;

public class Asset {
    private int id;
    private int marketId;
    private String symbol;
    private String name;
    private String currency;
    private String yahooSymbol;
    private MarketType market;

    public Asset() {}

    public Asset(int id, int marketId, String symbol, String name, String currency, String yahooSymbol) {
        this.id = id;
        this.marketId = marketId;
        this.symbol = symbol;
        this.name = name;
        this.currency = currency;
        this.yahooSymbol = yahooSymbol;
    }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getMarketId() { return marketId; }
    public void setMarketId(int marketId) { this.marketId = marketId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getYahooSymbol() { return yahooSymbol; }
    public void setYahooSymbol(String yahooSymbol) { this.yahooSymbol = yahooSymbol; }

    public MarketType getMarket() { return market; }
    public void setMarket(MarketType market) { this.market = market; }
}

package dao;

import util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class PortfolioDao {

    public static class PortfolioRow {
        public final int assetId;
        public final String symbol;
        public final String name;
        public final String market;
        public final double quantity;
        public final double avgCost;

        public PortfolioRow(int assetId, String symbol, String name, String market, double quantity, double avgCost) {
            this.assetId = assetId;
            this.symbol = symbol;
            this.name = name;
            this.market = market;
            this.quantity = quantity;
            this.avgCost = avgCost;
        }
    }
    public List<PortfolioRow> getPortfolioSummary(int userId) {
        String sql =
                "SELECT a.id AS asset_id, a.symbol, a.name, m.code AS market_code, " +
                        "       SUM(CASE WHEN t.trade_type='BUY' THEN t.quantity ELSE -t.quantity END) AS net_qty, " +
                        "       CASE WHEN SUM(CASE WHEN t.trade_type='BUY' THEN t.quantity ELSE 0 END) = 0 THEN 0 " +
                        "            ELSE SUM(CASE WHEN t.trade_type='BUY' THEN t.quantity*t.price ELSE 0 END) / " +
                        "                 SUM(CASE WHEN t.trade_type='BUY' THEN t.quantity ELSE 0 END) " +
                        "       END AS avg_cost " +
                        "FROM trades t " +
                        "JOIN assets a  ON a.id = t.asset_id " +
                        "JOIN markets m ON m.id = a.market_id " +
                        "WHERE t.user_id = ? " +
                        "GROUP BY a.id, a.symbol, a.name, m.code " +
                        "HAVING SUM(CASE WHEN t.trade_type='BUY' THEN t.quantity ELSE -t.quantity END) <> 0 " +
                        "ORDER BY m.code, a.symbol";

        List<PortfolioRow> list = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PortfolioRow(
                            rs.getInt("asset_id"),
                            rs.getString("symbol"),
                            rs.getString("name"),
                            rs.getString("market_code"),
                            rs.getDouble("net_qty"),
                            rs.getDouble("avg_cost")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}

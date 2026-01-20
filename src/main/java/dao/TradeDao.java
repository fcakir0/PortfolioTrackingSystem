package dao;

import model.MarketType;
import model.Trade;
import model.TradeType;
import util.DatabaseUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TradeDao {

    public void insert(Trade t) {
        String sql = """
            INSERT INTO trades(user_id, asset_id, trade_type, quantity, price, trade_time)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, t.getUserId());
            ps.setInt(2, t.getAssetId());
            ps.setString(3, t.getTradeType().name()); // BUY / SELL
            ps.setDouble(4, t.getQuantity());
            ps.setDouble(5, t.getPrice());
            ps.setTimestamp(6, Timestamp.valueOf(t.getTradeDate()));

            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Trade> findAllByUserId(int userId) {
        String sql = """
            SELECT id, user_id, asset_id, trade_type, quantity, price, trade_time
            FROM trades
            WHERE user_id = ?
            ORDER BY trade_time DESC, id DESC
        """;

        List<Trade> list = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Trade t = new Trade();
                    t.setId(rs.getInt("id"));
                    t.setUserId(rs.getInt("user_id"));
                    t.setAssetId(rs.getInt("asset_id"));
                    t.setTradeType(TradeType.valueOf(rs.getString("trade_type")));
                    t.setQuantity(rs.getDouble("quantity"));
                    t.setPrice(rs.getDouble("price"));

                    Timestamp ts = rs.getTimestamp("trade_time");
                    t.setTradeDate(ts != null ? ts.toLocalDateTime() : LocalDateTime.now());

                    list.add(t);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Belirli bir kullanıcının, belirli bir varlığa ait tüm işlemlerini siler.
     * Portföy tablosundaki bir satırı kaldırmak için kullanılır.
     */
    public void deleteAllByUserAndAsset(int userId, int assetId) {
        String sql = """
            DELETE FROM trades
            WHERE user_id = ? AND asset_id = ?
        """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, assetId);

            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- PortfolioService için eklenen aggregate yapı ---

    /**
     * Bir kullanıcının her enstrümanı için net miktar ve ağırlıklı ortalama maliyetini hesaplar.
     * Ayrıca enstrümanın market bilgisini de döner.
     */
    public record PositionAgg(
            int assetId,
            String symbol,
            MarketType marketType,
            double netQty,
            double avgCost
    ) {}

    public List<PositionAgg> getAggregatedPositions(int userId) {
        String sql = """
            SELECT
                a.id AS asset_id,
                a.symbol AS symbol,
                m.code AS market_code,
                SUM(CASE WHEN t.trade_type = 'BUY'  THEN t.quantity
                         WHEN t.trade_type = 'SELL' THEN -t.quantity
                         ELSE 0 END) AS net_qty,
                CASE
                    WHEN SUM(CASE WHEN t.trade_type = 'BUY' THEN t.quantity ELSE 0 END) = 0
                        THEN 0
                    ELSE
                        SUM(CASE WHEN t.trade_type = 'BUY' THEN t.quantity * t.price ELSE 0 END)
                        /
                        SUM(CASE WHEN t.trade_type = 'BUY' THEN t.quantity ELSE 0 END)
                END AS avg_cost
            FROM trades t
            JOIN assets a  ON a.id = t.asset_id
            JOIN markets m ON m.id = a.market_id
            WHERE t.user_id = ?
            GROUP BY a.id, a.symbol, m.code
        """;

        List<PositionAgg> list = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String marketCode = rs.getString("market_code");
                    MarketType mt;
                    try {
                        mt = MarketType.valueOf(marketCode);
                    } catch (IllegalArgumentException e) {
                        // Enum'da yoksa, bu kaydı atlayabilir ya da null verebilirsin
                        mt = null;
                    }

                    list.add(new PositionAgg(
                            rs.getInt("asset_id"),
                            rs.getString("symbol"),
                            mt,
                            rs.getDouble("net_qty"),
                            rs.getDouble("avg_cost")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }
}

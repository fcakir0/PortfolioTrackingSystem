package dao;

import util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PriceHistoryDao {

    public void insert(int assetId, double price) {
        String sql = """
            INSERT INTO prices_history (asset_id, price, created_at)
            VALUES (?, ?, now())
        """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, assetId);
            ps.setDouble(2, price);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Verilen varlık için en son kaydedilen fiyatı döner.
     * Kayıt yoksa null döner.
     */
    public Double findLatestPrice(int assetId) {
        String sql = """
            SELECT price
            FROM prices_history
            WHERE asset_id = ?
            ORDER BY id DESC
            LIMIT 1
        """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, assetId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("price");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}

package dao;

import model.Asset;
import util.DatabaseUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AssetDao {

    public List<Asset> findAll() {
        List<Asset> list = new ArrayList<>();

        String sql = """
            SELECT id, market_id, symbol, name, currency, yahoo_symbol
            FROM assets
        """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Asset a = new Asset();
                a.setId(rs.getInt("id"));
                a.setMarketId(rs.getInt("market_id"));
                a.setSymbol(rs.getString("symbol"));
                a.setName(rs.getString("name"));
                a.setCurrency(rs.getString("currency"));
                a.setYahooSymbol(rs.getString("yahoo_symbol"));
                list.add(a);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}

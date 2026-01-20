package dao;

import model.MarketType;
import util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class MarketDao {

    public List<MarketType> findAll() {
        String sql = "SELECT code FROM markets ORDER BY code";
        List<MarketType> markets = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String code = rs.getString("code");
                try {
                    markets.add(MarketType.valueOf(code));
                } catch (IllegalArgumentException e) {
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return markets;
    }
}



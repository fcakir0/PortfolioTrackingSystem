package dao;

import util.DatabaseUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PortfolioValueDao {

    /**
     * PortfolioValue kaydı için record sınıfı
     */
    public record PortfolioValue(
            int id,
            int userId,
            double totalValue,
            LocalDateTime calculatedAt
    ) {}

    /**
     * Kullanıcının portföy toplam değerini kaydeder.
     * @param userId Kullanıcı ID
     * @param totalValue Toplam portföy değeri (TL cinsinden)
     */
    public void insert(int userId, double totalValue) {
        String sql = """
            INSERT INTO portfolio_values (user_id, total_value, calculated_at)
            VALUES (?, ?, now())
        """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setDouble(2, totalValue);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Kullanıcının en son kaydedilen portföy değerini döner.
     * @param userId Kullanıcı ID
     * @return En son PortfolioValue kaydı, yoksa null
     */
    public PortfolioValue getLast(int userId) {
        String sql = """
            SELECT id, user_id, total_value, calculated_at
            FROM portfolio_values
            WHERE user_id = ?
            ORDER BY calculated_at DESC, id DESC
            LIMIT 1
        """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("calculated_at");
                    return new PortfolioValue(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getDouble("total_value"),
                            ts != null ? ts.toLocalDateTime() : LocalDateTime.now()
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Kullanıcının belirli bir tarih aralığındaki portföy değerlerini döner.
     * Grafik için kullanılır.
     * @param userId Kullanıcı ID
     * @param from Başlangıç tarihi (null ise tüm kayıtlar)
     * @param to Bitiş tarihi (null ise tüm kayıtlar)
     * @return PortfolioValue listesi, tarihe göre artan sırada
     */
    public List<PortfolioValue> listByUser(int userId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT id, user_id, total_value, calculated_at
            FROM portfolio_values
            WHERE user_id = ?
            AND (? IS NULL OR calculated_at >= ?)
            AND (? IS NULL OR calculated_at <= ?)
            ORDER BY calculated_at ASC, id ASC
        """;

        List<PortfolioValue> list = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            if (from != null) {
                ps.setTimestamp(2, Timestamp.valueOf(from));
                ps.setTimestamp(3, Timestamp.valueOf(from));
            } else {
                ps.setNull(2, Types.TIMESTAMP);
                ps.setNull(3, Types.TIMESTAMP);
            }
            if (to != null) {
                ps.setTimestamp(4, Timestamp.valueOf(to));
                ps.setTimestamp(5, Timestamp.valueOf(to));
            } else {
                ps.setNull(4, Types.TIMESTAMP);
                ps.setNull(5, Types.TIMESTAMP);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("calculated_at");
                    list.add(new PortfolioValue(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getDouble("total_value"),
                            ts != null ? ts.toLocalDateTime() : LocalDateTime.now()
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Kullanıcının son N gün içindeki portföy değerlerini döner.
     * @param userId Kullanıcı ID
     * @param days Son kaç gün (örn: 30 = son 30 gün)
     * @return PortfolioValue listesi, tarihe göre artan sırada
     */
    public List<PortfolioValue> getLastNDays(int userId, int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return listByUser(userId, from, null);
    }

    /**
     * Kullanıcının son K kaydını döner.
     * @param userId Kullanıcı ID
     * @param limit Son kaç kayıt (örn: 100 = son 100 kayıt)
     * @return PortfolioValue listesi, tarihe göre artan sırada
     */
    public List<PortfolioValue> getLastKRecords(int userId, int limit) {
        String sql = """
            SELECT id, user_id, total_value, calculated_at
            FROM portfolio_values
            WHERE user_id = ?
            ORDER BY calculated_at DESC, id DESC
            LIMIT ?
        """;

        List<PortfolioValue> list = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("calculated_at");
                    list.add(new PortfolioValue(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getDouble("total_value"),
                            ts != null ? ts.toLocalDateTime() : LocalDateTime.now()
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        // Sonuçları tarihe göre artan sırada döndür (grafik için)
        list.sort((a, b) -> a.calculatedAt().compareTo(b.calculatedAt()));
        return list;
    }
}


package util;

import model.Asset;
import service.YahooFinancePriceService;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Set;

public class PriceHistorySeeder {

    /**
     * Tüm varlıklar için güncel fiyatları çekip veritabanına kaydeder.
     * @param verbose true ise konsola detaylı log yazdırır
     * @return Başarılı ve başarısız işlem sayılarını içeren bir dizi [ok, fail]
     */
    public static int[] fetchAndSavePrices(boolean verbose) {
        if (verbose) {
            System.out.println("=== Fiyatlar Çekilmeye başlandı ===");
        }
        YahooFinancePriceService priceService = new YahooFinancePriceService();

        String selectSql = "SELECT id, symbol, currency, yahoo_symbol FROM assets ORDER BY id";
        String insertSql = "INSERT INTO prices_history(asset_id, price, currency, source, price_time) " +
                "VALUES (?, ?, ?, ?, ?)";

        int ok = 0, fail = 0;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement psSelect = conn.prepareStatement(selectSql);
             ResultSet rs = psSelect.executeQuery();
             PreparedStatement psInsert = conn.prepareStatement(insertSql)) {

            while (rs.next()) {
                int assetId = rs.getInt("id");
                String symbol = rs.getString("symbol");
                String currency = rs.getString("currency");
                String yahooSymbol = rs.getString("yahoo_symbol");

                Asset a = new Asset();
                a.setId(assetId);
                a.setSymbol(symbol);
                a.setCurrency(currency);
                a.setYahooSymbol(yahooSymbol);

                try {
                    double price = priceService.fetchCurrentPrice(a);

                    psInsert.setInt(1, assetId);
                    psInsert.setDouble(2, price);
                    psInsert.setString(3, currency != null ? currency : "TRY");
                    psInsert.setString(4, "YAHOO");
                    psInsert.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                    psInsert.executeUpdate();

                    ok++;
                    if (verbose) {
                        System.out.println("OK  asset_id=" + assetId + " yahoo=" + yahooSymbol + " price=" + price);
                    }
                } catch (Exception ex) {
                    fail++;
                    if (verbose) {
                        System.out.println("!! HATA asset_id=" + assetId + " yahoo=" + yahooSymbol + " => " + ex.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            if (verbose) {
                e.printStackTrace();
            }
            throw new RuntimeException("Fiyat çekme işlemi sırasında hata oluştu: " + e.getMessage(), e);
        }

        if (verbose) {
            System.out.println("=== Bitti. OK=" + ok + " FAIL=" + fail + " ===");
        }
        return new int[]{ok, fail};
    }

    /**
     * Tüm varlıklar için güncel fiyatları çekip veritabanına kaydeder (konsol çıktısı olmadan).
     * @return Başarılı ve başarısız işlem sayılarını içeren bir dizi [ok, fail]
     */
    public static int[] fetchAndSavePrices() {
        return fetchAndSavePrices(false);
    }

    /**
     * Belirli asset ID'lerine sahip varlıklar için güncel fiyatları çekip veritabanına kaydeder.
     * @param assetIds Fiyatları çekilecek asset ID'leri listesi
     * @return Başarılı ve başarısız işlem sayılarını içeren bir dizi [ok, fail]
     */
    public static int[] fetchAndSavePricesForAssets(Set<Integer> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return new int[]{0, 0};
        }

        YahooFinancePriceService priceService = new YahooFinancePriceService();

        // IN clause için placeholder'lar oluştur
        String placeholders = assetIds.stream()
                .map(id -> "?")
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        String selectSql = "SELECT id, symbol, currency, yahoo_symbol FROM assets WHERE id IN (" + placeholders + ") ORDER BY id";
        String insertSql = "INSERT INTO prices_history(asset_id, price, currency, source, price_time) " +
                "VALUES (?, ?, ?, ?, ?)";

        int ok = 0, fail = 0;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement psSelect = conn.prepareStatement(selectSql);
             PreparedStatement psInsert = conn.prepareStatement(insertSql)) {

            // IN clause parametrelerini set et
            int paramIndex = 1;
            for (Integer assetId : assetIds) {
                psSelect.setInt(paramIndex++, assetId);
            }
            try (ResultSet rs = psSelect.executeQuery()) {
                while (rs.next()) {
                    int assetId = rs.getInt("id");
                    String symbol = rs.getString("symbol");
                    String currency = rs.getString("currency");
                    String yahooSymbol = rs.getString("yahoo_symbol");

                    Asset a = new Asset();
                    a.setId(assetId);
                    a.setSymbol(symbol);
                    a.setCurrency(currency);
                    a.setYahooSymbol(yahooSymbol);
                    try {
                        double price = priceService.fetchCurrentPrice(a);
                        psInsert.setInt(1, assetId);
                        psInsert.setDouble(2, price);
                        psInsert.setString(3, currency != null ? currency : "TRY");
                        psInsert.setString(4, "YAHOO");
                        psInsert.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                        psInsert.executeUpdate();
                        ok++;
                    } catch (Exception ex) {
                        fail++;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Fiyat çekme işlemi sırasında hata oluştu: " + e.getMessage(), e);
        }
        return new int[]{ok, fail};
    }
    public static void main(String[] args) {
        fetchAndSavePrices(true); // Konsol çıktısı ile
    }
}


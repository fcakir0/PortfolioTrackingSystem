package service;

import dao.AssetDao;
import dao.PriceHistoryDao;
import model.Asset;

import java.util.List;

public class PortfolioService {

    private final AssetDao assetDao = new AssetDao();
    private final PriceHistoryDao priceHistoryDao = new PriceHistoryDao();
    private final YahooFinancePriceService priceService =
            new YahooFinancePriceService();

    public void fetchAndSaveCurrentPrices() {

        List<Asset> assets = assetDao.findAll();

        for (Asset asset : assets) {
            try {
                double price = priceService.fetchCurrentPrice(asset);
                priceHistoryDao.insert(asset.getId(), price);

                System.out.println(asset.getSymbol() + " → " + price);

            } catch (Exception e) {
                System.err.println(
                        "Fiyat alınamadı: " + asset.getYahooSymbol()
                );
            }
        }
    }
}

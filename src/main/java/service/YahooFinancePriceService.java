package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.Asset;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class YahooFinancePriceService {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public double fetchCurrentPrice(Asset asset) throws Exception {
        String yahoo = asset.getYahooSymbol();
        if (yahoo == null || yahoo.isBlank()) {
            throw new RuntimeException("yahoo_symbol boş: asset_id=" + asset.getId());
        }

        // v8 chart endpoint (quote yerine bunu kullan)
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + yahoo + "?interval=1m&range=1d";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .header("Referer", "https://finance.yahoo.com/")
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("YahooFinance HTTP " + res.statusCode() + " body=" +
                    (res.body() == null ? "" : res.body().substring(0, Math.min(200, res.body().length()))));
        }

        JsonNode root = mapper.readTree(res.body());
        JsonNode result0 = root.path("chart").path("result").get(0);
        if (result0 == null || result0.isNull()) {
            throw new RuntimeException("chart.result boş: " + yahoo);
        }

        // meta.regularMarketPrice çoğu enstrümanda dolu
        JsonNode p = result0.path("meta").path("regularMarketPrice");
        if (p.isMissingNode() || p.isNull()) {
            throw new RuntimeException("regularMarketPrice yok: " + yahoo);
        }
        return p.asDouble();
    }
}

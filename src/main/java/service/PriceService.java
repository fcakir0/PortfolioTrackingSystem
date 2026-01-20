package service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class PriceService {

    public double fetchYahooPrice(String symbol) {
        try {
            String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String urlStr = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=" + encoded;

            URI uri = URI.create(urlStr);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            if (code != 200) throw new RuntimeException("Yahoo HTTP " + code);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            
            JsonObject root = JsonParser.parseString(sb.toString()).getAsJsonObject();
            JsonObject quoteResponse = root.getAsJsonObject("quoteResponse");
            JsonArray result = quoteResponse.getAsJsonArray("result");
            
            if (result.isEmpty()) throw new RuntimeException("Symbol bulunamadı: " + symbol);

            JsonObject item = result.get(0).getAsJsonObject();

            return item.get("regularMarketPrice").getAsDouble();
        } catch (Exception e) {
            throw new RuntimeException("Fiyat çekilemedi: " + symbol + " / " + e.getMessage(), e);
        }
    }
}

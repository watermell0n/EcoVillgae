package com.b201.ecovillage.investment.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.investment.application.HistoricalIndexPath;
import com.b201.ecovillage.investment.application.HistoricalIndexPathProvider;
import com.b201.ecovillage.investment.domain.HistoricalIndexReference;

@Component
public class FredHistoricalIndexPathProvider implements HistoricalIndexPathProvider {

    private static final String SOURCE_URL = "https://fred.stlouisfed.org/series/SP500";
    private static final URI CSV_URI = URI.create(
        "https://fred.stlouisfed.org/graph/fredgraph.csv?id=SP500&cosd=2020-02-03&coed=2020-04-30"
    );
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    @Override
    public Optional<HistoricalIndexPath> load(int requiredSteps) {
        try {
            HttpRequest request = HttpRequest.newBuilder(CSV_URI)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "text/csv")
                .GET()
                .build();
            HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            List<Double> closes = parseCloses(response.body());
            if (closes.size() < requiredSteps) {
                return Optional.empty();
            }
            List<Integer> rates = new ArrayList<>();
            rates.add(0);
            for (int index = 1; index < requiredSteps; index++) {
                double rate = (closes.get(index) / closes.get(index - 1) - 1.0) * 10_000.0;
                rates.add((int) Math.round(rate));
            }
            return Optional.of(new HistoricalIndexPath(
                new HistoricalIndexReference(
                    "SP500",
                    "2020년 세계 시장 급변 구간",
                    "Federal Reserve Bank of St. Louis FRED",
                    SOURCE_URL,
                    LocalDate.parse("2020-02-03"),
                    LocalDate.parse("2020-04-30")
                ),
                rates
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException | java.io.IOException exception) {
            return Optional.empty();
        }
    }

    private static List<Double> parseCloses(String csv) {
        List<Double> closes = new ArrayList<>();
        String[] lines = csv.split("\\R");
        for (int index = 1; index < lines.length; index++) {
            String[] columns = lines[index].split(",", -1);
            if (columns.length < 2 || columns[1].isBlank() || ".".equals(columns[1])) {
                continue;
            }
            closes.add(Double.parseDouble(columns[1]));
        }
        return closes;
    }
}

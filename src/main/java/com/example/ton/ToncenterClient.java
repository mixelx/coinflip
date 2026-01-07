package com.example.ton;

import com.example.config.AppConfig;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for Toncenter API
 */
@Singleton
public class ToncenterClient {

    private static final Logger LOG = LoggerFactory.getLogger(ToncenterClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppConfig.ToncenterConfig config;

    public ToncenterClient(
            @Client HttpClient httpClient,
            ObjectMapper objectMapper,
            AppConfig appConfig
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.config = appConfig.getToncenter();
    }

    /**
     * Get transactions for an address
     *
     * @param address TON address (any format)
     * @param limit   Maximum number of transactions to return
     * @return List of transactions
     */
    public List<TonTransaction> getTransactions(String address, int limit) {
        try {
            URI uri = UriBuilder.of(config.getBaseUrl())
                    .path("/getTransactions")
                    .queryParam("address", address)
                    .queryParam("limit", limit)
                    .build();

            MutableHttpRequest<?> request = HttpRequest.GET(uri);

            // Add API key if configured
            String apiKey = config.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("X-API-Key", apiKey);
            }

            LOG.debug("Fetching transactions from Toncenter: {}", uri);

            String responseBody = httpClient.toBlocking().retrieve(request);
            return parseTransactionsResponse(responseBody);

        } catch (Exception e) {
            LOG.error("Failed to get transactions from Toncenter for address {}: {}", address, e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<TonTransaction> parseTransactionsResponse(String responseBody) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

            if (!Boolean.TRUE.equals(response.get("ok"))) {
                String error = String.valueOf(response.get("error"));
                LOG.error("Toncenter API error: {}", error);
                return List.of();
            }

            List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
            if (result == null) {
                return List.of();
            }

            List<TonTransaction> transactions = new ArrayList<>();
            for (Map<String, Object> txData : result) {
                TonTransaction tx = parseTransaction(txData);
                if (tx != null) {
                    transactions.add(tx);
                }
            }

            LOG.debug("Parsed {} transactions from Toncenter", transactions.size());
            return transactions;

        } catch (Exception e) {
            LOG.error("Failed to parse Toncenter response: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private TonTransaction parseTransaction(Map<String, Object> txData) {
        try {
            TonTransaction tx = new TonTransaction();

            // Parse transaction_id for hash
            Map<String, Object> transactionId = (Map<String, Object>) txData.get("transaction_id");
            if (transactionId != null) {
                tx.setHash((String) transactionId.get("hash"));
                Object lt = transactionId.get("lt");
                if (lt != null) {
                    tx.setLt(Long.parseLong(lt.toString()));
                }
            }

            // Parse utime
            Object utime = txData.get("utime");
            if (utime != null) {
                tx.setUtime(Long.parseLong(utime.toString()));
            }

            // Parse in_msg
            Map<String, Object> inMsgData = (Map<String, Object>) txData.get("in_msg");
            if (inMsgData != null) {
                TonTransaction.InMessage inMsg = new TonTransaction.InMessage();
                inMsg.setValue((String) inMsgData.get("value"));
                inMsg.setSource((String) inMsgData.get("source"));
                inMsg.setDestination((String) inMsgData.get("destination"));
                tx.setInMsg(inMsg);
            }

            return tx;

        } catch (Exception e) {
            LOG.warn("Failed to parse transaction: {}", e.getMessage());
            return null;
        }
    }
}


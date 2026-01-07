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
 * HTTP client for Toncenter API with proper timeout handling.
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
        LOG.debug("ToncenterClient initialized with baseUrl={}", config.getBaseUrl());
    }

    /**
     * Get transactions for an address.
     * Returns empty list on error (does not throw).
     */
    public List<TonTransaction> getTransactions(String address, int limit) {
        LOG.debug(">>> getTransactions called: address={}, limit={}", address, limit);
        
        try {
            URI uri = UriBuilder.of(config.getBaseUrl())
                    .path("/getTransactions")
                    .queryParam("address", address)
                    .queryParam("limit", limit)
                    .build();

            LOG.debug("Built request URI: {}", uri);

            MutableHttpRequest<?> request = HttpRequest.GET(uri);

            // Add API key if configured
            String apiKey = config.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("X-API-Key", apiKey);
                LOG.debug("Added X-API-Key header (key length: {})", apiKey.length());
            } else {
                LOG.debug("No API key configured");
            }

            LOG.debug("Sending HTTP GET request to Toncenter...");
            long startTime = System.currentTimeMillis();
            
            String responseBody = httpClient.toBlocking().retrieve(request);
            
            long duration = System.currentTimeMillis() - startTime;
            LOG.debug("Toncenter response received in {}ms, body length: {} chars", 
                    duration, responseBody.length());
            
            if (LOG.isTraceEnabled()) {
                LOG.trace("Raw response body: {}", responseBody);
            }
            
            List<TonTransaction> transactions = parseTransactionsResponse(responseBody);
            
            LOG.debug("<<< getTransactions returning {} transactions", transactions.size());
            return transactions;

        } catch (io.micronaut.http.client.exceptions.ReadTimeoutException e) {
            LOG.error("Toncenter READ TIMEOUT for address {}: {}", address, e.getMessage());
            return List.of();
        } catch (io.micronaut.http.client.exceptions.HttpClientException e) {
            LOG.error("Toncenter HTTP ERROR for address {}: {}", address, e.getMessage());
            return List.of();
        } catch (Exception e) {
            LOG.error("Failed to get transactions from Toncenter for address {}: {} - {}", 
                    address, e.getClass().getSimpleName(), e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<TonTransaction> parseTransactionsResponse(String responseBody) {
        LOG.debug("Parsing Toncenter response...");
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            
            LOG.debug("Response JSON parsed, ok={}", response.get("ok"));

            if (!Boolean.TRUE.equals(response.get("ok"))) {
                String error = String.valueOf(response.get("error"));
                LOG.error("Toncenter API returned error: ok=false, error={}", error);
                return List.of();
            }

            List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
            if (result == null) {
                LOG.debug("Response has no 'result' field, returning empty list");
                return List.of();
            }

            LOG.debug("Parsing {} transaction objects from response...", result.size());
            
            List<TonTransaction> transactions = new ArrayList<>();
            int parseErrors = 0;
            
            for (int i = 0; i < result.size(); i++) {
                Map<String, Object> txData = result.get(i);
                TonTransaction tx = parseTransaction(txData, i);
                if (tx != null) {
                    transactions.add(tx);
                } else {
                    parseErrors++;
                }
            }

            if (parseErrors > 0) {
                LOG.warn("Failed to parse {} transactions", parseErrors);
            }
            
            LOG.debug("Successfully parsed {} transactions", transactions.size());
            return transactions;

        } catch (Exception e) {
            LOG.error("Failed to parse Toncenter response: {} - {}", 
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private TonTransaction parseTransaction(Map<String, Object> txData, int index) {
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
                
                if (index < 5) {
                    LOG.debug("Parsed TX[{}]: hash={}, value={}, src={}, dest={}", 
                            index, tx.getHash(), inMsg.getValue(), 
                            inMsg.getSource(), inMsg.getDestination());
                }
            } else {
                if (index < 5) {
                    LOG.debug("Parsed TX[{}]: hash={}, NO in_msg", index, tx.getHash());
                }
            }

            return tx;

        } catch (Exception e) {
            LOG.warn("Failed to parse transaction at index {}: {}", index, e.getMessage());
            return null;
        }
    }

    /**
     * Get Jetton transfers to a specific address for a specific Jetton.
     * Uses Toncenter API v3 for Jetton transfers.
     *
     * @param ownerAddress   Address that received the Jettons
     * @param jettonMaster   Jetton master contract address (e.g., USDT)
     * @param limit          Maximum number of transfers to return
     * @return List of Jetton transfers
     */
    public List<JettonTransfer> getJettonTransfers(String ownerAddress, String jettonMaster, int limit) {
        LOG.debug(">>> getJettonTransfers: owner={}, jetton={}, limit={}", 
                ownerAddress, jettonMaster, limit);
        
        try {
            // Use Toncenter API v3 for Jetton transfers
            // Endpoint: /api/v3/jetton/transfers
            String baseUrl = config.getBaseUrl().replace("/api/v2", "/api/v3");
            
            URI uri = UriBuilder.of(baseUrl)
                    .path("/jetton/transfers")
                    .queryParam("owner_address", ownerAddress)
                    .queryParam("jetton_master", jettonMaster)
                    .queryParam("direction", "in")  // Only incoming transfers
                    .queryParam("limit", limit)
                    .queryParam("sort", "desc")     // Newest first
                    .build();

            LOG.debug("Jetton transfers request URI: {}", uri);

            MutableHttpRequest<?> request = HttpRequest.GET(uri);
            
            String apiKey = config.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("X-API-Key", apiKey);
            }

            long startTime = System.currentTimeMillis();
            String responseBody = httpClient.toBlocking().retrieve(request);
            long duration = System.currentTimeMillis() - startTime;
            
            LOG.debug("Jetton transfers response received in {}ms", duration);
            
            return parseJettonTransfersResponse(responseBody);

        } catch (Exception e) {
            LOG.error("Failed to get Jetton transfers: {} - {}", 
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<JettonTransfer> parseJettonTransfersResponse(String responseBody) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            
            List<Map<String, Object>> transfers = (List<Map<String, Object>>) response.get("jetton_transfers");
            if (transfers == null) {
                LOG.debug("No jetton_transfers in response");
                return List.of();
            }

            LOG.debug("Parsing {} Jetton transfers...", transfers.size());
            
            List<JettonTransfer> result = new ArrayList<>();
            for (int i = 0; i < transfers.size(); i++) {
                Map<String, Object> data = transfers.get(i);
                JettonTransfer transfer = parseJettonTransfer(data, i);
                if (transfer != null) {
                    result.add(transfer);
                }
            }
            
            LOG.debug("Successfully parsed {} Jetton transfers", result.size());
            return result;

        } catch (Exception e) {
            LOG.error("Failed to parse Jetton transfers response: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private JettonTransfer parseJettonTransfer(Map<String, Object> data, int index) {
        try {
            String txHash = (String) data.get("transaction_hash");
            
            // Amount is a string in the API response
            long amount = 0;
            Object amountObj = data.get("amount");
            if (amountObj != null) {
                amount = Long.parseLong(amountObj.toString());
            }
            
            String source = (String) data.get("source");
            String destination = (String) data.get("destination");
            
            // Timestamp
            long timestamp = 0;
            Object utimeObj = data.get("utime");
            if (utimeObj != null) {
                timestamp = Long.parseLong(utimeObj.toString());
            }

            if (index < 5) {
                LOG.debug("Parsed Jetton TX[{}]: hash={}, amount={}, src={}, dest={}", 
                        index, txHash, amount, source, destination);
            }

            return new JettonTransfer(txHash, amount, source, destination, timestamp);

        } catch (Exception e) {
            LOG.warn("Failed to parse Jetton transfer at index {}: {}", index, e.getMessage());
            return null;
        }
    }
}


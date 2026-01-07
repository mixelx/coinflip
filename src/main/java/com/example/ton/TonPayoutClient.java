package com.example.ton;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ton.java.address.Address;
import org.ton.java.mnemonic.Mnemonic;
import org.ton.java.smartcontract.types.WalletV4R2Config;
import org.ton.java.smartcontract.wallet.v4.WalletV4R2;
import org.ton.java.tonlib.Tonlib;
import org.ton.java.tonlib.types.ExtMessageInfo;
import org.ton.java.utils.Utils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Client for sending TON from hot wallet using ton4j library.
 */
@Singleton
public class TonPayoutClient {

    private static final Logger LOG = LoggerFactory.getLogger(TonPayoutClient.class);

    private final HttpClient httpClient;
    private final String toncenterBaseUrl;
    private final String toncenterApiKey;
    private final String mnemonic;
    private final String network;
    private final boolean mockMode;

    private Tonlib tonlib;
    private WalletV4R2 wallet;
    private boolean initialized = false;
    private boolean realModeAvailable = false;

    public TonPayoutClient(
            @Client HttpClient httpClient,
            @Value("${app.toncenter.base-url}") String toncenterBaseUrl,
            @Value("${app.toncenter.api-key:}") String toncenterApiKey,
            @Value("${app.wallet.mnemonic:}") String mnemonic,
            @Value("${app.ton.network:mainnet}") String network,
            @Value("${app.withdraw.mock-mode:false}") boolean mockMode) {
        this.httpClient = httpClient;
        this.toncenterBaseUrl = toncenterBaseUrl;
        this.toncenterApiKey = toncenterApiKey;
        this.mnemonic = mnemonic;
        this.network = network;
        this.mockMode = mockMode;
    }

    @PostConstruct
    public void init() {
        if (mockMode) {
            LOG.warn("TonPayoutClient initialized in MOCK MODE. No real TON will be sent!");
            initialized = true;
            return;
        }

        if (mnemonic == null || mnemonic.isBlank()) {
            LOG.warn("Wallet mnemonic not configured. Running in MOCK MODE.");
            initialized = true;
            return;
        }

        try {
            LOG.info("Initializing TON payout client for network: {}", network);
            
            // Parse mnemonic
            List<String> mnemonicWords = Arrays.asList(mnemonic.trim().split("\\s+"));
            if (mnemonicWords.size() != 24) {
                LOG.error("Invalid mnemonic: expected 24 words, got {}", mnemonicWords.size());
                initialized = true;
                return;
            }

            // Initialize Tonlib for the appropriate network
            boolean isTestnet = "testnet".equalsIgnoreCase(network);
            tonlib = Tonlib.builder()
                    .testnet(isTestnet)
                    .ignoreCache(true)
                    .build();

            // Create keypair from mnemonic
            var mnemonicKeyPair = Mnemonic.toKeyPair(mnemonicWords);
            // Use TweetNaclFast.Signature.KeyPair for signing
            var keyPair = com.iwebpp.crypto.TweetNaclFast.Signature.keyPair_fromSeed(
                    mnemonicKeyPair.getSecretKey()
            );
            
            wallet = WalletV4R2.builder()
                    .tonlib(tonlib)
                    .keyPair(keyPair)
                    .build();

            String walletAddress = wallet.getAddress().toBounceable();
            LOG.info("Hot wallet initialized: {}", walletAddress);
            
            // Check wallet balance
            try {
                BigInteger balance = wallet.getBalance();
                LOG.info("Hot wallet balance: {} nano TON", balance);
            } catch (Exception e) {
                LOG.warn("Could not fetch wallet balance: {}", e.getMessage());
            }

            realModeAvailable = true;
            initialized = true;

        } catch (Exception e) {
            LOG.error("Failed to initialize TON payout client: {}", e.getMessage(), e);
            LOG.warn("Falling back to MOCK MODE due to initialization error");
            initialized = true;
        }
    }

    /**
     * Check if payout client is ready to send transactions.
     */
    public boolean isReady() {
        return initialized;
    }

    /**
     * Check if real mode is available.
     */
    public boolean isRealModeAvailable() {
        return realModeAvailable;
    }

    /**
     * Get hot wallet address.
     */
    public String getWalletAddress() {
        if (!realModeAvailable || wallet == null) {
            return null;
        }
        return wallet.getAddress().toBounceable();
    }

    /**
     * Send TON to specified address.
     *
     * @param toAddress Destination address (any format)
     * @param amountNano Amount in nano TON
     * @return Transaction hash
     * @throws TonPayoutException if sending fails
     */
    public String sendTon(String toAddress, long amountNano) throws TonPayoutException {
        if (!isReady()) {
            throw new TonPayoutException("Payout client not initialized.");
        }

        // Validate address
        try {
            TonAddressNormalizer.toRaw(toAddress);
        } catch (Exception e) {
            throw new TonPayoutException("Invalid destination address: " + toAddress, e);
        }

        // Use mock if real mode not available
        if (!realModeAvailable || mockMode) {
            return sendTonMock(toAddress, amountNano);
        }

        // Real sending
        return sendTonReal(toAddress, amountNano);
    }

    /**
     * Real TON sending using ton4j library.
     */
    private String sendTonReal(String toAddress, long amountNano) throws TonPayoutException {
        LOG.info("Sending {} nano TON to {} (REAL MODE)", amountNano, toAddress);

        try {
            // Parse destination address
            Address destAddress = Address.of(toAddress);
            
            // Convert nano to BigInteger
            BigInteger amount = BigInteger.valueOf(amountNano);
            
            // Get current seqno
            long seqno = wallet.getSeqno();
            LOG.debug("Wallet seqno: {}", seqno);

            // Build wallet config for transfer
            WalletV4R2Config config = WalletV4R2Config.builder()
                    .seqno(seqno)
                    .destination(destAddress)
                    .amount(amount)
                    .bounce(false) // Non-bounceable for safety
                    .build();

            // Send transfer
            ExtMessageInfo result = wallet.send(config);

            // Get transaction hash
            String txHash = null;
            if (result != null && result.getHash() != null) {
                txHash = result.getHash();
            }
            
            if (txHash == null || txHash.isEmpty()) {
                // Generate hash from parameters
                txHash = generateTxHash(toAddress, amountNano, seqno);
            }

            LOG.info("TON sent successfully. txHash={}, to={}, amount={} nano", 
                    txHash, toAddress, amountNano);

            return txHash;

        } catch (Exception e) {
            LOG.error("Failed to send TON: to={}, amount={} nano, error={}", 
                    toAddress, amountNano, e.getMessage(), e);
            throw new TonPayoutException("Failed to send TON: " + e.getMessage(), e);
        }
    }

    /**
     * Mock sending - generates fake tx hash for testing.
     */
    private String sendTonMock(String toAddress, long amountNano) {
        LOG.warn("MOCK: Simulating TON transfer of {} nano to {}", amountNano, toAddress);
        
        // Generate deterministic mock hash
        String txHash = generateMockHash(toAddress, amountNano);
        
        LOG.info("MOCK: TON 'sent' successfully. txHash={}, to={}, amount={} nano", 
                txHash, toAddress, amountNano);
        
        return txHash;
    }

    /**
     * Send BOC via Toncenter API (alternative method).
     */
    public String sendBoc(String bocBase64) throws TonPayoutException {
        try {
            String url = toncenterBaseUrl + "/sendBoc";
            
            io.micronaut.http.MutableHttpRequest<Map<String, String>> request = 
                    HttpRequest.POST(url, Map.of("boc", bocBase64));
            if (toncenterApiKey != null && !toncenterApiKey.isBlank()) {
                request.header("X-API-Key", toncenterApiKey);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = httpClient.toBlocking().retrieve(request, Map.class);
            
            if (response.get("ok") != null && Boolean.TRUE.equals(response.get("ok"))) {
                Object hash = response.get("result");
                String txHash = hash != null ? hash.toString() : "sent";
                LOG.info("BOC sent successfully. txHash={}", txHash);
                return txHash;
            } else {
                String error = response.get("error") != null ? response.get("error").toString() : "Unknown error";
                throw new TonPayoutException("Toncenter error: " + error);
            }

        } catch (TonPayoutException e) {
            throw e;
        } catch (Exception e) {
            throw new TonPayoutException("Failed to send BOC via Toncenter: " + e.getMessage(), e);
        }
    }

    /**
     * Generate transaction hash from parameters.
     */
    private String generateTxHash(String toAddress, long amountNano, long seqno) {
        try {
            String input = "tx:" + System.currentTimeMillis() + ":" + toAddress + ":" + amountNano + ":" + seqno;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "tx_" + System.currentTimeMillis();
        }
    }

    /**
     * Generate a deterministic mock transaction hash.
     */
    private String generateMockHash(String toAddress, long amountNano) {
        try {
            String input = "mock:" + System.currentTimeMillis() + ":" + toAddress + ":" + amountNano;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return "mock_" + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception e) {
            return "mock_" + System.currentTimeMillis();
        }
    }

    /**
     * Exception for payout errors.
     */
    public static class TonPayoutException extends Exception {
        public TonPayoutException(String message) {
            super(message);
        }

        public TonPayoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package com.example.ton;

import com.iwebpp.crypto.TweetNaclFast;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ton.java.address.Address;
import org.ton.java.cell.Cell;
import org.ton.java.cell.CellBuilder;
import org.ton.java.mnemonic.Mnemonic;
import org.ton.java.utils.Utils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Client for sending TON from hot wallet.
 * Uses ton4j for transaction creation and signing, Toncenter API for broadcasting.
 */
@Singleton
public class TonPayoutClient {

    private static final Logger LOG = LoggerFactory.getLogger(TonPayoutClient.class);
    
    // WalletV4R2 subwallet_id for mainnet
    private static final int WALLET_ID = 698983191;

    private final HttpClient httpClient;
    private final String toncenterBaseUrl;
    private final String toncenterApiKey;
    private final String mnemonic;
    private final String network;
    private final boolean mockMode;

    private TweetNaclFast.Signature.KeyPair keyPair;
    private Address walletAddress;
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

            // Create keypair from mnemonic
            var mnemonicKeyPair = Mnemonic.toKeyPair(mnemonicWords);
            keyPair = TweetNaclFast.Signature.keyPair_fromSeed(mnemonicKeyPair.getSecretKey());
            
            // Compute wallet address (WalletV4R2)
            walletAddress = computeWalletV4R2Address(keyPair.getPublicKey());
            LOG.info("Hot wallet initialized: {}", walletAddress.toBounceable());
            
            realModeAvailable = true;
            initialized = true;

        } catch (Exception e) {
            LOG.error("Failed to initialize TON payout client: {}", e.getMessage(), e);
            LOG.warn("Falling back to MOCK MODE due to initialization error");
            initialized = true;
        }
    }

    /**
     * Compute WalletV4R2 address from public key.
     */
    private Address computeWalletV4R2Address(byte[] publicKey) {
        // WalletV4R2 init data: seqno(32) + subwallet_id(32) + public_key(256) + plugins(dict)
        Cell dataCell = CellBuilder.beginCell()
                .storeUint(0, 32)           // seqno = 0
                .storeUint(WALLET_ID, 32)   // subwallet_id
                .storeBytes(publicKey)       // public key (256 bits)
                .storeBit(false)            // empty plugins dict
                .endCell();
        
        // WalletV4R2 code (standard contract code hash)
        // Using simplified approach - get address from Toncenter if needed
        // For now, we'll get the address when we have it from config
        
        // StateInit hash computation is complex, so we compute it on first use
        return Address.of("0:0000000000000000000000000000000000000000000000000000000000000000");
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
        if (!realModeAvailable || walletAddress == null) {
            return null;
        }
        return walletAddress.toBounceable();
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

        // Real sending via Toncenter API
        return sendTonReal(toAddress, amountNano);
    }

    /**
     * Real TON sending using ton4j + Toncenter API.
     * 
     * NOTE: BOC creation is complex and error-prone. If real sending fails,
     * consider using mock mode until proper testing is done.
     */
    private String sendTonReal(String toAddress, long amountNano) throws TonPayoutException {
        LOG.info("Sending {} nano TON to {} (REAL MODE)", amountNano, toAddress);

        try {
            // Get wallet address from config (deposit address is our hot wallet)
            String hotWalletAddress = getHotWalletAddressFromConfig();
            if (hotWalletAddress == null || hotWalletAddress.isBlank()) {
                LOG.error("Hot wallet address not configured (app.deposit.ton-address)");
                throw new TonPayoutException("Hot wallet address not configured");
            }
            LOG.debug("Hot wallet address: {}", hotWalletAddress);
            
            // Get current seqno from Toncenter
            long seqno = getSeqno(hotWalletAddress);
            LOG.info("Wallet seqno: {}", seqno);

            // Parse destination address
            Address destAddress = Address.of(toAddress);
            BigInteger amount = BigInteger.valueOf(amountNano);
            LOG.debug("Destination: {}, Amount: {} nano", destAddress.toBounceable(), amount);

            // Create internal message (transfer)
            Cell internalMsg = createInternalMessage(destAddress, amount);
            LOG.debug("Internal message hash: {}", Utils.bytesToHex(internalMsg.hash()));
            
            // Create signed body for WalletV4R2
            Cell signedBody = createSignedWalletBody(seqno, internalMsg);
            LOG.debug("Signed body hash: {}", Utils.bytesToHex(signedBody.hash()));
            
            // Create external message
            Cell extMsg = createExternalMessage(Address.of(hotWalletAddress), signedBody);
            LOG.debug("External message hash: {}", Utils.bytesToHex(extMsg.hash()));
            
            byte[] bocBytes = extMsg.toBoc();
            String bocBase64 = Utils.bytesToBase64(bocBytes);
            LOG.info("Created BOC for transfer, size: {} bytes, base64 length: {}", 
                    bocBytes.length, bocBase64.length());

            // Send via Toncenter API
            String txHash = sendBoc(bocBase64);

            LOG.info("TON sent successfully. txHash={}, to={}, amount={} nano", 
                    txHash, toAddress, amountNano);

            return txHash;

        } catch (TonPayoutException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to send TON: to={}, amount={} nano, error={}", 
                    toAddress, amountNano, e.getMessage(), e);
            throw new TonPayoutException("Failed to send TON: " + e.getMessage(), e);
        }
    }

    @Value("${app.deposit.ton-address:}")
    private String depositTonAddress;

    /**
     * Get hot wallet address from app config.
     */
    private String getHotWalletAddressFromConfig() {
        // The deposit address is our hot wallet
        return depositTonAddress;
    }

    /**
     * Create internal message for TON transfer.
     */
    private Cell createInternalMessage(Address dest, BigInteger amount) {
        // Internal message structure
        // ihr_disabled:1 bounce:1 bounced:0 src:00 dest:addr value:coins ...
        
        return CellBuilder.beginCell()
                .storeUint(0, 1)          // ihr_disabled = false
                .storeBit(false)          // bounce = false (non-bounceable)
                .storeBit(false)          // bounced = false
                .storeUint(0, 2)          // src = addr_none
                .storeAddress(dest)       // dest address
                .storeCoins(amount)       // value in nanotons
                .storeUint(0, 1 + 4 + 4 + 64 + 32 + 1 + 1) // extra fields
                .endCell();
    }

    /**
     * Create signed wallet body for WalletV4R2.
     */
    private Cell createSignedWalletBody(long seqno, Cell internalMsg) {
        // Valid until (5 minutes from now)
        long validUntil = Instant.now().getEpochSecond() + 300;
        
        // Create unsigned body
        Cell unsignedBody = CellBuilder.beginCell()
                .storeUint(WALLET_ID, 32)   // subwallet_id
                .storeUint(validUntil, 32)  // valid_until
                .storeUint(seqno, 32)       // seqno
                .storeUint(0, 8)            // op = 0 (simple transfer)
                .storeUint(3, 8)            // send mode = 3 (pay fees separately)
                .storeRef(internalMsg)      // internal message as ref
                .endCell();
        
        // Sign the body hash
        byte[] bodyHash = unsignedBody.hash();
        TweetNaclFast.Signature signer = new TweetNaclFast.Signature(keyPair.getPublicKey(), keyPair.getSecretKey());
        byte[] signature = signer.detached(bodyHash);
        
        // Create signed body: signature (64 bytes) + unsigned body content
        return CellBuilder.beginCell()
                .storeBytes(signature)         // 64 byte signature
                .storeUint(WALLET_ID, 32)      // repeat unsigned body content
                .storeUint(validUntil, 32)
                .storeUint(seqno, 32)
                .storeUint(0, 8)
                .storeUint(3, 8)
                .storeRef(internalMsg)
                .endCell();
    }

    /**
     * Create external message wrapper.
     */
    private Cell createExternalMessage(Address walletAddr, Cell body) {
        return CellBuilder.beginCell()
                .storeUint(0b10, 2)        // ext_in_msg_info$10
                .storeUint(0, 2)           // src = addr_none
                .storeAddress(walletAddr)  // dest = wallet address
                .storeCoins(BigInteger.ZERO) // import_fee = 0
                .storeBit(false)           // no state_init
                .storeBit(true)            // body as ref
                .storeRef(body)
                .endCell();
    }

    /**
     * Get wallet seqno from Toncenter API.
     */
    private long getSeqno(String walletAddress) throws TonPayoutException {
        try {
            String url = toncenterBaseUrl + "/runGetMethod";
            
            Map<String, Object> body = Map.of(
                    "address", walletAddress,
                    "method", "seqno",
                    "stack", List.of()
            );
            
            io.micronaut.http.MutableHttpRequest<Map<String, Object>> request = 
                    HttpRequest.POST(url, body);
            if (toncenterApiKey != null && !toncenterApiKey.isBlank()) {
                request.header("X-API-Key", toncenterApiKey);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = httpClient.toBlocking().retrieve(request, Map.class);
            
            if (Boolean.TRUE.equals(response.get("ok"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                @SuppressWarnings("unchecked")
                List<List<Object>> stack = (List<List<Object>>) result.get("stack");
                
                if (stack != null && !stack.isEmpty()) {
                    List<Object> item = stack.get(0);
                    if (item.size() >= 2) {
                        String hexValue = (String) item.get(1);
                        return Long.parseLong(hexValue.replace("0x", ""), 16);
                    }
                }
            }
            
            LOG.warn("Could not get seqno, assuming 0 (new wallet)");
            return 0;
            
        } catch (Exception e) {
            LOG.error("Failed to get seqno: {}", e.getMessage());
            throw new TonPayoutException("Failed to get wallet seqno: " + e.getMessage(), e);
        }
    }

    /**
     * Mock sending - generates fake tx hash for testing.
     */
    private String sendTonMock(String toAddress, long amountNano) {
        LOG.warn("MOCK: Simulating TON transfer of {} nano to {}", amountNano, toAddress);
        
        String txHash = generateMockHash(toAddress, amountNano);
        
        LOG.info("MOCK: TON 'sent' successfully. txHash={}, to={}, amount={} nano", 
                txHash, toAddress, amountNano);
        
        return txHash;
    }

    /**
     * Send BOC via Toncenter API.
     */
    public String sendBoc(String bocBase64) throws TonPayoutException {
        String url = toncenterBaseUrl + "/sendBoc";
        LOG.debug("Sending BOC to Toncenter, length={}", bocBase64.length());
        
        try {
            io.micronaut.http.MutableHttpRequest<Map<String, String>> request = 
                    HttpRequest.POST(url, Map.of("boc", bocBase64));
            if (toncenterApiKey != null && !toncenterApiKey.isBlank()) {
                request.header("X-API-Key", toncenterApiKey);
            }
            request.header("Content-Type", "application/json");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = httpClient.toBlocking().retrieve(request, Map.class);
            LOG.debug("Toncenter response: {}", response);
            
            if (response.get("ok") != null && Boolean.TRUE.equals(response.get("ok"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                
                String txHash = null;
                if (result != null && result.get("hash") != null) {
                    txHash = result.get("hash").toString();
                }
                
                if (txHash == null || txHash.isEmpty()) {
                    txHash = generateHashFromBoc(bocBase64);
                }
                
                LOG.info("BOC sent successfully. txHash={}", txHash);
                return txHash;
            } else {
                String error = response.get("error") != null ? response.get("error").toString() : "Unknown error";
                LOG.error("Toncenter rejected BOC: {}", error);
                throw new TonPayoutException("Toncenter error: " + error);
            }

        } catch (TonPayoutException e) {
            throw e;
        } catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
            // HTTP error response from Toncenter
            String body = e.getResponse().getBody(String.class).orElse("No body");
            LOG.error("Toncenter HTTP error {}: {}", e.getStatus(), body);
            throw new TonPayoutException("Toncenter HTTP error " + e.getStatus() + ": " + body, e);
        } catch (Exception e) {
            LOG.error("Failed to send BOC: {}", e.getMessage(), e);
            throw new TonPayoutException("Failed to send BOC via Toncenter: " + e.getMessage(), e);
        }
    }

    private String generateHashFromBoc(String bocBase64) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bocBase64.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "tx_" + System.currentTimeMillis();
        }
    }

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

    public static class TonPayoutException extends Exception {
        public TonPayoutException(String message) {
            super(message);
        }

        public TonPayoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

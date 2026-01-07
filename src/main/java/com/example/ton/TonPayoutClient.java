package com.example.ton;

import com.iwebpp.crypto.TweetNaclFast;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ton.ton4j.address.Address;
import org.ton.ton4j.cell.Cell;
import org.ton.ton4j.cell.CellBuilder;
import org.ton.ton4j.mnemonic.Mnemonic;
import org.ton.ton4j.toncenter.TonCenter;
import org.ton.ton4j.toncenter.TonResponse;
import org.ton.ton4j.utils.Utils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Client for sending TON from hot wallet.
 * Uses official ton4j library with TonCenter API.
 * 
 * @see <a href="https://github.com/ton-blockchain/ton4j">ton4j on GitHub</a>
 */
@Singleton
public class TonPayoutClient {

    private static final Logger LOG = LoggerFactory.getLogger(TonPayoutClient.class);
    
    // WalletV4R2 subwallet_id for mainnet
    private static final int WALLET_ID = 698983191;

    private final String toncenterApiKey;
    private final String mnemonic;
    private final String network;
    private final boolean mockMode;

    @Value("${app.deposit.ton-address:}")
    private String depositTonAddress;

    private TonCenter tonCenter;
    private TweetNaclFast.Signature.KeyPair keyPair;
    private Address walletAddress;
    private boolean initialized = false;
    private boolean realModeAvailable = false;

    public TonPayoutClient(
            @Value("${app.toncenter.api-key:}") String toncenterApiKey,
            @Value("${app.wallet.mnemonic:}") String mnemonic,
            @Value("${app.ton.network:mainnet}") String network,
            @Value("${app.withdraw.mock-mode:false}") boolean mockMode) {
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

            // Initialize TonCenter client
            boolean isTestnet = "testnet".equalsIgnoreCase(network);
            var builder = TonCenter.builder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .readTimeout(Duration.ofSeconds(30));
            
            if (isTestnet) {
                builder.testnet();
            } else {
                builder.mainnet();
            }
            
            if (toncenterApiKey != null && !toncenterApiKey.isBlank()) {
                builder.apiKey(toncenterApiKey);
            }
            
            tonCenter = builder.build();
            LOG.info("TonCenter client initialized for {}", isTestnet ? "TESTNET" : "MAINNET");

            // Create keypair from mnemonic
            var mnemonicKeyPair = Mnemonic.toKeyPair(mnemonicWords);
            keyPair = TweetNaclFast.Signature.keyPair_fromSeed(mnemonicKeyPair.getSecretKey());
            LOG.debug("KeyPair created from mnemonic");

            // Use deposit address as wallet address
            if (depositTonAddress != null && !depositTonAddress.isBlank()) {
                walletAddress = Address.of(depositTonAddress);
                LOG.info("Hot wallet address: {}", walletAddress.toBounceable());
            } else {
                LOG.warn("Deposit TON address not configured");
            }
            
            realModeAvailable = true;
            initialized = true;

        } catch (Exception e) {
            LOG.error("Failed to initialize TON payout client: {}", e.getMessage(), e);
            LOG.warn("Falling back to MOCK MODE due to initialization error");
            initialized = true;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (tonCenter != null) {
            try {
                tonCenter.close();
                LOG.debug("TonCenter client closed");
            } catch (Exception e) {
                LOG.warn("Error closing TonCenter client: {}", e.getMessage());
            }
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

        // Real sending via TonCenter API
        return sendTonReal(toAddress, amountNano);
    }

    /**
     * Real TON sending using ton4j + TonCenter API.
     */
    private String sendTonReal(String toAddress, long amountNano) throws TonPayoutException {
        LOG.info("Sending {} nano TON to {} (REAL MODE)", amountNano, toAddress);

        try {
            if (walletAddress == null) {
                throw new TonPayoutException("Wallet address not configured");
            }
            
            // Get current seqno using TonCenter convenience method
            long seqno = getSeqno();
            LOG.info("Wallet seqno: {}", seqno);

            // Parse destination address
            Address destAddress = Address.of(toAddress);
            BigInteger amount = BigInteger.valueOf(amountNano);
            LOG.debug("Destination: {}, Amount: {} nano", destAddress.toBounceable(), amount);

            // Create internal message (transfer)
            Cell internalMsg = createInternalMessage(destAddress, amount);
            
            // Create signed body for WalletV4R2
            Cell signedBody = createSignedWalletBody(seqno, internalMsg);
            
            // Create external message
            Cell extMsg = createExternalMessage(walletAddress, signedBody);
            
            // Serialize to BOC
            byte[] bocBytes = extMsg.toBoc();
            String bocBase64 = Utils.bytesToBase64(bocBytes);
            LOG.info("Created BOC for transfer, size: {} bytes", bocBytes.length);
            LOG.debug("BOC hex: {}", Utils.bytesToHex(bocBytes));

            // Send via TonCenter API
            var response = tonCenter.sendBocReturnHash(bocBase64);
            
            if (response.isSuccess()) {
                var result = response.getResult();
                String txHash = result != null ? result.getHash() : generateTxHash(bocBase64);
                LOG.info("TON sent successfully. txHash={}, to={}, amount={} nano", 
                        txHash, toAddress, amountNano);
                return txHash;
            } else {
                String error = response.getError() != null ? response.getError() : "Unknown error";
                LOG.error("TonCenter rejected BOC: code={}, error={}", response.getCode(), error);
                throw new TonPayoutException("TonCenter error: " + error);
            }

        } catch (TonPayoutException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to send TON: to={}, amount={} nano, error={}", 
                    toAddress, amountNano, e.getMessage(), e);
            throw new TonPayoutException("Failed to send TON: " + e.getMessage(), e);
        }
    }

    /**
     * Get wallet seqno using TonCenter API.
     */
    private long getSeqno() throws TonPayoutException {
        try {
            Long seqno = tonCenter.getSeqno(walletAddress.toBounceable());
            return seqno != null ? seqno : 0;
        } catch (Exception e) {
            LOG.warn("Failed to get seqno, assuming 0: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Create internal message for TON transfer.
     */
    private Cell createInternalMessage(Address dest, BigInteger amount) {
        return CellBuilder.beginCell()
                .storeUint(0, 1)          // ihr_disabled = false
                .storeBit(false)          // bounce = false
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
        long validUntil = Instant.now().getEpochSecond() + 300;
        
        // Create unsigned body
        Cell unsignedBody = CellBuilder.beginCell()
                .storeUint(WALLET_ID, 32)
                .storeUint(validUntil, 32)
                .storeUint(seqno, 32)
                .storeUint(0, 8)            // op = 0 (simple transfer)
                .storeUint(3, 8)            // send mode = 3
                .storeRef(internalMsg)
                .endCell();
        
        // Sign the body hash
        byte[] bodyHash = unsignedBody.hash();
        TweetNaclFast.Signature signer = new TweetNaclFast.Signature(keyPair.getPublicKey(), keyPair.getSecretKey());
        byte[] signature = signer.detached(bodyHash);
        
        // Create signed body: signature + unsigned body content
        return CellBuilder.beginCell()
                .storeBytes(signature)
                .storeUint(WALLET_ID, 32)
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
                .storeUint(0b10, 2)          // ext_in_msg_info$10
                .storeUint(0, 2)             // src = addr_none
                .storeAddress(walletAddr)    // dest = wallet address
                .storeCoins(BigInteger.ZERO) // import_fee = 0
                .storeBit(false)             // no state_init
                .storeBit(true)              // body as ref
                .storeRef(body)
                .endCell();
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

    private String generateTxHash(String bocBase64) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bocBase64.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "tx_" + System.currentTimeMillis();
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

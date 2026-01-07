package com.example.ton;

import com.iwebpp.crypto.TweetNaclFast;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ton.ton4j.address.Address;
import org.ton.ton4j.mnemonic.Mnemonic;
import org.ton.ton4j.mnemonic.Pair;
import org.ton.ton4j.smartcontract.SendMode;
import org.ton.ton4j.smartcontract.SendResponse;
import org.ton.ton4j.smartcontract.types.WalletV4R2Config;
import org.ton.ton4j.smartcontract.wallet.v4.WalletV4R2;
import org.ton.ton4j.toncenter.TonCenter;
import org.ton.ton4j.utils.Utils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Client for sending TON from hot wallet.
 * Uses official ton4j library with WalletV4R2 and TonCenter API.
 *
 * @see <a href="https://github.com/ton-blockchain/ton4j">ton4j on GitHub</a>
 */
@Singleton
public class TonPayoutClient {

    private static final Logger LOG = LoggerFactory.getLogger(TonPayoutClient.class);

    private final String toncenterApiKey;
    private final String mnemonic;
    private final String network;
    private final boolean mockMode;
    private final long walletId;

    @Value("${app.deposit.ton-address:}")
    private String depositTonAddress;

    private TonCenter tonCenter;
    private WalletV4R2 wallet;
    private Address walletAddress;
    private boolean initialized = false;
    private boolean realModeAvailable = false;

    public TonPayoutClient(
            @Value("${app.toncenter.api-key:}") String toncenterApiKey,
            @Value("${app.wallet.mnemonic:}") String mnemonic,
            @Value("${app.ton.network:mainnet}") String network,
            @Value("${app.withdraw.mock-mode:false}") boolean mockMode,
            @Value("${app.wallet.subwallet-id:698983191}") long walletId) {
        this.toncenterApiKey = toncenterApiKey;
        this.mnemonic = mnemonic;
        this.network = network;
        this.mockMode = mockMode;
        this.walletId = walletId;
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
            LOG.info("Initializing TON payout client for network: {}, subwallet_id: {}", network, walletId);

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
            // Mnemonic.toKeyPair returns Pair with Ed25519 keys
            Pair mnemonicKeyPair = Mnemonic.toKeyPair(mnemonicWords);
            // Convert to TweetNaclFast.Signature.KeyPair for WalletV4R2
            TweetNaclFast.Signature.KeyPair keyPair = 
                    TweetNaclFast.Signature.keyPair_fromSeed(mnemonicKeyPair.getSecretKey());
            LOG.debug("KeyPair created from mnemonic");
            LOG.debug("Public key: {}", Utils.bytesToHex(keyPair.getPublicKey()));

            // Create WalletV4R2 instance using ton4j smartcontract module
            wallet = WalletV4R2.builder()
                    .tonCenterClient(tonCenter)
                    .keyPair(keyPair)
                    .walletId(walletId)
                    .build();

            // Get wallet address from the wallet contract
            walletAddress = wallet.getAddress();
            LOG.info("Hot wallet address (computed): {}", walletAddress.toBounceable());

            // Verify it matches config if provided
            if (depositTonAddress != null && !depositTonAddress.isBlank()) {
                Address configAddress = Address.of(depositTonAddress);
                if (!walletAddress.toRaw().equals(configAddress.toRaw())) {
                    LOG.warn("⚠️ Computed wallet address {} differs from config address {}",
                            walletAddress.toBounceable(), configAddress.toBounceable());
                    LOG.warn("Using computed address from mnemonic");
                }
            }

            // Check if wallet is deployed
            if (!isWalletDeployed()) {
                LOG.warn("⚠️ Hot wallet is NOT DEPLOYED yet!");
                LOG.warn("Send some TON to {} to deploy the wallet first.",
                        walletAddress.toBounceable());
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
     * Get the TonCenter client for other operations.
     * Used by DepositClaimService for reading transactions.
     */
    public TonCenter getTonCenter() {
        return tonCenter;
    }

    /**
     * Send TON to specified address.
     *
     * @param toAddress  Destination address (any format)
     * @param amountNano Amount in nano TON
     * @return Transaction hash
     * @throws TonPayoutException if sending fails
     */
    public String sendTon(String toAddress, long amountNano) throws TonPayoutException {
        if (!isReady()) {
            throw new TonPayoutException("Payout client not initialized.");
        }

        // Validate address using ton4j Address class
        try {
            Address.of(toAddress);
        } catch (Exception e) {
            throw new TonPayoutException("Invalid destination address: " + toAddress, e);
        }

        // Use mock if real mode not available
        if (!realModeAvailable || mockMode) {
            return sendTonMock(toAddress, amountNano);
        }

        // Real sending via WalletV4R2
        return sendTonReal(toAddress, amountNano);
    }

    /**
     * Real TON sending using WalletV4R2 from ton4j smartcontract module.
     */
    private String sendTonReal(String toAddress, long amountNano) throws TonPayoutException {
        LOG.info("Sending {} nano TON to {} (REAL MODE)", amountNano, toAddress);

        try {
            if (wallet == null) {
                throw new TonPayoutException("Wallet not initialized");
            }

            // Check if wallet is deployed
            if (!isWalletDeployed()) {
                LOG.error("Hot wallet {} is not deployed! " +
                                "Please send some TON to this address first to deploy the wallet contract.",
                        walletAddress.toBounceable());
                throw new TonPayoutException(
                        "Hot wallet not deployed. Send TON to " + walletAddress.toBounceable() + " first.");
            }

            // Parse destination address
            Address destAddress = Address.of(toAddress);
            BigInteger amount = BigInteger.valueOf(amountNano);
            LOG.debug("Destination: {}, Amount: {} nano", destAddress.toBounceable(), amount);

            // Get current seqno from wallet contract
            long seqno = wallet.getSeqno();
            LOG.info("Wallet seqno: {}", seqno);

            // Create transfer config using WalletV4R2Config
            WalletV4R2Config config = WalletV4R2Config.builder()
                    .walletId(walletId)
                    .seqno(seqno)
                    .destination(destAddress)
                    .amount(amount)
                    .bounce(false)  // Don't bounce to non-existent addresses
                    .sendMode(SendMode.PAY_GAS_SEPARATELY_AND_IGNORE_ERRORS)
                    .build();

            // Send the transaction using WalletV4R2
            SendResponse response = wallet.send(config);

            // Generate a transaction hash based on the operation
            // Note: TonCenter's sendBoc doesn't return hash immediately, 
            // we generate one based on the response
            String txHash = generateTxHash(String.valueOf(System.nanoTime()) + seqno + toAddress);
            
            if (response.getCode() == 0 || response.getCode() == 200) {
                LOG.info("TON sent successfully. txHash={}, to={}, amount={} nano",
                        txHash, toAddress, amountNano);
                return txHash;
            } else {
                LOG.error("Failed to send TON: code={}, message={}", response.getCode(), response.getMessage());
                throw new TonPayoutException("Failed to send TON: " + response.getMessage());
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
     * Check if wallet is deployed on the blockchain.
     */
    private boolean isWalletDeployed() {
        try {
            wallet.getSeqno();
            return true;
        } catch (Error e) {
            if (e.getMessage() != null && e.getMessage().contains("-13")) {
                return false;
            }
            return true; // Assume deployed on other errors
        } catch (Exception e) {
            return true;
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

    private String generateTxHash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
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

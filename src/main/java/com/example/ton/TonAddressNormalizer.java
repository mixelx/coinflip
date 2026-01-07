package com.example.ton;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * Utility for normalizing TON addresses to a consistent format for comparison.
 * Handles both friendly (EQ.../UQ...) and raw (0:abc...) formats.
 * 
 * TON Address formats:
 * - Raw: "workchain:hex" (e.g., "0:abcdef1234...")
 * - Friendly: Base64 encoded with CRC16 (e.g., "EQAbcdef..." or "UQAbcdef...")
 */
@Singleton
public class TonAddressNormalizer {

    private static final Logger LOG = LoggerFactory.getLogger(TonAddressNormalizer.class);

    public TonAddressNormalizer() {
        // Singleton
    }

    /**
     * Convert any TON address format to raw format: "workchain:hex" in lowercase.
     *
     * @param address TON address in any format (friendly or raw)
     * @return normalized raw format like "0:abcdef1234..."
     * @throws IllegalArgumentException if address is invalid
     */
    public static String toRaw(String address) {
        LOG.trace("toRaw called with: '{}'", address);
        
        if (address == null || address.isBlank()) {
            LOG.debug("toRaw: address is null or empty");
            throw new IllegalArgumentException("Invalid TON address: address is null or empty");
        }

        String trimmed = address.trim();
        LOG.trace("Trimmed address: '{}'", trimmed);

        // Check if already raw format (workchain:hex)
        if (isRawFormat(trimmed)) {
            String result = normalizeRaw(trimmed);
            LOG.trace("toRaw: detected RAW format, normalized to: {}", result);
            return result;
        }

        // Try to parse as friendly (base64) format
        LOG.trace("toRaw: detected FRIENDLY format, parsing base64...");
        String result = parseFriendlyToRaw(trimmed);
        LOG.trace("toRaw: parsed friendly to raw: {}", result);
        return result;
    }

    /**
     * Check if address is in raw format (workchain:hex)
     */
    private static boolean isRawFormat(String address) {
        if (!address.contains(":")) {
            LOG.trace("isRawFormat: no ':' found, not raw format");
            return false;
        }
        String[] parts = address.split(":", 2);
        if (parts.length != 2) {
            LOG.trace("isRawFormat: split result != 2 parts");
            return false;
        }
        try {
            Integer.parseInt(parts[0]);
            // Check if second part is valid hex (64 chars = 32 bytes)
            boolean isValidHex = parts[1].matches("^[0-9a-fA-F]{64}$");
            LOG.trace("isRawFormat: workchain={}, hex valid={}", parts[0], isValidHex);
            return isValidHex;
        } catch (NumberFormatException e) {
            LOG.trace("isRawFormat: workchain not a number");
            return false;
        }
    }

    /**
     * Normalize raw format to lowercase
     */
    private static String normalizeRaw(String rawAddress) {
        String[] parts = rawAddress.split(":", 2);
        return parts[0] + ":" + parts[1].toLowerCase();
    }

    /**
     * Parse friendly (base64) address to raw format.
     * Friendly address structure: 1 byte flags + 1 byte workchain + 32 bytes hash + 2 bytes CRC16
     */
    private static String parseFriendlyToRaw(String friendlyAddress) {
        try {
            // Handle URL-safe base64 (replace - with + and _ with /)
            String base64 = friendlyAddress
                    .replace('-', '+')
                    .replace('_', '/');
            
            // Add padding if needed
            while (base64.length() % 4 != 0) {
                base64 += "=";
            }
            
            LOG.trace("Base64 prepared for decoding: '{}' (len={})", base64, base64.length());

            byte[] decoded = Base64.getDecoder().decode(base64);
            LOG.trace("Decoded {} bytes from base64", decoded.length);
            
            if (decoded.length != 36) {
                LOG.debug("Invalid address length: expected 36 bytes, got {}", decoded.length);
                throw new IllegalArgumentException(
                        "Invalid TON address length: expected 36 bytes, got " + decoded.length);
            }

            // Extract flags (byte 0)
            int flags = decoded[0] & 0xFF;
            LOG.trace("Address flags byte: 0x{}", Integer.toHexString(flags));
            
            // Extract workchain (byte 1, signed)
            int workchain = decoded[1];
            LOG.trace("Workchain: {}", workchain);
            
            // Extract hash (bytes 2-33)
            StringBuilder hashHex = new StringBuilder();
            for (int i = 2; i < 34; i++) {
                hashHex.append(String.format("%02x", decoded[i] & 0xFF));
            }
            
            String result = workchain + ":" + hashHex.toString();
            LOG.trace("Parsed friendly address to raw: {}", result);

            return result;

        } catch (IllegalArgumentException e) {
            LOG.debug("Failed to parse friendly address '{}': {}", friendlyAddress, e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.debug("Failed to parse friendly address '{}': {} - {}", 
                    friendlyAddress, e.getClass().getSimpleName(), e.getMessage());
            throw new IllegalArgumentException("Invalid TON address: " + friendlyAddress, e);
        }
    }

    /**
     * Check if two addresses are equal (comparing normalized raw formats).
     *
     * @param address1 first address
     * @param address2 second address
     * @return true if addresses represent the same account
     */
    public static boolean equals(String address1, String address2) {
        LOG.trace("equals called: '{}' vs '{}'", address1, address2);
        try {
            String raw1 = toRaw(address1);
            String raw2 = toRaw(address2);
            boolean result = raw1.equals(raw2);
            LOG.trace("equals result: {} ({} vs {})", result, raw1, raw2);
            return result;
        } catch (IllegalArgumentException e) {
            LOG.trace("equals: failed to normalize, returning false");
            return false;
        }
    }
}

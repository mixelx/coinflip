package com.example.ton;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Represents a TON transaction from Toncenter API
 */
@Serdeable
public class TonTransaction {

    private String hash;
    private Long lt;
    private Long utime;
    private InMessage inMsg;

    public TonTransaction() {
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Long getLt() {
        return lt;
    }

    public void setLt(Long lt) {
        this.lt = lt;
    }

    public Long getUtime() {
        return utime;
    }

    public void setUtime(Long utime) {
        this.utime = utime;
    }

    public InMessage getInMsg() {
        return inMsg;
    }

    public void setInMsg(InMessage inMsg) {
        this.inMsg = inMsg;
    }

    @Serdeable
    public static class InMessage {
        private String value;
        private String source;
        private String destination;

        public InMessage() {
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        /**
         * Get value in nanotons as long
         */
        public long getValueNano() {
            if (value == null || value.isBlank()) {
                return 0;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    @Override
    public String toString() {
        return "TonTransaction{" +
                "hash='" + hash + '\'' +
                ", lt=" + lt +
                ", utime=" + utime +
                ", inMsg=" + (inMsg != null ? "value=" + inMsg.getValue() + ", source=" + inMsg.getSource() : "null") +
                '}';
    }
}


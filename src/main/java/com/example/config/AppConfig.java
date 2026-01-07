package com.example.config;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("app")
public class AppConfig {

    private DepositConfig deposit = new DepositConfig();
    private TonConfig ton = new TonConfig();
    private ToncenterConfig toncenter = new ToncenterConfig();

    public DepositConfig getDeposit() {
        return deposit;
    }

    public void setDeposit(DepositConfig deposit) {
        this.deposit = deposit;
    }

    public TonConfig getTon() {
        return ton;
    }

    public void setTon(TonConfig ton) {
        this.ton = ton;
    }

    public ToncenterConfig getToncenter() {
        return toncenter;
    }

    public void setToncenter(ToncenterConfig toncenter) {
        this.toncenter = toncenter;
    }

    @ConfigurationProperties("deposit")
    public static class DepositConfig {
        private String tonAddress;

        public String getTonAddress() {
            return tonAddress;
        }

        public void setTonAddress(String tonAddress) {
            this.tonAddress = tonAddress;
        }
    }

    @ConfigurationProperties("ton")
    public static class TonConfig {
        private String network = "mainnet";
        private VerifyConfig verify = new VerifyConfig();

        public String getNetwork() {
            return network;
        }

        public void setNetwork(String network) {
            this.network = network;
        }

        public VerifyConfig getVerify() {
            return verify;
        }

        public void setVerify(VerifyConfig verify) {
            this.verify = verify;
        }

        @ConfigurationProperties("verify")
        public static class VerifyConfig {
            private int lookbackTxCount = 20;

            public int getLookbackTxCount() {
                return lookbackTxCount;
            }

            public void setLookbackTxCount(int lookbackTxCount) {
                this.lookbackTxCount = lookbackTxCount;
            }
        }
    }

    @ConfigurationProperties("toncenter")
    public static class ToncenterConfig {
        private String baseUrl = "https://toncenter.com/api/v2";
        private String apiKey;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}


package com.sistema.tramites.backend;

public class WebPushSubscribeRequestDTO {
    private String endpoint;
    private WebPushKeysDTO keys;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public WebPushKeysDTO getKeys() {
        return keys;
    }

    public void setKeys(WebPushKeysDTO keys) {
        this.keys = keys;
    }

    public static class WebPushKeysDTO {
        private String p256dh;
        private String auth;

        public String getP256dh() {
            return p256dh;
        }

        public void setP256dh(String p256dh) {
            this.p256dh = p256dh;
        }

        public String getAuth() {
            return auth;
        }

        public void setAuth(String auth) {
            this.auth = auth;
        }
    }
}

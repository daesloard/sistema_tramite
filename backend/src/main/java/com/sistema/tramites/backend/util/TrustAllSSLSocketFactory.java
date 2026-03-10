package com.sistema.tramites.backend.util;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;

/**
 * SOLO PARA DESARROLLO - Confía en todos los certificados SSL
 * NO USAR EN PRODUCCIÓN
 */
public class TrustAllSSLSocketFactory extends SSLSocketFactory {
    
    private static SSLSocketFactory socketFactory;

    public TrustAllSSLSocketFactory() {
        try {
            // Crear un TrustManager que acepta todos los certificados
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Confiar en todos sin validar
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Confiar en todos sin validar
                    }
                }
            };

            // Inicializar contexto SSL con nuestro TrustManager
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAllCerts, new java.security.SecureRandom());
            socketFactory = ctx.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Error creating TrustAllSSLSocketFactory", e);
        }
    }

    public static SocketFactory getDefault() {
        try {
            return new TrustAllSSLSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Error creating TrustAllSSLSocketFactory", e);
        }
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return socketFactory.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException {
        return socketFactory.createSocket(host, port, clientHost, clientPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return socketFactory.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress clientAddress, int clientPort) throws IOException {
        return socketFactory.createSocket(address, port, clientAddress, clientPort);
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return socketFactory.createSocket(s, host, port, autoClose);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return socketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return socketFactory.getSupportedCipherSuites();
    }
}

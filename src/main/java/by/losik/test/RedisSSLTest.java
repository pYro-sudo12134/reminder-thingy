package by.losik.test;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

public class RedisSSLTest { //test connection with SSL
    public static void main(String[] args) throws Exception {
        System.setProperty("javax.net.debug", "all");

        String host = "localhost";
        int port = 6380;
        String password ="hYKsqw/Rk/Tdl7TZmmpsOA==";
        String truststorePath = "redis/certs/redis_truststore.jks";
        String truststorePassword = "changeit";

        System.out.println("Testing Redis SSL connection to " + host + ":" + port);

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(new FileInputStream(truststorePath), truststorePassword.toCharArray());
        System.out.println("Truststore loaded successfully");

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        System.out.println("TrustManagerFactory initialized");

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        System.out.println("SSLContext created");

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(1);
        poolConfig.setMaxIdle(1);
        poolConfig.setMinIdle(0);

        javax.net.ssl.HostnameVerifier hostnameVerifier = (hostname, session) -> {
            System.out.println("Hostname verification for: " + hostname);
            return true;
        };

        try (JedisPool jedisPool = new JedisPool(poolConfig,
                host,
                port,
                5000,
                password,
                true,
                sslSocketFactory,
                null,
                hostnameVerifier); Jedis jedis = jedisPool.getResource()) {
            System.out.println("JedisPool created successfully");
            System.out.println("Got Jedis resource from pool");
            String result = jedis.ping();
            System.out.println("PING result: " + result);

            jedis.set("test_key", "test_value");
            String value = jedis.get("test_key");
            System.out.println("GET test_key: " + value);

            System.out.println("SUCCESS: Redis SSL connection works!");
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
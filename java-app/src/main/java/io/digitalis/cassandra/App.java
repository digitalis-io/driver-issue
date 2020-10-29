package io.digitalis.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * Hello Cassandra!
 */
public class App {
    private final static Logger LOG = LoggerFactory.getLogger(App.class);


    public static final String DC1_NAME="alpha";
    public static final String DC2_NAME="omega";
    public static final File TRUST_STORE_FILE = new File("src/main/resources/truststore.jks");
    public static final String STORE_PASS = "aib123";
    public static KeyStore TRUST_STORE;

    public static final int CQL_PORT = 9042;
    public static final InetSocketAddress DC1_CONTACT1=new InetSocketAddress("127.0.0.1", CQL_PORT);

    public static String CASS_USER="readonly";
    public static String CASS_PASSWORD="password";

    public static void main(String[] args) {
        LOG.info("Testing driver weirdness");

        String session1 = "localDcSession";
        String session2 = "remoteDcSession";

        String profile1 = "app1-local-dc-reads";
        String profile2 = "app1-local-dc-writes";
        String profile3 = "default-local-dc-reads";
        String profile4 = "default-local-dc-writes";

        try {


            SSLContext sslContext = App.sslContext();

            LOG.info("Testing profile {}", profile1);


            DriverConfigLoader localSessionConfigLoader =
                    new DefaultDriverConfigLoader(() -> App.loadConfig("localDcSession"));

            CqlSession readonlyCqlSession = CqlSession.builder()
                    .withConfigLoader(localSessionConfigLoader)
                    .withLocalDatacenter(DC1_NAME)
                    .addContactPoint(DC1_CONTACT1)
                    .withSslContext(sslContext)
                    .withAuthCredentials(CASS_USER, CASS_PASSWORD)
                    .build();

            LOG.info("Session built");

            ResultSet rs = readonlyCqlSession.execute("SELECT release_version FROM system.local");
            LOG.info("Select Query Executed");
            String version = rs.iterator().next().getString("release_version");
            LOG.info("Cassandra Version {}", version);

            LOG.info("Finished testing profile {}", profile1);

        } catch (Throwable t) {
            LOG.error("Got an error! "+t.getMessage(), t);
            t.printStackTrace();
            throw new RuntimeException("Got an error", t);
        }

    }

    public static Config loadConfig(String prefix) {
        LOG.info("Loading config profile for driver: '{}", prefix);
        // Make sure we see the changes when reloading:
        ConfigFactory.invalidateCaches();
        LOG.info("Loading config profile for driver: '{}", prefix);

        // Every config file in the classpath, without stripping the prefixes
        Config root = ConfigFactory.load();

        // The driver's built-in defaults, under the default prefix in reference.conf:
        Config reference = root.getConfig("datastax-java-driver");
        LOG.info("Got root datastax-java-driver config: '{}", reference);


        // Everything under your custom prefix in application.conf:
        Config application = root.getConfig(prefix);
        LOG.info("Got {} profile application config: '{}", prefix, application);

        Config result = application.withFallback(reference);
        LOG.info("Returning result config with fallback to root {} ", result);

        return result;
    }


    public static SSLContext sslContext() throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException {
        TRUST_STORE = App.trustStoreFromFile();

        LOG.info("Constructing SSLContext");
        SSLContextBag SSLContextBag = new SSLContextBag();
        SSLContextBag.setTrustStore(TRUST_STORE);
        SSLContextBag.init();

        return SSLContextBag.getContext();
    }

    public static KeyStore trustStoreFromFile() throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException {
        LOG.info("Loading trust store for test from file system: "+TRUST_STORE_FILE.getAbsolutePath());
        KeyStore keyStore = KeyStore.getInstance("JKS");
        InputStream is = new FileInputStream(TRUST_STORE_FILE);
        keyStore.load(is, STORE_PASS.toCharArray());
        return keyStore;
    }
}

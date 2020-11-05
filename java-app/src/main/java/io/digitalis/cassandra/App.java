package io.digitalis.cassandra;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
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
    public static final String STORE_PASS = "aib123";
    public static KeyStore TRUST_STORE;

    public static final int CQL_PORT = 9042;
    public static final InetSocketAddress DC1_CONTACT1 = new InetSocketAddress("127.0.0.1", CQL_PORT);
    public static final InetSocketAddress DC2_CONTACT1 = new InetSocketAddress("127.0.1.1", CQL_PORT);

    public static String CASS_USER = "readonly";
    public static String CASS_PASSWORD = "password";

    public static void main(String[] args) {
        LOG.info("Testing driver weirdness");

        try {

            testSessionAndProfiles("localDcSession", DC1_NAME, DC1_CONTACT1,
                    "app1-local-dc-reads",
                    "app1-local-dc-writes",
                    "default-local-dc-reads",
                    "default-local-dc-writes");

            testSessionAndProfiles("remoteDcSession", DC2_NAME, DC2_CONTACT1,
                    "app1-remote-dc-reads",
                    "app1-remote-dc-writes",
                    "default-remote-dc-reads",
                    "default-remote-dc-writes");

        } catch (Throwable t) {
            LOG.error("Got an error! " + t.getMessage(), t);
            t.printStackTrace();
            throw new RuntimeException("Got an error", t);
        }

    }

    private static void testSessionAndProfiles(String prefix, String dcName, InetSocketAddress contactPoint, String... profiles) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {

        SSLContext sslContext = App.sslContext();

        LOG.info("Testing session prefix {}", prefix);

        DriverConfigLoader localSessionConfigLoader =
                new DefaultDriverConfigLoader(() -> App.loadConfig(prefix));

        CqlSessionBuilder builder = CqlSession.builder()
                .withConfigLoader(localSessionConfigLoader)
                .withLocalDatacenter(dcName)
                .addContactPoint(contactPoint)
                .withSslContext(sslContext)
                .withAuthCredentials(CASS_USER, CASS_PASSWORD)
                ;

        for (String profile : profiles) {
            // YOU MUST SET THE LOCAL DC FOR EACH PROFILE
            builder = builder.withLocalDatacenter(profile, dcName);
        }

        try ( CqlSession session = builder.build()) {

            LOG.info("Session built");

            testProfile(session, DriverExecutionProfile.DEFAULT_NAME);

            for (String profile : profiles) {
                testProfile(session, profile);
            }

        }

    }

    private static void testProfile(CqlSession session, String profile) {
        try {
            Statement<?> stmt = SimpleStatement.newInstance("SELECT release_version FROM system.local")
                    .setExecutionProfileName(profile);
            ResultSet rs = session.execute(stmt);
            LOG.info("Select Query Executed");
            String version = rs.iterator().next().getString("release_version");
            LOG.info("Cassandra Version {}", version);
            LOG.info("Finished testing profile {} with session {}", profile, session.getName());
        } catch (AllNodesFailedException e) {
            Throwable nodeError = e.getAllErrors().entrySet().iterator().next().getValue().get(0);
            LOG.error("Testing session {} with profile {} failed:", session.getName(), profile, nodeError);
        } catch (Exception e) {
            LOG.error("Testing session {} with profile {} failed:", session.getName(), profile, e);
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
        LOG.info("Loading trust store for test from classpath");
        KeyStore keyStore = KeyStore.getInstance("JKS");
        InputStream is = App.class.getResourceAsStream("/truststore.jks");
        keyStore.load(is, STORE_PASS.toCharArray());
        return keyStore;
    }
}

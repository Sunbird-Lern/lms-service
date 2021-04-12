package org.sunbird;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class BaseTest {
    public static String host = "localhost";
    public static int port = 9042;
    protected static Session session = null;

    private static Session getSession() {
        if (session != null && !session.isClosed()) return session;
        Cluster cluster = Cluster.builder().addContactPoints(host).withPort(port).withoutJMXReporting().build();
        session = cluster.connect();
        return session;
    }

    @BeforeClass
    public static void before() {
        setupEmbeddedCassandra();
    }

    @AfterClass
    public static void after() {
        tearEmbeddedCassandraSetup();
    }

    protected static void setupEmbeddedCassandra() {
        try {
            EmbeddedCassandraServerHelper.startEmbeddedCassandra("/cassandra-unit.yaml", 100000L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void executeScript(String... queries) {
        session = getSession();
        for (String query : queries) {
            session.execute(query);
        }
    }

    private static void tearEmbeddedCassandraSetup() {
        try {
            if (null != session && !session.isClosed())
                session.close();
            EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


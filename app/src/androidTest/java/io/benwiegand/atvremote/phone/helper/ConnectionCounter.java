package io.benwiegand.atvremote.phone.helper;

import static org.junit.Assert.assertEquals;

import io.benwiegand.atvremote.phone.dummytv.FakeTVServer;

public class ConnectionCounter {

    private final FakeTVServer server;
    private int expectedConnects = 0;
    private int expectedDisconnects = 0;
    private final long timeout;

    public ConnectionCounter(FakeTVServer server, long timeout) {
        this.server = server;
        this.timeout = timeout;
    }


    public void assertConnections() {
        server.waitForCounters(expectedConnects, expectedDisconnects, timeout);
        assertEquals("expecting " + expectedConnects + " total connections to fake TV receiver", expectedConnects, server.getTotalConnects());
        assertEquals("expecting " + expectedDisconnects + " total disconnections from fake TV receiver", expectedDisconnects, server.getTotalDisconnects());
    }

    public void expectConnections(int number) {
        expectedConnects += number;
        assertConnections();
    }

    public void expectConnection() {
        expectConnections(1);
    }

    public void expectDisconnections(int number) {
        expectedDisconnects += number;
        assertConnections();
    }

    public void expectDisconnection() {
        expectDisconnections(1);
    }

    public void expect(int connections, int disconnections) {
        expectedConnects += connections;
        expectedDisconnects += disconnections;
        assertConnections();
    }

}

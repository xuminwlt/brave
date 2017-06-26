package com.github.kristofa.brave.scribe;

import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.InternalSpan;
import com.twitter.zipkin.gen.Span;
import org.apache.thrift.transport.TTransportException;
import org.junit.*;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;


public class ITScribeSpanCollectorOfferTimeout {
    static {
        InternalSpan.initializeInstanceForTests();
    }

    private static final Logger LOGGER = Logger.getLogger(ITScribeSpanCollector.class.getName());
    private static final int QUEUE_SIZE = 5;

    private static final int PORT = FreePortProvider.getNewFreePort();
    Span span = InternalSpan.instance.toSpan(SpanId.builder().traceId(1).spanId(2).build());

    private static ScribeServer scribeServer;

    @BeforeClass
    public static void setupBeforeClass() throws TTransportException {
        scribeServer = new ScribeServer(PORT);
        scribeServer.start();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        scribeServer.stop();
    }

    @Before
    public void setup() {
        scribeServer.clearReceivedSpans();
    }

    /**
     * The isSampled will submit a burst of 110 spans in a for loop without delay while only having a small queue size (configured
     * to 5). But the server is configured to take a long time to consume span, longer than the 5 seconds. So implicitly this
     * tests:
     * <ol>
     * <li>a full queue at which client will be blocked for more then its time out value. This means spans are lost, do not
     * end up in collector server.</li>
     * <li>it also shows that stopping ScribeSpanCollector before all queued spans have been submitted works.</li>
     * </ol>
     *
     * @throws TTransportException
     * @throws InterruptedException
     */
    @Test
    public void testOfferTimeOut() throws TTransportException, InterruptedException {

        final int hundredTen = 110;

        final ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setQueueSize(QUEUE_SIZE);
        params.setBatchSize(50);

        final ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector("localhost", PORT, params);
        try {
            for (int i = 1; i <= hundredTen; i++) {
                LOGGER.info("Submitting Span nr " + i + "/" + hundredTen);
                scribeSpanCollector.collect(span);
            }
            LOGGER.info("Sleep 5 seconds");
            Thread.sleep(5000);
        } finally {
            scribeSpanCollector.close();
        }
        final List<Span> serverCollectedSpans = scribeServer.getReceivedSpans();
        assertTrue(serverCollectedSpans.size() < hundredTen);

    }
}

package org.meshwork.app.host.l3.router.serial.perf;

import org.meshwork.app.host.l3.router.MessageDispatcherImpl;
import org.meshwork.app.host.l3.router.RouterConfiguration;
import org.meshwork.core.AbstractMessage;
import org.meshwork.core.AbstractMessageTransport;
import org.meshwork.core.host.l3.*;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Created by Sinisha Djukic on 14-2-25.
 */
public class PerfMessageDispatcherImpl extends MessageDispatcherImpl {

    protected TestSummaryStats summaryStats;

    public PerfMessageDispatcherImpl(MessageAdapter adapter, AbstractMessageTransport transport, RouterConfiguration config, PrintWriter writer, TestSummaryStats summaryStats) {
        super(adapter, transport, config, writer);
        this.summaryStats = summaryStats;
        autoCfgRequestAllowed = false;
    }

    @Override
    public void run() {
        synchronized (this) {
            running = true;
            this.notifyAll();
        }
        testStartAllTests();
    }

    public void testStartAllTests() {
        readMessagesAndDiscardAll();

        writer.println();
        writer.println("======================================");
        writer.println("PERFORMANCE TESTS: START");
        writer.flush();
        ArrayList<TestStats> stats = summaryStats.getTestStats();
        if ( stats.size() > 0 ) {
            for (TestStats test : stats) {
                switch ( test.getTestUID() ) {
                    case TestSendDirectStats.UID_SEND_DIRECT: testSendDirect((TestSendDirectStats) test);break;
                    case TestSendRoutedStats.UID_SEND_ROUTED: testSendRouted((TestSendRoutedStats) test);break;
                    case TestSendFloodStats.UID_SEND_FLOOD:   testSendFlood((TestSendFloodStats) test);break;
                    default: writer.println("Unknown test: UID="+test.getTestUID()+", Name="+test.getTestName());
                }
                writer.flush();
            }
        } else {
            writer.println("No tests to execute!");
        }
        stats = summaryStats.getTestStats();
        if ( stats.size() > 0 ) {
            summaryStats.printResults(writer);
        }
        writer.println("PERFORMANCE TESTS: END");
        writer.println("======================================");
        writer.flush();
    }

    public TestSummaryStats getSummaryStats() {
        return summaryStats;
    }

    protected void testSendImpl(TestStats stats, ArrayList<Byte> dst, int iterationDelaySeconds) {
        AbstractTestConfiguration testConfig = stats.getTestConfiguration();
        int time = testConfig.getMaxTime();
        int iterations = testConfig.getMaxIterations();
        if ( time < 1 && iterations < 1 ) {
            writer.println("Skipping test: both time and iterations parameters are invalid");
        } else {
            if ( dst == null || dst.size() < 1 ) {
                writer.println("Skipping test: destination list is empty");
            } else {
                //normalize values
                time = time < 1 ? Integer.MAX_VALUE : time;
                iterations = iterations < 1 ? Integer.MAX_VALUE : iterations;
                int iter = iterations;
                long start = System.currentTimeMillis();
                AbstractMessage resp = null;
                byte[] senddata = new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
                byte dstnode;
                do {
                    long sleepTime = iterationDelaySeconds * 1000;
                    int dstcount = dst.size();
                    for ( int i = 0; i < dstcount; i ++ ) {
//                        //TODO cleanup this shortcut
//                        if ( stats.getTestUID() == TestSendFloodStats.UID_SEND_FLOOD )
//                            routeMap.clearRouteList();
                        dstnode = dst.get(i);
                        MRFSend req = new MRFSend(nextSeq());
                        req.dst = dstnode;
                        req.port = 5;//to simplify the test config we fix the port
                        req.data = senddata;
                        req.datalen = (byte) (req.data == null ? 0 : req.data.length);
                        try {
                            resp = sendMessageAndReceive(req);
                            if ( resp != null && resp.getCode() == Constants.MSGCODE_RFSENDACK ) {
                                stats.successCount ++;
                            } else {
                                stats.failCount ++;
                            }
                            writer.println();
                        } catch (Throwable t) {
                            writer.println("Error sending '"+resp+"' to '"+dstnode+"' due to: "+t.getMessage());
                            t.printStackTrace(writer);
                            stats.failCount ++;
                        }
                        stats.runCount ++;
                        writer.flush();
                    }
                    if ( sleepTime > 0 ) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                        }
                    }
                } while ( (System.currentTimeMillis() - start < time) && (iterations > 0 && (--iter > 0)) );
                stats.runTime = (int) (System.currentTimeMillis() - start ) / 1000;
            }
        }
    }

    public void testSendDirect(TestSendDirectStats stats) {
        writer.println("--------------------------------------");
        writer.println("Starting test: "+stats.getTestName());
        writer.println();

        try {
            //reconfigure for DIRECT only
            MConfigBasic cfg = new MConfigBasic(nextSeq());
            cfg.delivery = Constants.DELIVERY_DIRECT;
            cfg.nwkcaps = config.getNwkcaps();
            cfg.retry = config.getRetry();
            AbstractMessage resp = sendMessageAndReceive(cfg);
            if ( resp == null || resp.getCode() != Constants.MSGCODE_OK )
                throw new Exception("Could not reconfigure the Controller");
            testSendImpl(stats, ((TestSendDirectConfiguration) stats.config).dstlist, stats.config.iterationDelay);
        } catch (Throwable t) {
            writer.println("Error setting up test due to:"+t.getMessage());
            t.printStackTrace(writer);
        }

        writer.println("Finished test: "+stats.getTestName());
        writer.println("--------------------------------------");
    }

    public void testSendRouted(TestSendRoutedStats stats) {
        writer.println("--------------------------------------");
        writer.println("Starting test: "+stats.getTestName());
        writer.println();

        try {
            //reconfigure for ROUTED only
            MConfigBasic cfg = new MConfigBasic(nextSeq());
            cfg.delivery = Constants.DELIVERY_ROUTED;
            cfg.nwkcaps = config.getNwkcaps();
            cfg.retry = config.getRetry();
            AbstractMessage resp = sendMessageAndReceive(cfg);
            if ( resp == null || resp.getCode() != Constants.MSGCODE_OK )
                throw new Exception("Could not reconfigure the Controller");

            //prepare a dstlist - last element of each route is our dst
            //prepare routes
            routeMap.clearRouteList();
            ArrayList<Byte> dstlist = new ArrayList<Byte>();
            ArrayList<ArrayList<Byte>> routelist = ((TestSendRoutedConfiguration)stats.config).routelist;
            int routelistsize = routelist.size();
            for ( int i = 0; i < routelistsize; i ++ ) {
                ArrayList<Byte> temp = routelist.get(i);
                int tempsize = temp.size();
                if ( tempsize > 0 ) {
                    byte dst = temp.get(tempsize - 1);
                    dstlist.add(dst);
                    //now, fill in the route
                    //NO DUPLICATION CHECKS PERFORMED!
                    RouteList routeList = routeMap.getRouteList(dst, true);
                    Route route = new Route();
                    route.src = config.getNodeid();
                    route.dst = dst;
                    route.hopCount = (byte) (tempsize - 1);
                    if ( route.hopCount > 0 ) {
                        route.hops = new byte[route.hopCount];
                        for ( int j = 0; j < route.hopCount; j ++ )
                            route.hops[j] = temp.get(j);
                    }
                    routeList.addRoute(route);
                }
            }
            testSendImpl(stats, dstlist, stats.config.iterationDelay);
        } catch (Throwable t) {
            writer.println("Error setting up test due to:"+t.getMessage());
            t.printStackTrace(writer);
        }

        writer.println("Finished test: "+stats.getTestName());
        writer.println("--------------------------------------");
    }

    public void testSendFlood(TestSendFloodStats stats) {
        writer.println("--------------------------------------");
        writer.println("Starting test: "+stats.getTestName());
        writer.println();

        try {
            //reconfigure for FLOOD only
            MConfigBasic cfg = new MConfigBasic(nextSeq());
            cfg.delivery = Constants.DELIVERY_FLOOD;
            cfg.nwkcaps = config.getNwkcaps();
            cfg.retry = config.getRetry();
            AbstractMessage resp = sendMessageAndReceive(cfg);
            if ( resp == null || resp.getCode() != Constants.MSGCODE_OK )
                throw new Exception("Could not reconfigure the Controller");
            testSendImpl(stats, ((TestSendFloodConfiguration)stats.config).dstlist, stats.config.iterationDelay);
        } catch (Throwable t) {
            writer.println("Error setting up test due to:"+t.getMessage());
            t.printStackTrace(writer);
        }

        writer.println("Finished test: "+stats.getTestName());
        writer.println("--------------------------------------");
    }

}
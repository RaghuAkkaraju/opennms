//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2002-2003 The OpenNMS Group, Inc. All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Modifications:
//
// 2004 May 05: Switch from SocketChannel to Socket with connection timeout.
// 2003 Jul 21: Explicitly closed socket.
// 2003 Jul 18: Enabled retries for monitors.
// 2003 Jun 11: Added a "catch" for RRD update errors. Bug #748.
// 2003 Jan 31: Added the ability to imbed RRA information in poller packages.
// 2003 Jan 31: Cleaned up some unused imports.
// 2003 Jan 29: Added response times to certain monitors.
// 2002 Nov 14: Used non-blocking I/O socket channel classes.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp. All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
//      OpenNMS Licensing <license@opennms.org>
//      http://www.opennms.org/
//      http://www.opennms.com/
//
// Tab Size = 8
//

package org.opennms.netmgt.poller.monitors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.util.Map;

import org.apache.log4j.Level;
import org.opennms.core.utils.ParameterMap;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.core.utils.TimeoutTracker;
import org.opennms.netmgt.model.PollStatus;
import org.opennms.netmgt.poller.Distributable;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.NetworkInterface;
import org.opennms.netmgt.poller.NetworkInterfaceNotSupportedException;

/**
 * <P>
 * This class is designed to be used by the service poller framework to test the
 * availability of the IMAP service on remote interfaces. The class implements
 * the ServiceMonitor interface that allows it to be used along with other
 * plug-ins by the service poller framework.
 * </P>
 *
 * @author <A HREF="mailto:tarus@opennms.org">Tarus Balog </A>
 * @author <A HREF="mailto:sowmya@opennms.org">Sowmya Nataraj </A>
 * @author <A HREF="mailto:mike@opennms.org">Mike Davidson </A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS </A>
 * @author <A HREF="mailto:tarus@opennms.org">Tarus Balog </A>
 * @author <A HREF="mailto:sowmya@opennms.org">Sowmya Nataraj </A>
 * @author <A HREF="mailto:mike@opennms.org">Mike Davidson </A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS </A>
 * @author <A HREF="mailto:tarus@opennms.org">Tarus Balog </A>
 * @author <A HREF="mailto:sowmya@opennms.org">Sowmya Nataraj </A>
 * @author <A HREF="mailto:mike@opennms.org">Mike Davidson </A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS </A>
 * @author <A HREF="mailto:tarus@opennms.org">Tarus Balog </A>
 * @author <A HREF="mailto:sowmya@opennms.org">Sowmya Nataraj </A>
 * @author <A HREF="mailto:mike@opennms.org">Mike Davidson </A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS </A>
 * @version CVS 1.1.1.1
 */
@Distributable
final public class ImapMonitor extends AbstractServiceMonitor {

    /**
     * Default IMAP port.
     */
    private static final int DEFAULT_PORT = 143;

    /**
     * Default retries.
     */
    private static final int DEFAULT_RETRY = 0;

    /**
     * Default timeout. Specifies how long (in milliseconds) to block waiting
     * for data from the monitored interface.
     */
    private static final int DEFAULT_TIMEOUT = 3000;

    /**
     * The start of the initial banner received from the server
     */
    private static String IMAP_START_RESPONSE_PREFIX = "* OK ";

    /**
     * The LOGOUT request sent to the server to close the connection
     */
    private static String IMAP_LOGOUT_REQUEST = "ONMSPOLLER LOGOUT\r\n";

    /**
     * The BYE response received from the server in response to the logout
     */
    private static String IMAP_BYE_RESPONSE_PREFIX = "* BYE ";

    /**
     * The LOGOUT response received from the server in response to the logout
     */
    private static String IMAP_LOGOUT_RESPONSE_PREFIX = "ONMSPOLLER OK ";

    /**
     * {@inheritDoc}
     *
     * <P>
     * Poll the specified address for IMAP service availability.
     * </P>
     *
     * <P>
     * During the poll an attempt is made to connect on the specified port (by
     * default TCP port 143). If the connection request is successful, the
     * banner line generated by the interface is parsed and if it starts with a '*
     * OK , it indicates that we are talking to an IMAP server and we continue.
     * Next, a 'LOGOUT' command is sent to the interface. Again the response is
     * parsed and the response is verified to see that we get a '* OK'. If the
     * interface's response is valid we set the service status to
     * SERVICE_AVAILABLE and return.
     * </P>
     */
    public PollStatus poll(MonitoredService svc, Map<String, Object> parameters) {
        NetworkInterface<InetAddress> iface = svc.getNetInterface();

        // Get interface address from NetworkInterface
        //
        if (iface.getType() != NetworkInterface.TYPE_INET)
            throw new NetworkInterfaceNotSupportedException("Unsupported interface type, only TYPE_INET currently supported");

        // Process parameters
        //
        ThreadCategory log = ThreadCategory.getInstance(getClass());

        
        TimeoutTracker tracker = new TimeoutTracker(parameters, DEFAULT_RETRY, DEFAULT_TIMEOUT);
        // Retries
        //
        int port = ParameterMap.getKeyedInteger(parameters, "port", DEFAULT_PORT);

        // Get interface address from NetworkInterface
        //
        InetAddress ipv4Addr = (InetAddress) iface.getAddress();

        if (log.isDebugEnabled())
            log.debug("ImapMonitor.poll: address: " + ipv4Addr + " port: " + port + tracker);

        PollStatus serviceStatus = PollStatus.unavailable();

        for (tracker.reset(); tracker.shouldRetry() && !serviceStatus.isAvailable(); tracker.nextAttempt()) {
            Socket socket = null;
            try {
                //
                // create a connected socket
                //
                tracker.startAttempt();

                socket = new Socket();
                socket.connect(new InetSocketAddress(ipv4Addr, port), tracker.getConnectionTimeout());
                socket.setSoTimeout(tracker.getSoTimeout());

                // We're connected, so upgrade status to unresponsive
                serviceStatus = PollStatus.unresponsive();
                
                BufferedReader rdr = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                //
                // Tokenize the Banner Line, and check the first
                // line for a valid return.
                //
                String banner = rdr.readLine();
                
                double responseTime = tracker.elapsedTimeInMillis();

                if (log.isDebugEnabled())
                    log.debug("ImapMonitor.Poll(): banner: " + banner);

                if (banner != null && banner.startsWith(IMAP_START_RESPONSE_PREFIX)) {
                    //
                    // Send the LOGOUT
                    //
                    socket.getOutputStream().write(IMAP_LOGOUT_REQUEST.getBytes());

                    //
                    // get the returned string, tokenize, and
                    // verify the correct output.
                    //
                    String response = rdr.readLine();
                    if (response != null && response.startsWith(IMAP_BYE_RESPONSE_PREFIX)) {
                        response = rdr.readLine();
                        if (response != null && response.startsWith(IMAP_LOGOUT_RESPONSE_PREFIX)) {
                            serviceStatus = PollStatus.available(responseTime);
                        }
                    }
                }

                // If we get this far and the status has not been set
                // to available, then something didn't verify during
                // the banner checking or logout process.
                if (!serviceStatus.isAvailable()) {
                    serviceStatus = PollStatus.unavailable();
                }

            } catch (NoRouteToHostException e) {
            	
            	serviceStatus = logDown(Level.WARN,"No route to host exception for address: " + ipv4Addr, e);

            } catch (ConnectException e) {
                // Connection refused. Continue to retry.
            	serviceStatus = logDown(Level.DEBUG, "Connection exception for address: " + ipv4Addr, e);
            } catch (InterruptedIOException e) {
            	serviceStatus = logDown(Level.DEBUG, "did not connect to host with " + tracker);
            } catch (IOException e) {
            	serviceStatus = logDown(Level.DEBUG, "IOException while polling address: " + ipv4Addr, e);
            } finally {
                try {
                    // Close the socket
                    if (socket != null)
                        socket.close();
                } catch (IOException e) {
                    e.fillInStackTrace();
                    log.debug("ImapMonitor.poll: Error closing socket.", e);
                }
            }
        }

        //
        // return the status of the service
        //
        return serviceStatus;
    }

}

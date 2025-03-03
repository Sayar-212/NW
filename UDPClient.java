import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class UDPClient {
    private static final int BUFFER_SIZE = 1024;
    private static final int DEFAULT_TIMEOUT = 5000; // 5 seconds timeout
    
    // List of time servers to try in order
    private static List<ServerInfo> serverList = new ArrayList<>();
    
    // Flags to track operation success
    private static final AtomicBoolean responseReceived = new AtomicBoolean(false);
    private static ServerInfo currentServer = null;
    
    public static void main(String[] args) {
        // Initialize server list
        initializeServerList();
        
        // Try different approaches and measure performance
        System.out.println("\n----- Testing Sleep Approach -----");
        long sleepStart = System.currentTimeMillis();
        boolean sleepSuccess = runClientWithSleepApproach();
        long sleepTime = System.currentTimeMillis() - sleepStart;
        
        // Reset for next test
        responseReceived.set(false);
        
        System.out.println("\n----- Testing Socket Timeout Approach -----");
        long timeoutStart = System.currentTimeMillis();
        boolean timeoutSuccess = runClientWithTimeoutApproach();
        long timeoutTime = System.currentTimeMillis() - timeoutStart;
        
        // Compare approaches
        System.out.println("\n----- Performance Comparison -----");
        System.out.println("Sleep approach: " + sleepTime + "ms, Success: " + sleepSuccess);
        System.out.println("Socket timeout approach: " + timeoutTime + "ms, Success: " + timeoutSuccess);
        
        if (sleepSuccess && timeoutSuccess) {
            if (sleepTime < timeoutTime) {
                System.out.println("Sleep approach performed better by " + (timeoutTime - sleepTime) + "ms");
            } else {
                System.out.println("Socket timeout approach performed better by " + (sleepTime - timeoutTime) + "ms");
            }
        } else {
            System.out.println("Cannot compare performance as at least one approach failed");
        }
    }
    
    private static void initializeServerList() {
        // Add servers to the list (hostname/IP and port)
        serverList.add(new ServerInfo("localhost", 9876));
        serverList.add(new ServerInfo("localhost", 9877)); // Backup server
        serverList.add(new ServerInfo("localhost", 9878)); // Another backup
    }
    
    private static boolean runClientWithSleepApproach() {
        try (DatagramSocket clientSocket = new DatagramSocket()) {
            CountDownLatch latch = new CountDownLatch(1);
            responseReceived.set(false);
            
            // First try to find a working server
            for (ServerInfo server : serverList) {
                currentServer = server;
                System.out.println("Trying server: " + server);
                
                // Create and start send thread
                Thread sendThread = new Thread(new SendThread(clientSocket, server, latch));
                sendThread.start();
                
                // Create and start receive thread
                Thread receiveThread = new Thread(new ReceiveThreadWithSleep(clientSocket));
                receiveThread.start();
                
                // Wait for the send thread to complete
                sendThread.join();
                
                // Wait a bit to see if we get a response
                Thread.sleep(DEFAULT_TIMEOUT);
                
                if (responseReceived.get()) {
                    // We got a response, break out of the server loop
                    break;
                } else {
                    System.out.println("No response from server: " + server);
                    // Try to interrupt the receive thread before moving to next server
                    receiveThread.interrupt();
                }
            }
            
            // Signal to the main thread that it's ok to exit
            latch.countDown();
            
            return responseReceived.get();
            
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean runClientWithTimeoutApproach() {
        try (DatagramSocket clientSocket = new DatagramSocket()) {
            CountDownLatch latch = new CountDownLatch(1);
            responseReceived.set(false);
            
            // Set socket timeout
            clientSocket.setSoTimeout(DEFAULT_TIMEOUT);
            
            // First try to find a working server
            for (ServerInfo server : serverList) {
                currentServer = server;
                System.out.println("Trying server: " + server);
                
                // Create and start send thread
                Thread sendThread = new Thread(new SendThread(clientSocket, server, latch));
                sendThread.start();
                
                // Create and start receive thread
                Thread receiveThread = new Thread(new ReceiveThreadWithTimeout(clientSocket));
                receiveThread.start();
                
                // Wait for the send thread to complete
                sendThread.join();
                
                // Wait for the receive thread to complete or timeout
                receiveThread.join();
                
                if (responseReceived.get()) {
                    // We got a response, break out of the server loop
                    break;
                } else {
                    System.out.println("No response from server: " + server);
                }
            }
            
            // Signal to the main thread that it's ok to exit
            latch.countDown();
            
            return responseReceived.get();
            
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            return false;
        }
    }
    
    // Class to represent server information
    static class ServerInfo {
        private final String host;
        private final int port;
        
        public ServerInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }
        
        public String getHost() {
            return host;
        }
        
        public int getPort() {
            return port;
        }
        
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
    
    // Thread to send request to server
    static class SendThread implements Runnable {
        private final DatagramSocket socket;
        private final ServerInfo server;
        private final CountDownLatch latch;
        
        public SendThread(DatagramSocket socket, ServerInfo server, CountDownLatch latch) {
            this.socket = socket;
            this.server = server;
            this.latch = latch;
        }
        
        @Override
        public void run() {
            try {
                // Prepare request message
                String message = "TIME_REQUEST";
                byte[] sendData = message.getBytes();
                
                // Get server address
                InetAddress serverAddress = InetAddress.getByName(server.getHost());
                
                // Create packet and send to server
                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length, serverAddress, server.getPort());
                
                socket.send(sendPacket);
                System.out.println("Sent request to server: " + server);
                
            } catch (IOException e) {
                System.err.println("Error in send thread: " + e.getMessage());
            }
        }
    }
    
    // Receive thread using sleep approach
    static class ReceiveThreadWithSleep implements Runnable {
        private final DatagramSocket socket;
        
        public ReceiveThreadWithSleep(DatagramSocket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                // Buffer to receive server response
                byte[] receiveBuffer = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                
                // Wait for response
                socket.receive(receivePacket);
                
                // Process response
                String timeFromServer = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("Time from server " + currentServer + ": " + timeFromServer);
                
                // Mark as received
                responseReceived.set(true);
                
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println("Error in receive thread: " + e.getMessage());
                }
            }
        }
    }
    
    // Receive thread using socket timeout approach
    static class ReceiveThreadWithTimeout implements Runnable {
        private final DatagramSocket socket;
        
        public ReceiveThreadWithTimeout(DatagramSocket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                // Buffer to receive server response
                byte[] receiveBuffer = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                
                // Wait for response (socket already has timeout set)
                socket.receive(receivePacket);
                
                // Process response
                String timeFromServer = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("Time from server " + currentServer + ": " + timeFromServer);
                
                // Mark as received
                responseReceived.set(true);
                
            } catch (SocketTimeoutException e) {
                System.out.println("Socket timeout while waiting for response from " + currentServer);
            } catch (IOException e) {
                System.err.println("Error in receive thread: " + e.getMessage());
            }
        }
    }
}

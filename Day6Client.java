/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
package com.mycompany.client;
/**
 *
 * @author KIIT
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Client {
    // List of time servers to try
    private static final List<ServerInfo> TIME_SERVERS = Arrays.asList(
        new ServerInfo("localhost", 0001),
        new ServerInfo("localhost", 0002),  // Second server (can be changed to actual server)
        new ServerInfo("localhost", 0003)   // Third server (can be changed to actual server)
    );
    
    // Timeout settings
    private static final int SOCKET_TIMEOUT_MS = 5000;
    private static final int THREAD_SLEEP_TIMEOUT_MS = 5000;
    
    // Timeout approach
    private static final boolean USE_SOCKET_TIMEOUT = true; // true = socket timeout, false = thread sleep
    
    public static void main(String[] args) {
        System.out.println("Using " + (USE_SOCKET_TIMEOUT ? "socket timeout" : "thread sleep") + " approach");
        
        long startTime = System.currentTimeMillis();
        boolean success = false;
        
        // Try each server until one succeeds
        for (ServerInfo server : TIME_SERVERS) {
            if (connectToServer(server)) {
                success = true;
                break;
            }
            System.out.println("Trying next server...");
        }
        
        long endTime = System.currentTimeMillis();
        
        if (success) {
            System.out.println("Successfully connected to a time server");
        } else {
            System.out.println("Failed to connect to any time server");
        }
        
        System.out.println("Total execution time: " + (endTime - startTime) + " ms");
    }
    
    private static boolean connectToServer(ServerInfo server) {
        System.out.println("Attempting to connect to server at " + server.getAddress() + ":" + server.getPort());
        
        DatagramSocket socket = null;
        AtomicBoolean receivedResponse = new AtomicBoolean(false);
        CountDownLatch sendCompleteLatch = new CountDownLatch(1);
        AtomicLong responseTime = new AtomicLong(0);
        
        try {
            socket = new DatagramSocket();
            
            if (USE_SOCKET_TIMEOUT) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            }
            
            // Get client's own IP and port
            InetAddress clientIP = InetAddress.getLocalHost();
            int clientPort = socket.getLocalPort();
            
            final DatagramSocket finalSocket = socket;
            
            // Start receive thread
            Thread receiveThread = new Thread(() -> {
                try {
                    long startReceiveTime = System.currentTimeMillis();
                    
                    byte[] receiveBuffer = new byte[1024];
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    
                    // Wait for response
                    finalSocket.receive(receivePacket);
                    
                    long endReceiveTime = System.currentTimeMillis();
                    responseTime.set(endReceiveTime - startReceiveTime);
                    
                    // Process response
                    String serverResponse = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    System.out.println("Received from server: " + serverResponse);
                    receivedResponse.set(true);
                } catch (SocketTimeoutException e) {
                    if (USE_SOCKET_TIMEOUT) {
                        System.out.println("Socket timeout: Server " + server.getAddress() + ":" + server.getPort() + " is not responding");
                    }
                } catch (IOException e) {
                    System.out.println("Error in receive thread: " + e.getMessage());
                }
            });
            
            // Start send thread
            Thread sendThread = new Thread(() -> {
                try {
                    // Prepare client information
                    String clientInfo = clientIP.getHostAddress() + ":" + clientPort;
                    byte[] sendBuffer = clientInfo.getBytes();
                    
                    // Create packet
                    DatagramPacket sendPacket = new DatagramPacket(
                        sendBuffer,
                        sendBuffer.length,
                        InetAddress.getByName(server.getAddress()),
                        server.getPort()
                    );
                    
                    // Send packet
                    finalSocket.send(sendPacket);
                    System.out.println("Sent client information: " + clientInfo);
                    
                    if (!USE_SOCKET_TIMEOUT) {
                        // Sleep as timeout mechanism
                        System.out.println("Send thread sleeping for " + THREAD_SLEEP_TIMEOUT_MS + " ms...");
                        Thread.sleep(THREAD_SLEEP_TIMEOUT_MS);
                        System.out.println("Send thread woke up from sleep");
                    }
                } catch (Exception e) {
                    System.out.println("Error in send thread: " + e.getMessage());
                } finally {
                    sendCompleteLatch.countDown();
                }
            });
            
            // Start both threads
            receiveThread.start();
            sendThread.start();
            
            // Wait for send thread to complete
            sendCompleteLatch.await();
            
            // If using thread sleep approach, check if received response
            if (!USE_SOCKET_TIMEOUT && !receivedResponse.get()) {
                System.out.println("Thread sleep timeout: Server " + server.getAddress() + ":" + server.getPort() + " is not responding");
            }
            
            // Wait for receive thread to finish if it's still running
            if (receiveThread.isAlive()) {
                receiveThread.interrupt();
                try {
                    receiveThread.join(1000);
                } catch (InterruptedException e) {
                    System.out.println("Interrupted while waiting for receive thread");
                }
            }
            
            if (receivedResponse.get()) {
                System.out.println("Response time: " + responseTime.get() + " ms");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.out.println("Error in client: " + e.getMessage());
            return false;
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
    
    // Helper class to store server information
    static class ServerInfo {
        private final String address;
        private final int port;
        
        public ServerInfo(String address, int port) {
            this.address = address;
            this.port = port;
        }
        
        public String getAddress() {
            return address;
        }
        
        public int getPort() {
            return port;
        }
    }
}

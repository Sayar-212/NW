/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
package com.mycompany.server;
/**
 *
 * @author KIIT
 */
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int DEFAULT_PORT = 0002;
    private static final int THREAD_POOL_SIZE = 10;
    private static final boolean CONCURRENT_SERVER = true; // Set to false for iterative server

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        
        // Parse port from command line arguments if provided
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port " + DEFAULT_PORT);
            }
        }
        
        DatagramSocket socket = null;
        
        try {
            socket = new DatagramSocket(port);
            System.out.println("Server started on port " + port);
            System.out.println(CONCURRENT_SERVER ? "Running in concurrent mode" : "Running in iterative mode");
            
            ExecutorService executor = null;
            if (CONCURRENT_SERVER) {
                executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            }
            
            while (true) {
                byte[] receiveBuffer = new byte[1024];
                final DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                
                socket.receive(receivePacket);
                
                // Added print statement to display client IP and port number
                System.out.println("Client connected from IP: " + receivePacket.getAddress().getHostAddress() + ", Port: " + receivePacket.getPort());
                
                if (CONCURRENT_SERVER) {
                    // Concurrent server - handle each client in a separate thread
                    final DatagramSocket finalSocket = socket;
                    final int serverPort = port;
                    executor.execute(() -> handleClient(finalSocket, receivePacket, serverPort));
                } else {
                    // Iterative server - handle clients one by one
                    handleClient(socket, receivePacket, port);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
    
    private static void handleClient(DatagramSocket socket, DatagramPacket packet, int serverPort) {
        try {
            String clientData = new String(packet.getData(), 0, packet.getLength());
            String[] clientInfo = clientData.split(":");
            
            if (clientInfo.length == 2) {
                String clientIp = clientInfo[0];
                int clientPort = Integer.parseInt(clientInfo[1]);
                
                // Get current server time
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                String currentTime = formatter.format(new Date());
                
                System.out.println("Connection from client at " + clientIp + ":" + clientPort);
                
                // Send time back to client
                String response = "Server Time>>> " + currentTime + " (from server on port " + serverPort + ")";
                byte[] sendBuffer = response.getBytes();
                
                DatagramPacket sendPacket = new DatagramPacket(
                    sendBuffer,
                    sendBuffer.length,
                    packet.getAddress(),
                    packet.getPort()
                );
                
                socket.send(sendPacket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

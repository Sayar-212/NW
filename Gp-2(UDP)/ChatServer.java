/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */

package com.mycompany.chatserver;

/**
 *
 * @author KIIT
 */
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
   // Port for server operations
   private static final int SERVER_PORT = 9000;
   // Time in milliseconds after which a client is considered inactive
   private static final long CLIENT_TIMEOUT = 30000; // 30 seconds
  
   // Thread-safe map to store active clients
   private static final Map<String, ClientInfo> activeClients = new ConcurrentHashMap<>();
  
   public static void main(String[] args) {
       try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {
           System.out.println("Chat Server started on port " + SERVER_PORT);
          
           // Start a thread to check for inactive clients
           startClientTimeoutChecker();
          
           byte[] receiveBuffer = new byte[1024];
          
           while (true) {
               DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
               serverSocket.receive(receivePacket);
              
               // Process the received packet in a separate thread
               new Thread(() -> processPacket(serverSocket, receivePacket)).start();
           }
       } catch (IOException e) {
           System.err.println("Server Error: " + e.getMessage());
       }
   }
  
   private static void startClientTimeoutChecker() {
       Timer timer = new Timer(true);
       timer.schedule(new TimerTask() {
           @Override
           public void run() {
               long currentTime = System.currentTimeMillis();
               List<String> clientsToRemove = new ArrayList<>();
              
               // Check for clients that haven't sent a heartbeat recently
               for (Map.Entry<String, ClientInfo> entry : activeClients.entrySet()) {
                   if (currentTime - entry.getValue().getLastHeartbeat() > CLIENT_TIMEOUT) {
                       clientsToRemove.add(entry.getKey());
                   }
               }
              
               // Remove inactive clients
               for (String username : clientsToRemove) {
                   System.out.println("Removing inactive client: " + username);
                   activeClients.remove(username);
               }
           }
       }, 5000, 5000); // Check every 5 seconds
   }
  
   private static void processPacket(DatagramSocket socket, DatagramPacket packet) {
       try {
           String message = new String(packet.getData(), 0, packet.getLength());
           String[] parts = message.split(":", 2);
          
           if (parts.length < 2) {
               System.out.println("Invalid message format: " + message);
               return;
           }
          
           String command = parts[0];
           String data = parts[1];
          
           switch (command) {
               case "REGISTER":
                   handleRegistration(socket, packet, data);
                   break;
               case "GET_USERS":
                   sendUserList(socket, packet);
                   break;
               case "HEARTBEAT":
                   updateClientHeartbeat(data);
                   break;
               case "LOGOUT":
                   removeClient(data);
                   break;
               default:
                   System.out.println("Unknown command: " + command);
           }
       } catch (Exception e) {
           System.err.println("Error processing packet: " + e.getMessage());
       }
   }
  
   private static void handleRegistration(DatagramSocket socket, DatagramPacket packet, String data) throws IOException {
       String[] userParts = data.split(",");
       if (userParts.length != 2) {
           sendResponse(socket, packet, "ERROR:Invalid registration format");
           return;
       }
      
       String username = userParts[0];
       int clientPort = Integer.parseInt(userParts[1]);
      
       if (activeClients.containsKey(username)) {
           sendResponse(socket, packet, "ERROR:Username already taken");
           return;
       }
      
       // Store client information
       ClientInfo clientInfo = new ClientInfo(username, packet.getAddress(), clientPort);
       activeClients.put(username, clientInfo);
      
       System.out.println("Registered new client: " + username + " at " +
                          packet.getAddress().getHostAddress() + ":" + clientPort);
      
       sendResponse(socket, packet, "SUCCESS:Registration successful");
   }
  
   private static void sendUserList(DatagramSocket socket, DatagramPacket packet) throws IOException {
       StringBuilder userList = new StringBuilder("USERS:");
      
       for (Map.Entry<String, ClientInfo> entry : activeClients.entrySet()) {
           ClientInfo client = entry.getValue();
           userList.append(client.getUsername()).append(",")
                  .append(client.getAddress().getHostAddress()).append(",")
                  .append(client.getPort()).append(";");
       }
      
       sendResponse(socket, packet, userList.toString());
   }
  
   private static void updateClientHeartbeat(String username) {
       if (activeClients.containsKey(username)) {
           activeClients.get(username).updateHeartbeat();
       }
   }
  
   private static void removeClient(String username) {
       if (activeClients.containsKey(username)) {
           System.out.println("Client logged out: " + username);
           activeClients.remove(username);
       }
   }
  
   private static void sendResponse(DatagramSocket socket, DatagramPacket requestPacket,
                                    String responseMessage) throws IOException {
       byte[] responseData = responseMessage.getBytes();
       DatagramPacket responsePacket = new DatagramPacket(
           responseData, responseData.length,
           requestPacket.getAddress(), requestPacket.getPort()
       );
       socket.send(responsePacket);
   }
  
   // Class to store client information
   private static class ClientInfo {
       private final String username;
       private final InetAddress address;
       private final int port;
       private long lastHeartbeat;
      
       public ClientInfo(String username, InetAddress address, int port) {
           this.username = username;
           this.address = address;
           this.port = port;
           this.lastHeartbeat = System.currentTimeMillis();
       }
      
       public String getUsername() {
           return username;
       }
      
       public InetAddress getAddress() {
           return address;
       }
      
       public int getPort() {
           return port;
       }
      
       public long getLastHeartbeat() {
           return lastHeartbeat;
       }
      
       public void updateHeartbeat() {
           lastHeartbeat = System.currentTimeMillis();
       }
   }
}

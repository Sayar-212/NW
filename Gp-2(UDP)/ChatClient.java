/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */

package com.mycompany.chatclient;

/**
 *
 * @author KIIT
 */
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatClient {
    // Server information
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9000;
    
    // Timeout for server response (in milliseconds)
    private static final int SERVER_TIMEOUT = 5000; // 5 seconds
    
    // Maximum number of registration retries
    private static final int MAX_RETRIES = 3;
    
    // Client information
    private String username;
    private DatagramSocket socket;
    private int clientPort;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    // Store information about other clients
    private final Map<String, ClientInfo> knownClients = new ConcurrentHashMap<>();
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.print("Enter your username: ");
        String username = scanner.nextLine().trim();
        
        try {
            ChatClient client = new ChatClient(username);
            client.start();
            
            client.displayCommands();
            
            while (client.isRunning()) {
                String input = scanner.nextLine();
                client.processUserInput(input);
            }
            
            scanner.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    public ChatClient(String username) throws SocketException {
        this.username = username;
        this.socket = new DatagramSocket();
        this.clientPort = socket.getLocalPort();
    }
    
    public void start() {
        try {
            // Register with the server (with retries and timeout)
            boolean registered = registerWithRetries();
            
            if (!registered) {
                System.err.println("Failed to register with the server after " + MAX_RETRIES + " attempts. Exiting...");
                running.set(false);
                return;
            }
            
            // Start message receiver thread
            startMessageReceiver();
            
            // Start heartbeat thread
            startHeartbeatThread();
            
            // Fetch initial user list
            requestUserList();
            
            System.out.println("Chat client started. You are logged in as: " + username);
        } catch (IOException e) {
            System.err.println("Failed to start client: " + e.getMessage());
            running.set(false);
        }
    }
    
    private boolean registerWithRetries() throws IOException {
        int retries = 0;
        
        while (retries < MAX_RETRIES) {
            try {
                // Set a timeout for the socket
                socket.setSoTimeout(SERVER_TIMEOUT);
                
                // Attempt to register
                String registrationMessage = "REGISTER:" + username + "," + clientPort;
                sendToServer(registrationMessage);
                
                // Wait for response
                byte[] buffer = new byte[1024];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                
                String responseMessage = new String(response.getData(), 0, response.getLength());
                if (responseMessage.startsWith("ERROR")) {
                    throw new IOException("Registration failed: " + responseMessage.substring(6));
                }
                
                // Reset timeout to infinite for normal operation
                socket.setSoTimeout(0);
                
                // Registration successful
                return true;
            } catch (SocketTimeoutException e) {
                retries++;
                System.err.println("Server did not respond. Retrying (" + retries + "/" + MAX_RETRIES + ")...");
            } catch (IOException e) {
                retries++;
                System.err.println("Error during registration: " + e.getMessage() + ". Retrying (" + retries + "/" + MAX_RETRIES + ")...");
            }
        }
        
        // Failed after max retries
        return false;
    }
    
    private void startMessageReceiver() {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                
                while (running.get()) {
                    Arrays.fill(buffer, (byte) 0); // Clear the buffer
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    
                    processIncomingMessage(packet.getAddress(), packet.getPort(), message);
                }
            } catch (SocketException e) {
                if (running.get()) {
                    System.err.println("Socket error: " + e.getMessage());
                }
            } catch (IOException e) {
                System.err.println("Error receiving message: " + e.getMessage());
            }
        }).start();
    }
    
    private void startHeartbeatThread() {
        new Thread(() -> {
            try {
                while (running.get()) {
                    sendToServer("HEARTBEAT:" + username);
                    Thread.sleep(10000); // Send heartbeat every 10 seconds
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                System.err.println("Error sending heartbeat: " + e.getMessage());
            }
        }).start();
    }
    
    private void processIncomingMessage(InetAddress senderAddress, int senderPort, String message) {
        // Handle server responses
        if (message.startsWith("USERS:")) {
            processUserList(message.substring(6));
            return;
        } 
        
        if (message.startsWith("SUCCESS:") || message.startsWith("ERROR:")) {
            System.out.println("Server: " + message.substring(message.indexOf(':') + 1));
            return;
        }
        
        // Handle chat messages from other clients
        if (message.startsWith("CHAT:")) {
            String[] parts = message.substring(5).split(":", 2);
            if (parts.length == 2) {
                String sender = parts[0];
                String chatMessage = parts[1];
                System.out.println("\n" + sender + ": " + chatMessage);
            }
            return;
        }
    }
    
    private void processUserList(String userListData) {
        knownClients.clear();
        
        String[] users = userListData.split(";");
        for (String user : users) {
            if (user.trim().isEmpty()) continue;
            
            String[] userInfo = user.split(",");
            if (userInfo.length == 3) {
                String username = userInfo[0];
                try {
                    InetAddress address = InetAddress.getByName(userInfo[1]);
                    int port = Integer.parseInt(userInfo[2]);
                    
                    // Don't add ourselves to the list
                    if (!username.equals(this.username)) {
                        knownClients.put(username, new ClientInfo(username, address, port));
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing user info: " + e.getMessage());
                }
            }
        }
        
        // Display the updated user list
        displayUserList();
    }
    
    private void displayUserList() {
        if (knownClients.isEmpty()) {
            System.out.println("No other users are online.");
            return;
        }
        
        System.out.println("\nOnline Users:");
        for (String username : knownClients.keySet()) {
            System.out.println("- " + username);
        }
    }
    
    private void processUserInput(String input) {
        if (input.isEmpty()) {
            return;
        }
        
        // Command processing
        if (input.startsWith("/")) {
            String[] commandParts = input.substring(1).split("\\s+", 2);
            String command = commandParts[0].toLowerCase();
            
            switch (command) {
                case "users":
                    try {
                        requestUserList();
                    } catch (IOException e) {
                        System.err.println("Error fetching user list: " + e.getMessage());
                    }
                    break;
                case "msg":
                    if (commandParts.length < 2) {
                        System.out.println("Usage: /msg <username> <message>");
                        break;
                    }
                    
                    String[] msgParts = commandParts[1].split("\\s+", 2);
                    if (msgParts.length < 2) {
                        System.out.println("Usage: /msg <username> <message>");
                        break;
                    }
                    
                    String recipient = msgParts[0];
                    String message = msgParts[1];
                    
                    try {
                        sendChatMessage(recipient, message);
                    } catch (IOException e) {
                        System.err.println("Error sending message: " + e.getMessage());
                    }
                    break;
                case "exit":
                case "quit":
                    logout();
                    break;
                case "help":
                    displayCommands();
                    break;
                default:
                    System.out.println("Unknown command. Type /help for available commands.");
            }
        } else {
            System.out.println("Use /msg <username> <message> to send a message to a specific user.");
        }
    }
    
    private void sendChatMessage(String recipient, String message) throws IOException {
        ClientInfo recipientInfo = knownClients.get(recipient);
        
        if (recipientInfo == null) {
            System.out.println("User '" + recipient + "' is not online or doesn't exist.");
            System.out.println("Use /users to see the list of online users.");
            return;
        }
        
        String chatMessage = "CHAT:" + username + ":" + message;
        byte[] data = chatMessage.getBytes();
        
        DatagramPacket packet = new DatagramPacket(
            data, data.length, 
            recipientInfo.getAddress(), recipientInfo.getPort()
        );
        
        socket.send(packet);
        System.out.println("To " + recipient + ": " + message);
    }
    
    private void requestUserList() throws IOException {
        sendToServer("GET_USERS:");
    }
    
    private void logout() {
        try {
            System.out.println("Logging out...");
            sendToServer("LOGOUT:" + username);
            running.set(false);
            socket.close();
            System.out.println("Logged out successfully.");
        } catch (IOException e) {
            System.err.println("Error during logout: " + e.getMessage());
        }
    }
    
    private void sendToServer(String message) throws IOException {
        InetAddress serverAddress = InetAddress.getByName(SERVER_HOST);
        byte[] data = message.getBytes();
        
        DatagramPacket packet = new DatagramPacket(
            data, data.length, serverAddress, SERVER_PORT
        );
        
        socket.send(packet);
    }
    
    private void displayCommands() {
        System.out.println("\nAvailable commands:");
        System.out.println("/users - Display list of online users");
        System.out.println("/msg <username> <message> - Send a private message to a user");
        System.out.println("/exit or /quit - Exit the chat");
        System.out.println("/help - Display this help message");
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    // Class to store information about other clients
    private static class ClientInfo {
        private final String username;
        private final InetAddress address;
        private final int port;
        
        public ClientInfo(String username, InetAddress address, int port) {
            this.username = username;
            this.address = address;
            this.port = port;
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
    }
}

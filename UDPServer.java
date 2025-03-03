import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPServer {
    private static final int SERVER_PORT = 9876;
    private static final int BUFFER_SIZE = 1024;
    private static boolean running = true;
    
    public static void main(String[] args) {
        // Create thread pool for handling multiple clients
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {
            System.out.println("UDP Time Server started on port " + SERVER_PORT);
            
            while (running) {
                // Buffer to receive client data
                byte[] receiveBuffer = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                
                // Wait for client request
                System.out.println("Waiting for client request...");
                serverSocket.receive(receivePacket);
                
                // Submit client request to thread pool
                executor.submit(new ClientHandler(serverSocket, receivePacket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }
    
    // Inner class to handle client requests concurrently
    static class ClientHandler implements Runnable {
        private final DatagramSocket socket;
        private final DatagramPacket receivedPacket;
        
        public ClientHandler(DatagramSocket socket, DatagramPacket receivedPacket) {
            this.socket = socket;
            this.receivedPacket = receivedPacket;
        }
        
        @Override
        public void run() {
            try {
                // Get client address and port
                InetAddress clientAddress = receivedPacket.getAddress();
                int clientPort = receivedPacket.getPort();
                
                // Log client request
                String receivedMessage = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
                System.out.println("Received from client " + clientAddress + ":" + clientPort + " - " + receivedMessage);
                
                // Get current time
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                String timeStr = now.format(formatter);
                
                // Create response
                byte[] sendData = timeStr.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length, clientAddress, clientPort);
                
                // Send response to client
                socket.send(sendPacket);
                System.out.println("Sent time to client " + clientAddress + ":" + clientPort);
                
            } catch (IOException e) {
                System.err.println("Error handling client request: " + e.getMessage());
            }
        }
    }
}

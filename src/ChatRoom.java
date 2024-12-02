import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ChatRoom {
    private static final ConcurrentHashMap<String, ObjectOutputStream> clients = new ConcurrentHashMap<>();
    private static int clientCounter = 0;

    public static void main(String[] args) {
        System.out.print("Enter port number: ");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            int port = Integer.parseInt(reader.readLine());
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("ChatRoom is running on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {


                synchronized (clients) {
                    clientName = "User" + (++clientCounter);
                    clients.put(clientName, out);
                }

                broadcastToAll("[SERVER] " + clientName + " has joined the chat. Members: " + clients.size());
                sendToClient(out, "[SERVER] Welcome to the chat! Your name is " + clientName +
                        ". Use '/name [new name]' to change your name, or '/exit' to leave.");


                String message;
                while ((message = (String) in.readObject()) != null) {
                    if (message.startsWith("/name ")) {
                        handleChangeName(out, message.substring(6).trim());
                    } else if (message.startsWith("/pm ")) {
                        handlePrivateMessage(out, message.substring(4));
                    } else if (message.equalsIgnoreCase("/exit")) {
                        break;
                    } else {
                        String formattedMessage = clientName + ": " + message;
                        logToServerConsole(formattedMessage);
                        broadcastToOthers(out, formattedMessage);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println(clientName + " disconnected: " + e.getMessage());
            } finally {
                disconnectClient();
            }
        }

        private void broadcastToAll(String message) {
            logToServerConsole(message);
            for (ObjectOutputStream out : clients.values()) {
                sendToClient(out, message);
            }
        }

        private void broadcastToOthers(ObjectOutputStream sender, String message) {
            for (ObjectOutputStream out : clients.values()) {
                if (out != sender) {
                    sendToClient(out, message);
                }
            }
        }

        private void handlePrivateMessage(ObjectOutputStream out, String message) {
            String[] parts = message.split(" ", 2);
            if (parts.length < 2) {
                sendToClient(out, "[SERVER] Usage: /pm [recipient] [message]");
                return;
            }

            String recipient = parts[0];
            String privateMessage = parts[1];

            ObjectOutputStream recipientOut = clients.get(recipient);
            if (recipientOut != null) {
                sendToClient(recipientOut, "(Private) " + clientName + ": " + privateMessage);
            } else {
                sendToClient(out, "[SERVER] User " + recipient + " not found.");
            }
        }

        private void handleChangeName(ObjectOutputStream out, String newName) {
            if (newName.isEmpty() || clients.containsKey(newName)) {
                sendToClient(out, "[SERVER] Invalid or already taken name.");
                return;
            }

            synchronized (clients) {
                clients.remove(clientName);
                broadcastToAll("[SERVER] " + clientName + " changed name to " + newName);
                clientName = newName;
                clients.put(clientName, out);
            }
        }

        private void sendToClient(ObjectOutputStream out, String message) {
            try {
                out.writeObject(message);
            } catch (IOException ignored) {}
        }

        private void logToServerConsole(String message) {
            System.out.println(message);
        }

        private void disconnectClient() {
            try {
                socket.close();
            } catch (IOException ignored) {}

            synchronized (clients) {
                clients.remove(clientName);
                broadcastToAll("[SERVER] " + clientName + " has left the chat. Members: " + clients.size());
            }
        }
    }
}

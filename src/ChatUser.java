import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ChatUser {
    public static void main(String[] args) {
        while (true) {
            try (Socket socket = connectToServer();
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                 BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

                System.out.println((String) in.readObject());

                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> {
                    try {
                        String serverMessage;
                        while ((serverMessage = (String) in.readObject()) != null) {
                            System.out.println(serverMessage);
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        System.out.println("[SERVER] Connection lost.");
                    }
                });

                // Send messages to the server
                String input;
                while ((input = console.readLine()) != null) {
                    out.writeObject(input); // Send the message to the server
                    if (input.equalsIgnoreCase("/exit")) {
                        System.out.println("Exiting chat...");
                        break;
                    }
                }

                executor.shutdownNow();
                break;
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Connection failed.Retrying...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private static Socket connectToServer() throws IOException {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter server address: ");
        String address = console.readLine();
        System.out.print("Enter port number: ");
        int port = Integer.parseInt(console.readLine());
        return new Socket(address, port);
    }
}

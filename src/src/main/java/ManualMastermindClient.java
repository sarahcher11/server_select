import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class ManualMastermindClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;

    public static void main(String[] args) {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT));
            Scanner inputScanner = new Scanner(System.in);

            while (true) {
                System.out.print("Enter a combination (or /SCORE to see the high scores): ");
                String userGuess = inputScanner.nextLine();
                sendToServer(socketChannel, userGuess);

                String serverResponse = receiveFromServer(socketChannel);
                System.out.println("[SERVER] " + serverResponse);

                if (serverResponse.contains(" Félicitations")) {
                    System.out.print("Enter your name to save the score: ");
                    String playerName = inputScanner.nextLine();
                    sendToServer(socketChannel, playerName);
                    System.out.println("Score saved!");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("[CLIENT] Error: " + e.getMessage());
        }
    }

    private static void sendToServer(SocketChannel channel, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        channel.write(buffer);
    }

    private static String receiveFromServer(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        int bytesRead = channel.read(buffer);
        if (bytesRead == -1) {
            return ""; // Return empty string if the connection is closed
        }
        return new String(buffer.array(), 0, bytesRead).trim();
    }
}

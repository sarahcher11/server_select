import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Random;

public class MastermindBotClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;
    private static final String COLOR_OPTIONS = "GBORY";

    public static void main(String[] args) {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT));
            Random rand = new Random();
            int attemptCount = 0;

            while (true) {
                String generatedGuess = createRandomGuess(rand);
                sendToServer(socketChannel, generatedGuess);
                System.out.println("[BOT] Sending: " + generatedGuess);

                String serverResponse = receiveFromServer(socketChannel);
                System.out.println("[SERVER] " + serverResponse);

                attemptCount++;

                if (serverResponse.contains(" Félicitations")) {
                    sendToServer(socketChannel, "Bot_" + attemptCount);
                    System.out.println("[BOT] Score saved!");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("[BOT] Error: " + e.getMessage());
        }
    }

    private static String createRandomGuess(Random rand) {
        StringBuilder guess = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            guess.append(COLOR_OPTIONS.charAt(rand.nextInt(COLOR_OPTIONS.length())));
        }
        return guess.toString();
    }

    private static void sendToServer(SocketChannel channel, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        channel.write(buffer);
    }

    private static String receiveFromServer(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        int bytesRead = channel.read(buffer);
        if (bytesRead == -1) {
            return ""; // Return empty string if the connection is closed.
        }
        return new String(buffer.array(), 0, bytesRead).trim();
    }
}

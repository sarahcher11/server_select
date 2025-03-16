import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class IdleClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (SocketChannel clientChannel = SocketChannel.open()) {
            clientChannel.connect(new InetSocketAddress(SERVER_ADDRESS, SERVER_PORT));
            sendMessage(clientChannel, "BGOR");
            String response = receiveMessage(clientChannel);
            System.out.println("[CLIENT] Réponse du serveur : " + response);
        } catch (IOException e) {
            System.err.println("[CLIENT] Erreur : " + e.getMessage());
        }
    }

    private static void sendMessage(SocketChannel channel, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        channel.write(buffer);
    }

    private static String receiveMessage(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        channel.read(buffer);
        return new String(buffer.array()).trim();
    }
}

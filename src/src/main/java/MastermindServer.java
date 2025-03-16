import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MastermindServer {
    private static final int PORT = 1234;
    private static final int BUFFER_SIZE = 256;
    private static final Map<String, Integer> scoreBoard = new ConcurrentHashMap<>();
    private static Selector selector;

    public static void main(String[] args) {
        try {
            selector = Selector.open();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(PORT));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("[SERVER] Waiting for connections on port " + PORT + "...");

            while (true) {
                selector.select();
                Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();

                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();
                    if (!key.isValid()) continue;

                    try {
                        if (key.isAcceptable()) {
                            acceptNewClient(serverChannel);
                        } else if (key.isReadable()) {
                            handleClientRequest(key);
                        }
                    } catch (IOException e) {
                        key.cancel();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Critical error: " + e.getMessage());
        }
    }

    private static void acceptNewClient(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
        key.attach(new GameSession());
        System.out.println("[SERVER] New client connected: " + clientChannel.getRemoteAddress());
    }

    private static void handleClientRequest(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                disconnectClient(clientChannel);
                return;
            }

            buffer.flip();
            String input = new String(buffer.array(), 0, bytesRead).trim();
            System.out.println("[SERVER] Received: " + input);

            GameSession session = (GameSession) key.attachment();
            if (input.equals("/SCORE")) {
                sendToClient(clientChannel, getFormattedScoreBoard());
                return;
            }

            String response = session.processGuess(input);
            sendToClient(clientChannel, response);

            if (response.contains("Congratulations")) {
                sendToClient(clientChannel, "Enter your name to save the score:");
                buffer.clear();
                bytesRead = clientChannel.read(buffer);
                if (bytesRead > 0) {
                    buffer.flip();
                    String playerName = new String(buffer.array(), 0, bytesRead).trim();
                    if (!playerName.isEmpty()) {
                        scoreBoard.put(playerName, session.getAttempts());
                    }
                }
            }
        } catch (IOException e) {
            disconnectClient(clientChannel);
        }
    }

    private static void sendToClient(SocketChannel clientChannel, String message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap((message + "\n").getBytes());
            clientChannel.write(buffer);
        } catch (IOException e) {
            disconnectClient(clientChannel);
        }
    }

    private static void disconnectClient(SocketChannel clientChannel) {
        try {
            System.out.println("[SERVER] Client disconnected: " + clientChannel.getRemoteAddress());
            clientChannel.close();
        } catch (IOException e) {
            System.err.println("[SERVER] Disconnection error: " + e.getMessage());
        }
    }

    private static String getFormattedScoreBoard() {
        StringBuilder sb = new StringBuilder("Best Scores:\n");
        scoreBoard.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> sb.append(entry.getKey()).append(" : ").append(entry.getValue()).append(" attempts\n"));
        return sb.toString();
    }

    private static class GameSession {
        private final String secretCode;
        private int attempts;

        public GameSession() {
            this.secretCode = generateSecretCode();
            this.attempts = 0;
            System.out.println("[SERVER] Secret code generated: " + secretCode);
        }

        public String processGuess(String guess) {
            attempts++;
            int correctPosition = 0;
            int correctColor = 0;

            Map<Character, Integer> secretMap = new HashMap<>();
            for (char c : secretCode.toCharArray()) secretMap.put(c, secretMap.getOrDefault(c, 0) + 1);

            for (int i = 0; i < guess.length(); i++) {
                char g = guess.charAt(i);
                if (g == secretCode.charAt(i)) {
                    correctPosition++;
                    secretMap.put(g, secretMap.get(g) - 1);
                }
            }

            for (int i = 0; i < guess.length(); i++) {
                char g = guess.charAt(i);
                if (g != secretCode.charAt(i) && secretMap.getOrDefault(g, 0) > 0) {
                    correctColor++;
                    secretMap.put(g, secretMap.get(g) - 1);
                }
            }

            if (correctPosition == 4) {
                return "Congratulations! You've guessed the code in " + attempts + " attempts.";
            }
            return correctPosition + " correct position(s), " + correctColor + " correct color(s) (Attempt " + attempts + ")";
        }

        public int getAttempts() {
            return attempts;
        }

        private String generateSecretCode() {
            String colors = "BGORWY";
            Random random = new Random();
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                code.append(colors.charAt(random.nextInt(colors.length())));
            }
            return code.toString();
        }
    }
}

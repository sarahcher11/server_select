import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerNIO {
    private static final int PORT = 12345;
    private static final int BUFFER_SIZE = 256;
    private static final Map<SocketChannel, GameSession> clientSessions = new ConcurrentHashMap<>();
    private static final List<PlayerScore> highScores = new ArrayList<>();
    private static Selector selector;

    public static void main(String[] args) {
        try {
            selector = Selector.open();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(PORT));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("[SERVEUR] En attente de connexions sur le port " + PORT + "...");

            while (true) {
                int readyChannels = selector.select(100); // Timeout pour éviter les blocages

                if (readyChannels == 0) continue;

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    try {
                        if (key.isAcceptable()) {
                            acceptClient(serverChannel);
                        } else if (key.isReadable()) {
                            readClientData(key);
                        }
                    } catch (IOException e) {
                        key.cancel(); // Retirer la clé en cas d'erreur
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[SERVEUR] Erreur critique : " + e.getMessage());
        }
    }

    private static void acceptClient(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
        clientSessions.put(clientChannel, new GameSession());
        key.attach(new ClientData()); // Attacher un objet ClientData pour suivre les informations du client

        System.out.println("[SERVEUR] Nouveau client connecté : " + clientChannel.getRemoteAddress());

        // Envoi du message initial demandant une combinaison
        sendMessage(clientChannel, "Entrez une combinaison (4 lettres parmi B,G,O,R,W,Y) : ");
    }

    private static void readClientData(SelectionKey key) {
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
            System.out.println("[SERVEUR] Réception : " + input);

            GameSession session = clientSessions.get(clientChannel);
            ClientData clientData = (ClientData) key.attachment(); // Récupérer les données attachées

            if (session != null) {
                if (session.isGameFinished()) {
                    // Si le jeu est terminé, demander le nom du joueur
                    if (clientData.getPlayerName() == null) {
                        clientData.setPlayerName(input); // Nom du joueur reçu
                        int score = clientData.getAttempts();
                        PlayerScore playerScore = new PlayerScore(clientData.getPlayerName(), score);
                        session.setPlayerScore(playerScore);
                        highScores.add(playerScore);
                        highScores.sort(Comparator.comparingInt(PlayerScore::getScore)); // Trier par score

                        sendMessage(clientChannel, "Votre nom a été enregistré avec un score de " + score + " tentatives.\n");
                        sendMessage(clientChannel, "Tapez /SCORE pour voir le tableau des meilleurs scores.");
                        key.interestOps(SelectionKey.OP_READ); // Revenir en mode lecture pour recevoir /SCORE ou d'autres commandes
                    } else {
                        handleClientRequest(clientChannel, input); // Appeler la méthode pour gérer les commandes comme /SCORE
                    }
                } else {
                    // Si le jeu n'est pas encore terminé, traiter la tentative de combinaison
                    String response = session.processGuess(input);
                    clientData.incrementAttempts();
                    sendMessage(clientChannel, response);

                    if (response.contains("Félicitations")) {
                        sendMessage(clientChannel, "Félicitations ! Entrez votre nom : ");
                        key.interestOps(SelectionKey.OP_WRITE); // Passer en mode écriture pour obtenir le nom
                    } else {
                        sendMessage(clientChannel, "Entrez une nouvelle combinaison : ");
                        key.interestOps(SelectionKey.OP_READ); // Revenir en mode lecture pour recevoir la nouvelle tentative
                    }
                }
            }
        } catch (IOException e) {
            disconnectClient(clientChannel);
        }
    }

    private static void handleClientRequest(SocketChannel clientChannel, String input) {
        // Si le client envoie "/SCORE"
        if ("/SCORE".equals(input)) {
            // Afficher les meilleurs scores
            StringBuilder scoreList = new StringBuilder("Meilleurs scores :\n");

            // Trie les scores par ordre décroissant si ce n'est pas déjà fait
            highScores.sort((ps1, ps2) -> Integer.compare(ps2.getScore(), ps1.getScore()));

            // Construire la liste des meilleurs scores
            for (PlayerScore ps : highScores) {
                scoreList.append(ps).append("\n");
            }

            // Envoyer la liste des meilleurs scores au client
            sendMessage(clientChannel, scoreList.toString());
        }
    }



    private static int countCorrectPositions(String secretCode, String guess) {
        int count = 0;
        for (int i = 0; i < secretCode.length(); i++) {
            if (secretCode.charAt(i) == guess.charAt(i)) {
                count++;
            }
        }
        return count;
    }

    private static int countIncorrectPositions(String secretCode, String guess) {
        int count = 0;
        for (int i = 0; i < secretCode.length(); i++) {
            if (secretCode.indexOf(guess.charAt(i)) != -1 && secretCode.charAt(i) != guess.charAt(i)) {
                count++;
            }
        }
        return count;
    }


    private static void sendMessage(SocketChannel clientChannel, String message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            clientChannel.write(buffer);
        } catch (IOException e) {
            disconnectClient(clientChannel);
        }
    }

    private static void disconnectClient(SocketChannel clientChannel) {
        try {
            System.out.println("[SERVEUR] Client déconnecté : " + clientChannel.getRemoteAddress());
            GameSession session = clientSessions.remove(clientChannel);
            if (session != null && session.isGameFinished()) {
                PlayerScore score = session.getPlayerScore();
                if (score != null) {  // Vérifier si le score est bien défini
                    highScores.add(score);
                    highScores.sort(Comparator.comparingInt(PlayerScore::getScore)); // Trier par score
                }
            }
            clientChannel.close();
        } catch (IOException e) {
            System.err.println("[SERVEUR] Erreur de fermeture : " + e.getMessage());
        }
    }


    private static class GameSession {
        private final String secretCode;
        private boolean gameFinished = false;
        private PlayerScore playerScore;

        public GameSession() {
            this.secretCode = generateSecretCode();
            System.out.println("[SERVEUR] Code secret généré : " + secretCode);
        }

        public String processGuess(String guess) {
            if (guess.equals(secretCode)) {
                gameFinished = true;
                return "Félicitations ! Vous avez trouvé la combinaison correcte.";
            }
            return "Mauvaise combinaison, réessayez.";
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

        public boolean isGameFinished() {
            return gameFinished;
        }

        public PlayerScore getPlayerScore() {
            return playerScore;
        }

        public void setPlayerScore(PlayerScore playerScore) {
            this.playerScore = playerScore;
        }

        public String getSecretCode() {
            return secretCode;
        }
    }

    private static class ClientData {
        private int attempts = 0;
        private String playerName;

        public void incrementAttempts() {
            attempts++;
        }

        public int getAttempts() {
            return attempts;
        }

        public void setPlayerName(String name) {
            this.playerName = name;
        }

        public String getPlayerName() {
            return playerName;
        }
    }

    private static class PlayerScore {
        private final String name;
        private final int score;

        public PlayerScore(String name, int score) {
            this.name = name;
            this.score = score;
        }

        public String getName() {
            return name;
        }

        public int getScore() {
            return score;
        }

        @Override
        public String toString() {
            return name + ": " + score + " tentatives";
        }
    }
}

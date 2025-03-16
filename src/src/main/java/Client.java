import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Scanner;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;
    private static final int BUFFER_SIZE = 256;
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            // Connexion et ouverture du canal
            SocketChannel clientChannel = SocketChannel.open(new InetSocketAddress(SERVER_ADDRESS, PORT));
            clientChannel.configureBlocking(false);
            Selector selector = Selector.open();
            clientChannel.register(selector, SelectionKey.OP_READ);

            System.out.println("[CLIENT] Connecté au serveur sur " + SERVER_ADDRESS + ":" + PORT);

            // Attente de la demande du serveur
            while (true) {
                int readyChannels = selector.select(100); // Timeout pour éviter un blocage
                if (readyChannels == 0) continue;

                for (SelectionKey key : selector.selectedKeys()) {
                    selector.selectedKeys().remove(key);

                    if (key.isReadable()) {

                        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                        SocketChannel channel = (SocketChannel) key.channel();
                        int bytesRead = channel.read(buffer);

                        if (bytesRead == -1) {
                            System.out.println("[CLIENT] Serveur déconnecté");
                            channel.close();
                            return;
                        }

                        buffer.flip();
                        String message = new String(buffer.array(), 0, bytesRead).trim();
                        System.out.println("[CLIENT] " + message);

                        if (message.contains("Entrez une combinaison")) {
                            System.out.print("Entrez une combinaison (4 lettres parmi B,G,O,R,W,Y) : ");
                            String input = scanner.nextLine().toUpperCase();

                            if (!input.matches("[BGORWY]{4}")) {
                                System.out.println("Combinaison invalide.");
                                continue;
                            }

                            // Envoie la combinaison au serveur
                            ByteBuffer sendBuffer = ByteBuffer.wrap(input.getBytes());
                            channel.write(sendBuffer);
                        } else if (message.contains("Entrez votre nom")) {
                            // Demander à l'utilisateur son nom
                            System.out.print("Entrez votre nom : ");
                            String playerName = scanner.nextLine();

                            // Envoyer le nom au serveur
                            ByteBuffer sendBuffer = ByteBuffer.wrap(playerName.getBytes());
                            channel.write(sendBuffer);

                            // Attendre une confirmation de la part du serveur
                            ByteBuffer confirmationBuffer = ByteBuffer.allocate(BUFFER_SIZE);
                            int confirmationBytesRead = channel.read(confirmationBuffer);

                            if (confirmationBytesRead == -1) {
                                System.out.println("[CLIENT] Serveur déconnecté");
                                channel.close();
                                return;
                            }

                            confirmationBuffer.flip();
                            String confirmationMessage = new String(confirmationBuffer.array(), 0, confirmationBytesRead).trim();
                            System.out.println("[CLIENT] " + confirmationMessage);

                            // Afficher un message confirmant la victoire et la saisie du nom
                            System.out.println("[CLIENT] Vous avez gagné !");

                            // Demander les meilleurs scores
                            System.out.println("Tapez '/SCORE' pour voir les meilleurs scores.");
                            String command = scanner.nextLine();
                            if ("/SCORE".equals(command)) {
                                ByteBuffer scoreRequestBuffer = ByteBuffer.wrap(command.getBytes());
                                channel.write(scoreRequestBuffer);

                                // Lire la réponse avec les scores
                                ByteBuffer scoreBuffer = ByteBuffer.allocate(BUFFER_SIZE);
                                int scoreBytesRead = channel.read(scoreBuffer);

                                if (scoreBytesRead == -1) {
                                    System.out.println("[CLIENT] Serveur déconnecté");
                                    channel.close();
                                    return;
                                }

                                scoreBuffer.flip();
                                String scoreList = new String(scoreBuffer.array(), 0, scoreBytesRead).trim();
                                System.out.println("[CLIENT] Liste des meilleurs scores :");
                                System.out.println(scoreList);
                            }
                            channel.close();
                            return;
                        }

                        else if (message.contains("Félicitations")) {
                            System.out.println("[CLIENT] Vous avez gagné !");
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[CLIENT] Erreur de connexion : " + e.getMessage());
        }
    }

}
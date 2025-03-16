import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Stress1 {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final int MAX_CLIENTS = 1000;

    public static void main(String[] args) {
        String filePath = "mesures.csv";
        File file = new File(filePath);
        boolean isNewFile = !file.exists();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            if (isNewFile) {
                writer.write("Nombre_de_clients;Temps_moyen_ns;Min_Temps_ns;Max_Temps_ns;Ecart_Type_ns\n");
            }

            for (int n : new int[]{1, 2, 10, 100, 1000, 5000, 50000}) {
                ExecutorService executor = Executors.newCachedThreadPool();
                CountDownLatch latch = new CountDownLatch(n);
                AtomicLong totalResponseTime = new AtomicLong();
                List<Long> responseTimes = new CopyOnWriteArrayList<>();

                for (int i = 0; i < n; i++) {
                    executor.submit(() -> {
                        long startTime = System.nanoTime();
                        try (SocketChannel clientChannel = SocketChannel.open()) {
                            clientChannel.connect(new InetSocketAddress(SERVER_ADDRESS, SERVER_PORT));
                            sendMessage(clientChannel, "BGOR");
                            String response = receiveMessage(clientChannel);
                            long responseTime = System.nanoTime() - startTime;
                            totalResponseTime.addAndGet(responseTime);
                            responseTimes.add(responseTime);
                        } catch (IOException e) {
                            System.err.println("Erreur lors de la connexion client : " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await();
                executor.shutdown();

                // Calculs statistiques
                long averageResponseTime = totalResponseTime.get() / n;
                long minResponseTime = responseTimes.stream().min(Long::compare).orElse(0L);
                long maxResponseTime = responseTimes.stream().max(Long::compare).orElse(0L);

                // Calcul de la variance et écart-type
                double mean = totalResponseTime.get() / (double) n;
                double sumSquaredDifferences = 0.0;
                for (long responseTime : responseTimes) {
                    sumSquaredDifferences += Math.pow(responseTime - mean, 2);
                }
                double variance = sumSquaredDifferences / n;
                double ecartType = Math.sqrt(variance);

                writer.write(n + ";" + averageResponseTime + ";" + minResponseTime + ";" + maxResponseTime + ";" + ecartType + "\n");
                System.out.println("Clients: " + n + " | Moyenne: " + averageResponseTime + " ns | Min: " + minResponseTime + " ns | Max: " + maxResponseTime + " ns | Écart-type: " + ecartType + " ns");
            }

            System.out.println("Tests terminés. Résultats sauvegardés dans " + filePath);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
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

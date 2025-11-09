package p1;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class UDPChatServer extends JFrame {
    private JTextArea textArea;
    private DefaultListModel<String> listModel;
    private JList<String> clientsList;
    private DatagramSocket socket;
    private final int PORT = 5000;

    // Associe chaque client (adresse IP + port) à son nom
    private final Map<SocketAddress, String> clients = new ConcurrentHashMap<>();

    public UDPChatServer() {
        setTitle("Serveur UDP Chat");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        textArea = new JTextArea();
        textArea.setEditable(false);
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        listModel = new DefaultListModel<>();
        clientsList = new JList<>(listModel);
        clientsList.setBorder(BorderFactory.createTitledBorder("Clients connectés"));
        clientsList.setPreferredSize(new Dimension(200, 0));
        add(new JScrollPane(clientsList), BorderLayout.EAST);

        setVisible(true);

        startServer();
    }

    private void startServer() {
        new Thread(() -> {
            try {
                socket = new DatagramSocket(PORT);
                textArea.append("✅ Serveur démarré sur le port " + PORT + "\n");

                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    SocketAddress clientAddr = packet.getSocketAddress();

                    if (message.startsWith("CONNECT:")) {
                        String username = message.substring(8).trim();
                        clients.put(clientAddr, username);
                        textArea.append("🟢 " + username + " s'est connecté.\n");
                        updateClientList();
                        broadcast("LIST:" + String.join(",", clients.values())); // Envoi liste aux clients
                        broadcast("🟢 " + username + " a rejoint le chat !");
                    } 
                    else if (message.startsWith("PRIVATE:")) {
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            String destName = parts[1];
                            String privateMsg = parts[2];
                            String senderName = clients.get(clientAddr);
                            SocketAddress destAddr = null;
                            for (Map.Entry<SocketAddress, String> entry : clients.entrySet()) {
                                if (entry.getValue().equals(destName)) {
                                    destAddr = entry.getKey();
                                    break;
                                }
                            }
                            if (destAddr != null) {
                                sendToClient(destAddr, "🔒 " + senderName + " à vous : " + privateMsg);
                                sendToClient(clientAddr, "🔒 Vous à " + destName + " : " + privateMsg);
                            }
                        }
                    } 
                    else {
                        String senderName = clients.get(clientAddr);
                        if (senderName == null) senderName = "Inconnu";
                        String displayMsg = senderName + " : " + message;
                        textArea.append("📨 " + displayMsg + "\n");
                        broadcast(displayMsg);
                    }
                }

            } catch (Exception e) {
                textArea.append("❌ Erreur serveur : " + e.getMessage() + "\n");
            }
        }).start();
    }

    private void broadcast(String message) {
        try {
            byte[] data = message.getBytes();
            for (SocketAddress addr : clients.keySet()) {
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        ((InetSocketAddress) addr).getAddress(),
                        ((InetSocketAddress) addr).getPort());
                socket.send(packet);
            }
        } catch (Exception e) {
            textArea.append("Erreur broadcast : " + e.getMessage() + "\n");
        }
    }

    private void sendToClient(SocketAddress addr, String message) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    ((InetSocketAddress) addr).getAddress(),
                    ((InetSocketAddress) addr).getPort());
            socket.send(packet);
        } catch (Exception e) {
            textArea.append("Erreur envoi privé : " + e.getMessage() + "\n");
        }
    }

    private void updateClientList() {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (String name : clients.values()) {
                listModel.addElement(name);
            }
        });
    }

    public static void main(String[] args) {
        new UDPChatServer();
    }
}

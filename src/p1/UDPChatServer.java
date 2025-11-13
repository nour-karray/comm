package p1;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UDPChatServer extends JFrame {
    private JTextArea textArea;
    private DefaultListModel<String> listModel;
    private JList<String> clientsList;
    private DatagramSocket socket;
    private final int PORT = 5000;
    private final Map<SocketAddress, String> clients = new ConcurrentHashMap<>();

    public UDPChatServer() {
        setTitle("Serveur UDP Chat");
        setSize(800, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JLabel header = new JLabel("Serveur UDP Chat", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.setBorder(new EmptyBorder(8,8,8,8));
        add(header, BorderLayout.NORTH);

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        listModel = new DefaultListModel<>();
        clientsList = new JList<>(listModel);
        clientsList.setBorder(BorderFactory.createTitledBorder("Clients connectés"));
        clientsList.setPreferredSize(new Dimension(220,0));
        add(new JScrollPane(clientsList), BorderLayout.EAST);

        setVisible(true);
        startServer();
    }

    private void startServer() {
        new Thread(() -> {
            try {
                socket = new DatagramSocket(PORT);
                append("✅ Serveur démarré sur le port " + PORT);

                byte[] buffer = new byte[65507];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    SocketAddress clientAddr = packet.getSocketAddress();

                    if (message.startsWith("CONNECT:")) {
                        String username = message.substring(8).trim();
                        clients.put(clientAddr, username);
                        append("🟢 " + username + " s'est connecté depuis " + clientAddr);
                        updateClientList();
                        broadcast("LIST:" + String.join(",", clients.values()), null);
                        broadcast("🟢 " + username + " a rejoint le chat !", null);
                    } else if (message.startsWith("PRIVATE:")) {
                        String[] parts = message.split(":", 3);
                        if (parts.length == 3) {
                            String destName = parts[1];
                            String payload = parts[2];
                            String senderName = clients.get(clientAddr);
                            SocketAddress destAddr = null;
                            for (Map.Entry<SocketAddress, String> entry : clients.entrySet()) {
                                if (entry.getValue().equals(destName)) {
                                    destAddr = entry.getKey();
                                    break;
                                }
                            }
                            if (destAddr != null) {
                                sendToClient(destAddr, payload);
                                append("🔒 " + senderName + " -> " + destName + " (privé)");
                            } else {
                                sendToClient(clientAddr, "Serveur: Utilisateur '" + destName + "' non trouvé.");
                            }
                        }
                    } else { // Broadcast classique
                        if (message.startsWith("IMAGE:")) {
                            // Si c'est une image, broadcast sans modification
                            broadcast(message, clientAddr);
                            String senderName = clients.get(clientAddr);
                            append("📨 Image envoyée par " + senderName);
                        } else {
                            // Message texte classique
                            String senderName = clients.get(clientAddr);
                            if (senderName == null) senderName = "Inconnu";
                            String display = senderName + " : " + message;
                            append("📨 " + display);
                            broadcast(display, clientAddr);
                        }
                    }
                }

            } catch (Exception e) {
                append("❌ Erreur serveur : " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void broadcast(String message, SocketAddress exclude) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            for (SocketAddress addr : clients.keySet()) {
                if (addr.equals(exclude)) continue; // Ignore l'expéditeur
                InetSocketAddress inet = (InetSocketAddress) addr;
                DatagramPacket packet = new DatagramPacket(data, data.length, inet.getAddress(), inet.getPort());
                socket.send(packet);
            }
        } catch (Exception e) {
            append("Erreur broadcast : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendToClient(SocketAddress addr, String message) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            InetSocketAddress inet = (InetSocketAddress) addr;
            DatagramPacket packet = new DatagramPacket(data, data.length, inet.getAddress(), inet.getPort());
            socket.send(packet);
        } catch (Exception e) {
            append("Erreur envoi privé : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateClientList() {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (String name : clients.values()) listModel.addElement(name);
        });
    }

    private void append(String s) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(s + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(UDPChatServer::new);
    }
}

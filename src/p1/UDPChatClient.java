package p1;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.*;

public class UDPChatClient extends JFrame {
    private JTextArea textArea;
    private JTextField textField;
    private JButton sendButton;
    private JComboBox<String> recipientsBox;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private final int serverPort = 5000;
    private String username;

    public UDPChatClient() {
        username = JOptionPane.showInputDialog(null, "Entrez votre nom :", "Connexion au chat UDP", JOptionPane.QUESTION_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Nom invalide, fermeture du client.");
            System.exit(0);
        }

        setTitle("Client UDP - " + username);
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        textArea = new JTextArea();
        textArea.setEditable(false);
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        textField = new JTextField();
        sendButton = new JButton("Envoyer");

        recipientsBox = new JComboBox<>();
        recipientsBox.addItem("Tous"); // message global
        bottom.add(recipientsBox, BorderLayout.WEST);

        JPanel sendPanel = new JPanel(new BorderLayout());
        sendPanel.add(textField, BorderLayout.CENTER);
        sendPanel.add(sendButton, BorderLayout.EAST);
        bottom.add(sendPanel, BorderLayout.CENTER);

        add(bottom, BorderLayout.SOUTH);

        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName("127.0.0.1");
        } catch (Exception e) {
            e.printStackTrace();
        }

        sendMessage("CONNECT:" + username);

        new Thread(this::listenMessages).start();

        sendButton.addActionListener(e -> sendChatMessage());
        textField.addActionListener(e -> sendChatMessage());

        setVisible(true);
    }

    private void sendChatMessage() {
        String msg = textField.getText().trim();
        if (!msg.isEmpty()) {
            String recipient = (String) recipientsBox.getSelectedItem();
            if (!"Tous".equals(recipient)) {
                msg = "PRIVATE:" + recipient + ":" + msg;
            }
            sendMessage(msg);
            textField.setText("");
        }
    }

    private void sendMessage(String msg) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listenMessages() {
        try {
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());

                // Mise à jour liste clients si message du type LIST:...
                if (message.startsWith("LIST:")) {
                    String[] users = message.substring(5).split(",");
                    SwingUtilities.invokeLater(() -> {
                        recipientsBox.removeAllItems();
                        recipientsBox.addItem("Tous");
                        for (String user : users) {
                            if (!user.equals(username)) { // Ne pas s'ajouter soi-même
                                recipientsBox.addItem(user);
                            }
                        }
                    });
                } else {
                    textArea.append(message + "\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(UDPChatClient::new);
        SwingUtilities.invokeLater(UDPChatClient::new);
        SwingUtilities.invokeLater(UDPChatClient::new);
    }
}

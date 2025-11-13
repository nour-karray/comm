package UDP;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class UDPChatClient extends JFrame {
    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JTextField textField;
    private JButton sendButton, attachButton;
    private JComboBox<String> recipientsBox;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private final int serverPort = 5000;
    private String username;
    private static final int MAX_UDP_PACKET = 65507;

    public UDPChatClient() {
        username = JOptionPane.showInputDialog(null, "Entrez votre nom :", "Connexion au chat UDP", JOptionPane.QUESTION_MESSAGE);
        if (username == null || username.trim().isEmpty()) { JOptionPane.showMessageDialog(null, "Nom invalide."); System.exit(0); }

        setTitle("Client UDP - " + username);
        setSize(700, 520);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JLabel header = new JLabel("Chat UDP - " + username, SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.setBorder(new EmptyBorder(8,8,8,8));
        add(header, BorderLayout.NORTH);

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(245,245,250));
        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(chatScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8,8));
        bottom.setBorder(new EmptyBorder(8,8,8,8));

        recipientsBox = new JComboBox<>();
        recipientsBox.addItem("Tous");
        recipientsBox.setPreferredSize(new Dimension(160,30));
        bottom.add(recipientsBox, BorderLayout.WEST);

        JPanel sendPanel = new JPanel(new BorderLayout(6,6));
        textField = new JTextField();
        textField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sendPanel.add(textField, BorderLayout.CENTER);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        attachButton = new JButton("📎"); attachButton.setToolTipText("Envoyer une image");
        sendButton = new JButton("Envoyer");
        rightButtons.add(attachButton); rightButtons.add(sendButton);
        sendPanel.add(rightButtons, BorderLayout.EAST);
        bottom.add(sendPanel, BorderLayout.CENTER);

        add(bottom, BorderLayout.SOUTH);

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(0);
            serverAddress = InetAddress.getByName("127.0.0.1");
        } catch (Exception e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Erreur socket."); System.exit(1); }

        sendMessage("CONNECT:" + username);
        new Thread(this::listenMessages).start();

        sendButton.addActionListener(e -> sendChatMessage());
        textField.addActionListener(e -> sendChatMessage());
        attachButton.addActionListener(e -> chooseAndSendImage());

        setVisible(true);
    }

    private void sendChatMessage() {
        String msg = textField.getText().trim();
        if (!msg.isEmpty()) {
            String recipient = (String) recipientsBox.getSelectedItem();
            String payload = msg;
            if (!"Tous".equals(recipient)) payload = "PRIVATE:" + recipient + ":" + msg;
            sendMessage(payload);
            addMessageBubble("Vous", msg, true);
            textField.setText("");
        }
    }

    private void chooseAndSendImage() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File f = chooser.getSelectedFile();
                BufferedImage img = ImageIO.read(f);
                byte[] imgBytes = bufferedImageToJpegBytes(img, 600, 600, MAX_UDP_PACKET - 2000);
                String b64 = Base64.getEncoder().encodeToString(imgBytes);
                String imageMessage = "IMAGE:" + f.getName() + ":" + b64;
                String recipient = (String) recipientsBox.getSelectedItem();
                String payload = imageMessage;
                if (!"Tous".equals(recipient)) payload = "PRIVATE:" + recipient + ":" + imageMessage;
                sendMessage(payload);
                addImageBubble("Vous", img, true);
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private byte[] bufferedImageToJpegBytes(BufferedImage img, int maxW, int maxH, int maxBytes) throws Exception {
        int w = img.getWidth(), h = img.getHeight();
        double scale = Math.min(1.0, Math.min((double)maxW/w, (double)maxH/h));
        int newW = (int)(w*scale), newH = (int)(h*scale);
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(img.getScaledInstance(newW,newH,Image.SCALE_SMOOTH),0,0,null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resized,"jpg",baos);
        return baos.toByteArray();
    }

    private void sendMessage(String msg) {
        try {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            if (data.length > MAX_UDP_PACKET) { JOptionPane.showMessageDialog(this,"Message trop grand."); return; }
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (Exception e) { e.printStackTrace(); }
    }

private void listenMessages() {
    try {
        byte[] buffer = new byte[MAX_UDP_PACKET];
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String message = new String(packet.getData(),0,packet.getLength(),StandardCharsets.UTF_8);

            if (message.startsWith("LIST:")) {
                String[] users = message.substring(5).split(",");
                SwingUtilities.invokeLater(() -> {
                    recipientsBox.removeAllItems();
                    recipientsBox.addItem("Tous");
                    for (String user : users) if (!user.equals(username)) recipientsBox.addItem(user);
                });
            }else if (message.startsWith("PRIVATE:")) {
                String[] parts = message.split(":",3);
                if (parts.length==3) {
                    String payload = parts[2];
                    if (payload.startsWith("IMAGE:")) handleIncomingImageMessage(payload);
                    else SwingUtilities.invokeLater(() -> addMessageBubble("Privé", payload,false));
                }
            } 
            // ➤ Ajouter ceci pour les images broadcast
            else if (message.startsWith("IMAGE:")) {
                handleIncomingImageMessage(message); // décodage et affichage
            } 
            else {
                SwingUtilities.invokeLater(() -> addMessageBubble(null,message,false));
            }
        }
    } catch (Exception e) { e.printStackTrace(); }

        try {
            byte[] buffer = new byte[MAX_UDP_PACKET];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(),0,packet.getLength(),StandardCharsets.UTF_8);

                if (message.startsWith("LIST:")) {
                    String[] users = message.substring(5).split(",");
                    SwingUtilities.invokeLater(() -> {
                        recipientsBox.removeAllItems();
                        recipientsBox.addItem("Tous");
                        for (String user : users) if (!user.equals(username)) recipientsBox.addItem(user);
                    });
                } else if (message.startsWith("IMAGE:")) {
                    handleIncomingImageMessage(message);
                } else if (message.startsWith("PRIVATE:")) {
                    String[] parts = message.split(":",3);
                    if (parts.length==3) {
                        String payload = parts[2];
                        if (payload.startsWith("IMAGE:")) handleIncomingImageMessage(payload);
                        else SwingUtilities.invokeLater(() -> addMessageBubble("Privé", payload,false));
                    }
                } else SwingUtilities.invokeLater(() -> addMessageBubble(null,message,false));
            }
        } catch (Exception e) { e.printStackTrace();} }
    
    private void handleIncomingImageMessage(String payload) {
        try {
            String[] parts = payload.split(":",3);
            byte[] imgBytes = Base64.getDecoder().decode(parts[2]);
            BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(imgBytes));
            SwingUtilities.invokeLater(() -> addImageBubble(parts[1], img, false));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void addMessageBubble(String sender, String text, boolean mine) {
        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setOpaque(false);
        JTextArea label = new JTextArea(text);
        label.setLineWrap(true); label.setWrapStyleWord(true); label.setEditable(false);
        label.setBackground(mine?new Color(204,229,255):new Color(230,230,235));
        label.setBorder(new EmptyBorder(8,8,8,8));
        bubble.add(label, BorderLayout.CENTER);
        if (sender!=null) bubble.add(new JLabel(sender), BorderLayout.NORTH);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false); wrapper.setBorder(new EmptyBorder(6,6,6,6));
        wrapper.add(bubble, mine?BorderLayout.EAST:BorderLayout.WEST);
        chatPanel.add(wrapper); chatPanel.revalidate();
        SwingUtilities.invokeLater(() -> chatScroll.getVerticalScrollBar().setValue(chatScroll.getVerticalScrollBar().getMaximum()));
    }

    private void addImageBubble(String sender, BufferedImage img, boolean mine) {
        ImageIcon icon = new ImageIcon(img.getScaledInstance(-1,180,Image.SCALE_SMOOTH));
        JLabel picLabel = new JLabel(icon); picLabel.setBorder(new EmptyBorder(6,6,6,6));
        JPanel bubble = new JPanel(new BorderLayout()); bubble.setOpaque(false);
        if (sender!=null) bubble.add(new JLabel(sender), BorderLayout.NORTH);
        bubble.add(picLabel, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout()); wrapper.setOpaque(false); wrapper.setBorder(new EmptyBorder(6,6,6,6));
        wrapper.add(bubble, mine?BorderLayout.EAST:BorderLayout.WEST);
        chatPanel.add(wrapper); chatPanel.revalidate();
        SwingUtilities.invokeLater(() -> chatScroll.getVerticalScrollBar().setValue(chatScroll.getVerticalScrollBar().getMaximum()));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(UDPChatClient::new);
        SwingUtilities.invokeLater(UDPChatClient::new);
        SwingUtilities.invokeLater(UDPChatClient::new);
    }
}

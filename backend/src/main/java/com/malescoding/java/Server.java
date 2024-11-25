// File: Server.java
package com.malescoding.java;

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// The server that can be run as a console using WebSockets and JSON
public class Server extends WebSocketServer {
    // A unique ID for each connection
    private static int uniqueId;
    // To display time
    private SimpleDateFormat sdf;
    // Notification prefix
    private String notif = " *** ";
    // Map to manage chat forums
    private ConcurrentHashMap<String, ChatForum> forums;
    // Map to keep track of WebSocket connections and their corresponding ClientThread
    private ConcurrentHashMap<WebSocket, ClientThread> clients;
    // Gson instance for JSON parsing
    private Gson gson;

    // Constructor that receives the port to listen to for connection as parameter
    public Server(int port) {
        super(new InetSocketAddress(port));
        sdf = new SimpleDateFormat("HH:mm:ss");
        forums = new ConcurrentHashMap<>();
        clients = new ConcurrentHashMap<>();
        gson = new Gson();
        // Initialize with a default forum
        forums.put("General", new ChatForum("General"));
        System.out.println("Server initialized on port " + port);
    }

    @Override
    public void onStart() {
        System.out.println("Server started on port: " + getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // When a new connection is opened, expect the first message to be the username
        System.out.println("New connection from " + conn.getRemoteSocketAddress());
        // Create a new ClientThread and associate it with this WebSocket
        ClientThread client = new ClientThread(conn);
        clients.put(conn, client);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // When a connection is closed, remove the client
        ClientThread client = clients.get(conn);
        if (client != null) {
            client.logout();
            clients.remove(conn);
        }
        System.out.println("Connection closed: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // Parse the incoming JSON message
        ClientThread client = clients.get(conn);
        if (client == null) {
            // If client is not yet initialized, expect the first message to be the username
            client = new ClientThread(conn);
            clients.put(conn, client);
            client.setUsername(message);
            return;
        }
        client.handleMessage(message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("An error occurred on connection " + conn + ":" + ex);
    }

    // Inner class to represent a chat forum
    class ChatForum {
        private String name;
        private List<ClientThread> clients;

        public ChatForum(String name) {
            this.name = name;
            this.clients = Collections.synchronizedList(new ArrayList<>());
        }

        public String getName() {
            return name;
        }

        public List<ClientThread> getClients() {
            return clients;
        }

        public void addClient(ClientThread client) {
            clients.add(client);
        }

        public void removeClient(ClientThread client) {
            clients.remove(client);
        }

        public void removeClientById(int id) {
            clients.removeIf(client -> client.getId() == id);
        }

        public int getNumberOfClients() {
            return clients.size();
        }
    }

    // Inner class to represent a client
    class ClientThread {
        private WebSocket conn;
        private int id;
        private String username;
        private String date;
        private String currentForum;

        public ClientThread(WebSocket conn) {
            this.conn = conn;
            this.id = ++uniqueId;
            this.currentForum = "General"; // Default forum
            this.date = new Date().toString();
        }

        public int getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
            System.out.println(username + " just connected.");
            // Add client to the default forum
            ChatForum defaultForum = forums.get("General");
            if (defaultForum != null) {
                defaultForum.addClient(this);
                broadcastNotification(username + " has joined the 'General' forum.", "General");
            }
            // Send the list of forums to the client
            sendForumList();
            // Notify others in the general forum
            broadcastNotification(username + " has joined the chat server.", "General");
        }

        public void handleMessage(String message) {
            // Parse the JSON message
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();
            String content = json.get("content").getAsString();

            switch (type) {
                case "MESSAGE":
                    handleChatMessage(content);
                    break;
                case "LOGOUT":
                    logout();
                    break;
                case "WHOISIN":
                    sendWhoIsIn();
                    break;
                case "JOIN_FORUM":
                    joinForum(content);
                    break;
                case "ADD_FORUM":
                    addForum(content);
                    break;
                case "EXIT_FORUM":
                    exitForum();
                    break;
                case "LIST_FORUMS":
                    sendForumList();
                    break;
                default:
                    sendNotification("Unknown message type.");
                    break;
            }
        }

        private void handleChatMessage(String message) {
            if (message.startsWith("@")) {
                // Private message
                String[] parts = message.split(" ", 2);
                if (parts.length >= 2) {
                    String targetUser = parts[0].substring(1); // Remove '@'
                    String privateMsg = parts[1];
                    boolean sent = sendPrivateMessage(targetUser, privateMsg);
                    if (!sent) {
                        sendNotification("Sorry. No such user exists in the current forum.");
                    }
                } else {
                    sendNotification("Incorrect private message format. Use @username your_message");
                }
            } else {
                // Broadcast message within the current forum
                broadcastMessage(username + ": " + message, currentForum);
            }
        }

        private boolean sendPrivateMessage(String targetUser, String privateMsg) {
            ChatForum forum = forums.get(currentForum);
            if (forum == null) {
                return false;
            }

            for (ClientThread ct : forum.getClients()) {
                if (ct.getUsername().equalsIgnoreCase(targetUser)) {
                    JsonObject msg = new JsonObject();
                    msg.addProperty("type", "MESSAGE");
                    msg.addProperty("content", username + " (private): " + privateMsg);
                    ct.sendMessage(msg);
                    return true;
                }
            }
            return false;
        }

        private void sendWhoIsIn() {
            JsonObject response = new JsonObject();
            response.addProperty("type", "WHOISIN");
            ChatForum forum = forums.get(currentForum);
            if (forum != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("List of users in '").append(currentForum).append("' forum:\n");
                int count = 1;
                synchronized (forum.getClients()) {
                    for (ClientThread ct : forum.getClients()) {
                        sb.append(count).append(". ").append(ct.getUsername()).append(" (").append(ct.date).append(")\n");
                        count++;
                    }
                }
                response.addProperty("content", sb.toString());
            } else {
                response.addProperty("content", "No users found in the current forum.");
            }
            sendMessage(response);
        }

        private void joinForum(String forumName) {
            forumName = forumName.trim();
            if (!forums.containsKey(forumName)) {
                sendNotification("Forum '" + forumName + "' does not exist.");
                return;
            }

            if (currentForum.equals(forumName)) {
                sendNotification("You are already in the '" + forumName + "' forum.");
                return;
            }

            // Remove from current forum
            ChatForum oldForum = forums.get(currentForum);
            if (oldForum != null) {
                oldForum.removeClient(this);
                broadcastNotification(username + " has left the forum.", currentForum);
                // If no one is left in the old forum and it's not the default "General" forum, delete it
                if (oldForum.getClients().isEmpty() && !oldForum.getName().equals("General")) {
                    forums.remove(currentForum);
                    broadcastNotification("Forum '" + currentForum + "' has been deleted as no participants remain.", "General");
                }
            }

            // Add to new forum
            ChatForum newForum = forums.get(forumName);
            if (newForum != null) {
                newForum.addClient(this);
                currentForum = forumName;
                broadcastNotification(username + " has joined the forum.", currentForum);
            } else {
                sendNotification("Failed to join the forum.");
            }
        }

        private void addForum(String forumNameInput) {
            forumNameInput = forumNameInput.trim();
            if (forumNameInput.isEmpty()) {
                sendNotification("Forum name cannot be empty.");
                return;
            }

            String baseName = forumNameInput;
            String uniqueName = baseName;
            int suffix = 1;

            // Ensure unique forum name
            while (forums.containsKey(uniqueName)) {
                uniqueName = baseName + suffix;
                suffix++;
            }

            // Create and add the new forum
            ChatForum newForum = new ChatForum(uniqueName);
            forums.put(uniqueName, newForum);
            sendNotification("New forum '" + uniqueName + "' has been created.");
            // Notify all clients in the General forum about the new forum
            broadcastNotification("A new forum '" + uniqueName + "' has been created.", "General");
        }

        private void exitForum() {
            if (currentForum.equals("General")) {
                sendNotification("You are already in the 'General' forum.");
                return;
            }

            // Remove from current forum
            ChatForum oldForum = forums.get(currentForum);
            if (oldForum != null) {
                oldForum.removeClient(this);
                broadcastNotification(username + " has left the forum.", currentForum);
                // If no one is left in the old forum and it's not the default "General" forum, delete it
                if (oldForum.getClients().isEmpty() && !oldForum.getName().equals("General")) {
                    forums.remove(currentForum);
                    broadcastNotification("Forum '" + currentForum + "' has been deleted as no participants remain.", "General");
                }
            }

            // Add to General forum
            ChatForum generalForum = forums.get("General");
            if (generalForum != null) {
                generalForum.addClient(this);
                currentForum = "General";
                broadcastNotification(username + " has joined the 'General' forum.", currentForum);
            } else {
                sendNotification("Failed to join the 'General' forum.");
            }
        }

        private void logout() {
            ChatForum forum = forums.get(currentForum);
            if (forum != null) {
                forum.removeClient(this);
                broadcastNotification(username + " has left the forum.", currentForum);
                // If no one is left in the forum and it's not the default "General" forum, delete it
                if (forum.getClients().isEmpty() && !forum.getName().equals("General")) {
                    forums.remove(currentForum);
                    broadcastNotification("Forum '" + currentForum + "' has been deleted as no participants remain.", "General");
                }
            }
            broadcastNotification(username + " has left the chat server.", "General");
            clients.remove(conn);
            conn.close();
            System.out.println(username + " has logged out.");
        }

        private void sendForumList() {
            JsonObject response = new JsonObject();
            response.addProperty("type", "LIST_FORUMS");
            StringBuilder sb = new StringBuilder();
            sb.append("List of Forums:\n");
            int count = 1;
            for (Map.Entry<String, ChatForum> entry : forums.entrySet()) {
                sb.append(count).append(". ").append(entry.getKey())
                        .append(" (").append(entry.getValue().getNumberOfClients()).append(" participants)\n");
                count++;
            }
            response.addProperty("content", sb.toString());
            sendMessage(response);
        }

        private void broadcastMessage(String message, String forumName) {
            String time = sdf.format(new Date());
            String messageLf = time + " " + message;

            ChatForum forum = forums.get(forumName);
            if (forum == null) {
                sendNotification("Forum does not exist.");
                return;
            }

            // Display message on server console
            System.out.println(messageLf);

            // Create JSON message
            JsonObject json = new JsonObject();
            json.addProperty("type", "MESSAGE");
            json.addProperty("content", messageLf);

            // Iterate over clients in the forum
            synchronized (forum.getClients()) {
                for (ClientThread ct : forum.getClients()) {
                    ct.sendMessage(json);
                }
            }
        }

        private void broadcastNotification(String notification, String forumName) {
            String time = sdf.format(new Date());
            String messageLf = time + " " + notif + notification + notif;

            ChatForum forum = forums.get(forumName);
            if (forum == null) {
                return;
            }

            // Display notification on server console
            System.out.println(messageLf);

            // Create JSON message
            JsonObject json = new JsonObject();
            json.addProperty("type", "NOTIFICATION");
            json.addProperty("content", messageLf);

            // Iterate over clients in the forum
            synchronized (forum.getClients()) {
                for (ClientThread ct : forum.getClients()) {
                    ct.sendMessage(json);
                }
            }
        }

        private void sendNotification(String notification) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "NOTIFICATION");
            json.addProperty("content", notif + notification + notif);
            sendMessage(json);
        }

        private void sendMessage(JsonObject json) {
            conn.send(gson.toJson(json));
        }
    }

    // Main method to start the server
    public static void main(String[] args) {
        int port = 1500; // Default port
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number. Using default port 1500.");
            }
        }
        Server server = new Server(port);
        server.start();
    }
}

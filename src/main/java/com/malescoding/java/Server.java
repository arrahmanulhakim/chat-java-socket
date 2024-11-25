package com.malescoding.java;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// The server that can be run as a console
public class Server {
    // A unique ID for each connection
    private static int uniqueId;
    // A list to keep the list of the Clients
    private ArrayList<ClientThread> al;
    // To display time
    private SimpleDateFormat sdf;
    // The port number to listen for connection
    private int port;
    // To check if server is running
    private boolean keepGoing;
    // Notification prefix
    private String notif = " *** ";
    // Map to manage chat forums
    private ConcurrentHashMap<String, ChatForum> forums;

    // Constructor that receives the port to listen to for connection as parameter
    public Server(int port) {
        this.port = port;
        sdf = new SimpleDateFormat("HH:mm:ss");
        al = new ArrayList<ClientThread>();
        forums = new ConcurrentHashMap<>();
        // Initialize with a default forum
        forums.put("General", new ChatForum("General"));
    }

    public void start() {
        keepGoing = true;
        // Create socket server and wait for connection requests
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            display("Server waiting for Clients on port " + port + ".");

            // Infinite loop to wait for connections (till server is active)
            while (keepGoing) {
                Socket socket = serverSocket.accept();
                if (!keepGoing)
                    break;
                // If client is connected, create its thread
                ClientThread t = new ClientThread(socket);
                al.add(t);
                t.start();
            }
            // Try to stop the server
            try {
                serverSocket.close();
                for (ClientThread tc : al) {
                    try {
                        // Close all data streams and socket
                        tc.sInput.close();
                        tc.sOutput.close();
                        tc.socket.close();
                    } catch (IOException ioE) {
                        // Ignore
                    }
                }
            } catch (Exception e) {
                display("Exception closing the server and clients: " + e);
            }
        } catch (IOException e) {
            String msg = sdf.format(new Date()) + " Exception on new ServerSocket: " + e + "\n";
            display(msg);
        }
    }

    // To stop the server
    protected void stop() {
        keepGoing = false;
        try {
            new Socket("localhost", port);
        } catch (Exception e) {
            // Ignore
        }
    }

    // Display an event to the console
    private void display(String msg) {
        String time = sdf.format(new Date()) + " " + msg;
        System.out.println(time);
    }

    // To broadcast a message to all Clients in a specific forum
    private synchronized boolean broadcast(String message, String forumName) {
        String time = sdf.format(new Date());
        String messageLf = time + " " + message + "\n";

        ChatForum forum = forums.get(forumName);
        if (forum == null) {
            return false;
        }

        // Display message on server
        System.out.print(messageLf);

        // Iterate over clients in the forum
        for (ClientThread ct : forum.getClients()) {
            if (!ct.writeMsg(messageLf)) {
                // If failed to send, remove client
                al.remove(ct);
                forum.removeClient(ct);
                display("Disconnected Client " + ct.username + " removed from list.");
            }
        }
        return true;
    }

    // Overloaded broadcast for private messages
    private synchronized boolean broadcast(String message, String targetUser, String currentForum) {
        String time = sdf.format(new Date());
        String messageLf = time + " " + message + "\n";
        boolean found = false;

        ChatForum forum = forums.get(currentForum);
        if (forum == null) {
            return false;
        }

        for (ClientThread ct : forum.getClients()) {
            if (ct.username.equals(targetUser)) {
                if (ct.writeMsg(messageLf)) {
                    found = true;
                } else {
                    // If failed to send, remove client
                    al.remove(ct);
                    forum.removeClient(ct);
                    display("Disconnected Client " + ct.username + " removed from list.");
                }
                break;
            }
        }

        return found;
    }

    // If client sent LOGOUT message to exit
    synchronized void remove(int id) {
        String disconnectedClient = "";
        String currentForum = "";

        // Scan the array list until we find the Id
        for (int i = 0; i < al.size(); ++i) {
            ClientThread ct = al.get(i);
            if (ct.id == id) {
                disconnectedClient = ct.getUsername();
                currentForum = ct.currentForum;
                al.remove(i);
                break;
            }
        }

        if (!currentForum.isEmpty()) {
            ChatForum forum = forums.get(currentForum);
            if (forum != null) {
                forum.removeClientById(id);
                // Notify others in the forum
                broadcast(notif + disconnectedClient + " has left the forum." + notif, currentForum);
                // If no one is left in the forum and it's not the default "General" forum, delete it
                if (forum.getClients().isEmpty() && !forum.getName().equals("General")) {
                    forums.remove(currentForum);
                    broadcast(notif + "Forum '" + currentForum + "' has been deleted as no participants remain." + notif, currentForum);
                }
            }
        }

        broadcast(notif + disconnectedClient + " has left the chat server." + notif, "General");
    }

    /*
     * To run as a console application
     * > java Server
     * > java Server portNumber
     * If the port number is not specified 1500 is used
     */
    public static void main(String[] args) {
        // Start server on port 1500 unless a PortNumber is specified
        int portNumber = 1500;
        switch (args.length) {
            case 1:
                try {
                    portNumber = Integer.parseInt(args[0]);
                } catch (Exception e) {
                    System.out.println("Invalid port number.");
                    System.out.println("Usage is: > java Server [portNumber]");
                    return;
                }
            case 0:
                break;
            default:
                System.out.println("Usage is: > java Server [portNumber]");
                return;
        }
        // Create a server object and start it
        Server server = new Server(portNumber);
        server.start();
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
            clients.removeIf(client -> client.id == id);
        }

        public int getNumberOfClients() {
            return clients.size();
        }
    }

    // One instance of this thread will run for each client
    class ClientThread extends Thread {
        // The socket to get messages from client
        Socket socket;
        ObjectInputStream sInput;
        ObjectOutputStream sOutput;
        // My unique id (easier for deconnection)
        int id;
        // The Username of the Client
        String username;
        // Message object to receive message and its type
        ChatMessage cm;
        // Timestamp
        String date;
        // Current forum the client is in
        String currentForum;

        // Constructor
        ClientThread(Socket socket) {
            // A unique id
            id = ++uniqueId;
            this.socket = socket;
            currentForum = "General"; // Default forum
            // Creating both Data Stream
            display("Thread trying to create Object Input/Output Streams");
            try {
                sOutput = new ObjectOutputStream(socket.getOutputStream());
                sInput = new ObjectInputStream(socket.getInputStream());
                // Read the username
                username = (String) sInput.readObject();
                display(username + " just connected.");
                // Add client to the default forum
                ChatForum defaultForum = forums.get("General");
                if (defaultForum != null) {
                    defaultForum.addClient(this);
                    broadcast(notif + username + " has joined the 'General' forum." + notif, "General");
                }
                // Send the list of forums to the client
                sendForumList();
            } catch (IOException e) {
                display("Exception creating new Input/output Streams: " + e);
                return;
            } catch (ClassNotFoundException e) {
                // Ignore
            }
            date = new Date().toString() + "\n";
        }

        public String getUsername() {
            return username;
        }

        // Send the list of forums to the client
        private void sendForumList() {
            StringBuilder sb = new StringBuilder();
            sb.append("List of Forums:\n");
            int count = 1;
            for (Map.Entry<String, ChatForum> entry : forums.entrySet()) {
                sb.append(count).append(". ").append(entry.getKey())
                        .append(" (").append(entry.getValue().getNumberOfClients()).append(" participants)\n");
                count++;
            }
            writeMsg(sb.toString());
        }

        // Infinite loop to read and forward messages
        public void run() {
            // To loop until LOGOUT
            boolean keepGoing = true;
            while (keepGoing) {
                // Read a ChatMessage object
                try {
                    cm = (ChatMessage) sInput.readObject();
                } catch (IOException e) {
                    display(username + " Exception reading Streams: " + e);
                    break;
                } catch (ClassNotFoundException e2) {
                    break;
                }
                // Get the message from the ChatMessage object received
                String message = cm.getMessage();

                // Different actions based on type of message
                switch (cm.getType()) {

                    case ChatMessage.MESSAGE:
                        handleMessage(message);
                        break;

                    case ChatMessage.LOGOUT:
                        display(username + " disconnected with a LOGOUT message.");
                        keepGoing = false;
                        break;

                    case ChatMessage.WHOISIN:
                        handleWhoIsIn();
                        break;

                    case ChatMessage.JOIN_FORUM:
                        handleJoinForum(message);
                        break;

                    case ChatMessage.ADD_FORUM:
                        handleAddForum(message);
                        break;

                    case ChatMessage.EXIT_FORUM:
                        handleExitForum();
                        break;

                    case ChatMessage.LIST_FORUMS:
                        sendForumList();
                        break;

                    default:
                        writeMsg(notif + "Unknown message type." + notif);
                        break;
                }
            }
            // If out of the loop then disconnected and remove from client list
            remove(id);
            close();
        }

        // Handle regular and private messages
        private void handleMessage(String message) {
            if (message.startsWith("@")) {
                // Private message
                String[] parts = message.split(" ", 2);
                if (parts.length >= 2) {
                    String targetUser = parts[0].substring(1); // Remove '@'
                    String privateMsg = parts[1];
                    boolean sent = broadcast(username + " (private): " + privateMsg, targetUser, currentForum);
                    if (!sent) {
                        writeMsg(notif + "Sorry. No such user exists in the current forum." + notif);
                    }
                } else {
                    writeMsg(notif + "Incorrect private message format. Use @username your_message" + notif);
                }
            } else {
                // Broadcast message within the current forum
                broadcast(username + ": " + message, currentForum);
            }
        }

        // Handle WHOISIN command
        private void handleWhoIsIn() {
            StringBuilder sb = new StringBuilder();
            sb.append("List of the users in '").append(currentForum).append("' forum:\n");
            ChatForum forum = forums.get(currentForum);
            if (forum != null) {
                int count = 1;
                for (ClientThread ct : forum.getClients()) {
                    sb.append(count).append(". ").append(ct.username).append(" since ").append(ct.date);
                    sb.append("\n");
                    count++;
                }
            } else {
                sb.append("No users found.\n");
            }
            writeMsg(sb.toString());
        }

        // Handle JOIN_FORUM command
        private void handleJoinForum(String forumName) {
            forumName = forumName.trim();
            if (!forums.containsKey(forumName)) {
                writeMsg(notif + "Forum '" + forumName + "' does not exist." + notif);
                return;
            }

            if (currentForum.equals(forumName)) {
                writeMsg(notif + "You are already in the '" + forumName + "' forum." + notif);
                return;
            }

            // Remove from current forum
            ChatForum oldForum = forums.get(currentForum);
            if (oldForum != null) {
                oldForum.removeClient(this);
                broadcast(notif + username + " has left the forum." + notif, currentForum);
                // If no one is left in the old forum and it's not the default "General" forum, delete it
                if (oldForum.getClients().isEmpty() && !oldForum.getName().equals("General")) {
                    forums.remove(currentForum);
                    broadcast(notif + "Forum '" + currentForum + "' has been deleted as no participants remain." + notif, "General");
                }
            }

            // Add to new forum
            ChatForum newForum = forums.get(forumName);
            if (newForum != null) {
                newForum.addClient(this);
                currentForum = forumName;
                broadcast(notif + username + " has joined the forum." + notif, currentForum);
            } else {
                writeMsg(notif + "Failed to join the forum." + notif);
            }
        }

        // Handle ADD_FORUM command
        private void handleAddForum(String forumNameInput) {
            forumNameInput = forumNameInput.trim();
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
            writeMsg(notif + "New forum '" + uniqueName + "' has been created." + notif);
            // Notify all clients about the new forum
            broadcast(notif + "A new forum '" + uniqueName + "' has been created." + notif, "General");
        }

        // Handle EXIT_FORUM command
        private void handleExitForum() {
            if (currentForum.equals("General")) {
                writeMsg(notif + "You are already in the 'General' forum." + notif);
                return;
            }

            // Remove from current forum
            ChatForum oldForum = forums.get(currentForum);
            if (oldForum != null) {
                oldForum.removeClient(this);
                broadcast(notif + username + " has left the forum." + notif, currentForum);
                // If no one is left in the old forum and it's not the default "General" forum, delete it
                if (oldForum.getClients().isEmpty() && !oldForum.getName().equals("General")) {
                    forums.remove(currentForum);
                    broadcast(notif + "Forum '" + currentForum + "' has been deleted as no participants remain." + notif, "General");
                }
            }

            // Add to General forum
            ChatForum generalForum = forums.get("General");
            if (generalForum != null) {
                generalForum.addClient(this);
                currentForum = "General";
                broadcast(notif + username + " has joined the 'General' forum." + notif, currentForum);
            } else {
                writeMsg(notif + "Failed to join the 'General' forum." + notif);
            }
        }

        // Close all connections
        private void close() {
            try {
                if (sOutput != null)
                    sOutput.close();
            } catch (Exception e) {
            }
            try {
                if (sInput != null)
                    sInput.close();
            } catch (Exception e) {
            };
            try {
                if (socket != null)
                    socket.close();
            } catch (Exception e) {
            }
        }

        // Write a message to the Client output stream
        private boolean writeMsg(String msg) {
            // If Client is still connected send the message to it
            if (!socket.isConnected()) {
                close();
                return false;
            }
            // Write the message to the stream
            try {
                sOutput.writeObject(msg);
            }
            // If an error occurs, do not abort just inform the user
            catch (IOException e) {
                display(notif + "Error sending message to " + username + notif);
                display(e.toString());
            }
            return true;
        }
    }
}

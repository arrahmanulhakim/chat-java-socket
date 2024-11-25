package com.malescoding.java;

import java.net.*;
import java.io.*;
import java.util.*;

/**
 * The Client that can be run as a console with enhanced features for managing forums.
 */
public class Client {

    // Notification prefix
    private static String notif = " *** ";

    // For I/O
    private ObjectInputStream sInput;        // To read from the socket
    private static ObjectOutputStream sOutput;      // To write on the socket
    private Socket socket;                    // Socket object

    private String server, username;          // Server and username
    private int port;                         // Port

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /*
     * Constructor to set server address, port, and username
     */
    Client(String server, int port, String username) {
        this.server = server;
        this.port = port;
        this.username = username;
    }

    /*
     * To start the chat client
     */
    public boolean start() {
        // Try to connect to the server
        try {
            socket = new Socket(server, port);
        }
        // Exception handler if it failed
        catch (Exception ec) {
            display("Error connecting to server: " + ec);
            return false;
        }

        String msg = "Connection accepted " + socket.getInetAddress() + ":" + socket.getPort();
        display(msg);

        /* Creating both Data Streams */
        try {
            sInput = new ObjectInputStream(socket.getInputStream());
            sOutput = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException eIO) {
            display("Exception creating new Input/output Streams: " + eIO);
            return false;
        }

        // Create the Thread to listen from the server
        new ListenFromServer().start();
        // Send our username to the server. This is the only message that we
        // will send as a String. All other messages will be ChatMessage objects
        try {
            sOutput.writeObject(username);
        } catch (IOException eIO) {
            display("Exception doing login: " + eIO);
            disconnect();
            return false;
        }
        // Success, inform the caller that it worked
        return true;
    }

    /*
     * To send a message to the console
     */
    private static void display(String msg) {
        System.out.println(msg);
    }

    /*
     * To send a message to the server
     */
    static void sendMessage(ChatMessage msg) {
        try {
            sOutput.writeObject(msg);
        } catch (IOException e) {
            display("Exception writing to server: " + e);
        }
    }

    /*
     * When something goes wrong, close the Input/Output streams and disconnect
     */
    private void disconnect() {
        try {
            if (sInput != null)
                sInput.close();
        } catch (Exception e) {
        }
        try {
            if (sOutput != null)
                sOutput.close();
        } catch (Exception e) {
        }
        try {
            if (socket != null)
                socket.close();
        } catch (Exception e) {
        }

    }

    /*
     * To start the Client in console mode using command-line arguments
     */
    public static void main(String[] args) {
        // Default values if not entered
        int portNumber = 1500;
        String serverAddress = "localhost";
        String userName = "Anonymous";
        Scanner scan = new Scanner(System.in);

        System.out.println("Enter the username: ");
        userName = scan.nextLine();

        // Different case according to the length of the arguments
        switch (args.length) {
            case 3:
                // For > java Client username portNumber serverAddr
                serverAddress = args[2];
            case 2:
                // For > java Client username portNumber
                try {
                    portNumber = Integer.parseInt(args[1]);
                } catch (Exception e) {
                    System.out.println("Invalid port number.");
                    System.out.println("Usage is: > java Client [username] [portNumber] [serverAddress]");
                    return;
                }
            case 1:
                // For > java Client username
                userName = args[0];
            case 0:
                // For > java Client
                break;
            // If number of arguments are invalid
            default:
                System.out.println("Usage is: > java Client [username] [portNumber] [serverAddress]");
                return;
        }
        // Create the Client object
        Client client = new Client(serverAddress, portNumber, userName);
        // Try to connect to the server and return if not connected
        if (!client.start())
            return;

        System.out.println("\nHello, " + userName + "! Welcome to the chatroom.");
        System.out.println("Instructions:");
        System.out.println("1. To send a broadcast message, simply type your message and press Enter.");
        System.out.println("2. To send a private message, type '@username your_message' and press Enter.");
        System.out.println("3. To see the list of active users in the current forum, type 'WHOISIN' and press Enter.");
        System.out.println("4. To log off from the server, type 'LOGOUT' and press Enter.");
        System.out.println("5. To join a forum, type 'JOIN_[forum_name]' and press Enter.");
        System.out.println("6. To add a new forum, type 'ADD_[forum_name]' and press Enter.");
        System.out.println("7. To exit the current forum and return to 'General', type 'EXIT' and press Enter.");

        // Infinite loop to get the input from the user
        while (true) {
            System.out.print("> ");
            // Read message from user
            String msg = scan.nextLine();
            // Parse commands
            if (msg.equalsIgnoreCase("LOGOUT")) {
                sendMessage(new ChatMessage(ChatMessage.LOGOUT, ""));
                break;
            } else if (msg.equalsIgnoreCase("WHOISIN")) {
                sendMessage(new ChatMessage(ChatMessage.WHOISIN, ""));
            } else if (msg.startsWith("JOIN_")) {
                String forumName = msg.substring(5).trim();
                if (!forumName.isEmpty()) {
                    sendMessage(new ChatMessage(ChatMessage.JOIN_FORUM, forumName));
                } else {
                    System.out.println(notif + "Please specify a forum name to join. Usage: JOIN_[forum_name]" + notif);
                }
            } else if (msg.startsWith("ADD_")) {
                String forumName = msg.substring(4).trim();
                if (!forumName.isEmpty()) {
                    sendMessage(new ChatMessage(ChatMessage.ADD_FORUM, forumName));
                } else {
                    System.out.println(notif + "Please specify a forum name to add. Usage: ADD_[forum_name]" + notif);
                }
            } else if (msg.equalsIgnoreCase("EXIT")) {
                sendMessage(new ChatMessage(ChatMessage.EXIT_FORUM, ""));
            } else if (msg.equalsIgnoreCase("LIST_FORUMS")) {
                sendMessage(new ChatMessage(ChatMessage.LIST_FORUMS, ""));
            } else {
                // Regular text message
                sendMessage(new ChatMessage(ChatMessage.MESSAGE, msg));
            }
        }
        // Close resource
        scan.close();
        // Client completed its job. Disconnect client.
        client.disconnect();
    }

    /*
     * A class that waits for the message from the server
     */
    class ListenFromServer extends Thread {

        public void run() {
            while (true) {
                try {
                    // Read the message from the input data stream
                    String msg = (String) sInput.readObject();
                    // Print the message
                    System.out.println("\n" + msg);
                    System.out.print("> ");
                } catch (IOException e) {
                    display(notif + "Server has closed the connection: " + e + notif);
                    break;
                } catch (ClassNotFoundException e2) {
                    // Ignore
                }
            }
        }
    }
}

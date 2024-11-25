package com.malescoding.java;


import java.io.*;
/**
 * This class defines the different types of messages that will be exchanged between the
 * Clients and the Server. It now includes additional types for forum operations.
 */
public class ChatMessage implements Serializable {

    // The different types of messages sent by the Client
    static final int WHOISIN = 0;
    static final int MESSAGE = 1;
    static final int LOGOUT = 2;
    static final int JOIN_FORUM = 3;
    static final int ADD_FORUM = 4;
    static final int EXIT_FORUM = 5;
    static final int LIST_FORUMS = 6;

    private int type;
    private String message;

    // Constructor
    public ChatMessage(int type, String message) {
        this.type = type;
        this.message = message;
    }

    // Getters
    public int getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }
}

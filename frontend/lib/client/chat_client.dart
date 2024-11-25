import 'dart:io';
import 'dart:convert';

class ChatClient {
  Socket? _socket;
  final String serverAddress;
  final int port;
  final String username;
  Function(String message)? onMessageReceived;

  ChatClient({
    required this.serverAddress,
    required this.port,
    required this.username,
    this.onMessageReceived,
  });

  // Connect to the server
  Future<void> connect() async {
    try {
      _socket = await Socket.connect(serverAddress, port);
      _sendUsername();

      _socket!.listen(
        _handleMessage,
        onError: (error) {
          print("Error occurred: $error");
        },
        onDone: () {
          print("Server has closed the connection.");
        },
      );
    } catch (e) {
      print("Error: Could not connect to server - $e");
    }
  }

  // Send username to the server (similar to logging in)
  void _sendUsername() {
    if (_socket != null) {
      _sendData(username);
    } else {
      print("Error: Socket not connected");
    }
  }

  // Send data to server
  void _sendData(String message) {
    if (_socket != null) {
      _socket!.write(json.encode({"type": "MESSAGE", "message": message}));
    } else {
      print("Error: Socket not connected");
    }
  }

  // Send a normal chat message to the current forum
  void sendMessage(String message) {
    _sendData(message);
  }

  // Join a forum
  void joinForum(String forumName) {
    _sendData(json.encode({"type": "JOIN_FORUM", "forum": forumName}));
  }

  // Add a forum
  void addForum(String forumName) {
    _sendData(json.encode({"type": "ADD_FORUM", "forum": forumName}));
  }

  // List forums
  void listForums() {
    _sendData(json.encode({"type": "LIST_FORUMS"}));
  }

  // Logout
  void logout() {
    _sendData(json.encode({"type": "LOGOUT"}));
    disconnect();
  }

  // Handle incoming messages from the server
  void _handleMessage(List<int> data) {
    final message = utf8.decode(data);
    print("Message received: $message");
    if (onMessageReceived != null) {
      onMessageReceived!(message);
    }
  }

  // Disconnect client
  void disconnect() {
    _socket?.close();
  }
}


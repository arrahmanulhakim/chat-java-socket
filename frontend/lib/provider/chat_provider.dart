import 'dart:io';
import 'dart:convert';
import 'package:flutter/foundation.dart';

class ChatProvider with ChangeNotifier {
  Socket? _socket;
  String username = '';
  bool isConnected = false;

  final List<String> _messages = [];

  List<String> get messages => _messages;

  // Connect to the server
  Future<void> connectToServer(String serverAddress, int port, String username) async {
    try {
      this.username = username;
      _socket = await Socket.connect(serverAddress, port);
      isConnected = true;

      // Send initial username to the server
      _sendToServer(username);

      // Listen to incoming messages from the server
      _socket!.listen((List<int> event) {
        String message = utf8.decode(event);
        _messages.add(message);
        notifyListeners();
      }, onError: (error) {
        print("Error occurred: $error");
        isConnected = false;
        notifyListeners();
      }, onDone: () {
        print("Server has closed the connection.");
        isConnected = false;
        notifyListeners();
      });
    } catch (e) {
      print("Error: Could not connect to server - $e");
    }
  }

  // Disconnect from server
  void disconnect() {
    if (_socket != null) {
      _socket!.destroy();
      isConnected = false;
      notifyListeners();
    }
  }

  // Send message to the server
  void sendMessage(String message) {
    if (_socket != null && isConnected) {
      String formattedMessage = jsonEncode({"type": "MESSAGE", "message": message});
      _sendToServer(formattedMessage);
    }
  }

  // Handle sending data to server
  void _sendToServer(String message) {
    if (_socket != null && isConnected) {
      _socket!.write(message);
    }
  }
}

import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:web_socket_channel/io.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

void main() {
  runApp(ChatApp());
}

class ChatApp extends StatelessWidget {
  // Replace with your server's IP and port
  final String serverUrl =
      'ws://192.168.1.4:1500'; // e.g., 'ws://192.168.1.100:1500'

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Chat',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: LoginPage(serverUrl: serverUrl),
    );
  }
}

class LoginPage extends StatefulWidget {
  final String serverUrl;

  LoginPage({required this.serverUrl});

  @override
  _LoginPageState createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController _usernameController = TextEditingController();
  bool _isConnecting = false;
  String? _error;

  void _connect() {
    String username = _usernameController.text.trim();
    if (username.isEmpty) {
      setState(() {
        _error = "Username cannot be empty.";
      });
      return;
    }

    setState(() {
      _isConnecting = true;
      _error = null;
    });

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => ChatPage(
          serverUrl: widget.serverUrl,
          username: username,
        ),
      ),
    ).then((_) {
      // Reset connection state when returning to login
      setState(() {
        _isConnecting = false;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
          title: Text('Flutter Chat Login'),
        ),
        body: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: [
              Text("Enter your username to join the chat"),
              SizedBox(height: 20),
              TextField(
                controller: _usernameController,
                decoration: InputDecoration(
                  labelText: 'Username',
                  errorText: _error,
                  border: OutlineInputBorder(),
                ),
                onSubmitted: (_) => _connect(),
              ),
              SizedBox(height: 20),
              _isConnecting
                  ? CircularProgressIndicator()
                  : ElevatedButton(
                      onPressed: _connect,
                      child: Text('Connect'),
                    ),
            ],
          ),
        ));
  }
}

class ChatPage extends StatefulWidget {
  final String serverUrl;
  final String username;

  ChatPage({required this.serverUrl, required this.username});

  @override
  _ChatPageState createState() => _ChatPageState();
}

class _ChatPageState extends State<ChatPage> {
  late WebSocketChannel _channel;
  final TextEditingController _messageController = TextEditingController();
  final List<String> _messages = [];
  String _currentForum = "General";
  bool _isConnected = false;

  @override
  void initState() {
    super.initState();
    _connectWebSocket();
  }

  void _connectWebSocket() {
    try {
      _channel = IOWebSocketChannel.connect(widget.serverUrl);
      _isConnected = true;

      // Send the username as the first message
      _channel.sink.add(widget.username);

      // Listen for incoming messages
      _channel.stream.listen((message) {
        _handleIncomingMessage(message);
      }, onError: (error) {
        setState(() {
          _messages.add("Connection error: $error");
        });
      }, onDone: () {
        setState(() {
          _isConnected = false;
          _messages.add("Disconnected from server.");
        });
      });
    } catch (e) {
      setState(() {
        _isConnected = false;
        _messages.add("Could not connect to server: $e");
      });
    }
  }

  void _handleIncomingMessage(String message) {
    // Parse the JSON message
    try {
      Map<String, dynamic> json = jsonDecode(message);
      String type = json['type'];
      String content = json['content'];

      switch (type) {
        case 'MESSAGE':
          setState(() {
            _messages.add(content);
          });
          break;
        case 'NOTIFICATION':
          setState(() {
            _messages.add(content);
          });
          break;
        case 'WHOISIN':
          setState(() {
            _messages.add(content);
          });
          break;
        case 'LIST_FORUMS':
          setState(() {
            _messages.add(content);
          });
          break;
        default:
          setState(() {
            _messages.add("Unknown message type: $type");
          });
          break;
      }
    } catch (e) {
      setState(() {
        _messages.add("Error parsing message: $e");
      });
    }
  }

  void _sendMessage() {
    String text = _messageController.text.trim();
    if (text.isEmpty) return;

    if (!_isConnected) {
      setState(() {
        _messages.add("Not connected to server.");
      });
      return;
    }

    // Determine the message type
    String type = "MESSAGE";
    String content = text;

    if (text.startsWith("@")) {
      // Private message
      type = "MESSAGE";
    } else if (text.startsWith("LOGOUT")) {
      type = "LOGOUT";
      content = "";
    } else if (text.startsWith("WHOISIN")) {
      type = "WHOISIN";
      content = "";
    } else if (text.startsWith("JOIN_")) {
      type = "JOIN_FORUM";
      content = text.substring(5).trim();
    } else if (text.startsWith("ADD_")) {
      type = "ADD_FORUM";
      content = text.substring(4).trim();
    } else if (text.startsWith("EXIT")) {
      type = "EXIT_FORUM";
      content = "";
    } else if (text.startsWith("LIST_FORUMS")) {
      type = "LIST_FORUMS";
      content = "";
    }

    // Create JSON message
    Map<String, dynamic> message = {
      'type': type,
      'content': content,
    };

    // Send the message
    _channel.sink.add(jsonEncode(message));

    // Optionally, display the message locally
    if (type == "MESSAGE") {
      setState(() {
        _messages.add("Me: $text");
      });
    }

    _messageController.clear();
  }

  void _disconnect() {
    if (_isConnected) {
      // Send LOGOUT message
      Map<String, dynamic> message = {
        'type': 'LOGOUT',
        'content': '',
      };
      _channel.sink.add(jsonEncode(message));
      _channel.sink.close();
    }
    Navigator.pop(context);
  }

  void _showForumsDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        String newForumName = "";
        return AlertDialog(
          title: Text("Add New Forum"),
          content: TextField(
            onChanged: (value) {
              newForumName = value;
            },
            decoration: InputDecoration(hintText: "Forum Name"),
          ),
          actions: [
            TextButton(
              child: Text("Cancel"),
              onPressed: () {
                Navigator.of(context).pop();
              },
            ),
            ElevatedButton(
              child: Text("Add"),
              onPressed: () {
                if (newForumName.trim().isNotEmpty) {
                  Map<String, dynamic> message = {
                    'type': 'ADD_FORUM',
                    'content': newForumName.trim(),
                  };
                  _channel.sink.add(jsonEncode(message));
                }
                Navigator.of(context).pop();
              },
            ),
          ],
        );
      },
    );
  }

  void _showJoinForumDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        String forumName = "";
        return AlertDialog(
          title: Text("Join Forum"),
          content: TextField(
            onChanged: (value) {
              forumName = value;
            },
            decoration: InputDecoration(hintText: "Forum Name"),
          ),
          actions: [
            TextButton(
              child: Text("Cancel"),
              onPressed: () {
                Navigator.of(context).pop();
              },
            ),
            ElevatedButton(
              child: Text("Join"),
              onPressed: () {
                if (forumName.trim().isNotEmpty) {
                  Map<String, dynamic> message = {
                    'type': 'JOIN_FORUM',
                    'content': forumName.trim(),
                  };
                  _channel.sink.add(jsonEncode(message));
                  setState(() {
                    _currentForum = forumName.trim();
                  });
                }
                Navigator.of(context).pop();
              },
            ),
          ],
        );
      },
    );
  }

  void _exitForum() {
    Map<String, dynamic> message = {
      'type': 'EXIT_FORUM',
      'content': '',
    };
    _channel.sink.add(jsonEncode(message));
    setState(() {
      _currentForum = "General";
    });
  }

  @override
  void dispose() {
    if (_isConnected) {
      _disconnect();
    }
    _messageController.dispose();
    super.dispose();
  }

  Widget _buildMessageList() {
    return ListView.builder(
      reverse: false,
      padding: EdgeInsets.all(10.0),
      itemCount: _messages.length,
      itemBuilder: (context, index) {
        return Padding(
          padding: EdgeInsets.symmetric(vertical: 2.0),
          child: Text(
            _messages[index],
            style: TextStyle(fontSize: 16.0),
          ),
        );
      },
    );
  }

  Widget _buildInputArea() {
    return Container(
      padding: EdgeInsets.symmetric(horizontal: 8.0),
      color: Colors.grey[200],
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _messageController,
              decoration:
                  InputDecoration.collapsed(hintText: "Enter your message"),
              onSubmitted: (value) => _sendMessage(),
            ),
          ),
          IconButton(
            icon: Icon(Icons.send),
            onPressed: _sendMessage,
          ),
        ],
      ),
    );
  }

  Widget _buildForumInfo() {
    return Container(
      padding: EdgeInsets.all(8.0),
      color: Colors.blue[50],
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            "Current Forum: $_currentForum",
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          PopupMenuButton<String>(
            onSelected: (String choice) {
              switch (choice) {
                case 'Add Forum':
                  _showForumsDialog();
                  break;
                case 'Join Forum':
                  _showJoinForumDialog();
                  break;
                case 'Exit Forum':
                  _exitForum();
                  break;
                case 'Logout':
                  _disconnect();
                  break;
                default:
                  break;
              }
            },
            itemBuilder: (BuildContext context) {
              return {'Add Forum', 'Join Forum', 'Exit Forum', 'Logout'}
                  .map((String choice) {
                return PopupMenuItem<String>(
                  value: choice,
                  child: Text(choice),
                );
              }).toList();
            },
            icon: Icon(Icons.more_vert),
          ),
        ],
      ),
    );
  }

  Widget _buildBody() {
    return Column(
      children: [
        _buildForumInfo(),
        Divider(height: 1.0),
        Expanded(child: _buildMessageList()),
        Divider(height: 1.0),
        _buildInputArea(),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
          title: Text('Flutter Chat'),
          actions: [
            IconButton(
              icon: Icon(Icons.logout),
              onPressed: _disconnect,
            ),
          ],
        ),
        body: _buildBody());
  }
}

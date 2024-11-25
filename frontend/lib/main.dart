import 'package:chat_client_app/provider/chat_provider.dart';
import 'package:chat_client_app/ui/chat_screen.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ChatProvider()),
      ],
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'Chat Client',
        home: LoginScreen(),
      ),
    );
  }
}

class LoginScreen extends StatelessWidget {
  final _usernameController = TextEditingController();
  final _serverController = TextEditingController();
  final _portController = TextEditingController();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Login to Chat Server'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            TextField(
              controller: _usernameController,
              decoration: InputDecoration(labelText: 'Username'),
            ),
            TextField(
              controller: _serverController,
              decoration: InputDecoration(labelText: 'Server Address'),
            ),
            TextField(
              controller: _portController,
              decoration: InputDecoration(labelText: 'Port'),
              keyboardType: TextInputType.number,
            ),
            SizedBox(height: 20),
            ElevatedButton(
              child: Text('Connect'),
              onPressed: () {
                String username = _usernameController.text.trim();
                String server = _serverController.text.trim();
                int port = int.tryParse(_portController.text.trim()) ?? 1500;

                if (username.isNotEmpty && server.isNotEmpty) {
                  Provider.of<ChatProvider>(context, listen: false)
                      .connectToServer(server, port, username);
                  Navigator.of(context).push(
                      MaterialPageRoute(builder: (context) => ChatScreen()));
                }
              },
            )
          ],
        ),
      ),
    );
  }
}

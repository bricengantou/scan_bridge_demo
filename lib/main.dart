import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const MyApp());

const _method = MethodChannel('com.linnovlab/scan_bridge');
const _events = EventChannel('com.linnovlab/scan_bridge/stream');

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Scan Bridge Demo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFF2563EB),
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  StreamSubscription? _sub;
  String _lastCode = '';
  final _search = TextEditingController();

  @override
  void initState() {
    super.initState();
    _bindStream();
    _readLastScanFromPrefs(); // au cas où un scan arrive quand l’app n’était pas au premier plan
  }

  void _bindStream() {
    _sub = _events.receiveBroadcastStream().listen((data) {
      if (data is Map) {
        final code = (data['code'] ?? '') as String;
        setState(() {
          _lastCode = code;
          _search.text = code; // injection pour ta recherche
        });
      }
    });
  }

  Future<void> _readLastScanFromPrefs() async {
    try {
      final code = await _method.invokeMethod<String>('readLastScan');
      if (code != null && code.isNotEmpty) {
        setState(() {
          _lastCode = code;
          _search.text = code;
        });
      }
    } catch (_) {}
  }

  @override
  void dispose() {
    _sub?.cancel();
    _search.dispose();
    super.dispose();
  }

  Future<void> _startScan() async {
    try {
      // 1) tente soft-trigger Zebra DataWedge
      final ok = await _method.invokeMethod<bool>('softScanTrigger') ?? false;
      if (!ok) {
        // 2) sinon essaie d’ouvrir l’app de scan (liste de packages connus + fallback)
        await _method.invokeMethod('openScannerApp');
      }
    } on PlatformException catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Erreur: ${e.code} ${e.message}')),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  void _searchNow() {
    final q = _search.text.trim();
    // TODO: lance ta recherche produit par EAN `q`
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text('Recherche: $q')));
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Scan Bridge'),
        centerTitle: true,
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 20, 16, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                controller: _search,
                decoration: InputDecoration(
                  labelText: 'EAN / Code-barres',
                  border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12)),
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.search),
                    onPressed: _searchNow,
                    tooltip: 'Rechercher',
                  ),
                ),
                onSubmitted: (_) => _searchNow(),
              ),
              const SizedBox(height: 16),
              Card(
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16)),
                child: ListTile(
                  leading: CircleAvatar(
                    backgroundColor: cs.primaryContainer,
                    child: Icon(Icons.confirmation_number, color: cs.primary),
                  ),
                  title: const Text('Dernier scan'),
                  subtitle: Text(_lastCode.isEmpty ? '—' : _lastCode,
                      style: const TextStyle(fontFamily: 'RobotoMono')),
                  trailing: IconButton(
                    icon: const Icon(Icons.copy),
                    onPressed: _lastCode.isEmpty
                        ? null
                        : () =>
                            Clipboard.setData(ClipboardData(text: _lastCode)),
                  ),
                ),
              ),
              const Spacer(),
              Center(
                child: GestureDetector(
                  onTap: _startScan,
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    width: 120,
                    height: 120,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: RadialGradient(
                        colors: [cs.primaryContainer, cs.primary],
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: cs.primary.withOpacity(0.28),
                          blurRadius: 20,
                          spreadRadius: 4,
                        )
                      ],
                    ),
                    child: Icon(Icons.qr_code_scanner_rounded,
                        size: 44, color: cs.onPrimary),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Text(
                'Appuie sur “Scan” :\n• Zebra → soft trigger\n• Sinon → ouvre l’app de scan\nLe résultat arrive en broadcast.',
                textAlign: TextAlign.center,
                style: TextStyle(color: cs.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

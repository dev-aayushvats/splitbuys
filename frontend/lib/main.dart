import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_web_plugins/url_strategy.dart';
import 'package:provider/provider.dart';

import 'api.dart';
import 'auth_state.dart';
import 'router.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  usePathUrlStrategy();
  SystemChrome.setSystemUIOverlayStyle(
    SystemUiOverlayStyle.light.copyWith(statusBarColor: Colors.transparent, systemNavigationBarColor: AppColors.bg),
  );
  final api = ApiClient();
  final auth = AuthState(api);
  await auth.restore();
  runApp(SplitBuysApp(api: api, auth: auth));
}

class SplitBuysApp extends StatefulWidget {
  final ApiClient api;
  final AuthState auth;

  const SplitBuysApp({super.key, required this.api, required this.auth});

  @override
  State<SplitBuysApp> createState() => _SplitBuysAppState();
}

class _SplitBuysAppState extends State<SplitBuysApp> {
  late final _router = buildRouter(widget.auth);

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider.value(value: widget.api),
        ChangeNotifierProvider.value(value: widget.auth),
      ],
      child: MaterialApp.router(
        title: 'SplitBuys',
        theme: buildTheme(),
        routerConfig: _router,
        // The app is phone-shaped everywhere: on wide screens (web) it stays a centred phone-width column.
        builder: (context, child) => ColoredBox(
          color: AppColors.outer,
          child: Center(
            child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 480), child: child),
          ),
        ),
      ),
    );
  }
}

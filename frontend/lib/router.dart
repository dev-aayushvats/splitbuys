import 'package:go_router/go_router.dart';

import 'auth_state.dart';
import 'screens/group_screen.dart';
import 'screens/groups_screen.dart';
import 'screens/join_screen.dart';
import 'screens/login_screen.dart';
import 'screens/name_screen.dart';

GoRouter buildRouter(AuthState auth) {
  return GoRouter(
    refreshListenable: auth,
    redirect: (context, state) {
      final path = state.uri.path;
      final next = state.uri.queryParameters['next'];
      String withNext(String page) =>
          state.uri.toString() == '/' ? page : '$page?next=${Uri.encodeComponent(state.uri.toString())}';

      if (!auth.loggedIn) return path == '/login' ? null : withNext('/login');
      if (!auth.hasName) return path == '/name' ? null : withNext('/name');
      if (path == '/login' || path == '/name') return next ?? '/';
      return null;
    },
    routes: [
      GoRoute(path: '/', builder: (_, _) => const GroupsScreen()),
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      GoRoute(path: '/name', builder: (_, _) => const NameScreen()),
      GoRoute(path: '/join/:token', builder: (_, s) => JoinScreen(token: s.pathParameters['token']!)),
      GoRoute(path: '/groups/:id', builder: (_, s) => GroupScreen(groupId: int.parse(s.pathParameters['id']!))),
    ],
  );
}

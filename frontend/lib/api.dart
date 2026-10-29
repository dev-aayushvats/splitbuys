import 'dart:convert';

import 'package:http/http.dart' as http;

import 'config.dart';
import 'models.dart';

class ApiException implements Exception {
  final String message;
  final int? status;

  ApiException(this.message, [this.status]);

  @override
  String toString() => message;
}

class ApiClient {
  String? token;

  /// Called when the server rejects the token, so the app can sign the user out.
  void Function()? onUnauthorized;

  Future<dynamic> _send(String method, String path, [Object? body]) async {
    final request = http.Request(method, Uri.parse('$apiBaseUrl/api$path'));
    request.headers['Content-Type'] = 'application/json';
    if (token != null) request.headers['Authorization'] = 'Bearer $token';
    if (body != null) request.body = jsonEncode(body);

    http.Response response;
    try {
      response = await http.Response.fromStream(await request.send());
    } catch (_) {
      throw ApiException('Cannot reach the server. Check your connection.');
    }

    final decoded = response.body.isEmpty ? null : jsonDecode(response.body);
    if (response.statusCode >= 200 && response.statusCode < 300) return decoded;

    if (response.statusCode == 401 && token != null) onUnauthorized?.call();
    final message = decoded is Map && decoded['error'] is String ? decoded['error'] as String : 'Something went wrong';
    throw ApiException(message, response.statusCode);
  }

  // auth
  Future<void> requestOtp(String email) => _send('POST', '/auth/request-otp', {'email': email});

  Future<({String token, User user})> verifyOtp(String email, String code) async {
    final j = await _send('POST', '/auth/verify-otp', {'email': email, 'code': code}) as Map<String, dynamic>;
    return (token: j['token'] as String, user: User.fromJson(j['user'] as Map<String, dynamic>));
  }

  Future<User> me() async => User.fromJson(await _send('GET', '/me') as Map<String, dynamic>);

  Future<User> setName(String name) async =>
      User.fromJson(await _send('PUT', '/me', {'name': name}) as Map<String, dynamic>);

  // groups
  Future<List<GroupSummary>> groups() async =>
      (await _send('GET', '/groups') as List).map((g) => GroupSummary.fromJson(g as Map<String, dynamic>)).toList();

  Future<GroupDetail> createGroup(String name) async =>
      GroupDetail.fromJson(await _send('POST', '/groups', {'name': name}) as Map<String, dynamic>);

  Future<GroupDetail> group(int id) async =>
      GroupDetail.fromJson(await _send('GET', '/groups/$id') as Map<String, dynamic>);

  Future<GroupDetail> regenerateInvite(int id) async =>
      GroupDetail.fromJson(await _send('POST', '/groups/$id/invite/regenerate') as Map<String, dynamic>);

  Future<void> leaveGroup(int id) => _send('DELETE', '/groups/$id/members/me');

  // invites
  Future<InvitePreview> previewInvite(String token) async =>
      InvitePreview.fromJson(await _send('GET', '/invites/$token') as Map<String, dynamic>);

  Future<GroupDetail> joinInvite(String token) async =>
      GroupDetail.fromJson(await _send('POST', '/invites/$token/join') as Map<String, dynamic>);

  // ledger
  Future<List<Expense>> expenses(int groupId) async => (await _send('GET', '/groups/$groupId/expenses') as List)
      .map((e) => Expense.fromJson(e as Map<String, dynamic>))
      .toList();

  Future<void> addExpense(int groupId, Map<String, dynamic> body) => _send('POST', '/groups/$groupId/expenses', body);

  Future<void> updateExpense(int groupId, int expenseId, Map<String, dynamic> body) =>
      _send('PUT', '/groups/$groupId/expenses/$expenseId', body);

  Future<void> deleteExpense(int groupId, int expenseId) => _send('DELETE', '/groups/$groupId/expenses/$expenseId');

  Future<List<Settlement>> settlements(int groupId) async =>
      (await _send('GET', '/groups/$groupId/settlements') as List)
          .map((s) => Settlement.fromJson(s as Map<String, dynamic>))
          .toList();

  Future<void> addSettlement(int groupId, int fromUserId, int toUserId, int amountPaise) =>
      _send('POST', '/groups/$groupId/settlements',
          {'fromUserId': fromUserId, 'toUserId': toUserId, 'amountPaise': amountPaise});

  Future<void> deleteSettlement(int groupId, int settlementId) =>
      _send('DELETE', '/groups/$groupId/settlements/$settlementId');

  Future<Balances> balances(int groupId) async =>
      Balances.fromJson(await _send('GET', '/groups/$groupId/balances') as Map<String, dynamic>);
}

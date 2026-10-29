class User {
  final int id;
  final String email;
  final String? name;

  User({required this.id, required this.email, this.name});

  factory User.fromJson(Map<String, dynamic> j) =>
      User(id: j['id'] as int, email: j['email'] as String, name: j['name'] as String?);

  String get label => (name != null && name!.isNotEmpty) ? name! : email;
}

class GroupSummary {
  final int id;
  final String name;
  final int memberCount;
  final int myBalancePaise;

  GroupSummary({required this.id, required this.name, required this.memberCount, required this.myBalancePaise});

  factory GroupSummary.fromJson(Map<String, dynamic> j) => GroupSummary(
        id: j['id'] as int,
        name: j['name'] as String,
        memberCount: j['memberCount'] as int,
        myBalancePaise: j['myBalancePaise'] as int,
      );
}

class GroupDetail {
  final int id;
  final String name;
  final String inviteToken;
  final List<User> members;

  GroupDetail({required this.id, required this.name, required this.inviteToken, required this.members});

  factory GroupDetail.fromJson(Map<String, dynamic> j) => GroupDetail(
        id: j['id'] as int,
        name: j['name'] as String,
        inviteToken: j['inviteToken'] as String,
        members: (j['members'] as List).map((m) => User.fromJson(m as Map<String, dynamic>)).toList(),
      );
}

class InvitePreview {
  final String groupName;
  final int memberCount;
  final bool alreadyMember;
  final int? groupId;

  InvitePreview({required this.groupName, required this.memberCount, required this.alreadyMember, this.groupId});

  factory InvitePreview.fromJson(Map<String, dynamic> j) => InvitePreview(
        groupName: j['groupName'] as String,
        memberCount: j['memberCount'] as int,
        alreadyMember: j['alreadyMember'] as bool,
        groupId: j['groupId'] as int?,
      );
}

class Share {
  final User user;
  final int amountPaise;

  Share({required this.user, required this.amountPaise});

  factory Share.fromJson(Map<String, dynamic> j) =>
      Share(user: User.fromJson(j['user'] as Map<String, dynamic>), amountPaise: j['amountPaise'] as int);
}

class Expense {
  final int id;
  final String description;
  final int amountPaise;
  final User payer;
  final User createdBy;
  final DateTime createdAt;
  final String splitType; // EQUAL, EXACT, PERCENT, SHARES
  final List<Share> shares;

  Expense({
    required this.id,
    required this.description,
    required this.amountPaise,
    required this.payer,
    required this.createdBy,
    required this.createdAt,
    required this.splitType,
    required this.shares,
  });

  factory Expense.fromJson(Map<String, dynamic> j) => Expense(
        id: j['id'] as int,
        description: j['description'] as String,
        amountPaise: j['amountPaise'] as int,
        payer: User.fromJson(j['payer'] as Map<String, dynamic>),
        createdBy: User.fromJson(j['createdBy'] as Map<String, dynamic>),
        createdAt: DateTime.parse(j['createdAt'] as String),
        splitType: j['splitType'] as String,
        shares: (j['shares'] as List).map((s) => Share.fromJson(s as Map<String, dynamic>)).toList(),
      );

  int shareOf(int userId) => shares.where((s) => s.user.id == userId).fold(0, (sum, s) => sum + s.amountPaise);
}

class Settlement {
  final int id;
  final User from;
  final User to;
  final int amountPaise;
  final DateTime createdAt;

  Settlement({required this.id, required this.from, required this.to, required this.amountPaise, required this.createdAt});

  factory Settlement.fromJson(Map<String, dynamic> j) => Settlement(
        id: j['id'] as int,
        from: User.fromJson(j['fromUser'] as Map<String, dynamic>),
        to: User.fromJson(j['toUser'] as Map<String, dynamic>),
        amountPaise: j['amountPaise'] as int,
        createdAt: DateTime.parse(j['createdAt'] as String),
      );
}

class Balance {
  final User user;

  /// > 0: the member is owed money; < 0: the member owes money.
  final int netPaise;

  Balance({required this.user, required this.netPaise});
}

class Transfer {
  final User from;
  final User to;
  final int amountPaise;

  Transfer({required this.from, required this.to, required this.amountPaise});
}

class Balances {
  final List<Balance> balances;
  final List<Transfer> settleUp;

  Balances({required this.balances, required this.settleUp});

  factory Balances.fromJson(Map<String, dynamic> j) => Balances(
        balances: (j['balances'] as List)
            .map((b) => Balance(
                  user: User.fromJson(b['user'] as Map<String, dynamic>),
                  netPaise: b['netPaise'] as int,
                ))
            .toList(),
        settleUp: (j['settleUp'] as List)
            .map((t) => Transfer(
                  from: User.fromJson(t['from'] as Map<String, dynamic>),
                  to: User.fromJson(t['to'] as Map<String, dynamic>),
                  amountPaise: t['amountPaise'] as int,
                ))
            .toList(),
      );
}

/// Everything the group page shows, loaded together.
class GroupData {
  final GroupDetail group;
  final List<Expense> expenses;
  final List<Settlement> settlements;
  final Balances balances;

  GroupData({required this.group, required this.expenses, required this.settlements, required this.balances});
}

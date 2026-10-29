import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'models.dart';
import 'theme.dart';

/// Rounded container with a dashed outline, used to group sections.
class DashedBorder extends StatelessWidget {
  final Widget child;
  final double radius;
  final EdgeInsetsGeometry padding;

  const DashedBorder({super.key, required this.child, this.radius = 24, this.padding = const EdgeInsets.all(16)});

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _DashedPainter(radius),
      child: Padding(padding: padding, child: child),
    );
  }
}

class _DashedPainter extends CustomPainter {
  final double radius;

  _DashedPainter(this.radius);

  @override
  void paint(Canvas canvas, Size size) {
    final path = Path()..addRRect(RRect.fromRectAndRadius(Offset.zero & size, Radius.circular(radius)).deflate(0.6));
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2
      ..color = AppColors.dash;
    for (final metric in path.computeMetrics()) {
      for (var d = 0.0; d < metric.length; d += 11) {
        canvas.drawPath(metric.extractPath(d, math.min(d + 6, metric.length)), paint);
      }
    }
  }

  @override
  bool shouldRepaint(_DashedPainter old) => old.radius != radius;
}

/// Round, bordered 44px shell. Use [CircleIconButton] for taps; use this directly as a PopupMenuButton child.
class CircleShell extends StatelessWidget {
  final Widget child;

  const CircleShell({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 44,
      height: 44,
      alignment: Alignment.center,
      decoration: const BoxDecoration(
        shape: BoxShape.circle,
        color: AppColors.surface,
        border: Border.fromBorderSide(BorderSide(color: AppColors.border)),
      ),
      child: child,
    );
  }
}

class CircleIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback? onPressed;
  final String? tooltip;

  const CircleIconButton({super.key, required this.icon, this.onPressed, this.tooltip});

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip ?? '',
      child: InkResponse(
        onTap: onPressed,
        customBorder: const CircleBorder(),
        child: CircleShell(child: Icon(icon, size: 20, color: onPressed == null ? AppColors.muted : AppColors.text)),
      ),
    );
  }
}

/// Centred title with a round back button on the left and an optional round action on the right.
class TopBar extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final bool showBack;
  final Widget? action;

  const TopBar({super.key, required this.title, this.showBack = true, this.action});

  @override
  Size get preferredSize => const Size.fromHeight(76);

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
        child: Row(
          children: [
            SizedBox(
              width: 44,
              child: showBack
                  ? CircleIconButton(
                      icon: Icons.arrow_back_ios_new_rounded,
                      tooltip: 'Back',
                      onPressed: () => Navigator.of(context).canPop() ? Navigator.of(context).pop() : context.go('/'),
                    )
                  : null,
            ),
            Expanded(
              child: Text(
                title,
                textAlign: TextAlign.center,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
              ),
            ),
            SizedBox(width: 44, child: action),
          ],
        ),
      ),
    );
  }
}

const _avatarPalettes = [
  [Color(0xFFB26BFF), Color(0xFFFF7EB6)],
  [Color(0xFF4FACFE), Color(0xFF00D4E6)],
  [Color(0xFFFF9A44), Color(0xFFFC6076)],
  [Color(0xFF43E97B), Color(0xFF2BD9C0)],
  [Color(0xFFFFD84D), Color(0xFFFF9F43)],
  [Color(0xFF7F7FD5), Color(0xFF86A8E7)],
];

class Avatar extends StatelessWidget {
  final User user;
  final double size;

  const Avatar(this.user, {super.key, this.size = 44});

  @override
  Widget build(BuildContext context) {
    final colors = _avatarPalettes[user.id % _avatarPalettes.length];
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: LinearGradient(colors: colors, begin: Alignment.topLeft, end: Alignment.bottomRight),
      ),
      child: Text(
        user.label.characters.first.toUpperCase(),
        style: TextStyle(color: AppColors.onAccent, fontWeight: FontWeight.w800, fontSize: size * 0.42),
      ),
    );
  }
}

/// Overlapping avatars, with "+n" when there are more than [max].
class AvatarStack extends StatelessWidget {
  final List<User> users;
  final double size;
  final int max;

  const AvatarStack(this.users, {super.key, this.size = 34, this.max = 4});

  @override
  Widget build(BuildContext context) {
    final shown = users.take(max).toList();
    final extra = users.length - shown.length;
    final step = size * 0.62;
    final count = shown.length + (extra > 0 ? 1 : 0);
    Widget ring(Widget child) => Container(
          padding: const EdgeInsets.all(2),
          decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.bg),
          child: child,
        );
    return SizedBox(
      width: size + 4 + step * (count - 1),
      height: size + 4,
      child: Stack(
        children: [
          for (var i = 0; i < shown.length; i++) Positioned(left: i * step, child: ring(Avatar(shown[i], size: size))),
          if (extra > 0)
            Positioned(
              left: shown.length * step,
              child: ring(Container(
                width: size,
                height: size,
                alignment: Alignment.center,
                decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.surfaceHigh),
                child: Text('+$extra', style: TextStyle(fontSize: size * 0.34, fontWeight: FontWeight.w700)),
              )),
            ),
        ],
      ),
    );
  }
}

/// Primary action: a yellow pill with a round arrow badge on the left.
class ConfirmButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final bool busy;

  const ConfirmButton({super.key, required this.label, this.onPressed, this.busy = false});

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null && !busy;
    return Opacity(
      opacity: onPressed == null ? 0.45 : 1,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: enabled ? onPressed : null,
          borderRadius: BorderRadius.circular(32),
          child: Ink(
            height: 60,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(32),
              gradient: const LinearGradient(colors: [AppColors.accent, AppColors.accentDeep]),
            ),
            child: Row(
              children: [
                Padding(
                  padding: const EdgeInsets.only(left: 6),
                  child: CircleAvatar(
                    radius: 24,
                    backgroundColor: AppColors.onAccent,
                    child: busy
                        ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.arrow_forward_rounded, color: AppColors.accent),
                  ),
                ),
                Expanded(
                  child: Text(
                    label,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: AppColors.onAccent, fontSize: 16, fontWeight: FontWeight.w700),
                  ),
                ),
                const SizedBox(width: 54),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Pill-shaped segmented control.
class PillTabs extends StatelessWidget {
  final List<String> labels;
  final int index;
  final ValueChanged<int> onChanged;

  const PillTabs({super.key, required this.labels, required this.index, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          for (var i = 0; i < labels.length; i++)
            Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => onChanged(i),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  padding: const EdgeInsets.symmetric(vertical: 11),
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: i == index ? AppColors.accent : Colors.transparent,
                    borderRadius: BorderRadius.circular(26),
                  ),
                  child: Text(
                    labels[i],
                    maxLines: 1,
                    style: TextStyle(
                      fontSize: 13.5,
                      fontWeight: FontWeight.w700,
                      color: i == index ? AppColors.onAccent : AppColors.muted,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// Rounded dark list row.
class AppTile extends StatelessWidget {
  final Widget leading;
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;

  const AppTile({super.key, required this.leading, required this.title, this.subtitle, this.trailing, this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(22),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            border: Border.all(color: AppColors.border.withValues(alpha: 0.6)),
          ),
          child: Row(
            children: [
              leading,
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w600)),
                    if (subtitle != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 3),
                        child: Text(subtitle!, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12.5, color: AppColors.muted)),
                      ),
                  ],
                ),
              ),
              if (trailing != null) ...[const SizedBox(width: 10), trailing!],
            ],
          ),
        ),
      ),
    );
  }
}

/// Rounded-square icon used as the leading element of tiles.
class IconBadge extends StatelessWidget {
  final IconData icon;
  final Color color;

  const IconBadge(this.icon, {super.key, this.color = AppColors.accent});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 46,
      height: 46,
      decoration: BoxDecoration(color: color.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(15)),
      child: Icon(icon, color: color, size: 22),
    );
  }
}

/// Small bordered stat card with an icon, caption and value.
class InfoCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color? valueColor;

  const InfoCard({super.key, required this.icon, required this.label, required this.value, this.valueColor});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: AppColors.dash, width: 1.5),
        gradient: const LinearGradient(
          colors: [AppColors.surfaceHigh, AppColors.surface],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: Column(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(shape: BoxShape.circle, color: AppColors.accent.withValues(alpha: 0.16)),
            child: Icon(icon, color: AppColors.accent, size: 21),
          ),
          const SizedBox(height: 8),
          Text(label, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
          const SizedBox(height: 2),
          FittedBox(
            fit: BoxFit.scaleDown,
            child: Text(value, style: TextStyle(fontSize: 19, fontWeight: FontWeight.w800, color: valueColor)),
          ),
        ],
      ),
    );
  }
}

class SectionTitle extends StatelessWidget {
  final String text;
  final Widget? trailing;

  const SectionTitle(this.text, {super.key, this.trailing});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Expanded(child: Text(text, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700))),
          ?trailing,
        ],
      ),
    );
  }
}

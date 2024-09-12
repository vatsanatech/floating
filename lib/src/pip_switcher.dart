part of '../floating.dart';

/// Widget switching utility.
///
/// Depending on current PiP status will render [childWhenEnabled]
/// or [childWhenDisabled] widget.
class PiPSwitcher extends StatefulWidget {
  /// Floating instance that the listener will connect to.
  @visibleForTesting
  final Floating? floating;

  /// Child to render when PiP is enabled
  final Widget childWhenEnabled;

  /// Child to render when PiP is disabled or unavailable.
  final Widget childWhenDisabled;

  final Function play;
  final Function pause;
  final Function seekForward;
  final Function seekBackward;

  PiPSwitcher({
    Key? key,
    required this.childWhenEnabled,
    required this.childWhenDisabled,
    this.floating,
    required this.play,
    required this.pause,
    required this.seekForward,
    required this.seekBackward,
  }) : super(key: key);

  @override
  State<PiPSwitcher> createState() => _PipAwareState();
}

class _PipAwareState extends State<PiPSwitcher> {
  late final _floating = widget.floating ?? Floating();
  static const platform = MethodChannel('floating');

  @override
  void initState() {
    super.initState();
    // Listen for method calls from Android (when PiP controls are pressed)
    platform.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onPlayPressed':
          widget.play();
          break;
        case 'onPausePressed':
          widget.pause();
          break;
        case 'onSeekForwardPressed':
          widget.seekForward();
          break;
        case 'onSeekBackwardPressed':
          widget.seekBackward();
          break;
        default:
          print('Unknown method: ${call.method}');
          break;
      }
    });
  }

  @override
  Widget build(BuildContext context) => StreamBuilder(
        stream: _floating.pipStatusStream,
        initialData: PiPStatus.disabled,
        builder: (context, snapshot) => snapshot.data == PiPStatus.enabled
            ? widget.childWhenEnabled
            : widget.childWhenDisabled,
      );
}

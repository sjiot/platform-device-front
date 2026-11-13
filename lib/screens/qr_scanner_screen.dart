import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

/// 카메라 미리보기 좌우 반전 설정. 앱을 종료하기 전까지 유지된다.
final ValueNotifier<bool> cameraMirrored = ValueNotifier<bool>(true);

class QrScannerScreen extends StatefulWidget {
  const QrScannerScreen({super.key});

  @override
  State<QrScannerScreen> createState() => _QrScannerScreenState();
}

class _QrScannerScreenState extends State<QrScannerScreen> {
  bool _isProcessing = false;

  // 전면 카메라 사용
  final MobileScannerController _scannerController = MobileScannerController(
    facing: CameraFacing.front,
  );

  void _handleBarcode(BarcodeCapture capture) {
    if (_isProcessing) return;

    final List<Barcode> barcodes = capture.barcodes;

    if (barcodes.isEmpty) return;

    final String? value = barcodes.first.rawValue;

    if (value == null || value.isEmpty) return;

    _isProcessing = true;

    Navigator.of(context).pop(value);
  }

  @override
  void dispose() {
    _scannerController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            Positioned.fill(
              child: ValueListenableBuilder<bool>(
                valueListenable: cameraMirrored,
                builder: (context, mirrored, child) {
                  return Transform.flip(flipX: mirrored, child: child);
                },
                child: MobileScanner(
                  controller: _scannerController,
                  onDetect: _handleBarcode,
                ),
              ),
            ),

            Positioned(
              top: 20,
              left: 20,
              child: FilledButton.icon(
                onPressed: () {
                  Navigator.of(context).pop();
                },
                icon: const Icon(Icons.arrow_back),
                label: const Text('돌아가기'),
              ),
            ),

            Positioned(
              top: 20,
              right: 20,
              child: ValueListenableBuilder<bool>(
                valueListenable: cameraMirrored,
                builder: (context, mirrored, _) {
                  return FilledButton.tonalIcon(
                    onPressed: () => cameraMirrored.value = !mirrored,
                    icon: const Icon(Icons.flip),
                    label: Text(mirrored ? '좌우 반전 켬' : '좌우 반전 끔'),
                  );
                },
              ),
            ),

            Center(
              child: Container(
                width: 260,
                height: 260,
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.white, width: 3),
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
            ),

            const Positioned(
              left: 0,
              right: 0,
              bottom: 40,
              child: Text(
                'QR 코드를 사각형 안에 맞춰 주세요.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

# Moonlight Android — Genshin Impact Mobile Touch Overlay (KB+M)

Chế độ overlay cảm ứng kiểu **Genshin Impact Mobile** cho Moonlight Android, chuyển đổi trực tiếp thao tác cảm ứng đa điểm trên điện thoại Android thành gói tin **Bàn phím + Chuột (KB+M) chuẩn Moonlight** gửi tới **Sunshine** trên PC Windows — chơi Genshin Impact PC hoàn chỉnh mà không cần tay cầm hay bàn phím/chuột vật lý.

> **Bản quyền & Pháp lý:** Dự án giữ nguyên giấy phép **GPL-3.0** và credit gốc của [moonlight-stream/moonlight-android](https://github.com/moonlight-stream/moonlight-android). Không chứa bất kỳ asset/hình ảnh/logo nào của HoYoverse, không sửa game, không đọc bộ nhớ, không macro/auto-play/auto-aim. Overlay chỉ chuyển tiếp thao tác người dùng theo thời gian thực.

---

## 1. Kiến trúc & Luồng dữ liệu

```text
Chạm đa điểm trên Android (5+ ngón độc lập theo pointerId)
  -> GenshinOverlayView (layer trên StreamView, chặn rò touch xuống TouchContext gốc)
  -> GenshinInputHandler (state machine đối xứng down/up, releaseAll chống kẹt phím)
  -> NvConnection -> MoonBridge.sendKeyboardInput / sendMouseMove / sendMouseButton
  -> moonlight-common-c (LiSendKeyboardEvent2 / LiSendMouseMoveEvent / LiSendMouseButtonEvent)
  -> Sunshine (không sửa) -> Windows SendInput() / libvirtualhid
  -> Genshin Impact PC nhận như bàn phím & chuột thật
```

### API đã xác minh trực tiếp trong codebase
- `app/src/main/java/com/limelight/nvstream/jni/MoonBridge.java`:
  - `sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags)` (line 386)
  - `sendMouseMove(short deltaX, short deltaY)` (line 357)
  - `sendMousePosition(short x, short y, short referenceWidth, short referenceHeight)` (line 359)
  - `sendMouseButton(byte buttonEvent, byte mouseButton)` (line 363)
  - `sendTouchEvent(...)` (line 371)
- `app/src/main/jni/moonlight-core/simplejni.c`:
  - `sendKeyboardInput` -> `LiSendKeyboardEvent2` (lines 101–103)
  - `sendMouseMove` -> `LiSendMouseMoveEvent` (lines 14–16)
  - `sendMouseButton` -> `LiSendMouseButtonEvent` (lines 31–33)
- `app/src/main/java/com/limelight/binding/input/KeyboardTranslator.java`:
  - Keycode gửi lên host dùng Win32 Virtual Key với prefix `0x80` (`(short) ((0x80 << 8) | vk)`, lines 23, 151–373).
- Sunshine (`LizardByte/Sunshine`):
  - `src/input.cpp` định tuyến gói tin keyboard/mouse chuẩn từ Moonlight.
  - `src/platform/windows/input.cpp` inject trực tiếp xuống Windows bằng Win32 `SendInput()` (`INPUT_KEYBOARD`, `INPUT_MOUSE`) hoặc `libvirtualhid` — không cần driver tay cầm ảo (ViGEm).

---

## 2. Bảng mapping mặc định (Overlay -> Phím/Chuột PC)

Tất cả nút đều hiển thị nhãn phím PC nhỏ ngay trên nút và đổi màu/thu nhỏ khi nhấn.

| Thành phần overlay | Thao tác chạm | Gửi lên PC (Genshin mặc định) |
|---|---|---|
| **Joystick trái** (nổi theo ngón tay) | Kéo 8 hướng, có deadzone (15%) | `W` / `A` / `S` / `D` (chéo = 2 phím). Nghiêng `< 50%` giữ thêm `Left Ctrl` (đi bộ); `>= 50%` chạy thường. Thả tay nhả hết WASD + Ctrl |
| **Vùng kéo camera** (nửa phải màn hình, vùng trống) | Kéo 1 ngón | `sendMouseMove` tương đối (`delta * sensitivity`, 0.5x–2.0x), không nhảy con trỏ tuyệt đối |
| **Tấn công (ATK)** | Chạm = đánh thường, Giữ = đánh tụ lực | `Left Mouse Button` (down/up) |
| **Kỹ năng nguyên tố (SKILL)** | Chạm / Giữ | `E` |
| **Nộ (BURST)** | Chạm | `Q` |
| **Nhảy (JUMP)** | Chạm | `Space` |
| **Chạy nhanh / Né (DASH)** | Giữ (mặc định) hoặc Toggle trong Settings | `Left Shift` (hoặc `Right Mouse Button` trong Settings) |
| **Nhặt / Tương tác (PICK)** | Chạm | `F` |
| **Đổi nhân vật (P1–P4)** | Chạm ngắn (`< 400ms`) | `1` / `2` / `3` / `4` |
| **Đổi NV + Nộ ngay (P1–P4)** | Giữ `>= 400ms` | `Left Alt` down -> `1..4` down -> `1..4` up -> `Left Alt` up |
| **Ngắm (AIM)** | Chạm | `R` |
| **Gadget (GDG)** | Chạm | `Z` |
| **Tương tác đặc biệt (ACT)** | Chạm | `T` |
| **Định vị nhiệm vụ (NAV)** | Chạm | `V` |
| **Elemental Sight (EYE)** | Giữ | `Middle Mouse Button` |
| **Rơi khi leo (DROP)** | Chạm | `X` |
| **Bản đồ / Túi / Nhân vật / Paimon / Nhiệm vụ / Quick Wheel** | Chạm (hàng nút phụ, thu gọn được) | `M` / `B` / `C` / `Esc` / `J` / `Tab` |

---

## 3. Cấu hình phía Host (Windows 11 + Sunshine + Genshin Impact PC)

1. **Cài Sunshine bản mới nhất** trên Windows 11 ([LizardByte/Sunshine](https://github.com/LizardByte/Sunshine)).
2. Mở Sunshine Web UI (`https://localhost:47990`) -> **Configuration > Input**:
   - **Enable Keyboard Input** (`keyboard`): `enabled`
   - **Enable Mouse Input** (`mouse`): `enabled`
   - **Always send scancodes** (`always_send_scancodes`): `enabled` (khuyến nghị trên Windows để Genshin nhận chuẩn phím QWERTY)
   - **Enable Gamepad Input**: có thể để `disabled` nếu không dùng tay cầm vật lý (tránh Genshin tự chuyển sang UI Controller).
3. **Thiết lập Genshin Impact PC**:
   - **Settings > Controls**: chọn **Keyboard** (KHÔNG chọn Controller) và giữ keybind mặc định.
   - **Settings > Graphics**: chọn chế độ **Borderless Windowed** hoặc **Fullscreen** mà Sunshine capture mượt.
   - Chạy Sunshine dưới quyền Service / Administrator (mặc định khi cài bằng installer Windows) để `SendInput()` hoạt động khi Genshin chạy với quyền elevated (UAC).
4. **Mạng**: dùng Wi-Fi 5GHz (hoặc Wi-Fi 6) cùng mạng LAN giữa laptop Windows và điện thoại Android; ưu tiên laptop cắm dây LAN để giảm jitter.

---

## 4. Cách sử dụng trên Android

1. Mở Moonlight -> biểu tượng **Settings** -> kéo xuống mục **Genshin Impact Touch Overlay (KB+M)**:
   - Bật **Enable Genshin Mode** (khi bật sẽ tự tắt VirtualController tay cầm ảo và tắt touch-chuột mặc định để tránh xung đột).
   - Tuỳ chỉnh: **Layout profile** (`Default`, `Compact`, `Tablet`, `Custom`), **Camera sensitivity** (`50%–200%`), **Button size** (`60%–150%`), **Overlay opacity** (`15%–100%`), **Toggle sprint**, **Use RMB for sprint**, **Show secondary utility bar**.
2. Vào stream Genshin PC:
   - Nút **`BAR:ON/OFF`** ở cạnh trên giữa màn hình: thu gọn / mở rộng hàng nút phụ.
   - Nút **`EDIT`** ở cạnh trên giữa màn hình: vào chế độ chỉnh layout trực tiếp (`MOVE`, `SIZE`, `OP-`, `OP+`, `RESET`, `SAVE`). Layout được lưu riêng vào `SharedPreferences("GenshinOverlayProfiles")`, không ghi đè layout tay cầm gốc (`OSC`).
3. Tắt **Enable Genshin Mode** trong Settings để quay lại 100% hành vi Moonlight gốc (touch chuột + tay cầm ảo cũ).

---

## 5. Hướng dẫn Build từ source

### Yêu cầu môi trường
- **JDK**: Eclipse Temurin JDK 21 (compile target Java 17)
- **Gradle**: Wrapper `9.7.1` (tự tải bởi `./gradlew`)
- **Android Gradle Plugin**: `9.4.0`
- **Android SDK**: `compileSdk 37` (hoặc SDK cài đặt theo `app/build.gradle`), `build-tools 36.0.0+`
- **Android NDK**: `29.0.14206865`

### Các lệnh build & test

```bash
# 1. Clone repo và khởi tạo submodule bắt buộc (moonlight-common-c)
git submodule update --init --recursive

# 2. Chạy unit test logic input (8 hướng, 100x down/up, releaseAll, Alt+1..4)
./gradlew testNonRootDebugUnitTest

# 3. Build APK debug
./gradlew assembleDebug
```

File APK đầu ra:
- `app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk` (dùng cho hầu hết điện thoại Android hiện đại)

Cài lên điện thoại Android thật qua USB debugging:

```bash
adb install -r app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk
```

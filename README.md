# FinMana

Ứng dụng Android quản lý thu chi từ thông báo biến động số dư.

## Chức năng hiện có

- Đọc notification qua `NotificationListenerService`, bỏ qua notification của chính app.
- Nhận dạng số tiền và chiều giao dịch bằng parser cục bộ, pattern đã học, sau đó mới dùng AI fallback.
- Lưu giao dịch, ghi chú, danh mục và trạng thái đồng bộ trong Room.
- Dashboard thu/chi và biểu đồ theo ngày, tuần, tháng, quý, năm.
- Đăng nhập Google, tạo thư mục `FinMana`, tạo Google Sheet và append giao dịch.
- API AI dạng OpenAI-compatible; pattern hợp lệ do AI đề xuất được lưu để tái sử dụng.

## Chạy project

Yêu cầu:

- JDK 17
- Android SDK Platform 35 và Build Tools
- Thiết bị/emulator Android 8.0 trở lên, có Google Play Services

Mở project bằng Android Studio, hoặc chạy:

```powershell
.\gradlew.bat assembleDebug
```

## Kết nối điện thoại qua ADB

1. Mở **Cài đặt → Giới thiệu điện thoại**, nhấn **Số bản dựng / Build number** 7 lần để bật chế độ nhà phát triển.
2. Mở **Tùy chọn nhà phát triển / Developer options**, bật **USB debugging / Gỡ lỗi USB**.
3. Kết nối điện thoại với máy tính bằng cáp USB hỗ trợ truyền dữ liệu, chọn chế độ USB **Truyền tệp / File Transfer**.
4. Chấp nhận hộp thoại **Allow USB debugging** trên điện thoại.
5. Kiểm tra kết nối:

```powershell
C:\Users\Hi\AppData\Local\AndroidSdkTemp\platform-tools\adb.exe devices
```

Kết quả đúng:

```
List of devices attached
ABC123456    device
```

Nếu hiện `unauthorized`, mở khóa điện thoại và chấp nhận quyền USB debugging.

Cài APK lên điện thoại:

```powershell
C:\Users\Hi\AppData\Local\AndroidSdkTemp\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

Một số hãng yêu cầu thêm:

- **Xiaomi**: bật USB debugging (Security settings) và Install via USB.
- **Samsung**: cài Samsung USB Driver nếu máy tính không nhận.
- **Oppo/Vivo/Realme**: bật thêm USB installation trong Developer options.

## Cấu hình Google

### 1. Tạo project và bật API

1. Tạo project trong [Google Cloud Console](https://console.cloud.google.com/).
2. Mở **APIs & Services → Library**, tìm và bật lần lượt:
   - **Google Drive API**
   - **Google Sheets API**

> **Lưu ý**: Cloud Storage API không phải Google Drive API. Cloud Storage dùng cho bucket của Google Cloud, còn FinMana cần Google Drive của người dùng.

> Google Drive API và Google Sheets API **không mất tiền** cho ứng dụng cá nhân hoặc lượng người dùng nhỏ. Google áp dụng giới hạn số request theo phút; nếu vượt quota, request bị từ chối với lỗi 429, Google không tự động tính phí. Chi phí có thể phát sinh từ API AI, backend riêng, hoặc phí tài khoản Google Play Developer khi phát hành app.

### 2. Cấu hình OAuth consent screen (Google Auth Platform)

Google đã đổi OAuth consent screen thành khu vực **Google Auth Platform**.

1. Mở [Google Auth Platform](https://console.cloud.google.com/auth/), chọn đúng project, nhấn **Get started** nếu lần đầu.
2. **Branding**: điền App name (`FinMana`), User support email, Developer contact information. App logo và App domain có thể bỏ qua khi thử nghiệm. Nhấn **Save**.
3. **Audience**:
   - Chọn **External** nếu dùng tài khoản Gmail cá nhân.
   - Giữ Publishing status: **Testing**.
   - Trong **Test users**, nhấn **Add users** và thêm địa chỉ Gmail sẽ đăng nhập trên điện thoại. Khi app ở chế độ Testing, chỉ tài khoản trong danh sách Test users mới đăng nhập được.
4. **Data Access → Add or remove scopes**:
   - Tìm Google Drive API, chọn: `https://www.googleapis.com/auth/drive.file`
   - Tìm Google Sheets API, chọn: `https://www.googleapis.com/auth/spreadsheets`
   - Nếu không tìm thấy scope, nhập URI đầy đủ vào ô **Manually add scopes**.
   - Nhấn **Update**, sau đó **Save**.

Ý nghĩa quyền:

| Scope | Ý nghĩa |
|---|---|
| `drive.file` | App chỉ quản lý file Drive do app tạo hoặc người dùng chia sẻ cho app |
| `spreadsheets` | Đọc và chỉnh sửa Google Sheets |

> Không chọn scope `https://www.googleapis.com/auth/drive` — scope này cho quyền truy cập toàn bộ Drive và khó được Google phê duyệt hơn.

### 3. Tạo OAuth Client ID

1. Mở **Clients → Create client**.
2. Chọn loại **Android**.
3. Điền package: `com.finmana.app`.
4. Lấy SHA-1 debug key:

```powershell
.\gradlew.bat signingReport
```

5. Điền SHA-1 được hiển thị trong mục **Variant: debug**.

> Với app chỉ dùng cá nhân hoặc test, chưa cần gửi Google xét duyệt. Tài khoản phải nằm trong Test users và có thể gặp cảnh báo app chưa được xác minh.

## Cấu hình AI

Trong tab **Cài đặt**, nhập URL đầy đủ của endpoint chat completions, API key và model. Ví dụ URL:

```text
https://api.example.com/v1/chat/completions
```

API key hiện được lưu cục bộ bằng DataStore. Với bản production, nên đưa lời gọi AI qua backend riêng hoặc mã hóa secret bằng Android Keystore; không nên phát hành APK chứa key dùng chung.

## Lưu ý

- Người dùng phải tự bật quyền đọc thông báo trong Android Settings.
- Một số app ngân hàng ẩn nội dung notification hoặc không phát notification đầy đủ; FinMana không thể đọc phần bị app nguồn che.
- Google sync hiện là thao tác thủ công. Giao dịch chưa sync được giữ lại để retry.
- Cần rà soát chính sách Google Play về quyền truy cập notification trước khi phát hành.

# Chạy server trên điện thoại (Termux)

Server là Java 21 thuần, không cần root, chạy được Android 7 đến 14. Điện thoại nên có từ 4 GB RAM và còn khoảng 3 GB bộ nhớ trống (server tải về khoảng 1 GB).

## Cài (một dòng)
1. Cài **Termux** từ F-Droid hoặc GitHub (bản trên Google Play đã cũ, không cài được gói).
2. Mở Termux, dán:
```
curl -sL https://raw.githubusercontent.com/Eng77-gif/Nro_Project/main/termux_install.sh | bash
```
Script tự: cài git, Java 21, MariaDB → tải server từ GitHub về `~/nro_server` → tạo database, nạp dữ liệu, tạo user database `nro` / `nro123`, sửa `Config.properties` → tạo lệnh `nro` → bật server và in IP để kết nối.

## Dùng hằng ngày: gõ `nro`
Menu bấm 1 phím (không cần Enter), trạng thái tự làm mới mỗi 15 giây:
```
 1 ▶ Bật server          (thanh tiến trình tới khi sẵn sàng; lỗi thì hiện cuối log)
 2 ■ Tắt server          3 ↻ Khởi động lại
 4 ≡ Xem log trực tiếp   (lỗi tô đỏ, Ctrl + C để thoát)
 5 ↓ Cập nhật từ GitHub  (tự bật lại server nếu đang chạy)
 6 ⇪ Sao lưu database    7 ⇩ Khôi phục database   (lưu ở Download/nro_backup)
 8 ⚙ Đổi RAM             (chọn mức có sẵn; nhập số không đơn vị = MB)
 9 ✎ Đổi mật khẩu panel  0 ✕ Thoát (server vẫn chạy nền)
```
Mật khẩu panel (tài khoản `admin`) tạo sẵn và hiện ngay trên menu. Gõ thẳng cũng được: `nro start`, `nro stop`, `nro restart`, `nro log`, `nro update`, `nro backup`, `nro restore`, `nro ram`, `nro pass`, `nro ip`.

## Tự chạy khi bật máy
Cài thêm app **Termux:Boot** (F-Droid), mở nó 1 lần. Script đã tự đăng ký, bật máy là server tự chạy.

## Lưu ý
- Trong cài đặt Android, tắt **tối ưu pin** cho Termux, nếu không máy có thể tự tắt server khi để lâu.
- Chơi ngay trên điện thoại chạy server: dùng client bản `127.0.0.1:14445` (APK sửa IP bằng `tools/client-db249/mod_apk.py ... 127.0.0.1 14445 ...`). Termux tự đặt `server.ip=127.0.0.1`.
- Cùng wifi: client vào IP điện thoại (`nro ip`), cổng 14445; panel `http://IP:8088`.
- Ngoài mạng: điện thoại thường không có IP công khai, cần mở cổng trên router hoặc dùng Cloudflare Tunnel.
- Không có mạng / muốn chép tay: chép thư mục `nro_server` vào Download rồi chạy `bash /sdcard/Download/nro_server/run_termux.sh` (cần `termux-setup-storage` trước).

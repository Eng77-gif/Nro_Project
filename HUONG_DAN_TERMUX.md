# Chạy server trên điện thoại (Termux)

Server là Java 21 thuần, không cần root. Điện thoại nên có từ 4 GB RAM và còn khoảng 2 GB bộ nhớ trống (thư mục `data` khoảng 1 GB).

## Chuẩn bị (1 lần)
1. Cài **Termux** từ F-Droid hoặc GitHub (bản trên Google Play đã cũ, không cài được gói).
2. Chép thư mục `nro_server` vào điện thoại, ví dụ `Download/nro_server`.
3. Mở Termux, gõ `termux-setup-storage` và bấm Cho phép (để Termux đọc được thư mục Download).

## Chạy (một lệnh, lần đầu tự cài hết)
```
bash /sdcard/Download/nro_server/run_termux.sh
```
Lần đầu script tự làm:
- Chép server vào `~/nro_server` (Java không chạy được trong Download / thẻ nhớ).
- Cài Java 21 và MariaDB, khởi tạo database, nạp `sql/team2026.sql`.
- Tạo user database `nro` / mật khẩu `nro123` và sửa `Config.properties` (MariaDB trên Termux không cho đăng nhập root không mật khẩu).
- Giữ máy không ngủ, bật server, in ra địa chỉ để client và panel kết nối.

Những lần sau: chạy lại đúng lệnh trên (chép bản mới nhất từ Download rồi chạy), hoặc `bash ~/nro_server/run_termux.sh`.

Cập nhật server: chép bản mới (20.jar, data...) đè vào `Download/nro_server` rồi chạy lại lệnh, script tự chép sang.

## Tự chạy khi bật máy
Cài thêm app **Termux:Boot** (F-Droid), mở nó 1 lần. Script đã tự đăng ký, bật máy là server tự chạy (log ở `~/nro_server/logs/termux_boot.log`).

## Lưu ý
- Trong cài đặt Android, tắt **tối ưu pin** cho Termux, nếu không máy có thể tự tắt server khi để lâu.
- RAM cho server mặc định 512 MB, đổi bằng `RAM_MAX=768m bash run_termux.sh`. Tắt server: `Ctrl + C`.
- Cùng wifi: client vào thẳng IP điện thoại (script in ra), cổng 14445; panel `http://IP:8088`.
- Ngoài mạng: điện thoại thường không có IP công khai, cần mở cổng trên router hoặc dùng Cloudflare Tunnel.

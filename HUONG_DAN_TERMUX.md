# Chạy server trên điện thoại (Termux)

Server là Java 21 thuần, không cần root. Điện thoại nên có từ 4 GB RAM và còn khoảng 2 GB bộ nhớ trống (thư mục `data` khoảng 1 GB).

## Cài một lần
1. Cài **Termux** từ F-Droid hoặc GitHub (bản trên Google Play đã cũ, không cài được gói).
2. Trong Termux: `termux-setup-storage` (cho phép đọc bộ nhớ máy).
3. Chép cả thư mục `nro_server` vào Termux, ví dụ `~/nro_server` (Termux không chạy được Java trong thẻ nhớ / Download, phải nằm trong thư mục nhà của Termux):
   `cp -r /sdcard/Download/nro_server ~/`
4. `cd ~/nro_server && bash termux_setup.sh`
   - Cài Java 21 và MariaDB, nạp `sql/team2026.sql`.
   - Tạo user database `nro` / mật khẩu `nro123` (đổi bằng `DB_PASS=matkhau bash termux_setup.sh`) và sửa `Config.properties` theo.

## Chạy
`cd ~/nro_server && bash run_termux.sh`
- Tự bật MariaDB, giữ máy không ngủ (`termux-wake-lock`), RAM tối đa 512 MB (đổi: `RAM_MAX=768m bash run_termux.sh`).
- Tắt server: `Ctrl + C`.
- Trong cài đặt Android, tắt **tối ưu pin** cho Termux, nếu không máy có thể tự tắt server khi để lâu.

## Kết nối
- Cùng wifi: client trỏ tới IP của điện thoại (xem bằng `ifconfig`), cổng game 14445; panel `http://IP:8088`.
- Ngoài mạng: điện thoại thường không có IP công khai, cần mở cổng trên router hoặc dùng tunnel (Cloudflare Tunnel như hướng dẫn panel).

Không cần build lại: dùng luôn `20.jar` có sẵn.

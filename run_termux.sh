#!/data/data/com.termux/files/usr/bin/bash
# Chạy server NRO trên điện thoại bằng Termux. Một lệnh làm tất cả, lần đầu tự cài:
#   bash run_termux.sh            (hoặc chạy thẳng từ Download: bash /sdcard/Download/nro_server/run_termux.sh)
# - đang ở Download / thẻ nhớ: tự chép thư mục server vào ~/nro_server (Java không chạy được trong bộ nhớ chung)
# - tự cài Java 21 + MariaDB, khởi tạo database, tạo user, nạp sql/team2026.sql, sửa Config.properties
# - tự bật MariaDB, giữ máy không ngủ, tự chạy lại khi bật máy (nếu có app Termux:Boot)
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"
HOME_DIR="$HOME/nro_server"

# 1. Thư mục server phải nằm trong Termux
case "$SRC" in
    /sdcard/*|/storage/*)
        echo ">> Chép server vào $HOME_DIR ..."
        mkdir -p "$HOME_DIR"
        cp -r "$SRC"/. "$HOME_DIR"/
        exec bash "$HOME_DIR/run_termux.sh" "$@"
        ;;
esac
cd "$SRC"
chmod +x nro_termux.sh
bash nro_termux.sh setup
bash nro_termux.sh start
echo ">> Gõ:  nro   để mở menu (bật / tắt / log / cập nhật / sao lưu ...)"

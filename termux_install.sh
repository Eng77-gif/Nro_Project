#!/data/data/com.termux/files/usr/bin/bash
# Cài server NRO lên Termux bằng một dòng:
#   curl -sL https://raw.githubusercontent.com/Eng77-gif/Nro_Project/main/termux_install.sh | bash
# Tải server từ GitHub về ~/nro_server, cài Java 21 + MariaDB, tạo database, tạo lệnh `nro` (menu) rồi bật server.
set -e
REPO=https://github.com/Eng77-gif/Nro_Project
DIR="$HOME/nro_server"

echo ">> Cài gói cần thiết (vài phút) ..."
yes | pkg update || true
pkg install -y git openjdk-21 mariadb
[ -d "$HOME/storage" ] || termux-setup-storage || true   # cho phép lưu bản sao lưu vào Download

if [ -d "$DIR/.git" ]; then
    echo ">> Đã có $DIR, cập nhật ..."
    git -C "$DIR" checkout -- Config.properties 2>/dev/null || true
    git -C "$DIR" pull --ff-only
else
    echo ">> Tải server từ GitHub (khoảng 1 GB) ..."
    git clone --depth 1 "$REPO" "$DIR"
fi
chmod +x "$DIR/nro_termux.sh"
bash "$DIR/nro_termux.sh" setup
bash "$DIR/nro_termux.sh" start
echo
echo ">> Xong. Gõ:  nro   để mở menu (bật / tắt / cập nhật / sao lưu ...)"

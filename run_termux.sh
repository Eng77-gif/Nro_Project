#!/data/data/com.termux/files/usr/bin/bash
# Chạy server NRO trên điện thoại bằng Termux. Một lệnh làm tất cả, lần đầu tự cài:
#   bash run_termux.sh            (hoặc chạy thẳng từ Download: bash /sdcard/Download/nro_server/run_termux.sh)
# - đang ở Download / thẻ nhớ: tự chép thư mục server vào ~/nro_server (Java không chạy được trong bộ nhớ chung)
# - tự cài Java 21 + MariaDB, khởi tạo database, tạo user, nạp sql/team2026.sql, sửa Config.properties
# - tự bật MariaDB, giữ máy không ngủ, tự chạy lại khi bật máy (nếu có app Termux:Boot)
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"
HOME_DIR="$HOME/nro_server"
DB_USER=nro
DB_PASS=${DB_PASS:-nro123}

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

# 2. Cài gói còn thiếu
if ! command -v java >/dev/null || ! command -v mysqld_safe >/dev/null; then
    echo ">> Cài Java 21 và MariaDB (lần đầu, vài phút) ..."
    yes | pkg update || true
    pkg install -y openjdk-21 mariadb
fi

# 3. Bật MariaDB (khởi tạo lần đầu)
[ -d "$PREFIX/var/lib/mysql/mysql" ] || mysql_install_db >/dev/null
if ! mysqladmin ping >/dev/null 2>&1; then
    (mysqld_safe >/dev/null 2>&1 &)
    for i in $(seq 1 60); do mysqladmin ping >/dev/null 2>&1 && break; sleep 1; done
fi

# 4. Database + user riêng (MariaDB trên Termux chỉ cho user Termux đăng nhập qua socket, JDBC cần mật khẩu qua TCP)
if [ ! -f .termux_db_ok ]; then
    echo ">> Tạo database team2026 ..."
    mysql -u "$(whoami)" <<SQL
CREATE DATABASE IF NOT EXISTS team2026 CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
CREATE USER IF NOT EXISTS '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
    if [ "$(mysql -u "$(whoami)" -N -e "select count(*) from information_schema.tables where table_schema='team2026'")" = "0" ]; then
        echo ">> Nạp sql/team2026.sql ..."
        mysql -u "$(whoami)" team2026 < sql/team2026.sql
    fi
    touch .termux_db_ok
fi
# cấu hình database (mỗi lần: chép lại từ Download sẽ ghi đè Config.properties)
[ -f Config.properties ] || cp Config.default.properties Config.properties
sed -i "s/^database.user=.*/database.user=$DB_USER/; s/^database.pass=.*/database.pass=$DB_PASS/; s/^database.host=.*/database.host=127.0.0.1/" Config.properties

# 5. Tự chạy khi bật máy (cần app Termux:Boot, mở app đó 1 lần)
mkdir -p "$HOME/.termux/boot"
printf '#!/data/data/com.termux/files/usr/bin/bash\ncd %s && bash run_termux.sh > %s/logs/termux_boot.log 2>&1\n' "$SRC" "$SRC" > "$HOME/.termux/boot/nro_server.sh"
chmod +x "$HOME/.termux/boot/nro_server.sh"
mkdir -p logs

# 6. Chạy server
command -v termux-wake-lock >/dev/null && termux-wake-lock || true
IP=$(ip -4 addr show wlan0 2>/dev/null | awk '/inet /{print $2}' | cut -d/ -f1)
echo ">> Server khởi động. Client: ${IP:-ip-dien-thoai}:14445   Panel: http://${IP:-ip-dien-thoai}:8088"
set +e
RAM_MAX=${RAM_MAX:-512m} sh run_vps_yeu.sh

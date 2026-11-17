#!/data/data/com.termux/files/usr/bin/bash
# Quản lý server NRO trên Termux. Gõ `nro` để mở menu, hoặc: nro setup|start|stop|restart|log|update|backup|restore|ram|ip
DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
cd "$DIR" || exit 1
DB_USER=nro
DB_PASS=${DB_PASS:-nro123}
REPO=https://github.com/Eng77-gif/Nro_Project
BACKUP_DIR="$HOME/storage/downloads/nro_backup"
[ -d "$HOME/storage/downloads" ] || BACKUP_DIR="$DIR/backup"
RAM_FILE="$DIR/.termux_ram"

db_up() {
    [ -d "$PREFIX/var/lib/mysql/mysql" ] || mysql_install_db >/dev/null
    if ! mysqladmin ping >/dev/null 2>&1; then
        (mysqld_safe >/dev/null 2>&1 &)
        for i in $(seq 1 60); do mysqladmin ping >/dev/null 2>&1 && break; sleep 1; done
    fi
}

config_db() {  # git pull / chép lại sẽ ghi đè Config.properties: sửa mỗi lần
    [ -f Config.properties ] || cp Config.default.properties Config.properties
    sed -i "s/^database.user=.*/database.user=$DB_USER/; s/^database.pass=.*/database.pass=$DB_PASS/; s/^database.host=.*/database.host=127.0.0.1/" Config.properties
}

# user database cho server: chạy mỗi lần bật (MariaDB trên Termux chỉ cho user Termux đăng nhập qua socket;
# JDBC cần user có mật khẩu qua TCP; xoá user ẩn danh vì nó chen vào đăng nhập từ localhost)
msql() { mysql -u "$(whoami)" "$@" 2>/dev/null || mysql -u root "$@"; }
ensure_db() {
    db_up
    msql <<SQL
CREATE DATABASE IF NOT EXISTS team2026 CHARACTER SET utf8mb4;
DROP USER IF EXISTS ''@'localhost';
DROP USER IF EXISTS ''@'$(hostname)';
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
CREATE USER IF NOT EXISTS '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
    if [ "$(msql -N -e "select count(*) from information_schema.tables where table_schema='team2026'")" = "0" ]; then
        echo ">> Nạp sql/team2026.sql ..."
        msql team2026 < sql/team2026.sql
    fi
    config_db
    if ! mysql -h 127.0.0.1 -u "$DB_USER" -p"$DB_PASS" -e "select 1" team2026 >/dev/null 2>&1; then
        echo "!! User database $DB_USER chưa đăng nhập được qua 127.0.0.1. Chụp màn hình lệnh sau gửi admin:"
        echo "   mysql -h 127.0.0.1 -u $DB_USER -p$DB_PASS team2026 -e 'select 1'"
        return 1
    fi
}

setup() {
    if ! command -v java >/dev/null || ! command -v mysqld_safe >/dev/null || ! command -v git >/dev/null; then
        echo ">> Cài Java 21, MariaDB, git (lần đầu, vài phút) ..."
        yes | pkg update || true
        pkg install -y openjdk-21 mariadb git
    fi
    db_up
    ensure_db
    config_db
    mkdir -p logs "$HOME/.termux/boot"
    # tự chạy khi bật máy (cần app Termux:Boot, mở app đó 1 lần)
    printf '#!/data/data/com.termux/files/usr/bin/bash\ntermux-wake-lock\n%s/nro_termux.sh start\n' "$DIR" > "$HOME/.termux/boot/nro_server.sh"
    chmod +x "$HOME/.termux/boot/nro_server.sh"
    ln -sf "$DIR/nro_termux.sh" "$PREFIX/bin/nro"
}

running() { pgrep -f "java.*20.jar" >/dev/null; }

start() {
    if running; then echo ">> Server đang chạy."; return; fi
    ensure_db || return 1; mkdir -p logs
    command -v termux-wake-lock >/dev/null && termux-wake-lock
    RAM_MAX=$(cat "$RAM_FILE" 2>/dev/null || echo 512m) nohup sh run_vps_yeu.sh > logs/server.log 2>&1 &
    echo ">> Đang bật server (xem tiến trình: nro log)."
    ip_info
}

stop() {
    pkill -f "sh run_vps_yeu.sh" 2>/dev/null
    if running; then pkill -f "java.*20.jar"; for i in $(seq 1 30); do running || break; sleep 1; done; fi
    running && pkill -9 -f "java.*20.jar"
    echo ">> Đã tắt server."
}

ip_info() {
    IP=$(ifconfig 2>/dev/null | awk '/wlan0/{f=1} f&&/inet /{print $2; exit}')
    echo ">> Client: ${IP:-ip-dien-thoai}:14445    Panel: http://${IP:-ip-dien-thoai}:8088"
}

update() {
    echo ">> Cập nhật từ GitHub ..."
    local was=0; running && was=1 && stop
    git checkout -- Config.properties 2>/dev/null
    git pull --ff-only || { echo "!! Không cập nhật được (mạng hoặc file sửa tay bị trùng)."; }
    chmod +x "$DIR/nro_termux.sh"
    # chạy lại bằng bản script vừa tải (menu đang mở vẫn giữ code cũ trong bộ nhớ)
    if [ $was = 1 ]; then exec bash "$DIR/nro_termux.sh" start-menu; else exec bash "$DIR/nro_termux.sh"; fi
}

backup() {
    db_up; mkdir -p "$BACKUP_DIR"
    local f="$BACKUP_DIR/team2026_$(date +%Y%m%d_%H%M%S).sql"
    mysqldump -u "$(whoami)" team2026 > "$f" && echo ">> Đã sao lưu: $f"
}

restore() {
    db_up
    ls -1t "$BACKUP_DIR"/*.sql 2>/dev/null | head -10 | nl
    read -rp "Chọn số bản sao lưu để khôi phục (Enter = huỷ): " n
    local f=$(ls -1t "$BACKUP_DIR"/*.sql 2>/dev/null | sed -n "${n}p")
    [ -n "$n" ] && [ -n "$f" ] || { echo "Huỷ."; return; }
    local was=0; running && was=1 && stop
    mysql -u "$(whoami)" team2026 < "$f" && echo ">> Đã khôi phục $f"
    [ $was = 1 ] && start
}

ram() {
    read -rp "RAM tối đa cho server (hiện $(cat "$RAM_FILE" 2>/dev/null || echo 512m), vd 384m / 768m / 1g): " r
    [ -n "$r" ] && echo "$r" > "$RAM_FILE" && echo ">> Lần bật sau dùng $r."
}

menu() {
    while :; do
        echo
        echo "===== SERVER NRO ($(running && echo 'đang chạy' || echo 'đang tắt')) ====="
        echo " 1. Bật server        2. Tắt server       3. Khởi động lại"
        echo " 4. Xem log (Ctrl+C để thoát xem)"
        echo " 5. Cập nhật từ GitHub"
        echo " 6. Sao lưu database  7. Khôi phục database"
        echo " 8. Đổi RAM           9. Xem IP kết nối    0. Thoát"
        read -rp "Chọn: " c
        case $c in
            1) start ;; 2) stop ;; 3) stop; start ;;
            4) tail -n 50 -f logs/server.log ;;
            5) update ;; 6) backup ;; 7) restore ;; 8) ram ;; 9) ip_info ;;
            0) exit 0 ;;
        esac
    done
}

case "$1" in
    setup) setup ;; start) start ;; stop) stop ;; restart) stop; start ;;
    log) tail -n 50 -f logs/server.log ;; update) update ;; backup) backup ;; restore) restore ;;
    ram) ram ;; ip) ip_info ;; start-menu) start; menu ;; *) menu ;;
esac

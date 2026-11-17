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
MYSQL=$(command -v mariadb || command -v mysql)
DUMP=$(command -v mariadb-dump || command -v mysqldump)
# user quản trị: chỉ nhận user đọc được mysql.user (user ẩn danh ''@localhost "đăng nhập được" bằng mọi tên nhưng không có quyền)
DBA=()
db_admin() {
    local a
    for a in "-u $(whoami)" "-u root" "-u $DB_USER -p$DB_PASS" "-h 127.0.0.1 -u $DB_USER -p$DB_PASS"; do
        "$MYSQL" $a -e "select 1 from mysql.user limit 1" >/dev/null 2>&1 && { DBA=($a); return 0; }
    done
    return 1
}
msql() { "$MYSQL" "${DBA[@]}" "$@"; }
# đợi tiến trình thoát hẳn (ping tắt sớm hơn, bật lại ngay sẽ bị khoá file dữ liệu)
db_wait_down() { for i in $(seq 1 30); do pgrep -f 'mariadbd|mysqld' >/dev/null || return 0; sleep 1; done; }
# không user nào có quyền: bật MariaDB bỏ qua kiểm tra quyền (chỉ socket nội bộ), tạo user nro toàn quyền rồi bật lại bình thường
reset_grants() {
    echo ">> Không có user quản trị MariaDB, đang khôi phục quyền ..."
    pkill -f mariadbd 2>/dev/null; pkill -f mysqld 2>/dev/null; db_wait_down
    (mysqld_safe --skip-grant-tables --skip-networking >/dev/null 2>&1 &)
    for i in $(seq 1 60); do mysqladmin ping >/dev/null 2>&1 && break; sleep 1; done
    "$MYSQL" -u root <<SQL
FLUSH PRIVILEGES;
DROP USER IF EXISTS ''@'localhost';
DROP USER IF EXISTS ''@'$(hostname)';
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
CREATE USER IF NOT EXISTS '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'127.0.0.1' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL
    mysqladmin -u root shutdown >/dev/null 2>&1 || { pkill -f mariadbd; pkill -f mysqld; }
    db_wait_down; db_up
}
ensure_db() {
    db_up
    if ! db_admin; then reset_grants; db_admin || { echo "!! Không khôi phục được quyền MariaDB."; return 1; }; fi
    msql <<SQL
CREATE DATABASE IF NOT EXISTS team2026 CHARACTER SET utf8mb4;
DROP USER IF EXISTS ''@'localhost';
DROP USER IF EXISTS ''@'$(hostname)';
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
CREATE USER IF NOT EXISTS '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
ALTER USER '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'127.0.0.1' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL
    if [ "$(msql -N -e "select count(*) from information_schema.tables where table_schema='team2026'")" = "0" ]; then
        echo ">> Nạp sql/team2026.sql ..."
        msql team2026 < sql/team2026.sql
    fi
    config_db
    if ! "$MYSQL" -h 127.0.0.1 -u "$DB_USER" -p"$DB_PASS" -e "select 1" team2026 >/dev/null 2>&1; then
        echo "!! User database $DB_USER chưa đăng nhập được qua 127.0.0.1. Chụp màn hình lệnh sau gửi admin:"
        echo "   mariadb -h 127.0.0.1 -u $DB_USER -p$DB_PASS team2026 -e 'select 1'"
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
    db_admin || { echo "!! Chưa có quyền database, chọn Bật server 1 lần trước."; return 1; }
    "$DUMP" "${DBA[@]}" team2026 > "$f" && echo ">> Đã sao lưu: $f"
}

restore() {
    db_up
    ls -1t "$BACKUP_DIR"/*.sql 2>/dev/null | head -10 | nl
    read -rp "Chọn số bản sao lưu để khôi phục (Enter = huỷ): " n
    local f=$(ls -1t "$BACKUP_DIR"/*.sql 2>/dev/null | sed -n "${n}p")
    [ -n "$n" ] && [ -n "$f" ] || { echo "Huỷ."; return; }
    local was=0; running && was=1 && stop
    db_admin || { echo "!! Chưa có quyền database."; return 1; }
    msql team2026 < "$f" && echo ">> Đã khôi phục $f"
    [ $was = 1 ] && start
}

ram() {
    read -rp "RAM tối đa cho server (hiện $(cat "$RAM_FILE" 2>/dev/null || echo 512m), vd 384m / 768m / 1g): " r
    [ -n "$r" ] && echo "$r" > "$RAM_FILE" && echo ">> Lần bật sau dùng $r."
}

# ---------- giao diện ----------
C0=$'\e[0m'; B=$'\e[1m'; DIM=$'\e[2m'; R=$'\e[31m'; G=$'\e[32m'; Y=$'\e[33m'; CY=$'\e[36m'; M=$'\e[35m'
line() { printf "${CY}%s${C0}\n" "  ──────────────────────────────────────"; }
row()  { printf "  ${DIM}%s${C0} %s\n" "$1" "$2"; }  # nhãn tự đệm sẵn (printf đếm byte, chữ có dấu bị lệch)
pause() { echo; read -rp "  ${DIM}Nhấn Enter để quay lại menu...${C0}" _; }
my_ip() { ifconfig 2>/dev/null | awk '/wlan0/{f=1} f&&/inet /{print $2; exit}'; }

status() {
    local pid up mem ip
    pid=$(pgrep -f "java.*20.jar" | head -1)
    ip=$(my_ip)
    clear
    printf "${Y}${B}%s${C0}\n" "  ╔══════════════════════════════════════╗"
    printf "${Y}${B}%s${C0}\n" "  ║     ★  NGỌC RỒNG ONLINE SERVER  ★    ║"
    printf "${Y}${B}%s${C0}\n" "  ╚══════════════════════════════════════╝"
    line
    if [ -n "$pid" ]; then
        up=$(ps -o etime= -p "$pid" 2>/dev/null | tr -d ' ')
        mem=$(ps -o rss= -p "$pid" 2>/dev/null | awk '{printf "%d MB", $1/1024}')
        row "Server    " "${G}${B}● ĐANG CHẠY${C0}  ${DIM}(${up:-?}, ${mem:-?})${C0}"
    else
        row "Server    " "${R}${B}● ĐANG TẮT${C0}"
    fi
    if mysqladmin ping >/dev/null 2>&1; then row "Database  " "${G}● MariaDB chạy${C0}"; else row "Database  " "${R}● MariaDB tắt${C0}"; fi
    row "Game      " "${B}${ip:-chưa có wifi}:14445${C0}"
    row "Panel     " "${B}http://${ip:-ip}:8088${C0}"
    row "RAM       " "$(cat "$RAM_FILE" 2>/dev/null || echo 512m)"
    row "Phiên bản " "$(git log -1 --format='%h  %cd' --date=format:'%d/%m %H:%M' 2>/dev/null)"
    line
}

menu() {
    while :; do
        status
        printf "  ${G}1${C0}  Bật server            ${R}2${C0}  Tắt server\n"
        printf "  ${Y}3${C0}  Khởi động lại         ${CY}4${C0}  Xem log trực tiếp\n"
        printf "  ${M}5${C0}  Cập nhật từ GitHub\n"
        printf "  ${CY}6${C0}  Sao lưu database      ${CY}7${C0}  Khôi phục database\n"
        printf "  ${CY}8${C0}  Đổi RAM               ${DIM}0  Thoát${C0}\n"
        line
        read -rp "  Chọn: " c
        echo
        case $c in
            1) start; pause ;;
            2) stop; pause ;;
            3) stop; start; pause ;;
            4) echo "  ${DIM}Ctrl + C để thoát xem log${C0}"; trap 'true' INT; tail -n 40 -f logs/server.log; trap - INT ;;
            5) update ;;
            6) backup; pause ;;
            7) restore; pause ;;
            8) ram; pause ;;
            0) clear; exit 0 ;;
        esac
    done
}

case "$1" in
    setup) setup ;; start) start ;; stop) stop ;; restart) stop; start ;;
    log) tail -n 50 -f logs/server.log ;; update) update ;; backup) backup ;; restore) restore ;;
    ram) ram ;; ip) ip_info ;; start-menu) start; menu ;; *) menu ;;
esac

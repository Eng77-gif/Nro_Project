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
PASS_FILE="$DIR/.termux_panel"
LOG=logs/server.log

db_up() {
    [ -d "$PREFIX/var/lib/mysql/mysql" ] || mysql_install_db >/dev/null
    if ! mysqladmin ping >/dev/null 2>&1; then
        (mysqld_safe >/dev/null 2>&1 &)
        for i in $(seq 1 60); do mysqladmin ping >/dev/null 2>&1 && break; sleep 1; done
    fi
}

panel_pass() {  # mật khẩu panel cố định cho Termux (Config.properties bị git ghi đè khi cập nhật)
    [ -s "$PASS_FILE" ] || tr -dc 'a-z0-9' < /dev/urandom 2>/dev/null | head -c 8 > "$PASS_FILE"
    cat "$PASS_FILE"
}
config_db() {  # git pull / chép lại sẽ ghi đè Config.properties: sửa mỗi lần
    [ -f Config.properties ] || cp Config.default.properties Config.properties
    sed -i "s/^panel.pass=.*/panel.pass=$(panel_pass)/" Config.properties
    sed -i "s/^database.user=.*/database.user=$DB_USER/; s/^database.pass=.*/database.pass=$DB_PASS/; s/^database.host=.*/database.host=127.0.0.1/; s/^server.ip=.*/server.ip=127.0.0.1/" Config.properties
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
launcher() { pgrep -f "sh run_vps_yeu.sh" >/dev/null; }

# ---------- giao diện ----------
C0=$'\e[0m'; B=$'\e[1m'; DIM=$'\e[2m'; R=$'\e[31m'; G=$'\e[32m'; Y=$'\e[33m'; CY=$'\e[36m'; M=$'\e[35m'; W=$'\e[97m'
c256() { printf '\e[38;5;%sm' "$1"; }
HIDE=$'\e[?25l'; SHOW=$'\e[?25h'
trap 'printf "%s" "$SHOW"' EXIT
line() { printf "  ${DIM}%s${C0}\n" "────────────────────────────────────"; }
ok()   { printf "  ${G}✔${C0} %s\n" "$*"; }
warn() { printf "  ${Y}!${C0} %s\n" "$*"; }
err()  { printf "  ${R}✘${C0} %s\n" "$*"; }
row()  { printf "  ${DIM}%s${C0} %s\n" "$1" "$2"; }  # nhãn tự đệm sẵn (printf đếm byte, chữ có dấu bị lệch)
pause() { echo; printf "  ${DIM}Nhấn phím bất kỳ để quay lại menu...${C0}"; read -rsn1 _; }
my_ip() { ifconfig 2>/dev/null | awk '/wlan0/{f=1} f&&/inet /{print $2; exit}'; }
mem_total() { awk '/MemTotal/{print int($2/1024)}' /proc/meminfo 2>/dev/null; }

# chạy lệnh nền kèm vòng xoay; trả mã thoát của lệnh, lỗi thì in phần cuối kết quả
spin() {
    local msg=$1 out f=0 rc; shift
    out=$(mktemp); "$@" >"$out" 2>&1 &
    local pid=$! fr=(⠋ ⠙ ⠹ ⠸ ⠼ ⠴ ⠦ ⠧ ⠇ ⠏)
    printf "%s" "$HIDE"
    while kill -0 $pid 2>/dev/null; do printf "\r  ${CY}%s${C0} %s" "${fr[f++ % 10]}" "$msg"; sleep 0.1; done
    wait $pid; rc=$?
    if [ $rc = 0 ]; then printf "\r  ${G}✔${C0} %s\n" "$msg"; else printf "\r  ${R}✘${C0} %s\n" "$msg"; tail -n 8 "$out" | sed "s/^/    ${DIM}/;s/\$/${C0}/"; fi
    printf "%s" "$SHOW"; rm -f "$out"; return $rc
}

bar() {  # bar <phần trăm> <chữ>
    local n=$(( $1 * 24 / 100 )) full empty
    full=$(printf '%*s' $n '' | sed 's/ /█/g'); empty=$(printf '%*s' $((24 - n)) '' | sed 's/ /░/g')
    printf "\r  $(c256 214)%s${DIM}%s${C0} %3d%%  %-14s" "$full" "$empty" "$1" "$2"
}

banner() {  # $1=anim: hiện từng dòng + 7 viên ngọc sáng dần
    local cols=(202 208 214 220 226 227) i=0 l
    local art=(' ███╗   ██╗██████╗  ██████╗ ' ' ████╗  ██║██╔══██╗██╔═══██╗' ' ██╔██╗ ██║██████╔╝██║   ██║'
               ' ██║╚██╗██║██╔══██╗██║   ██║' ' ██║ ╚████║██║  ██║╚██████╔╝' ' ╚═╝  ╚═══╝╚═╝  ╚═╝ ╚═════╝ ')
    for l in "${art[@]}"; do printf "   $(c256 ${cols[i++]})${B}%s${C0}\n" "$l"; [ "$1" ] && sleep 0.05; done
    printf "   "
    for i in 1 2 3 4 5 6 7; do printf "$(c256 214)●${C0} "; [ "$1" ] && sleep 0.06; done
    printf " ${W}${B}NGỌC RỒNG ONLINE${C0}\n"
}

status() {
    local pid up mem ip pass
    pid=$(pgrep -f "java.*20.jar" | head -1); ip=$(my_ip); pass=$(panel_pass)
    printf '\e[H\e[2J'; banner "$1"; echo; line
    if [ -n "$pid" ]; then
        up=$(ps -o etime= -p "$pid" 2>/dev/null | tr -d ' ')
        mem=$(ps -o rss= -p "$pid" 2>/dev/null | awk '{printf "%d MB", $1/1024}')
        if grep -q "Bot: giữ" "$LOG" 2>/dev/null; then row "Server    " "${G}${B}● ĐANG CHẠY${C0} ${DIM}${up:-?} · ${mem:-?}${C0}"
        else row "Server    " "${Y}${B}◐ ĐANG KHỞI ĐỘNG${C0} ${DIM}${up:-?}${C0}"; fi
    else
        row "Server    " "${R}${B}○ ĐANG TẮT${C0}"
    fi
    if mysqladmin ping >/dev/null 2>&1; then row "Database  " "${G}● MariaDB${C0}"; else row "Database  " "${R}○ MariaDB tắt${C0}"; fi
    row "Game      " "${W}${B}127.0.0.1:14445${C0}"
    [ -n "$ip" ] && row "Cùng wifi " "${W}$ip:14445${C0}"
    row "Panel     " "${W}http://${ip:-127.0.0.1}:8088${C0}"
    row "Đăng nhập " "${W}admin${C0} / ${Y}${B}$pass${C0}"
    row "RAM       " "$(ram_val) ${DIM}(máy $(mem_total) MB)${C0}"
    row "Phiên bản " "$(git log -1 --format='%h · %cd' --date=format:'%d/%m %H:%M' 2>/dev/null)"
    line
}

# ---------- RAM ----------
to_mb() { case $1 in *[gG]) echo $(( ${1%?} * 1024 ));; *[mM]) echo ${1%?};; esac; }
ram_val() {  # đọc + sửa giá trị lưu sai (vd "8000" thiếu đơn vị, Java hiểu là 8000 byte)
    local r; r=$(tr -d ' \r\n' < "$RAM_FILE" 2>/dev/null)
    [[ $r =~ ^[0-9]+$ ]] && r=${r}m
    [[ $r =~ ^[0-9]+[mMgG]$ ]] || r=512m
    local mb=$(to_mb "$r") tot=$(mem_total)
    [ "$mb" -lt 256 ] && r=256m
    [ -n "$tot" ] && [ "$mb" -gt $((tot * 7 / 10)) ] && r=$((tot * 6 / 10))m
    echo "$r"
}
ram() {
    local tot=$(mem_total) c r
    echo "  RAM điện thoại: ${B}${tot:-?} MB${C0}   Server đang dùng tối đa: ${B}$(ram_val)${C0}"; echo
    printf "  ${CY}1${C0}  384m   máy 2-3 GB RAM\n  ${CY}2${C0}  512m   ${G}khuyên dùng${C0}\n  ${CY}3${C0}  768m   nhiều bot\n  ${CY}4${C0}  1g     máy từ 6 GB RAM\n  ${CY}5${C0}  Tự nhập (vd 640 = 640 MB, 2g = 2 GB)\n\n"
    printf "  Chọn: "; read -rsn1 c; echo "$c"
    case $c in 1) r=384m;; 2) r=512m;; 3) r=768m;; 4) r=1g;;
        5) read -rp "  Nhập RAM: " r; r=$(echo "$r" | tr -d ' '); [[ $r =~ ^[0-9]+$ ]] && r=${r}m ;;
        *) echo "  Huỷ."; return;; esac
    if ! [[ $r =~ ^[0-9]+[mMgG]$ ]]; then err "Sai định dạng: $r"; return; fi
    echo "$r" > "$RAM_FILE"; r=$(ram_val); echo "$r" > "$RAM_FILE"
    ok "Đã đặt RAM $r (bật lại server để áp dụng)"
}

# ---------- server ----------
start() {
    if running; then ok "Server đang chạy."; return; fi
    spin "Kiểm tra database" ensure_db || { err "Database lỗi, xem dòng trên."; return 1; }
    mkdir -p logs; : > "$LOG"
    command -v termux-wake-lock >/dev/null && termux-wake-lock
    RAM_MAX=$(ram_val) nohup sh run_vps_yeu.sh > "$LOG" 2>&1 &
    local t=0 pct=0 txt n
    printf "%s" "$HIDE"
    while :; do
        sleep 0.5; t=$((t + 1))
        if [ $t -gt 4 ] && ! running && ! launcher; then
            printf "%s\n" "$SHOW"; err "Server tắt ngay khi bật. Cuối log:"
            tail -n 12 "$LOG" | sed "s/^/    ${R}/;s/\$/${C0}/"; return 1
        fi
        if grep -q "Bot: giữ" "$LOG"; then bar 100 "Sẵn sàng!"; break
        elif grep -q "đang mở cổng" "$LOG"; then pct=92; txt="Mở cổng"
        else n=$(grep -c "Successfully" "$LOG"); pct=$((5 + n * 3)); [ $pct -gt 88 ] && pct=88; txt="Nạp dữ liệu"; fi
        bar $pct "$txt"
        if [ $t -gt 600 ]; then printf "%s\n" "$SHOW"; warn "Vẫn đang khởi động, xem log (4)."; return; fi
    done
    printf "%s\n\n" "$SHOW"
    ok "Server đã chạy sau $((t / 2)) giây"
    ip_info
}

stop_now() {
    pkill -f "sh run_vps_yeu.sh" 2>/dev/null
    running && pkill -f "java.*20.jar"
    for i in $(seq 1 30); do running || return 0; sleep 1; done
    pkill -9 -f "java.*20.jar"
}
stop() {
    if ! running && ! launcher; then ok "Server đang tắt."; return; fi
    spin "Tắt server (lưu dữ liệu người chơi)" stop_now
}

ip_info() {
    local ip=$(my_ip)
    row "Chơi trên máy này" "${W}${B}127.0.0.1:14445${C0}"
    [ -n "$ip" ] && row "Máy khác cùng wifi" "${W}$ip:14445${C0}"
    row "Panel" "${W}http://${ip:-127.0.0.1}:8088${C0}  admin / ${Y}$(panel_pass)${C0}"
}

update() {
    local was=0; running && was=1 && stop
    git checkout -- Config.properties 2>/dev/null
    spin "Tải bản mới từ GitHub" git pull --ff-only || warn "Không cập nhật được (mạng hoặc file sửa tay bị trùng)."
    chmod +x "$DIR/nro_termux.sh"
    sleep 1
    # chạy lại bằng bản script vừa tải (menu đang mở vẫn giữ code cũ trong bộ nhớ)
    if [ $was = 1 ]; then exec bash "$DIR/nro_termux.sh" start-menu; else exec bash "$DIR/nro_termux.sh"; fi
}

do_backup() { local f="$BACKUP_DIR/team2026_$(date +%Y%m%d_%H%M%S).sql"; "$DUMP" "${DBA[@]}" team2026 > "$f" && echo "$f"; }
backup() {
    db_up; mkdir -p "$BACKUP_DIR"
    db_admin || { err "Chưa có quyền database, bật server 1 lần trước."; return 1; }
    spin "Sao lưu database" do_backup && ok "Lưu ở $(ls -1t "$BACKUP_DIR"/*.sql | head -1)"
}

restore() {
    db_up
    local files n f
    files=$(ls -1t "$BACKUP_DIR"/*.sql 2>/dev/null | head -9)
    [ -z "$files" ] && { warn "Chưa có bản sao lưu nào trong $BACKUP_DIR"; return; }
    echo "$files" | awk -F/ '{printf "  \033[36m%d\033[0m  %s\n", NR, $NF}'
    echo; printf "  Chọn bản (phím khác = huỷ): "; read -rsn1 n; echo "$n"
    f=$(echo "$files" | sed -n "${n}p" 2>/dev/null)
    [[ $n =~ ^[1-9]$ ]] && [ -n "$f" ] || { echo "  Huỷ."; return; }
    local was=0; running && was=1 && stop
    db_admin || { err "Chưa có quyền database."; return 1; }
    spin "Khôi phục $(basename "$f")" sh -c '"$0" "$@" team2026 < "'"$f"'"' "$MYSQL" "${DBA[@]}"
    [ $was = 1 ] && start
}

set_pass() {
    local p
    echo "  Mật khẩu panel hiện tại: ${Y}$(panel_pass)${C0}"
    read -rp "  Mật khẩu mới (chữ thường + số, 4-20 ký tự, Enter = huỷ): " p
    [ -z "$p" ] && { echo "  Huỷ."; return; }
    [[ $p =~ ^[a-zA-Z0-9]{4,20}$ ]] || { err "Chỉ dùng chữ không dấu và số, 4-20 ký tự."; return; }
    echo "$p" > "$PASS_FILE"; config_db; ok "Đã đổi. Khởi động lại server để áp dụng."
}

view_log() {
    echo "  ${DIM}Đang xem log · chạm Ctrl rồi C để thoát${C0}"; echo
    trap 'true' INT
    tail -n 40 -f "$LOG" | sed -u -e "s/.*\(Exception\|ERROR\|Error\|lỗi\).*/${R}&${C0}/" -e "s/.*Login.*/${CY}&${C0}/"
    trap - INT
}

item() {  # item <phím> <màu> <biểu tượng> <chữ> [ghi chú]
    printf "   ${2}${B}%s${C0}  ${2}%s${C0}  %s ${DIM}%s${C0}\n" "$1" "$3" "$4" "$5"
}

menu() {
    local first=1 c
    while :; do
        status ${first:+anim}; first=
        if running; then item 1 "$DIM" "▶" "Bật server" "(đang chạy)"; else item 1 "$G" "▶" "Bật server"; fi
        item 2 "$R" "■" "Tắt server"
        item 3 "$Y" "↻" "Khởi động lại"
        item 4 "$CY" "≡" "Xem log trực tiếp"
        item 5 "$M" "↓" "Cập nhật từ GitHub"
        item 6 "$CY" "⇪" "Sao lưu database"
        item 7 "$CY" "⇩" "Khôi phục database"
        item 8 "$CY" "⚙" "Đổi RAM"
        item 9 "$CY" "✎" "Đổi mật khẩu panel"
        item 0 "$DIM" "✕" "Thoát" "(server vẫn chạy nền)"
        line
        printf "  ${B}Chọn số:${C0} "; read -rsn1 -t 15 c || continue  # 15 giây không bấm: vẽ lại trạng thái
        echo "$c"; echo
        case $c in
            1) start; pause ;;
            2) stop; pause ;;
            3) stop; start; pause ;;
            4) view_log ;;
            5) update ;;
            6) backup; pause ;;
            7) restore; pause ;;
            8) ram; pause ;;
            9) set_pass; pause ;;
            0) printf '\e[H\e[2J'; ok "Tạm biệt! Gõ ${B}nro${C0} để mở lại menu."; exit 0 ;;
        esac
    done
}

case "$1" in
    setup) setup ;; start) start ;; stop) stop ;; restart) stop; start ;;
    log) view_log ;; update) update ;; backup) backup ;; restore) restore ;;
    ram) ram ;; ip) ip_info ;; pass) set_pass ;; start-menu) start; menu ;; *) menu ;;
esac

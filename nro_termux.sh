#!/data/data/com.termux/files/usr/bin/bash
# Quản lý server NRO trên Termux. Gõ `nro` để mở menu, hoặc: nro setup|start|stop|restart|log|update|backup|restore|ram|ip
DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
cd "$DIR" || exit 1
# Java / MariaDB trên Termux không tự biết múi giờ Android (chạy theo UTC, lệch 7 tiếng với dữ liệu từ VPS)
[ -z "$TZ" ] && TZ=$(getprop persist.sys.timezone 2>/dev/null) && export TZ
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
    config_db
    if [ "$(msql -N -e "select count(*) from information_schema.tables where table_schema='team2026'")" = "0" ]; then
        # có dữ liệu mã hoá trên GitHub + mật khẩu thì nạp bản đó, không thì dữ liệu mẫu
        if [ -f sql/team2026.sql.enc ] && [ -f backup.key ] && java -cp 20.jar nro.models.data.SqlBackup import; then :
        else echo ">> Nạp sql/team2026.sql ..."; msql team2026 < sql/team2026.sql; fi
    fi
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

vlen() { printf '%s' "$1" | sed 's/\x1b\[[0-9;]*m//g' | LC_ALL=C tr -d '\200-\277' | wc -c; }  # độ rộng hiển thị (bỏ mã màu, đếm ký tự UTF-8)
pad() { local n=$(( $2 - $(vlen "$1") )); printf '%s%*s' "$1" $(( n > 0 ? n : 0 )) ''; }
BC=$(c256 208)  # màu khung
IW=36           # bề rộng trong khung (vừa điện thoại dọc ~40 cột)
hr()   { local l; l=$(printf '%*s' $IW '' | sed 's/ /─/g'); printf "  ${BC}%s%s%s${C0}\n" "$1" "$l" "$2"; }
crow() { printf "  ${BC}│${C0} %s ${BC}│${C0}\n" "$(pad "$1" $((IW - 2)))"; }
kv()   { crow "${DIM}$(pad "$1" 7)${C0} $2"; }
mini() {  # mini <đã dùng> <tối đa> <ô>: thanh nhỏ
    local n=$(( $2 > 0 ? $1 * $3 / $2 : 0 )); [ $n -gt $3 ] && n=$3
    local c=46; [ $n -gt $(( $3 * 6 / 10 )) ] && c=214; [ $n -gt $(( $3 * 85 / 100 )) ] && c=196
    printf "$(c256 $c)%s${DIM}%s${C0}" "$(printf '%*s' $n '' | sed 's/ /■/g')" "$(printf '%*s' $(( $3 - n )) '' | sed 's/ /·/g')"
}
nice_time() {  # etime [[dd-]hh:]mm:ss -> "1n 2g 05p"
    local t=$1 d=0 h=0 m=0
    [[ $t == *-* ]] && { d=${t%%-*}; t=${t#*-}; }
    IFS=: read -ra p <<< "$t"
    case ${#p[@]} in 3) h=${p[0]}; m=${p[1]};; 2) m=${p[0]};; esac
    h=$((10#$h)); m=$((10#$m))
    if [ $d -gt 0 ]; then echo "${d}n ${h}g"; elif [ $h -gt 0 ]; then echo "${h}g ${m}p"; else echo "${m}p"; fi
}

ART=(' ███╗   ██╗██████╗  ██████╗ ' ' ████╗  ██║██╔══██╗██╔═══██╗' ' ██╔██╗ ██║██████╔╝██║   ██║'
     ' ██║╚██╗██║██╔══██╗██║   ██║' ' ██║ ╚████║██║  ██║╚██████╔╝' ' ╚═╝  ╚═══╝╚═╝  ╚═╝ ╚═════╝ ')
FIRE=(196 202 208 214 220 226 227 226 220 214 208 202)
draw_art() { local i; for i in 0 1 2 3 4 5; do printf "       $(c256 ${FIRE[(i + $1) % 12]})${B}%s${C0}\n" "${ART[i]}"; done; }
banner() {  # $1=anim: logo hiện dần, lửa chạy qua chữ, 7 viên ngọc sáng lần lượt
    if [ "$1" ]; then
        printf "%s" "$HIDE"
        local i k
        for i in 0 1 2 3 4 5; do printf "       $(c256 ${FIRE[i]})${B}%s${C0}\n" "${ART[i]}"; sleep 0.04; done
        for k in 1 2 3 4 5 6 7 8 9 10 11 12; do printf '\e[6A'; draw_art $k; sleep 0.045; done
        printf "       "
        for i in 1 2 3 4 5 6 7; do printf "$(c256 220)★${C0} "; sleep 0.05; printf '\b\b'; printf "$(c256 208)●${C0} "; done
        printf "%s" "$SHOW"
    else
        draw_art 0; printf "       "; for i in 1 2 3 4 5 6 7; do printf "$(c256 208)●${C0} "; done
    fi
    printf "\n      ${W}${B}N G Ọ C   R Ồ N G   O N L I N E${C0}\n"
}

status() {
    local pid up mem ip pass st rmax
    pid=$(pgrep -f "java.*20.jar" | head -1); ip=$(my_ip); pass=$(panel_pass)
    rmax=$(to_mb "$(ram_val)")
    printf '\e[H\e[2J'; echo; banner "$1"; echo
    hr ╭ ╮
    if [ -n "$pid" ]; then
        up=$(nice_time "$(ps -o etime= -p "$pid" 2>/dev/null | tr -d ' ')")
        mem=$(ps -o rss= -p "$pid" 2>/dev/null | awk '{print int($1/1024)}')
        if grep -q "Bot: giữ" "$LOG" 2>/dev/null; then st="${G}${B}● ĐANG CHẠY${C0}"; else st="${Y}${B}◐ ĐANG KHỞI ĐỘNG${C0}"; fi
        crow "$(pad "$st" 22)${DIM}chạy ${C0}${W}$up${C0}"
        crow "${DIM}RAM${C0}  $(mini "${mem:-0}" "$rmax" 14) ${W}${mem:-?}${C0}${DIM}/${rmax} MB${C0}"
    else
        crow "${R}${B}○ ĐANG TẮT${C0}   ${DIM}bấm ${C0}${G}${B}1${C0}${DIM} để bật${C0}"
        crow "${DIM}RAM tối đa${C0} ${W}${rmax} MB${C0} ${DIM}/ máy $(mem_total) MB${C0}"
    fi
    if [ -n "$pid" ] && grep -q "clock leap" "$LOG" 2>/dev/null; then  # Hikari: tiến trình bị dừng > vài giây
        crow "${R}${B}! Android đang tạm dừng server${C0}"
        crow "${DIM}Bấm ${C0}${Y}${B}L${C0}${DIM} để xem cách sửa lag${C0}"
    fi
    hr ├ ┤
    kv "Game" "${W}${B}127.0.0.1:14445${C0}"
    [ -n "$ip" ] && kv "Wifi" "${W}$ip:14445${C0}"
    kv "Panel" "${CY}http://${ip:-127.0.0.1}:8088${C0}"
    kv "Login" "${W}admin${C0} ${DIM}/${C0} ${Y}${B}$pass${C0}"
    hr ├ ┤
    if mysqladmin ping >/dev/null 2>&1; then st="${G}●${C0} MariaDB"; else st="${R}○${C0} MariaDB"; fi
    crow "$(pad "$st" 13)${DIM}bản $(git log -1 --format='%h · %cd' --date=format:'%d/%m %H:%M' 2>/dev/null)${C0}"
    hr ╰ ╯
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

# ---------- dữ liệu GitHub ----------
SB="java -cp 20.jar nro.models.data.SqlBackup"
gh_menu() {
    local c k u t
    echo "  ${B}Dữ liệu GitHub${C0} ${DIM}(database mã hoá: $( [ -f backup.key ] && echo "${G}đã bật" || echo "${R}chưa có mật khẩu")${C0}${DIM})${C0}"
    echo "  ${DIM}Sau mỗi lần bảo trì server tự đẩy sql/team2026.sql.enc lên GitHub.${C0}"; echo
    printf "  ${CY}1${C0}  Đặt mật khẩu mã hoá (mọi máy dùng chung)\n  ${CY}2${C0}  Đẩy dữ liệu lên GitHub ngay\n  ${CY}3${C0}  Tải dữ liệu từ GitHub, nạp đè máy này\n  ${CY}4${C0}  Lưu token GitHub (cần để đẩy)\n  ${CY}5${C0}  Quên mật khẩu\n\n"
    printf "  Chọn: "; read -rsn1 c; echo "$c"; echo
    case $c in
        1) read -rsp "  Mật khẩu (từ 6 ký tự, không hiện khi gõ): " k; echo
           [ ${#k} -ge 6 ] || { err "Mật khẩu quá ngắn."; return; }
           printf '%s\n' "$k" > backup.key; chmod 600 backup.key; ok "Đã lưu backup.key. Máy khác đặt đúng mật khẩu này để nạp." ;;
        2) [ -f backup.key ] || { err "Chưa đặt mật khẩu (mục 1)."; return; }
           ensure_db >/dev/null 2>&1; spin "Xuất database mã hoá + đẩy GitHub" $SB export push && ok "Đã đẩy." ;;
        3) [ -f backup.key ] || { err "Chưa đặt mật khẩu (mục 1)."; return; }
           printf "  ${Y}Dữ liệu trên máy này sẽ bị ghi đè. Bấm ${B}y${C0}${Y} để tiếp tục:${C0} "; read -rsn1 k; echo
           [ "$k" = y ] || { echo "  Huỷ."; return; }
           local was=0; running && was=1 && stop
           git checkout -- Config.properties 2>/dev/null
           spin "Tải bản mới từ GitHub" git pull --ff-only
           config_db
           [ -f sql/team2026.sql.enc ] || { err "GitHub chưa có sql/team2026.sql.enc."; return; }
           spin "Giải mã và nạp database" $SB import && ok "Đã nạp dữ liệu từ GitHub."
           [ $was = 1 ] && start ;;
        4) echo "  Tạo token: github.com > Settings > Developer settings > Personal access tokens"
           echo "  (quyền Contents: Read and write cho repo này)."
           read -rp "  Tên GitHub: " u; read -rsp "  Token (không hiện khi gõ): " t; echo
           [ -n "$u" ] && [ -n "$t" ] || { echo "  Huỷ."; return; }
           git config credential.helper store
           printf 'https://%s:%s@github.com\n' "$u" "$t" > "$HOME/.git-credentials"; chmod 600 "$HOME/.git-credentials"
           ok "Đã lưu token (chỉ trên máy này)." ;;
        5) # mật khẩu không khôi phục được từ file mã hoá: xem lại trên máy đã lưu, hoặc đặt mật khẩu mới rồi mã hoá lại từ database máy này
           if [ -f backup.key ]; then
               printf "  Máy này đang lưu mật khẩu. Bấm ${B}y${C0} để hiện: "; read -rsn1 k; echo
               [ "$k" = y ] && echo "  Mật khẩu: ${Y}$(head -1 backup.key)${C0}"
               echo
           fi
           echo "  ${DIM}Không nhớ mật khẩu thì bản trên GitHub không giải mã được nữa.${C0}"
           echo "  ${DIM}Đặt mật khẩu mới: mã hoá lại database ĐANG CHẠY trên máy này rồi đẩy đè lên GitHub.${C0}"
           printf "  Đặt mật khẩu mới? Bấm ${B}y${C0}: "; read -rsn1 k; echo
           [ "$k" = y ] || { echo "  Huỷ."; return; }
           read -rsp "  Mật khẩu mới (từ 6 ký tự, không hiện khi gõ): " k; echo
           [ ${#k} -ge 6 ] || { err "Mật khẩu quá ngắn."; return; }
           read -rsp "  Nhập lại: " u; echo
           [ "$k" = "$u" ] || { err "Hai lần nhập không khớp."; return; }
           [ -f backup.key ] && cp backup.key backup.key.old
           printf '%s\n' "$k" > backup.key; chmod 600 backup.key
           ensure_db >/dev/null 2>&1
           if spin "Mã hoá lại database bằng mật khẩu mới + đẩy GitHub" $SB export push; then
               ok "Xong. Máy khác (VPS) đặt mật khẩu mới này ở mục 1 để nạp."
           else
               [ -f backup.key.old ] && mv backup.key.old backup.key
               err "Chưa đẩy được (kiểm tra token mục 4). Đã giữ lại mật khẩu cũ."
           fi ;;
        *) echo "  Huỷ." ;;
    esac
}

view_log() {
    echo "  ${DIM}Đang xem log · chạm Ctrl rồi C để thoát${C0}"; echo
    trap 'true' INT
    tail -n 40 -f "$LOG" | sed -u -e "s/.*\(Exception\|ERROR\|Error\|lỗi\).*/${R}&${C0}/" -e "s/.*Login.*/${CY}&${C0}/"
    trap - INT
}

lag_help() {
    cat <<EOF
  ${Y}${B}Chơi trên chính điện thoại chạy server bị lag / đứng${C0}
  Android tự tạm dừng Termux khi mở app khác (game),
  server đứng theo. Làm đủ các bước:

  ${G}1${C0} Kéo thanh thông báo, ở Termux bấm ${B}Acquire wakelock${C0}
  ${G}2${C0} Cài đặt > Ứng dụng > Termux > Pin: ${B}Không hạn chế${C0}
  ${G}3${C0} Tuỳ chọn nhà phát triển (bấm 7 lần Số hiệu bản dựng):
     - ${B}Tạm ngưng thực thi ứng dụng trong bộ nhớ đệm${C0}
       (Suspend execution for cached apps): ${B}Tắt${C0}
     - ${B}Tắt hạn chế tiến trình con${C0} (Disable child
       process restrictions, Android 14+): ${B}Bật${C0}
  ${G}4${C0} Xiaomi / Oppo / Vivo: Tự khởi chạy Termux = Bật,
     khoá Termux trong đa nhiệm (biểu tượng ổ khoá)
  ${G}5${C0} Chắc chắn nhất: ${B}chia đôi màn hình${C0} hoặc cửa sổ nổi,
     Termux luôn hiện thì Android không dừng được

  Làm xong khởi động lại server (phím 3).
EOF
}

key() { printf "$(c256 16)\e[48;5;%sm${B} %s ${C0}" "$1" "$2"; }   # nút phím có nền màu
two() { printf "   %s %s\n" "$(pad "$(key $1 $2) $3" 19)" "${4:+$(key $4 $5) $6}"; }
section() { printf "  $(c256 214)${B}%s${C0} ${DIM}%s${C0}\n" "$1" "$(printf '%*s' $(( IW - $(vlen "$1") )) '' | sed 's/ /┄/g')"; }

menu() {
    local first=1 c
    while :; do
        status ${first:+anim}; first=
        echo
        section "SERVER"
        if running; then two 240 1 "${DIM}Đang chạy${C0}" 196 2 "Tắt"; else two 46 1 "${G}${B}Bật server${C0}" 196 2 "Tắt"; fi
        two 220 3 "Khởi động lại" 51 4 "Xem log"
        section "DỮ LIỆU"
        two 201 5 "Cập nhật" 39 6 "Sao lưu"
        two 39 7 "Khôi phục"
        section "CÀI ĐẶT"
        two 147 8 "Đổi RAM" 147 9 "Mật khẩu"
        two 34 G "Dữ liệu GitHub" 208 L "Hết lag"
        two 240 0 "Thoát"
        echo
        printf "  ${DIM}Bấm số để chọn · tự làm mới mỗi 15 giây${C0} "; read -rsn1 -t 15 c || continue
        printf '\r\e[K'; echo
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
            l|L) lag_help; pause ;;
            g|G) gh_menu; pause ;;
            0) printf '\e[H\e[2J'; echo; draw_art 0; echo; ok "Server vẫn chạy nền. Gõ ${B}nro${C0} để mở lại menu."; exit 0 ;;
        esac
    done
}

case "$1" in
    setup) setup ;; start) start ;; stop) stop ;; restart) stop; start ;;
    log) view_log ;; update) update ;; backup) backup ;; restore) restore ;;
    ram) ram ;; ip) ip_info ;; pass) set_pass ;; github) gh_menu ;; start-menu) start; menu ;; *) menu ;;
esac

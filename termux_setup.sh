#!/data/data/com.termux/files/usr/bin/bash
# Cài server NRO trên điện thoại Android bằng Termux (chạy 1 lần): Java 21, MariaDB, database team2026.
# Cách dùng: đặt thư mục nro_server trong Termux (vd ~/nro_server), rồi: bash termux_setup.sh
set -e
cd "$(dirname "$0")"
DB_USER=nro
DB_PASS=${DB_PASS:-nro123}

pkg update -y
pkg install -y openjdk-21 mariadb

# Khởi tạo và bật MariaDB (lần đầu)
if [ ! -d "$PREFIX/var/lib/mysql/mysql" ]; then
    mysql_install_db
fi
if ! mysqladmin ping >/dev/null 2>&1; then
    (mysqld_safe >/dev/null 2>&1 &)
    for i in $(seq 1 30); do mysqladmin ping >/dev/null 2>&1 && break; sleep 1; done
fi

# MariaDB trên Termux chỉ cho user Termux đăng nhập qua socket: tạo user riêng cho server (JDBC đăng nhập bằng mật khẩu qua TCP)
mysql -u "$(whoami)" <<SQL
CREATE DATABASE IF NOT EXISTS team2026 CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
CREATE USER IF NOT EXISTS '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON *.* TO '$DB_USER'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

# Nạp dữ liệu nếu database còn trống
if [ "$(mysql -u "$(whoami)" -N -e "select count(*) from information_schema.tables where table_schema='team2026'")" = "0" ]; then
    echo "Đang nạp sql/team2026.sql ..."
    mysql -u "$(whoami)" team2026 < sql/team2026.sql
fi

# Cấu hình server dùng user vừa tạo
[ -f Config.properties ] || cp Config.default.properties Config.properties
sed -i "s/^database.user=.*/database.user=$DB_USER/; s/^database.pass=.*/database.pass=$DB_PASS/; s/^database.host=.*/database.host=127.0.0.1/" Config.properties

echo "Xong. Chạy server: bash run_termux.sh"

#!/data/data/com.termux/files/usr/bin/bash
# Chạy server NRO trên Termux: giữ máy không ngủ, bật MariaDB nếu chưa chạy, rồi chạy như VPS yếu.
cd "$(dirname "$0")"
command -v termux-wake-lock >/dev/null && termux-wake-lock  # Android không tắt Termux khi tắt màn hình
if ! mysqladmin ping >/dev/null 2>&1; then
    (mysqld_safe >/dev/null 2>&1 &)
    for i in $(seq 1 30); do mysqladmin ping >/dev/null 2>&1 && break; sleep 1; done
fi
RAM_MAX=${RAM_MAX:-512m} sh run_vps_yeu.sh

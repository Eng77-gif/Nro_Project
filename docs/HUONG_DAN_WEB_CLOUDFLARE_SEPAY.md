# Gắn Cloudflare và SePay cho trang web người chơi

Trang web người chơi chạy cùng server game ở cổng `web.port` (mặc định 8080). Tài liệu này hướng dẫn:

1. Đưa web lên tên miền có **https** qua **Cloudflare**.
2. Tự động cộng tiền chuyển khoản bằng **SePay**.

---

## 1. Cloudflare

Chuẩn bị: tên miền đã thêm vào Cloudflare (nameserver trỏ về Cloudflare).

### Cách A (khuyên dùng): Cloudflare Tunnel

Không cần mở cổng nào trên VPS, https có sẵn, giấu IP máy chủ.

1. Trên VPS cài `cloudflared` (Ubuntu / Debian):
   ```bash
   curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb -o cloudflared.deb
   sudo dpkg -i cloudflared.deb
   ```
   Windows: tải `cloudflared-windows-amd64.msi` ở cùng trang releases.
2. Cloudflare Dashboard → **Zero Trust** → **Networks** → **Tunnels** → **Create a tunnel** → chọn **Cloudflared**, đặt tên (vd. `nro-web`).
3. Chép lệnh cài đặt Cloudflare đưa ra (dạng `sudo cloudflared service install <token>`) rồi chạy trên VPS. Tunnel hiện **Healthy** là được.
4. Tab **Public Hostname** → **Add a public hostname**:
   - Subdomain: để trống hoặc `www`; Domain: tên miền của anh.
   - Service: `HTTP` → `localhost:8080`.
5. Sửa `Config.properties` rồi khởi động lại server:
   ```properties
   web.host=127.0.0.1      # chỉ nhận từ cloudflared, không lộ cổng 8080 ra ngoài
   web.cloudflare=true     # lấy IP thật của người chơi từ header CF-Connecting-IP
   ```
6. Mở `https://ten-mien-cua-anh` để kiểm tra.

### Cách B: proxy DNS (đám mây cam)

1. Cloudflare → **DNS** → thêm bản ghi `A`, tên `@`, trỏ IP VPS, bật **Proxied** (đám mây cam).
2. **SSL/TLS** → chế độ **Flexible**: Cloudflare https với người chơi, http tới VPS.
3. Cloudflare chỉ nối tới VPS đúng cổng người chơi gõ, nên cần đổi cổng.
   - Vào **Rules** → **Origin Rules** → **Create rule**.
   - Hostname bằng tên miền → **Destination Port**: rewrite to `8080`.
4. Mở cổng 8080 trên tường lửa VPS. Tốt nhất chỉ cho các dải IP của Cloudflare (https://www.cloudflare.com/ips/).
5. `Config.properties`: `web.cloudflare=true`.

### Lưu ý

- `web.cloudflare=true` chỉ tin header IP khi yêu cầu đến từ dải IP Cloudflare hoặc từ chính máy chủ (tunnel). Người ngoài không giả IP được để né chống spam.
- **Không** đưa panel quản trị (cổng 8088) lên tên miền công khai. Nếu cần quản trị từ xa qua tunnel, thêm hostname riêng và bật **Cloudflare Access** (Zero Trust → Access → Applications) để chỉ email của anh vào được.
- Cổng game (14445) **không** đi qua Cloudflare. Người chơi vẫn kết nối thẳng IP VPS như cũ.

---

## 2. SePay: tự động cộng tiền chuyển khoản

SePay đọc biến động số dư tài khoản ngân hàng và gọi về server mỗi khi có tiền vào. Server tìm mã `NAP<số>` trong nội dung chuyển khoản, khớp với yêu cầu nạp trên web rồi tự cộng tiền. Không còn phải duyệt tay.

1. Đăng ký tại https://my.sepay.vn, liên kết tài khoản ngân hàng nhận tiền.
   - Dùng đúng tài khoản đã điền ở `web.bank.code` / `web.bank.account`.
2. **Cấu hình công ty** → **Cấu trúc mã thanh toán**: thêm tiền tố `NAP`.
   - Không bắt buộc: server tự đọc `NAP<số>` trong nội dung.
3. **Tích hợp WebHooks** → **Thêm webhooks**:
   - Sự kiện: **Có tiền vào**.
   - Gọi đến URL: `https://ten-mien-cua-anh/callback/sepay`.
   - Kiểu chứng thực: **API Key**. Tạo một chuỗi ngẫu nhiên dài (vd. 40 ký tự) và dán vào ô API Key.
   - Bỏ qua nếu không có mã thanh toán: **Không**. Server vẫn lưu giao dịch để anh xem.
4. Dán cùng chuỗi đó vào `Config.properties` rồi khởi động lại server:
   ```properties
   web.sepay.apikey=chuoi-ngau-nhien-cua-anh
   ```
5. Thử: nạp 10.000đ trên web, chuyển khoản đúng nội dung `NAP<mã> <tài khoản>`.
   - Vài giây sau lịch sử nạp chuyển sang **Thành công**, số dư tăng.
   - Nếu chưa được: panel → **Nạp qua web** → bảng **Giao dịch ngân hàng từ SePay** cho biết lý do. Trang webhook của SePay có nhật ký gọi.

### Cách server xử lý

- Chỉ nhận yêu cầu có header `Authorization: Apikey <web.sepay.apikey>`; sai key trả 401.
- Mỗi giao dịch lưu theo mã giao dịch SePay (`web_bank_txn.sepay_id` duy nhất). SePay gửi lại nhiều lần cũng chỉ cộng 1 lần.
- Cộng theo **số tiền thật nhận được** (chuyển thiếu / thừa thì cộng đúng số đó, ghi chú lại).
- Mỗi yêu cầu `NAP<mã>` chỉ cộng 1 lần. Người chơi chuyển 2 lần cùng nội dung thì lần sau hiện ở bảng "Chưa khớp" để anh xử lý tay.
- Giao dịch tiền ra, không có mã NAP, hoặc mã không tồn tại: chỉ lưu lại, không cộng.

---

## 3. Thẻ cào qua Cloudflare

Khi web có tên miền, đặt link callback ở cổng gạch thẻ là `https://ten-mien-cua-anh/callback/card`.

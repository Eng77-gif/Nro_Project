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

## 1a. Đang dùng: Cloudflare Worker, chưa cần tên miền

Trang chủ chạy ở **https://nro-web.dov77471.workers.dev** (Cloudflare Worker `nro-web`). Worker gồm:

- Giao diện `data/web/index.html`.
- Bộ chuyển tiếp `cloudflare/lib/proxy.js` qua `cloudflare/worker.js`, chuyển `/api/*` và `/icon/*` về server game qua `BACKEND_URL = http://160-191-243-165.sslip.io:8080`.
  - `sslip.io` là tên miền miễn phí tự trỏ về IP VPS, vì Cloudflare không cho gọi thẳng địa chỉ IP.

Trên VPS Windows, chạy **một lần** `cloudflare/setup_vps_windows.bat`: chuột phải → **Run as administrator**, dán `PROXY_KEY`. File này làm 2 việc:

- Mở cổng 8080 trên tường lửa Windows.
- Ghi `web.proxy.key` vào `Config.properties`.

Sau đó khởi động lại server game. Nếu nhà cung cấp VPS có tường lửa riêng trên trang quản lý, mở thêm cổng TCP 8080 ở đó.

**Cập nhật trang** sau khi sửa `data/web/index.html`:

```bash
CF_API_TOKEN=... CF_ACCOUNT_ID=... BACKEND_URL=http://160-191-243-165.sslip.io:8080 PROXY_KEY=... bash cloudflare/deploy_worker.sh
```

`PROXY_KEY` phải trùng `web.proxy.key` trên VPS.

**Khi có tên miền**: làm theo mục 1 (Tunnel) và 1b để có https từ đầu tới cuối, rồi đổi `BACKEND_URL` sang `https://api.ten-mien`.

---

## 1b. Trang chủ chạy riêng trên Cloudflare Pages (khuyên dùng)

Trang chủ (giao diện) được Cloudflare phục vụ từ khắp nơi. Server game tắt hay bảo trì thì trang chủ vẫn mở được, chỉ các mục cần dữ liệu báo "đang bảo trì".

Dữ liệu (đăng nhập, diễn đàn, nạp...) vẫn lấy từ server game: Pages Function trong thư mục `cloudflare/` chuyển tiếp `/api/*` và `/icon/*` sang server qua Cloudflare Tunnel. Người chơi chỉ thấy 1 tên miền.

```
người chơi ──https──> nro.ten-mien.com (Cloudflare Pages: trang tĩnh + Function)
                              │  /api/*, /icon/*
                              └──https──> api.ten-mien.com (Tunnel) ──> server game :8080
SePay / cổng thẻ ──https──> api.ten-mien.com/callback/sepay | /callback/card
```

### Bước 1: tunnel cho API

Làm như **Cách A** ở trên, nhưng hostname đặt là `api` (ví dụ `api.ten-mien.com` → `HTTP localhost:8080`).

### Bước 2: tạo dự án Pages nối với GitHub

Mỗi lần đẩy code lên `main`, Cloudflare tự dựng lại trang.

1. Cloudflare Dashboard → **Workers & Pages** → **Create** → **Pages** → **Connect to Git** → chọn repo của server.
2. Build settings:
   - Production branch: `main`
   - Framework preset: `None`
   - **Root directory**: `cloudflare`
   - **Build command**: `bash build.sh`
   - **Build output directory**: `public`
3. **Environment variables** (Settings → Variables and Secrets, cho Production):
   - `BACKEND_URL` = `https://api.ten-mien.com`
   - `PROXY_KEY` = một chuỗi ngẫu nhiên dài. Chọn loại **Secret** (mã hoá).
4. **Custom domains** → thêm `ten-mien.com` hoặc `nro.ten-mien.com`.

### Bước 3: cấu hình server game

```properties
web.host=127.0.0.1
web.cloudflare=true
web.proxy.key=<cùng chuỗi PROXY_KEY ở bước 2>
```

Khởi động lại server. `web.proxy.key` để server tin IP người chơi mà Pages gửi kèm (chống spam, khoá đăng nhập sai đúng người). Thiếu hoặc sai khoá thì header IP bị bỏ qua.

### Triển khai bằng lệnh (không nối GitHub)

```bash
cd cloudflare
bash build.sh
npx wrangler pages deploy public --project-name nro-web
```

Lệnh phải chạy trong thư mục `cloudflare` để wrangler tìm thấy thư mục `functions/`.

### Ghi chú

- Nguồn giao diện vẫn là **một file** `data/web/index.html`. `build.sh` chép nó sang `cloudflare/public/`. Sửa giao diện chỉ sửa file đó; server game vẫn phục vụ cùng trang ở cổng 8080 làm dự phòng.
- Webhook SePay và callback thẻ cào trỏ thẳng `https://api.ten-mien.com/callback/...`, không qua Pages.

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

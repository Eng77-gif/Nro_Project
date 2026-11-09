# Chữ ký số "Việt" (Code Signing)

`20.jar` được ký bằng chữ ký số riêng của **Việt**.
- Chứng chỉ: CN=Việt, O=Việt, C=VN, RSA 3072, SHA-256, hạn 10 năm (đến 30/10/2036).
- File công khai: `viet-codesign.cer` (ai cũng xem được, dùng để kiểm tra chữ ký).
- Vân tay SHA-256: `5B:7B:71:49:C3:A5:F2:0A:9D:95:BC:90:FE:BE:09:12:40:36:12:39:8F:54:07:87:9B:39:3B:22:3A:3F:C6:08`

## Kiểm tra jar có đúng chữ ký Việt không
```
jarsigner -verify -verbose -certs 20.jar
```
Thấy dòng `jar verified.` và `CN=Việt` là đúng. Nếu ai sửa file trong jar thì kiểm tra sẽ báo lỗi.

## Tự ký lại sau khi build
Cần file khóa riêng `viet-codesign.p12` và mật khẩu (Claude gửi riêng cho bạn, **không có trong git**):
- Windows: `tools\codesign\sign_jar.bat 20.jar viet-codesign.p12`
- Linux / VPS: `sh tools/codesign/sign_jar.sh 20.jar viet-codesign.p12`

Giữ kín `viet-codesign.p12` và mật khẩu: ai có 2 thứ này là ký được dưới tên Việt. Đừng đưa lên git, nhóm chat hay VPS người khác.

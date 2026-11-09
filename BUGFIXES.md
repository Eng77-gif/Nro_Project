# Danh sách lỗi đã sửa — Teamobi2026

Commit `26569b3` là mã nguồn gốc. Mỗi commit sau đó gom một nhóm lỗi; xem `git log -p` để biết chi tiết từng thay đổi.

## 1. Nhân bản đồ / in tiền

| Khu vực | Lỗi | File |
|---|---|---|
| Ký gửi | Người mua trả **vàng thường**, người bán lại nhận **thỏi vàng** → in tiền bằng 2 tài khoản | `shop_ky_gui/ConsignShopService.java` |
| Ký gửi | Điều kiện `x > 99 && x < 0` luôn sai nên không có giới hạn số lượng/giá; không kiểm tra ô hành trang (lỗi id); hành trang đầy thì mất đồ/tiền | `ConsignShopService.java` |
| Ký gửi | Lưu bằng `TRUNCATE` + insert không transaction, lỗi giữa chừng là mất toàn bộ shop | `ConsignShopManager.java` |
| Ký gửi | Đưa lên trang đầu: kiểm tra 50 ngọc nhưng trừ 5, `isUpTop` kiểu byte bị cộng dồn đến tràn âm, không lưu DB | `npc/NpcFactory.java` |
| Giao dịch | Một người bấm "Đồng ý" 2 lần là giao dịch tự chốt dù bên kia chưa đồng ý | `services_func/Trade.java` |
| Giao dịch | Không kiểm tra số vàng thật đang có (vàng âm); hành trang bị ghi đè bằng bản sao lúc mở giao dịch; vẫn thêm được đồ sau khi khóa | `Trade.java` |
| Giao dịch với bot | Người chơi không bao giờ nhận được đồ của bot; số lượng bị làm tròn lên | `Trade.java`, `Bot/BotGiaoDich.java` |
| Nhặt đồ | `isPickedUp` không bao giờ được bật → gửi 2 gói nhặt cùng lúc thì nhận đồ 2 lần | `map/Zone.java` |
| Ghép đồ | Gửi cùng một ô hành trang 2 lần thì 1 vật phẩm được tính là 2 nguyên liệu; vẫn ghép được với đồ đã chuyển khỏi hành trang | `combine/CombineService.java` |
| Shop đặc biệt | Mua bằng ngọc **không bị trừ ngọc** | `shop/ShopService.java` |
| Shop | Giá hồng ngọc lại so với ngọc xanh; shop điểm sự kiện có giá mặc định 0; shop Chipi không thu tiền; SANTA_HEAD đổi kiểu tóc tùy ý miễn phí; trừ tiền trước rồi mới kiểm tra điều kiện | `ShopService.java` |
| Cộng tiềm năng | `point * (...)` bị tràn int → giá âm hoặc rất rẻ | `player/NPoint.java` |
| Thách đấu | Lớp cha gọi `start()` trước khi tiền cược được gán → không trừ cược nhưng người thắng vẫn nhận thưởng | `matches/ThachDau.java` |
| Form nhập | Form của admin (bot, tặng đồ, đổi tên…) không kiểm tra quyền; tặng ngọc bị tràn int (âm); tặng hồng ngọc lại trừ ngọc xanh; bán thỏi vàng nhận số âm; đổi VND không kiểm tra kết quả trừ tiền trong DB | `services_func/Input.java`, `database/PlayerDAO.java` |
| Vòng quay | Gửi số lượt âm → giá âm → được cộng ngọc/vàng | `services_func/LuckyRound.java` |
| Hàm dùng chung | `subQuantityItem` với số âm biến phép trừ thành phép cộng | `services/InventoryService.java` |
| Võ đài sinh tử | Hiển thị phí bằng "thỏi vàng" nhưng trừ vàng thường | `DeathOrAliveArenaService.java` |
| Chuyển hóa trang bị | Không kiểm tra đủ 2 tỷ vàng (vàng âm); bản "Ngọc" hiển thị 5000 ngọc nhưng lại trừ 2 tỷ vàng | `combine/ChuyenHoaTrangBi_*.java` |

## 2. Bảo mật / phân quyền

- **Bang hội**: chỉ cần gửi `clanId` là vào được bất kỳ bang nào (không cần lời mời) → giờ phải có lời mời, hiệu lực 5 phút.
- Giải tán bang ở NPC không kiểm tra quyền bang chủ; các thành viên khác vẫn giữ tham chiếu tới bang đã bị xóa.
- Bang chủ tự đuổi được chính mình (bang mất chủ).
- Mã bảo vệ 6 số dò được không giới hạn → khóa 10 phút sau 5 lần sai; trước đây còn chấp nhận cả mã âm.
- Path traversal ở lệnh xin ảnh theo tên (`../`).
- Tạo nhân vật: chấp nhận giới tính ngoài 0–2 và cho tạo nhiều nhân vật trên 1 tài khoản.
- Giftcode không bao giờ hết hạn (code so ngày tạo với ngày hết hạn); số lượt giftcode bị trừ trong race condition.

## 3. Crash / NullPointerException / logic

- Đổi đệ tử Uub/Kid Beerus/Jiren: gán `pet = null` rồi đọc `pet.gender` → crash và người chơi mất đệ tử; đổi Jiren lại tạo ra Kid Beerus.
- `MapService.getAllMaps()` gọi đệ quy vô hạn.
- `itemBodyToBox` có điều kiện kiểm tra bị đảo ngược nên chức năng cất đồ đang mặc vào rương không chạy.
- Vật phẩm loại 25 bị mặc 2 lần do switch fall-through (đồ cũ bị mặc lại).
- Danh hiệu bị thêm 2 lần; NPE khi nâng skill đệ tử; NPE ở các boss Majin Buu khi nhận sát thương không phải từ người chơi; NPE khi rồng thần rời đi; NPE ở màn hình nâng cấp vật phẩm.
- Kiểm tra trùng kẻ thù so sánh một phần tử với chính nó.
- VuaHung: `List<Integer>.contains(long)` luôn trả về false → trồng dưa nhiều lần trong một phase.
- `removeClanPlayer` gọi đệ quy lại khi lỗi DB → StackOverflowError.
- Học kỹ năng có thể làm tiềm năng âm; menu "Hủy học kỹ năng" không có code xử lý.
- Máu/sát thương boss phó bản bị tràn int ở cấp cao; x3/x5/x7 HP/KI ở Ngọc Rồng Sao Đen bị tràn khiến người chơi chết luôn.
- Minigame "Chọn ai đây": quỹ thưởng bị tràn int (người thắng nhận số âm); thông báo thắng đi qua đường chat của người chơi nên bị trừ ngọc và giới hạn thời gian.
- Giftcode giới hạn tiền ở 2 tỷ vàng / 200 triệu ngọc nên **làm giảm** tiền của người đang có nhiều hơn mức đó.

## 4. Đa luồng & cơ sở dữ liệu

- Pool kết nối DB `database.max=1` → chỉ cần một truy vấn lồng nhau là cả server treo; đã nâng lên 2/10.
- Reset quà hằng ngày kết nối cứng tới DB `ngocrong` (root, mật khẩu rỗng) thay vì DB đang dùng.
- Danh sách giao dịch, bang hội, rồng thần, giải đấu và chat thế giới được dùng chung giữa nhiều luồng mà không đồng bộ (ConcurrentModificationException); id bang bị trùng khi 2 bang được tạo cùng lúc; danh sách bang tải lên không theo thứ tự nhưng lại được tìm bằng binary search.
- `SimpleDateFormat` tĩnh dùng chung nhiều luồng; log admin không bao giờ được ghi.
- Rò rỉ statement và SQL ghép chuỗi trong phần dọn lịch sử giao dịch và cập nhật bảng xếp hạng.

## 5. Chạy được trên Linux / MySQL mới

- Code đọc `tile_set_info` nhưng file thật tên `tile_set_Info` → chỉ chạy được trên Windows.
- Vòng lệnh console ném `NoSuchElementException` khi chạy nền (không có stdin).
- `mysql-connector-java` 5.1.23 → 5.1.49 (thay thế trực tiếp, checksum khớp Maven Central). Bản 5.1.23 bị crash với MariaDB ≥ 10.10 / MySQL 8. Không nâng lên 8.x vì code dùng `rs.first()` trên ResultSet forward-only.

## Việc cần làm thêm (không sửa trong code)

- **`sql/team2026.sql` có sẵn 3 tài khoản admin `1`, `11`, `111` với mật khẩu `1`.** Hãy đổi mật khẩu hoặc xóa trước khi mở server.
- Mật khẩu tài khoản đang lưu dạng plain text (do web đăng ký quyết định).
- Lệnh khởi động lại dùng `cmd /c start restart_server.bat`, chỉ chạy được trên Windows.
- File `client/LÂU CỒ MOD/XUNGLORDLOCAL_Data/resources.assets.resS` (126 MB, vượt giới hạn 100 MB của GitHub) đã được cắt thành `.part00` + `.part01`. **Trước khi chạy client, hãy chạy `client/ghep_file_client.bat`** (Windows) hoặc `ghep_file_client.sh` để ghép lại.

## Cấu trúc sau khi gộp

- `sql/team2026.sql` là file SQL duy nhất. `nro1.sql` có cùng 41 bảng và cùng cấu trúc nhưng là bản cũ hơn; khác biệt duy nhất là 123 dòng bảng `part` mà `team2026.sql` đã cập nhật. Đã sửa 1 dòng `part` bị trùng id (vị trí 1919 bị ghi nhầm thành id 1949).
- `20.jar` được build lại từ code đã sửa (bản cũ chứa code còn lỗi). `run.bat` vẫn chạy `java -jar 20.jar` như trước.
- `data/` là tài nguyên game của server; `client/` là bản client mod; `Lệnh admin.docx` là tài liệu lệnh admin.
- `build/` và `dist/` là output cũ do NetBeans tự sinh từ code chưa sửa nên không đưa lên.

## Đã kiểm tra

- Toàn bộ mã nguồn biên dịch không lỗi (Java 17).
- SpotBugs chạy lại: tất cả lỗi mức High/Correctness liên quan đã biến mất, không có cảnh báo mới.
- Đã khởi động thật với MariaDB và `team2026.sql`: tải đủ dữ liệu, server lắng nghe cổng 14445, không có exception.
- Client test theo giao thức game: đăng nhập, tạo nhân vật (giới tính sai bị từ chối), một bên bấm đồng ý giao dịch 2 lần thì bị chặn, giao dịch hợp lệ chuyển đúng 5.000.000 vàng, đưa ra số vàng nhiều hơn số đang có thì giao dịch bị hủy.

> **Lưu ý:** id vật phẩm trong các mục đợt 1–5 ở trên là id cũ. Từ đợt 6, id đã xếp lại theo DataNRO; bảng đổi id cũ → mới nằm ở `sql/doi_id_vat_pham.txt`.

## Vật phẩm mới (gói res team 18th7 / 22th8 / 18th9)

Đã thêm 542 icon cho cả 4 mức zoom (x1–x3 tự thu nhỏ từ x4), 59 part mới (id 2099–2157) và 19 vật phẩm vào các slot trống id 1961–1979 trong `sql/team2026.sql`:

| Id | Tên | Loại |
|---|---|---|
| 1961 | Cải trang Broly Xanh | cải trang |
| 1962 | Cải trang Naruto | cải trang |
| 1963 | Cải trang Sasuke | cải trang |
| 1964 | Cải trang Hề Xanh | cải trang |
| 1965 | Cải trang Akatsuki | cải trang |
| 1966 | Cải trang Pan Trung Thu (+100% KI) | cải trang |
| 1967 | Cải trang Kính Vàng | cải trang |
| 1968 | Cải trang Hồn Ma Tóc Xanh | cải trang |
| 1969 | Cải trang Tóc Bạc | cải trang |
| 1970 | Cải trang Tóc Trắng | cải trang |
| 1971 | Cải trang Zamasu Hợp Thể | cải trang |
| 1972 | Cải trang Akaza | cải trang |
| 1973 | Cải trang Beerus (+70% sức đánh, đầu động 3 frame) | cải trang |
| 1974 | Cải trang Nappa Aura (+100% HP) | cải trang |
| 1975 | Pet Cửu Vĩ | pet đi theo |
| 1976 | Pet Robot Đỏ | pet đi theo |
| 1977 | Pet Cừu Bông | pet đi theo |
| 1978 | Pet Hề Súng Nước | pet đi theo |
| 1979 | Pet Ngựa Gỗ | pet đi theo |

- Tên do mình đặt theo hình, hãy đổi lại trong bảng `item_template` nếu cần. Vật phẩm chưa có chỉ số (option) và chưa được bán ở shop nào. Hãy thêm qua admin, giftcode hoặc `item_shop`.
- Pet loại 27 giờ tự hoạt động theo part trong `item_template`, không cần thêm `case` trong code. Thay đổi này cũng sửa pet Ghost Rider (1900) trước đây không bao giờ hiện ra.
- `vsData` và `vsItem` được tăng lên 10 để client tải lại dữ liệu vật phẩm và part.
- 4 frame động tác bị thiếu trong gói đầu (17275, 17281, 17369, 17386) đã được bổ sung từ gói `Icon19th7`, part dùng lại frame gốc. Gói này có thêm avatar 17122 cho bộ Hề Xanh và các icon 17390–17393.
- Các icon 17232–17235 trên server cũ là ảnh trống, không dùng ở đâu; đã được thay bằng hình của bộ Hồn Ma Tóc Xanh.
- Còn khoảng 80 icon trong gói không gắn với part nào (frame Sharingan 17054–17088, cúp World Cup, khóa, đèn lồng Doraemon, thỏ mặt trăng…). Mình đã chép các icon này vào `data/icon`, nhưng chưa tạo vật phẩm vì gói không có thông tin để biết chúng là gì.

- Beerus, Nappa Aura và Pan Trung Thu được bán ở SHOP_VIP › tab Cải trang với giá 300 ngọc, giống các cải trang VIP khác. Chỉ số nằm trong `item_shop_option`, nên vật phẩm phát qua admin hoặc form tặng đồ cũng nhận đúng chỉ số này.

## Đợt Trung Thu / World Cup 2026 (chỉ số, vật phẩm mới, vòng quay, boss)

### Chỉ số mặc định theo vật phẩm
Bảng mới `item_template_option` (template_id, option_id, param, param_max, chance, group_id) lưu chỉ số của từng vật phẩm, hỗ trợ:
- khoảng ngẫu nhiên, ví dụ Pet Xên "17–23%";
- tỉ lệ có dòng, ví dụ Pet Kuma "10% có chống lạnh";
- nhóm chọn 1 dòng, ví dụ Lồng Đèn "1 dòng ẩn trong các dòng sau".

Hàm `getListOptionItemShop` ưu tiên đọc bảng này, nên vòng quay, boss rơi đồ và lệnh admin/tặng đồ đều ra đúng chỉ số.

### Chỉ số mới có tác dụng thật
| Option | Tên | Cách tính |
|---|---|---|
| 10 | Sát thương chuẩn #% | Khi đánh người chơi, phần % này bỏ qua né, giáp % và giáp trừ. Khi đánh boss, cộng thẳng vào sát thương. |
| 204 | Tấn công +#% lên Boss | Nhân thêm khi mục tiêu là boss |
| 251 | Sát thương cuối +#% | Nhân vào sát thương cuối cùng |
| 252 | Chống năng lượng xấu | Miễn hiệu ứng "hôi, giảm HP" của người xung quanh (ví dụ E10) |
| 253 | +#% TN, SM cho đệ tử khi sư phụ mặc | Cộng vào TN, SM đệ tử nhận |
| 254 | Akaza: HP dưới 50% tăng #% sức đánh 3 giây | Hồi chiêu 180 giây |
| 255 / 256 / 258 | Hiệu ứng đi cùng | Naruto + Pet Kuma +7% HP; Sasuke + Cờ Vạn Hoa Đồng +1% sát thương cuối; Ván bay WC 2026 + Cờ World Cup 2026 +7% chí mạng |
| 257 | Ảo Thuật (Cờ Vạn Hoa Đồng) | +50% sức đánh và giảm 50% sát thương nhận vào trong 3 giây, hồi chiêu 120 giây, tự kích hoạt khi bị đánh |

Các option của danh hiệu (chí mạng, sát thương chuẩn…) trước đây bị bỏ qua, chỉ có sức đánh/HP/KI/né có tác dụng. Giờ mọi option của danh hiệu đều được tính. Đã sửa dữ liệu danh hiệu KOL bị ghi nhầm.

### Vật phẩm
- Chỉ số theo ảnh cho Akaza, Naruto, Sasuke, Akatsuki, Beerus, Nappa Aura, Pan Trung Thu 2026, Pet Kuma (đổi tên từ Pet Cửu Vĩ), Pet Ngựa Thành Troy. Hề Xanh đổi tên thành "Cải trang chú hề Picolo" theo DataNRO.
- Vật phẩm mới, id 1980–1995:

| Id | Tên | Ghi chú |
|---|---|---|
| 1980 | Bóng Mờ Trung Thu | |
| 1981 | Lồng Đèn Dôremon | hình động đèn Doraemon trên lưng |
| 1982 | Cờ Vạn Hoa Đồng | hình động Sharingan |
| 1983–1984 | Đại Bàng Đất Sét Nổ, bản VIP | |
| 1985 | Ván bay WC 2026 | |
| 1986 | Cờ World Cup 2026 | |
| 1987 | Ván bay Trung Thu 2026 | |
| 1988 | Bóng WC 2026 | |
| 1989 | Pet Minion 2026 | |
| 1990–1992 | Pet Xên bọ hung con / hoàn thiện / Max | |
| 1993–1995 | Danh hiệu Phá Cổ Trung Thu / VIP / Perfect | |

- Vật phẩm danh hiệu (type 36) giờ dùng được để kích hoạt danh hiệu: hạn theo vật phẩm, không có hạn thì vĩnh viễn.
- Có đủ 3 Pet Xên vĩnh viễn (trong hành trang, rương hoặc trên người) thì tự nhận danh hiệu Perfect khi vào game hoặc khi mặc Pet Xên.

### Nguồn nhận
- **Vòng quay ngọc/vé:** mỗi ô có 3% ra vật phẩm đặc biệt (danh sách trong `RewardService.SPECIAL_WHEEL_ITEMS`). Mỗi món có 3% vĩnh viễn (×3 so với 1% của vật phẩm thường), còn lại có hạn 3–15 ngày.
- **Boss Super Broly:** giáp 65%. Rơi đá nâng cấp, item chí mạng, đồ thần linh (cả găng), ngọc rồng 1–4 sao, sao pha lê cấp 2 ×4–8, sao pha lê, đồ cấp 8–11 (3% ra đồ 8 sao), set Hủy Diệt 1% (từ đợt 3 là SKH Thần hủy diệt Beerus), 1 triệu vàng 5%.

### Phần tạm hoặc chưa làm được
- Gói tài nguyên không có hình nên mình tạm mượn:
  - Đại Bàng dùng hình bay của sao chổi trắng (mount 25).
  - Ván bay WC 2026 dùng tàu vàng (33); Ván bay Trung Thu 2026 dùng mây vàng (34).
  - 3 Pet Xên cùng dùng hình CT Xên nhí (chưa có bản xanh/xanh lá/đỏ).
  - Pet Minion 2026 dùng hình Pet Minion.
  - Bóng WC 2026 và Cờ World Cup 2026 dùng icon cúp.
  - Bóng Mờ Trung Thu chưa có hình trên lưng.
  - Danh hiệu mới chưa có hiệu ứng hình (idEffect 300–302).
- Tỉ lệ rơi của boss và vòng quay do mình đặt (ảnh không ghi), chỉnh trong `SuperBroly.reward` và `RewardService`.
- "Hộp đá cấp 9" và "Hộp NRO Xanh" chưa có trong database. Boss rơi đá nâng cấp thay cho hộp đá; Hộp NRO Xanh chưa rơi.
- Tanjiro, Inosuke (2 bản), Zenitsu, Nezuko, Pet cậu vàng, "Cờ Mãi Dell Thành Công" chưa có hình nên chưa tạo. Thượng Tam = Akaza đã có.
- "Hề Hước: +5% né cho người xung quanh" của chú hề Picolo mới chỉ là mô tả, chưa có hiệu ứng.

## Đợt 3: Đường Phố, SKH Beerus, bóng bước chân, hộp quà, đua top Quy Lão

### Vật phẩm
| id | Tên | Chỉ số |
|---|---|---|
| 1996 | Cải trang Bulma Đường Phố | SĐ 25%, SĐ +25.000, SĐ chí mạng 11%, chí mạng 7% |
| 1997 | Cải trang Lý Tiểu Nương Đường Phố | HP 25%, HP +30.000, giảm ST 10%, né 10% |
| 1998 | Cải trang Android 18 Đường Phố | (ảnh chưa ghi chỉ số) |
| 1973 | Cải trang Beerus (cập nhật) | SĐ 70%, SĐ lên Boss 20%, đi cùng SKH Beerus +10% chí mạng |
| 1999 | Bóng bước chân Hoa Hồng Xanh | SĐ 3–7%, SĐ +3.000–10.000, đi cùng Pet Xên hoàn thiện +6% ST chí mạng |
| 1940 | Bóng bước chân Hoa Hồng Đỏ | HP 3–7%, HP +5.000–15.000, đi cùng Pet Xên Max +5% giảm ST |
| 1941 | Ván Rồng 7 Sao | SĐ 12%, SĐ lên quái 7%, lên Boss 7%, SĐ chí mạng 7%, chí mạng 7%, hút máu 7%, vàng rơi 7% |
| 1942–1945 | Capsule vỡ, Mảnh capsule vỡ, Khóa chỉ số bông tai cấp 3, Trứng Xên | nguyên liệu |
| 1946–1951 | Thiệp chúc VIP, Hộp quà TT 2026 VIP, Hộp bánh TT 2026, Hộp bánh TT chay 2026, Quà Trăng Tròn, Quà Trăng Khuyết | hộp mở ngẫu nhiên |

Phần hình 3 cải trang Đường Phố là part 2158–2166, lấy từ `Nr_Part.sql` của gói team new 16th10. `vsData` và `vsItem` tăng lên 11 để client tải lại dữ liệu.

### SKH Thần hủy diệt Beerus
- Đồ Hủy Diệt rơi từ Super Broly (1%) mang dòng set 260 và 3 dòng mô tả 261–263. (Đợt 9 đổi sang đồ cấp 12.)
- Mặc 2 món: +10% SĐ, +10% ST chí mạng.
- Mặc 4 món: +15% SĐ, +20% ST chí mạng.
- Mặc 5 món: +30% SĐ, +30% ST chí mạng.
- Set không có aura. Aura lấy theo thứ tự: thẻ radar, rồi aura thưởng top.

### Bóng bước chân (ô riêng)
- Dùng vật phẩm để mang vào ô riêng, không chiếm ô cờ hay ô pet. Mang món khác thì món cũ trả về hành trang.
- Chat `thao buoc chan` để tháo ra.
- Ô này lưu ở bảng `player_extra`.
- Client cũ không có ô này nên chỉ có chỉ số, chưa hiện hình dưới chân.

### Hộp quà (bảng `item_box_reward`)
- Mỗi dòng gồm vật phẩm (-1 vàng, -2 ngọc, -3 hồng ngọc), số lượng, tỉ trọng, các mức HSD và % ra vĩnh viễn. Sửa bảng rồi khởi động lại server là có tác dụng.
- Hộp 20/10 (1720) cũng dùng bảng này.
- Tỉ lệ do mình đặt, ảnh không ghi.
- Vật phẩm chưa có trong database nên chưa đưa vào hộp: Chi Chi Áo Cưới, Pan VIP, Năng lượng tà ác, Bunma Rider mới, Janemba, Pet ngựa KPI, Ván bay Magikarp, Hộp nâng cấp đồ thiên sứ, Tờ mảnh VIP, Cánh hào khí, Chi Chi giỗ tổ, Thiên sư phụ, Cờ ngọc rồng 4 sao / Super, Bóng sinh nhật NRO, Picolo Kid.
- Hiện chưa có nguồn rơi Thiệp chúc VIP và các hộp Trung Thu. Admin phát bằng lệnh `item`, hoặc thêm vào shop / boss.

### Quy Lão
- **Đua top ngọc rồng:** tặng 1 bộ ngọc rồng 1–7 sao được 1 điểm, có nút tặng tất cả và nút xem top 10.
- **Chốt top:** admin chat `chottopql` để chốt top 100 và đưa điểm về 0.
  - Người đang online nhận quà ngay. Người offline nhận khi vào game, nếu hành trang đủ chỗ.
  - Top 1–3 nhận thêm aura 122/121/120 trong 30 ngày (`Event2026Service.TOP_AURA_DAYS`).
- **Quà Trung Thu:** đổi Quà Trăng Tròn bằng 99 điểm sự kiện, Quà Trăng Khuyết bằng 20 triệu vàng.

## Đợt 4: gói Icon11th7
- Bù 6 icon đang thiếu: Đai lưng 1752–1754 (14523–14525), item 673 (15313) và 2 khung chân của part 2017 (15199, 15200).
- Thêm Pet Charmander (id 1952, part 2167–2169) vào Hộp bánh Trung Thu 2026 và Hộp bánh chay (HSD 7/15/30 ngày, 5% vĩnh viễn). Ảnh chưa ghi chỉ số nên pet chưa có chỉ số.
- `vsData` và `vsItem` tăng lên 13.
- Icon 17662 (1 khung chân của Cải Trang Póc đường phố, part 2166) đã bổ sung từ gói `resteam`.
- Hợp thể Bông tai cấp 3 của Trái Đất đổi sang Goku Blue cầm kiếm: 3 khung đầu động, part 2170–2174, array_head_2_frames 53. Trước đây Trái Đất dùng chung thân và chân với Xayda.
- Thêm Cải trang Poc Thỏ Đen (id 1953, part 2175–2177) vào Hộp quà Trung Thu 2026 VIP (HSD 7/15/30 ngày, 20% vĩnh viễn vì có bảo hiểm). Ảnh chưa ghi chỉ số nên chưa có chỉ số.

## Đợt 6: xếp lại id vật phẩm theo DataNRO
Nguồn: `ItemTemplates.json` của DataNRO (TeaMobi), gồm 2103 vật phẩm, id 0–2102.

**Cách ghép:**
- Ghép từng món của server với danh sách chính thức theo tên và icon. Những món từng mượn icon thì ghép tay.
- Tên lấy theo DataNRO. Riêng 5 món DataNRO chỉ đặt tên tạm "Cải trang N" (2048–2052) thì giữ tên của server.
- Icon cũng lấy theo DataNRO nếu server đã có file icon đó.
- Cột `description` chỉ dài 75 ký tự nên mô tả dài hơn bị cắt.

**Kết quả** (chi tiết ở `sql/doi_id_vat_pham.txt`):
- Giữ nguyên id: 1759 món.
- Đổi sang id chính thức: 133 món. Phần lớn nằm ở dải 1758–1999, nơi server cũ tự đánh số riêng; vd. Bông tai Porata cấp 3 1819 → 1884, Cải trang Beerus 1973 → 2082, bóng bước chân → Bóng mờ hoa hồng 2096/2097.
- Thêm 223 món của DataNRO mà server chưa có. Các món này mới chỉ có tên, icon và loại, chưa có hình mặc hay chức năng.
  - Loại 18 (pet), 19 và 38 (bóng mờ) của client mới được đổi thành loại 27.
  - Cờ không giữ `part` vì id flag_bag của server khác bản chính thức.
- 9 món riêng của server không có trong DataNRO nhưng code hoặc DB vẫn dùng: dời ra sau, id 2103–2111. Gồm Hộp thần linh, 2 thẻ Oozaru phụ, Phiếu thức ăn, Super 2/3/4 sao, Vé KOL VIP và Capsule vỡ.
- 19 món không có trong DataNRO và không còn chỗ nào dùng: bỏ, trong đó có Thiệp chúc VIP và các "Cải trang Gốc" thiếu icon.
- Các món trùng gộp lại: Pet Minion 2026 vào Pet Minion 2059, Pet Xên Max cũ vào Pet Xên bọ hung MAX 2038.

**Những chỗ đã đổi theo id mới:**
- **Bảng DB:** `item_template_option`, `item_box_reward`, `item_shop`, `data_badges`, `radar`, `shop_ky_gui`, cùng đồ trong dữ liệu người chơi và ô bước chân.
- **Code Java:** 114 dòng dùng id cứng; các số không phải id vật phẩm (thời gian 1800, id part, toạ độ…) giữ nguyên.
- **Kiểm tra Rồng nhí:** trước là khoảng 1765–1771, nay là 1814–1819 và 1929.

File `sql/team2026.sql` được xuất lại từ DB đã đổi. `vsData` và `vsItem` tăng lên 14.

**Lưu ý khi cập nhật server đang chạy:** nếu database đang có người chơi thật thì đừng import đè. Sao lưu database, rồi chạy trên chính database đó (đúng thứ tự):

1. `python3 tools/remap.py tools/DataNRO_ItemTemplates.json <tên_db> out tools/remap_code_ids.txt`
2. `mysql <tên_db> < out/remap.sql`
3. `python3 tools/remap_players.py <tên_db> out/map.json`

Bước 3 đổi luôn đồ trong hành trang, rương và trên người.

## Đợt 7: bù icon từ gói Hunr2025 và Icon11th7
- Gói Hunr2025 chỉ có 12.550 icon x4 (`Small0`–`Small32249`). Phần lớn giống icon server đã có; những icon id lớn còn lại thuộc server khác, không có vật phẩm nào dùng nên không lấy.
- Đã bù 79 icon mà vật phẩm, part, avatar, cờ hoặc thẻ radar đang dùng nhưng còn thiếu: 60 icon từ Icon11th7, 19 icon từ Hunr2025. Đã tạo đủ x1–x4.
- Phần lớn là icon của các món mới thêm từ DataNRO, vd. Ván bay Magikarp, Bóng sinh nhật NRO, Pet Pikachu/Squirtle/MewTwo, Kochiro, Musashi, Thú cưỡi rồng.
- Còn 574 icon thiếu (17410–17735 và 20097–32052) mà cả hai gói đều không có. Phần lớn thuộc các part riêng của source cũ.

## Đợt 8: Thẻ khỉ con và bảng chỉ số (theo video)
**Thẻ khỉ con** (radar id 2040, thẻ no.20):
- Sưu tầm bằng Mảnh hồn khỉ con (id 2040). Cần 10 mảnh mỗi cấp, tối đa Lv.3. Hình thẻ là Goku GT Kid.
- Chỉ số:
  - Mở khóa: Sát thương cuối +1%.
  - Sử dụng Lv.1: Sát thương cuối +1%, HP +10%, KI +10%.
  - Sử dụng Lv.2: Sát thương cuối +2%, Sức đánh +10%.
  - Sử dụng Lv.3: Sát thương cuối +2%, Chí mạng +10%, Giảm 5% sát thương.
- Shop Đổi thưởng của Quy Lão bán Mảnh hồn khỉ con giá 8250 điểm sự kiện, không giao dịch được.

**Đổi cách tính thẻ radar cho giống bản chính thức:**
- Dòng "Mở khóa" (activeCard 0) nay có tác dụng với mọi thẻ đã sưu tầm, kể cả khi không dùng. Trước đây chỉ có tác dụng với thẻ đang dùng mà còn ở cấp đầu.
- Dòng "Sử dụng Lv.x" có tác dụng khi đang dùng thẻ ở đúng cấp x.
- Chỉ số của thẻ không có xử lý riêng (vd. Sát thương cuối) nay được cộng như chỉ số đồ.
- Cấp tối đa của thẻ lấy theo cấp cao nhất ghi trong thẻ, tối thiểu là 2.

**Bảng chỉ số chi tiết:** client là Unity đã biên dịch nên không thêm được bảng nổi như video. Thay vào đó:
- Chat `chiso` để xem chỉ số của mình, `chiso de` để xem chỉ số đệ tử.
- Bảng gồm: HP, MP, Sức đánh, Chí mạng, STCM, Giáp, Giảm sát thương, Sát thương chuẩn, Sát thương cuối, Sát thương Boss, Né đòn, Hút máu, Hút KI, TNSM, Vàng rơi từ quái, và các set đang kích hoạt.

## Đợt 9: Hộp đen bí ẩn và SKH Beerus theo bản chính thức
**Hộp đen bí ẩn** (id 2095):
- NPC Chi Chi bán giá 20 ngọc, có nút mua 1 hộp hoặc 10 hộp.
- Mở ra nhận ngẫu nhiên một trong các món:
  - Bóng WC 2026: có bảo hiểm, 20% vĩnh viễn
  - Cải trang Hit
  - Mảnh thẻ VIP (Tờ mảnh VIP)
  - Năng lượng tà ác
  - Pet rắn cute
  - Phượng hoàng băng
  - Pet Pikachu
  - Cải trang Goku cận vô cực
- Đồ mặc được có HSD 7/15/30 ngày hoặc vĩnh viễn. Tỉ lệ do mình đặt vì bài đăng không ghi.

**Đua top mua hộp:**
- Mỗi hộp mua được tính 1 điểm. Xem top ở Chi Chi; admin chat `chottophdba` để chốt top.
- Quà:
  - Top 1 (theo bài đăng): Danh hiệu Đón Xuân 180 ngày, Đá bảo vệ x100, Capsule 1 món kích hoạt x3, Capsule vỡ x3, Bông tai Porata cấp 3, Giáp tập luyện cấp 5.
  - Top 2–3: Đá bảo vệ x70/x50, Capsule 1 món kích hoạt x2/x1, Capsule vỡ x2/x1.
  - Top 4–10: Đá bảo vệ x20, Capsule vỡ x1.
  - Quà top 2 trở đi do mình đặt vì ảnh bài đăng bị cắt.
- Danh hiệu Đón Xuân (1978) đã có dòng trong `data_badges` (idEffect 303) để kích hoạt được, nhưng chưa có chỉ số.

**SKH Thần hủy diệt Beerus:**
- Dòng mô tả đổi theo bản chính thức: "[2] Tăng vừa phải…", "[4] Tăng đáng kể…", "[5] Tăng mạnh sức đánh và sát thương chí mạng". Đồ mang set này không giao dịch được.
- Super Broly rơi set này trên đồ cấp 12 (jean Calic / vàng Zealot / lưỡng long, Rada cấp 12) thay cho đồ Hủy Diệt.
- "Set kích hoạt Beerus 1 món tự chọn" (2102): dùng để chọn Áo/Quần/Găng/Giày/Rada đúng hành tinh.

**Chưa có chức năng:**
- Pet rắn cute và Pet Pikachu chưa có hình đi theo, vì chưa có part.
- Năng lượng tà ác chưa có hiệu ứng khi dùng.

## Đợt 10: tool tải và nhập res, sửa part 1999
**Sửa lỗi part 1999:** part đầu 1999 chỉ có 2 khung, trong khi client luôn đọc đầu đủ 3 khung. Vì vậy client đọc lệch mọi part từ 2000 trở đi, gồm cả các cải trang mới thêm. Đã thêm khung trống cho đủ 3 và tăng `vsData` lên 15 để client tải lại part.

### `tools/res_download.py`: tải res từ một server NRO
Tool đăng nhập server như một client thật, rồi tải về:
- icon (`--icons 0-20000`)
- toàn bộ part, ghi ra `Nr_Part.sql` (`--parts`)
- mount, aura và ảnh theo tên (`--mounts 0-120 --auras 0-130 --names ...`)
- effect (`--effects 0-1200`, cần đăng nhập)
- file trong `data/res` (`--res`)

Ví dụ:
```
python tools/res_download.py --host <ip> --port 14445 --user <tk> --password <mk> --icons 0-20000 --parts --mounts 0-120 --auras 0-130 --effects 0-1200 --out res_out
```
- Chỉ dùng với server bạn có quyền tải dữ liệu, bằng tài khoản của bạn.
- Server chính thức của TeaMobi có thể đòi chuỗi phiên bản hoặc thông tin client khác; đổi bằng `--platform` và `--version`.
- Tải nhanh quá có thể bị server chặn; chỉnh nhịp tải bằng `--batch`, `--wait`, `--delay`.

### `tools/res_import.py`: nhập gói res vào server
Tool đọc một thư mục gói res (gói team hoặc thư mục do `res_download.py` tải về):
- Icon, mount/aura và effect: chép vào `data/`. Ảnh chỉ có cỡ x4 được tự thu nhỏ ra x1–x3.
- Part trong `Nr_Part.sql`:
  - Mặc định chỉ thêm part dùng icon có trong gói và chưa có y hệt trong database.
  - Part mới được đánh id nối tiếp id lớn nhất hiện có; bảng đổi id thư viện → id mới ghi ở đầu file SQL.
- Mount/aura: tự thêm dòng `img_by_name` với số khung (lấy từ `img_by_name.json` hoặc tự đoán).

Ví dụ:
```
python tools/res_import.py "D:/res team new 16th10" --db team2026 --apply
```
- Thêm `--apply` để chạy SQL bằng lệnh `mysql`; không có thì chỉ ghi file `res_import.sql`.
- `--parts 2064-2072` để chỉ nhập các part chỉ định; `--overwrite` để ghi đè file đã có.
- Nhập xong nhớ tăng `vsData` để client tải lại part.

## Đợt 11: giao diện web cho tool res, sửa 2 lỗi làm client đọc sai dữ liệu
### `tools/res_web.py`: giao diện web
Chạy `python tools/res_web.py` rồi mở `http://127.0.0.1:8765` (thêm `--bind 0.0.0.0` để máy khác trong mạng LAN mở được). Trang có 3 tab:
- **Tải res:** điền host/port/tài khoản, chọn icon, part, mount, aura, effect, file res. Có ô "Server TeaMobi chính thức".
- **Nhập res:** nhập thư mục gói, hoặc kéo thả file .zip để tải lên; chọn database rồi bấm Nhập.
- **Xem:** duyệt icon, mount/aura, effect của một thư mục, có lọc theo tên/id.

Mỗi lần chạy hiện nhật ký trực tiếp và có nút Dừng. Tool chỉ cần Python 3 và Pillow.

### Lỗi 1: danh sách vật phẩm vượt 65.535 byte
- Gói `-28` chỉ có 2 byte độ dài. Từ khi có 2112 vật phẩm, gói thứ hai (id 750–2111) bị cắt, client đọc lệch mọi gói sau đó.
- Đã chia thành nhiều gói "thêm vật phẩm", mỗi gói dưới 60 KB.
- Server giờ in cảnh báo nếu còn gói nào vượt 65.535 byte.

### Lỗi 2: quá 255 dòng chỉ số
- Server gửi số lượng dòng chỉ số bằng 1 byte, nên client LÂU CỒ chỉ đọc được tối đa 255 dòng. Có 266 dòng (id 0–265), client chỉ đọc được 10.
- 11 dòng mới đã dời về các id trống hoặc chưa nơi nào dùng:

| Dòng chỉ số | Id cũ → mới |
|---|---|
| Pet Cửu Vĩ Hồ | 255 → 172 |
| Cờ Vạn Hoa Đồng | 256 → 39 |
| Ảo Thuật | 257 → 90 |
| Cờ World Cup | 258 → 91 |
| Đi cùng SKH Beerus | 259 → 92 |
| Set Beerus | 260 → 115 |
| Mô tả [2] / [4] / [5] | 261 / 262 / 263 → 163 / 167 / 168 |
| Pet Xên hoàn thiện | 264 → 169 |
| Pet Xên Max | 265 → 42 |

- Các dòng 251–254 giữ nguyên. Id ghi ở các đợt trên là id cũ.
- **Database đang chạy thật:** chạy `python tools/migrate_option_ids.py --db <tên_db>`. Script đổi luôn chỉ số trong đồ người chơi, ký gửi, danh hiệu và thẻ radar.
- `vsItem` tăng lên 15.

## Đợt 12: `tools/NroResTool.exe`, tool tải res chạy thẳng trên Windows
- Một file exe duy nhất, không cần cài Python hay Java. Bấm đúp là tool tự mở giao diện trên trình duyệt; tắt bằng cách đóng cửa sổ đen.
- Tab **Tải res**: phần **Server** điền host/port/tài khoản một lần; phần **Tải từng mục** có nút tải riêng cho:
  - res (`data/res`)
  - part (ghi ra `Nr_Part.sql`)
  - icon (id)
  - effect (id, cần tài khoản để vào game)
  - mount (id)
  - aura (id)
  - ảnh theo tên
- Mỗi lần chỉ chạy một mục để không dồn yêu cầu làm server quá tải. Ô "Bỏ qua file đã có" (bật sẵn) chỉ tải những id còn thiếu, nên server bảo trì giữa chừng thì tải lại cùng khoảng id là chạy tiếp. Nhật ký các lần tải được ghi nối vào `nhat_ky.txt`.
- Server chính thức ngừng trả lời sau khoảng 100 yêu cầu mỗi phiên: tool tự mở phiên mới (đăng nhập lại) khi 2 đợt liền không nhận được gì, và khi tích "Server TeaMobi chính thức" thì cứ 90 yêu cầu lại mở phiên mới.
- Ô "Server TeaMobi chính thức" bật thêm định dạng gói của server gốc. Nếu bị server chặn vì tải nhanh, chỉnh mục "Nâng cao" (số yêu cầu mỗi đợt, thời gian chờ).
- Tab **Xem**: duyệt icon, mount/aura, effect vừa tải.
- Kết quả lưu vào thư mục `res_out` cạnh file exe, cùng cấu trúc với `data/` của server: `icon/x4`, `img_by_name/x4`, `effect/x4`, `effdata`, `res/x4`, `Nr_Part.sql`, `img_by_name.json`.
- Server chính thức giới hạn lưu lượng (ảnh aura, mount có thể gần 1 MB mỗi ảnh). Tool xử lý như sau:
  - Tải mount/aura từng ảnh một.
  - Mỗi phiên server chỉ trả vài ảnh rồi im, nên khi server ngừng trả lời thì tool mở phiên mới ngay và gửi lại đúng chỗ đó. Phiên mới vẫn im thì chờ 1, 2, 5 rồi 10 phút trước khi mở phiên tiếp.
  - "Mở phiên mới sau N yêu cầu" chỉ áp dụng cho icon. Effect, mount, aura không tự đổi phiên nữa, vì mỗi lần đổi phiên server bắt chờ 28 giây.
  - Nhật ký và giao diện ghi số phiên bản tool để biết đang chạy bản nào.
  - Một ảnh không có trả lời: tool gửi lại một ảnh chắc chắn có (ảnh nhỏ nhất đã tải) để kiểm tra. Server trả ảnh kiểm tra thì ảnh kia không có trên server (ví dụ aura_8): bỏ qua ngay và ghi vào `khong_co_tren_server_<mục>.txt`. Không trả cả ảnh kiểm tra mới là bị giới hạn: đăng nhập lại, chờ dần lâu hơn.
  - Một ảnh không có trả lời thì tạm bỏ qua và thử cái kế tiếp (có thể server không có ảnh đó). Cái kế tiếp cũng im mới coi là bị giới hạn. Cuối cùng thử lại các ảnh đã bỏ qua với phiên mới; còn thiếu thì ghi vào `chua_tai_duoc_<mục>.txt`. Nhật ký ghi dung lượng ảnh nhận được mỗi phiên.
  - Hiểu lệnh 122 của server ("chờ N giây mới cho đăng nhập" khi đăng nhập lại quá nhanh): chờ đủ rồi tự đăng nhập lại. Nhật ký cảnh báo nếu đăng nhập xong mà chưa vào được game.
  - Chỉ bỏ qua khi chờ hết các lần trên mà vẫn không có trả lời.
- Chỉ dùng với server bạn có quyền tải dữ liệu.
- Mã nguồn ở `tools/nro-res-exe` (Go). Build lại bằng: `cd tools/nro-res-exe && GOOS=windows GOARCH=amd64 go build -ldflags "-s -w" -o ../NroResTool.exe .`


## Đợt 13: Sự kiện theo mùa (theo các bài thông báo sự kiện chính thức)
Sự kiện tự bật và tắt theo lịch mỗi năm: Tết, Giỗ tổ, Trung Thu tính theo âm lịch (thuật toán Hồ Ngọc Đức), các sự kiện khác theo dương lịch. Tất cả tham gia ở **NPC Santa** (Đảo Kamê, Đảo Guru, Vách núi đen), nút đầu tiên "Sự kiện".

| id | Sự kiện | Thời gian | Nội dung chính |
|---|---|---|---|
| 1 | Tết Nguyên Đán | mùng 1 Tết đến +17 ngày | Nấu bánh chưng, bánh tét (nhận thêm Capsule Tết 2026); top Lì xì 2026, Pháo hoa, Hộp Nón Tết; boss Lân con |
| 2 | Valentine | 10/2 - 17/2 | Hoa hồng giấy, Sôcôla đổi hộp quà |
| 3 | Phái đẹp 8/3 | 6/3 - 31/3 | Giấy màu, Hộp đựng quà, Hộp quà Nhẹ Nhàng / Chỉn Chu, tặng quà, trồng hoa hồng; top tặng hộp Chỉn Chu (danh hiệu Em xinh em đẹp) |
| 10 | Cá tháng 4 | 1/4 - 20/4 | Hộp Cá Tháng Tư (ngọc), Hộp Tháng Tư (điểm sự kiện), 2 top |
| 4 | Giỗ tổ Hùng Vương | 8/3 âm - 14/4 âm | Dâng sính lễ (Ngà voi, Cựa gà, Hồng mao), nấu và dâng Bánh chưng Lang Liêu, Bánh dầy, Hộp Hào Khí; 3 top; boss Sơn Tinh, Thủy Tinh |
| 11 | Quốc tế Thiếu nhi | 1/6 - 15/6 | Phiếu bé ngoan đổi Hộp quà thiếu nhi |
| 5 | Sự kiện Hè | 16/6 - 18/8 | Săn cá (Cá nóc, Cá bảy màu, Cá diêu hồng, Xô cá), tìm vỏ ốc, nước mía, vé World Cup; 2 top. Thức ăn cho Thần (Tayaki...) chỉ rơi trong Hè |
| 6 | Trung Thu | 21/8 - 18/10 (theo rằm tháng 8) | Làm bánh Gà Quay / hảo hạng (30% thêm Thập Cẩm) / Hạt Sen, đổi Pet Thỏ xám, Thỏ trắng, Mèo đen; Đuôi khỉ, Cà rốt; top Hộp bánh 2026 và Chay; boss Khỉ đột, Nguyệt thần |
| 7 | Halloween | 29/10 - 19/11 | Kẹo bàn tay chế Hộp Kẹo Ma Quỷ, đổi Kẹo mù, Ngọc bí ngô gọi Rồng Xương, ước nguyện 50 ngọc; 3 top; boss Bí ma, Ma trơi, Dơi |
| 8 | Nhà giáo 20/11 | 15/11 - 22/11 | Bông hoa, Thiệp chúc, Sách giáo khoa |
| 9 | Giáng sinh | 18/12 - 1/1 | Chuông, Kẹo giáng sinh, Tất; boss Ông già Noel |

- **Dữ liệu** nằm trong các bảng, sửa xong gõ `sukien reload` là áp dụng, không cần khởi động lại:
  - `season_event`: lịch (dương/âm, số ngày trước/sau ngày chính), boss gọi ra.
  - `season_event_drop`: vật phẩm rơi từ quái hoặc boss, tỉ lệ /10000, giới hạn mỗi ngày, lọc theo mẫu quái / boss / bản đồ.
  - `season_event_exchange`: công thức (nhóm nút, nguyên liệu, vàng/ngọc/hồng ngọc/điểm sự kiện); quà có HSD hoặc vĩnh viễn, quà thêm theo tỉ lệ, "mở ngay" (tặng quà NPC), điểm sự kiện, điểm top.
  - `season_event_top` + `season_event_top_reward`: đua top và quà theo hạng.
  - `item_box_reward`: quà trong hộp.
- **Đua top**:
  - Tự chốt khi hết sự kiện, quà vào hành trang khi người chơi vào game.
  - Admin chốt sớm bằng `sukien chottop <id>`, xem danh sách id bằng `sukien top`.
- **Lệnh admin**:
  - `sukien`: xem trạng thái các sự kiện.
  - `sukien bat|tat|tudong <id>`: bật tay, tắt tay, hoặc trả về theo lịch.
  - `sukien ngay yyyy-mm-dd`: giả lập ngày để thử; `sukien ngay` trả về ngày thật.
  - `sukien reload`: nạp lại dữ liệu.
- **Sửa lỗi kèm theo**:
  - Các luồng boss sự kiện (Tết, Hùng Vương, Trung Thu, Halloween, Noel) trước đây không chạy nên boss đứng im; giờ đã chạy.
  - Hộp quà giáng sinh (648) và Hộp quà giỗ tổ (1823, 1824) dùng id vật phẩm cũ trước khi xếp lại id; giờ lấy quà từ `item_box_reward`.
  - Menu Santa xếp lại theo danh sách chức năng, không còn lệch nút khi có Phiếu giảm giá.
- **Phần đơn giản hóa so với bản chính thức**:
  - Nguyên liệu lấy từ võ đài, nhiệm vụ Bò Mộng, phó bản được chuyển thành rơi khi đánh quái, có giới hạn mỗi ngày.
  - Không làm các phần sau: Ra Đa Ngọc Rồng VIP (không có vật phẩm), KOL, Hái lộc, Ngũ Hành Sơn, Thành Cổ, rước đèn, thời gian chờ trồng hoa.


## Đợt 14: Ngựa ăn cỏ (Tết), Đại chiến Zombie (Halloween), hình pet ngựa
- **Ngựa ăn cỏ** (Tết):
  - Ba ngựa con (Ngựa Đỏ, Ngựa Be, Ngựa Trắng) xuất hiện ngẫu nhiên ở các bản đồ ba hành tinh, có thông báo toàn server.
  - Ngựa không đánh ai và không bị đánh chết.
  - Đứng gần ngựa (trong khoảng 150) và dùng **Bó rơm** để cho ngựa ăn, nhận 1 Capsule Tết 2026.
  - Ngựa Đỏ có 1% cho **Pet Ngựa Đỏ** vĩnh viễn.
  - Mỗi con ăn 30 bó rơm (hoặc sau 10-15 phút) thì chạy sang bản đồ khác.
  - Bó rơm: đánh quái (99/ngày) hoặc mua 1 ngọc ở Santa.
- **Đại chiến Zombie** (Halloween):
  - Từ 21h00 đến 22h00 mỗi ngày (giờ Việt Nam), 30 Zombie lang thang ở khu 2 trở lên, không vào làng tân thủ.
  - Zombie cắn ai thì người đó hóa bộ xương 5 phút.
  - Mỗi đòn đánh chỉ trừ tối đa 1% máu Zombie nên phải hợp lực.
  - Người hạ Zombie nhận 1 phần quà ngẫu nhiên (Cá Koi zombie, Cải trang bộ xương có hạn hoặc vĩnh viễn, Ngọc bí ngô, hồng ngọc...), kèm Kẹo bàn tay rơi ra đất.
  - **Rađa dò Zombie** (1 ngọc ở Santa) hiện bản đồ và khu của các Zombie đang lang thang.
  - Admin gọi Zombie ngay để thử: `sukien zombie [phút]`.
- **Hình pet**: thêm part cho Pet Ngựa Đỏ, Ngựa Be, Ngựa Trắng, Ngựa Thần Tài, Kirby, Rồng xương. Trước đây các pet này mặc vào không hiện hình. `vsData` lên 16 để client tải lại part. Quà top Hộp bánh Trung Thu Chay đổi từ Pet ngựa chiến (chưa có hình) sang Pet Ngựa Be.
- **Sửa lỗi**: hiệu ứng hóa ma Halloween (Bí ma, Ma trơi, Dơi, Zombie) trước đây không bao giờ hết; giờ hết đúng thời gian.
- **SQL**: tất cả nằm trong một file `sql/team2026.sql`.


## Đợt 15: Halloween theo video hướng dẫn chính thức
- **Rồng Xương**:
  - Đủ 7 viên Bí ngô 1–7 sao thì dùng một viên để gọi rồng (chỉ trong sự kiện Halloween, không gọi ở làng hay nhà).
  - Rồng chờ 5 phút. Ước xong hoặc hết giờ thì rồng đi; phải chờ 1 phút mới gọi lại được.
  - 9 điều ước, chia hai trang như trong game:
    1. Đổi 3 kỹ năng đầu của đệ tử (cấp 1, có thể trùng).
    2. X3 tiềm năng sức mạnh đệ tử 30 phút.
    3. +15% HP khi hợp thể bông tai Porata 30 phút.
    4. +15% KI khi hợp thể bông tai Porata 30 phút.
    5. +15% sức đánh khi hợp thể bông tai Porata 30 phút.
    6. X3 tiềm năng sức mạnh bản thân 30 phút.
    7. Giảm 100 triệu sức mạnh (cần trên 40 tỷ).
    8. Pet Cerberus (chó địa ngục) hạn 15–90 ngày.
    9. 2% Quả trứng Mabư, hụt thì ra Rương sao pha lê VIP.
  - Thời gian các điều ước 2–6 lưu vào `player_extra.hlw_buffs`, hiện lại khi vào game.
  - Loại rồng gửi cho client mặc định là 1. Nếu client hiện sai hình, admin đổi bằng `sukien rongtype <số>`.
- **Bí ngô 1–7 sao** (tối đa 50/ngày):
  - Hạ Dơi, Ma trơi: phải mang **Giỏ đựng ngọc bí**, mất 1 giỏ mỗi lần nhặt.
  - Hạ Xên, Black Goku, Cumber: 30% ra 1 viên ngẫu nhiên, không cần giỏ.
- **Bà Hạt Mít / Santa**: Ước bằng 50 ngọc, Ước miễn phí (Vé ước miễn phí, có trong Kẹo mù).
- **Cửa hàng** theo giá trong video:
  - Giấy trang trí: 3 ngọc.
  - Giỏ đựng ngọc bí: 10 ngọc.
  - Máy dò ngọc rồng sự kiện: 30 ngọc.
  - Hòm Halloween: 99 ngọc.
  - Túi mù: 99 ngọc.
  - Capsule Halloween: 349 ngọc (Ván bay cung trăng, CT Android 21 Evil, Cánh thiên thần – Ác quỷ, Gohan Beast, Pet Ma tốc độ).
- **Túi mù Halloween** ra quà như video: Cờ hồn Xên bọ hung, Đá may mắn cấp 1, Cờ Ma đèn nhảy múa, Pet Cerberus, CT Cađíc SSJ4, Cánh thiên thần sa ngã, Cuốn sách cũ.
- **Zombie**: 10 triệu HP như trong game.
- **Hình pet**: thêm part cho Pet Cerberus.
- `season_event_drop` thêm cột `item_to` (rơi ngẫu nhiên trong khoảng id) và `need_item` (vật phẩm phải mang theo, mất 1 cái mỗi lần rơi).


## Đợt 16: Mục sự kiện ở đúng NPC như bản chính thức
- NPC nào có mục sự kiện đang diễn ra thì menu chính của NPC đó có thêm một nút ở cuối (tên sự kiện, ví dụ "Halloween"). Các nút cũ giữ nguyên vị trí. Bấm vào ra "<Sự kiện> vui vẻ / Ta có thể giúp gì cho con?" với các mục của NPC đó.
- Santa vẫn có đủ mọi mục như trước.
- Gán theo các bài thông báo và video:

| Sự kiện | NPC | Mục |
|---|---|---|
| Halloween | Bà Hạt Mít | Làm Hộp Kẹo Ma Quỷ (x1 / x10 / hết), Đổi Hộp Kẹo lấy Kẹo mù, Ước bằng 50 ngọc, Ước miễn phí |
| Halloween | Chi Chi | Shop sự kiện (Giỏ đựng kẹo, Giấy trang trí, Giỏ đựng ngọc bí, Máy dò, Capsule Halloween, Túi mù, Thiệp, Hòm, Rađa dò Zombie) |
| Tết | Nồi Bánh / Chi Chi | Nấu bánh / Cửa hàng |
| Valentine | Lý Tiểu Nương | Đổi quà |
| 8/3 | Lý Tiểu Nương, Bà Hạt Mít, Bunma / Chi Chi | Tặng quà / Cửa hàng |
| Cá tháng 4 | Chi Chi, Quy Lão | Hộp Cá Tháng Tư, Hộp Hề / Hộp Tháng Tư |
| Giỗ tổ | Hùng Vương / Nồi Bánh / Chi Chi, Quy Lão | Dâng sính lễ, Dâng bánh / Nấu bánh / Cửa hàng, Hộp Hào Khí |
| Hè | Quy Lão / Chi Chi | Xô cá, Vỏ ốc / Cửa hàng |
| Trung Thu | NPC Trung Thu / Chi Chi | Làm bánh, Đổi bánh, Đổi quà / Hộp bánh |
| Thiếu nhi, 20/11 | Chi Chi | Đổi quà |

- Gán NPC bằng cột `npc` (danh sách id mẫu NPC) trong `season_event_exchange`. Sửa rồi gõ `sukien reload` là áp dụng.

## Đợt 17: NPC sự kiện tự xuất hiện ở nhiều bản đồ
- Bảng mới `season_event_npc` (event_id, npc_id, map_id, x, y). Khi sự kiện bật, NPC trong bảng được đặt thêm vào bản đồ; khi sự kiện tắt thì tự gỡ. `x = 0` thì tự tìm chỗ trống gần giữa map (cách NPC khác ≥ 70px), `y = 0` thì lấy mặt đất.
- Dữ liệu mặc định:
  - Santa ở 3 làng (0, 7, 14).
  - Các NPC có mục sự kiện của mùa đó (Chi Chi, Bà Hạt Mít, Lý Tiểu Nương, Nồi Bánh, Hùng Vương, Trung Thu) ở 3 làng và Đảo Kamê / Đảo Guru / Vách núi đen.
  - Bản đồ đã có sẵn NPC đó thì bỏ qua. Quy Lão, Bunma giữ nguyên chỗ cũ.
- NPC đặt thêm (trừ Santa) chỉ mở các mục sự kiện của nó, nên không dính các chức năng gắn với bản đồ gốc. Santa mở được ở mọi map.
- `Map.npcs` đổi sang CopyOnWriteArrayList để thêm/gỡ NPC lúc server đang chạy. NPC mặc định (không có menu riêng) mở menu qua `createOtherMenu`, nên cũng có nút sự kiện.
- Người đang đứng trong map sẽ thấy NPC mới sau khi chuyển map hoặc đăng nhập lại.

## Đợt 18: Bổ sung icon/res tải từ server chính thức (NroResTool)
- Thêm 436 icon có trên server chính thức mà server chưa có, và 150 file res mới. Bản gốc x4, tự thu nhỏ ra x1/x2/x3.
- `DataGame.vsRes` lên 2 để client tải lại res.
- Không ghi đè 232 icon trùng số nhưng khác hình (ví dụ 11120, 11576–11655). Bộ part 1656–2081 của server này khác bản chính thức, nên thay ảnh sẽ làm lệch hình các đồ đang dùng. Cũng không nhập Nr_Part.sql chính thức, vì 434 part khác số với dữ liệu hiện tại.

## Đợt 19: Xoá dữ liệu không có trên bản chính thức
- 81 part (trong khoảng 1779–1942) dùng icon không tồn tại cả trên server lẫn bản chính thức (đa số số 20097 trở lên) được làm rỗng: mọi khung về `[0,0,0]`. Không xoá dòng, vì part được đánh số theo thứ tự dòng; xoá sẽ làm lệch mọi part phía sau. Các part này không vật phẩm, boss hay NPC nào dùng.
- Xoá 11 dòng `head_avatar` của các part đầu đó.
- 3 vật phẩm 2093 (Cải Trang Goku Ninja), 2094 (Pet ma dơi), 2099 (Cải Trang công chúa quỷ) không có icon và không có cách nhận: đổi tên thành "Vật phẩm đã xoá", icon 0. Giữ dòng để id các vật phẩm sau không lệch.
- Sau khi dọn, không còn part, vật phẩm hay avatar nào trỏ tới icon bị thiếu. `vsData` lên 17 để client tải lại part.

## Đợt 20: Giỗ tổ Hùng Vương theo video hướng dẫn 2026
- **Hùng Vương**: xuất hiện ở 3 làng khi có sự kiện, menu giống bản chính thức: Đến cổ thành, (Trồng dưa), Đổi dưa hấu, Dâng sính lễ, Dâng sính lễ xịn, Dâng bánh dầy, Dâng bánh chưng Lang Liêu. Các mục dâng tính điểm sự kiện và đua top.
- **Trồng dưa**:
  - Trồng vào phút 25–29 và 55–59 mỗi giờ (mỗi khung một lần), dưa chín đúng phút 00 / 30.
  - Thu hoạch ở nhà xong thì trồng được lượt mới.
  - Đổi dưa + Tem chứng nhận Mai An Tiêm nhận **hồng ngọc** 5 / 40 / 120 / 185 / 250 (trước là ngọc xanh).
- **Tìm sính lễ**:
  - Voi Chín Ngà (20 triệu máu, rơi Ngà voi) và Gà Chín Cựa (12 triệu máu, rơi Cựa gà) có ở mọi khu Rừng nấm khi bật sự kiện, chết thì 5 phút sau hồi sinh.
  - Hồng mao rơi từ boss.
  - Bảng mới `season_event_mob` (quái to theo sự kiện).
- **Sơn Tinh – Thủy Tinh**: hạ Thủy Tinh rơi Cải trang Sơn Tinh 7 ngày, hạ Sơn Tinh rơi Cải trang Thủy Tinh 7 ngày; 10% thêm Yêu quái núi lãng lãng 45 ngày.
- **Thành cổ**:
  - Mua Lệnh bài thành cổ (50 ngọc) ở Chi Chi, nhờ Hùng Vương đưa đến Thành cổ 1.
  - Hùng Vương ở mỗi map: Đi tiếp → Thành cổ 2 → Đấu trường thành cổ.
  - Lên sàn đấu với Gohan chiến binh (máu bằng máu người chơi, chỉ người lên sàn đánh được); thắng nhận Hộp quà thường Giỗ tổ.
- **Shop Chi Chi**:

  | Món | Giá |
  |---|---|
  | Cải trang Sơn Tinh, Cải trang Thủy Tinh (30 ngày) | 99 ngọc |
  | Tem chứng nhận | 2 ngọc |
  | Chả lụa (35 ngày) | 10 ngọc |
  | Bản đồ truyền thuyết (2 ngày) | 2 ngọc |
  | Pháo bông (30 ngày) | 10 triệu vàng |
  | Lệnh bài thành cổ (30 ngày) | 50 ngọc |

- **Hộp hào khí** (Quy Lão): thêm Cải trang Goku / Chi Chi giỗ tổ, CT Hắc Mị Nương, Mảnh hồn bông tai, Rương sao pha lê VIP, Đá Linh Hồn / Hiện Thực / Sức Mạnh / Thời Gian.
- **Top Hộp quà Cao Cấp, Dâng Bánh Dầy**: thêm Rađa ngọc rồng VIP (100 / 50 / 30 / 20 / 10 / 5).
- **Chỉ số đúng bản chính thức**:

  | Món | Chỉ số |
  |---|---|
  | Cải trang Sơn Tinh | SĐ 22%, giảm 15% sát thương, HP 27%, KI 22%, +20% TN SM, 12% né |
  | Cải trang Thủy Tinh | SĐ 27% (các chỉ số còn lại đặt giống Sơn Tinh) |
  | Yêu quái núi lãng lãng | SĐ 19%, HP 25%, KI 21%, +72% TN SM |
  | Cải trang Goku giỗ tổ | KI 35%, KI +17.000, 21% né |
  | Cải trang Chi Chi giỗ tổ | biến 15% tấn công thành HP, +30% vàng, giảm 50% SĐ / HP / KI và +150% SM TN vàng |
  | Cánh hào khí | KI 40%, KI +40.000, +10% vàng |

- **Sửa lỗi**: option "Giảm 50% sức đánh, HP, KI và +#% SM, TN, vàng" trong code dùng nhầm id 156 (trong DB là 155).

## Đợt 21: Bản đồ truyền thuyết
- Map mới **186 Rừng nguyên sinh**: bản sao map 17, 30 khu, không có cổng.
  - Thêm bằng SQL vào `map_template`, chép `data/map/tile_map_data/186` và `data/map/item_bg_map_data/186` từ map 17.
  - `vsMap` lên 3 để client tải lại danh sách map.
- Dùng **Bản đồ truyền thuyết** (1565, mua 2 ngọc ở Chi Chi):
  - Được đưa vào một khu trống riêng của Rừng nguyên sinh, có 1 trong 3 quái to (ngẫu nhiên): Voi Chín Ngà (20 triệu máu), Gà Chín Cựa (12 triệu), Ngựa Chín Hồng Mao (15 triệu).
  - Thời gian 10 phút. Hạ xong quái thì 30 giây sau, hoặc hết giờ, tự đưa về nhà.
- **Rơi đồ trong map này**: Ngà voi / Cựa gà / Hồng mao 3–5 cái (100%). Ngựa Chín Hồng Mao ở mọi nơi đều rơi Hồng mao.
- Quái 84 đổi tên "Ngựa Chín Lmao" thành "Ngựa Chín Hồng Mao".

## Đợt 22: Vòng quay Thượng Đế mùa Giỗ tổ
- Khi sự kiện Giỗ tổ Hùng Vương đang diễn ra, "Vòng quay đặc biệt" ở Thượng Đế (và quay bằng ngọc / vé) có 5% mỗi ô ra 1 trong 9 món:
  - Cải trang Mị Nương, Búa Stormbreaker, Kiếm ánh sáng, Pet Minion, Cờ mèo Hoàng Thượng
  - Cây nắp ấm, Cải trang Drabura hóa băng, Hào quang rực rỡ, CT Hắc Mị Nương
- **Quay càng nhiều tỉ lệ vĩnh viễn càng cao (cộng dồn đến hết sự kiện)**: 1% + 1% mỗi 20 lần quay, tối đa 10%; còn lại có hạn 3–15 ngày. Sau mỗi lượt quay có thông báo số lần đã quay và tỉ lệ hiện tại.
  - Số lần quay lưu trong `season_event_point` (top_id âm = bộ đếm theo mùa, ví dụ -401), khởi động lại server vẫn giữ.
  - Sự kiện kết thúc thật (hết lịch, hoặc admin `sukien tudong` khi đã quá ngày) thì tự xoá cùng lúc chốt top.
- Chỉ số:
  - Cải trang Mị Nương theo ảnh bản chính thức: SĐ 24%, Đẹp +20% SĐ cho mình và người xung quanh, +20% tốc độ chạy, HP 24%.
  - 7 món còn lại chưa có chỉ số được đặt mặc định:
    - Drabura: 20–25%
    - Các món đeo: 8–12%
    - Pet Minion: 6–10%
- **Sửa lỗi**: nút "Vòng quay đặc biệt" trước đây đi vào nhánh quay thường, nên vật phẩm đặc biệt (cả danh sách Trung Thu / World Cup) không bao giờ ra.

## Đợt 23: Hình đi theo cho 54 pet (lấy từ dữ liệu chính thức)
- Đối chiếu ảnh icon pet của server với 133 pet trong `Nr_Part.sql` tải từ server chính thức (NroResTool).
- Mỗi pet có 3 part mới (id 2193–2348): đầu trống, thân và chân chép từ part chính thức. Gắn vào `head/body/leg` của vật phẩm. Pet trùng hình dùng chung part.
- **Icon**: 36 icon trùng số nhưng khác hình được chép sang số mới (sau 32237) để không đè icon đang dùng, đủ x1–x4.
- **Đã có hình**:
  - Pokemon: Pikachu, Squirtle, Bulbasaur, MewTwo, Meowth
  - Thú: Rắn cute, Đại bàng, Hổ, Hươu, Mèo thần tài, Cậu vàng
  - Godzilla, Kong, Po, Shimo, Shiba (2 bản), Ông già Noel, Albart (2 bản), Minion, Minion Otto, Baby Shark (3 bản)
  - Capybara (3 bản), Zịt vàng, Mini Desutoron Gas, Hải Ly (2 bản), Giru, Rồng Xanh, Tuần Lộc Cute, Cá mập Koi
  - Secret, Rabbit, Dino, Panda, Mắt Long Lanh, Mắt Rưng, Thỏ Vui Vẻ, Cá Dory, Cá Nemo, Gấu Dâu
  - Ngựa KPI, Bư Noel, Tuần lộc ngố, Ngựa chiến, Ma vàng phù thủy, Bọ cánh cứng, Ngài đêm, Heo bướm
- **Chưa tìm thấy hình chính thức**:
  - Khỉ bong bóng, các Pet mèo đuôi vàng, Pet Voi / Gà / Ngựa chín
  - Mông Quỷ, Mèo Phù Thủy, Chim cánh cụt, Rồng Pikachu thanh long, Rồng con thần tài
  - Thỏ Ú, Ma cầu mưa, Khủng Long ngok
- `vsData` lên 18 để client tải lại part.
- Script: `pets_import.py` (bảng ghép vật phẩm → part chính thức nằm trong script).

## Đợt 24: Hình đeo lưng còn thiếu
- **Hình đeo lưng mới** (khung động lấy từ icon chính thức, thêm vào `flag_bag`):

  | Món | flag_bag | Hình | Icon khung |
  |---|---|---|---|
  | Bóng WC 2026 | 159 | quả bóng nhỏ xoay | 32274–32277 (chép sang số mới vì số chính thức 16931–16934 trùng icon khác) |
  | Cờ World Cup 2026 | 160 | cúp vàng nhỏ | 17089–17092 |
  | Đeo lưng Mãi Dell thành công | 161 | ngựa "mã đáo thành công" | 16573–16576 |

- **Icon vật phẩm** của Bóng WC 2026 (16935) và Pet đại bàng (16930) trước là ảnh trống 1×1, nay dùng ảnh chính thức.
- **Chưa làm**:
  - Bóng mờ Trung Thu chưa tìm thấy khung đeo lưng.
  - Ván bay WC / Trung Thu là thú cưỡi, cần ảnh `mount_...` (mục Mount của NroResTool), vẫn đang mượn hình cũ.

## Đợt 25: Chỉ số theo ảnh vật phẩm bản chính thức
| id | Món | Chỉ số |
|---|---|---|
| 2086 | Cải Trang Póc đường phố | HP 27%, giảm 20% sát thương, 20% né, giảm 20% tác dụng khống chế khi dùng khiên |
| 1503 / 1504 / 1512 | CT Lý Tiểu Nương hầu gái xanh / hồng / Bikini | SĐ 22%, HP 21%, KI 21%, chí mạng 10%, né chí mạng 10%, +5% SĐ (tối đa 12%) khi ở gần Lý Tiểu Nương hầu gái / bikini khác loại |
| 1922 | Chổi bay phù thủy | SĐ 13%, HP 10%, KI 10%, +5% tấn công lên Boss, +10% sát thương chí mạng, bay không tốn KI |
| 2006 | Giáp tập luyện cấp 5 | HP 15%, KI 15%, giảm 5% sát thương, Giáp +50.000 |
| 1731 | CT Black Goku Rose | SĐ 33%, KI 29%, +18% sát thương chí mạng, chí mạng 15%, chống lạnh, đi cùng Lưỡi hái hồng +5% sát thương chí mạng |
| 1832 | Cải trang Gohan Beast | SĐ 29%, KI 29%, sát thương chuẩn 7%, chí mạng 10%, giảm 5% thời gian hồi Khiên |
| 2085 | Cải Trang Bunma bụi | SĐ 25%, tấn công +25.000, +11% sát thương chí mạng, chí mạng 7%, sát thương chuẩn 5% |

**Option mới / mới có tác dụng:**
- **255** "Đi cùng Lưỡi hái hồng tăng #% sức đánh chí mạng": kiểm tra trên server test, Black Goku Rose + Lưỡi hái hồng cho chí mạng 16%, sát thương chí mạng 23%.
- **256** "+#% sức đánh, tối đa 12% khi ở gần Lý Tiểu Nương: Hầu Gái xanh, hồng, bikini": tính lúc ra đòn, mỗi loại CT khác loại đứng trong 200px cộng thêm.
- **191** Né chí mạng: có tỉ lệ biến đòn chí mạng của người đánh thành đòn thường.
- **183** Giảm % thời gian hồi Khiên năng lượng.
- **227** Giảm % thời gian bị choáng / thôi miên / socola / mù khi đang bật khiên.

`vsData` lên 19 để client tải lại tên option.

**Chưa làm:** "Triệu hồi quái 120% sức đánh, CD 360s" của Chổi bay phù thủy cần cơ chế triệu hồi mới, chưa có.

## Đợt 26: Kiểm tra toàn bộ option và nội tại (test thật trên server)
Cách test:
- **Từng option:** mặc cải trang chỉ có đúng option đó, đọc biến chỉ số của server (lệnh admin `chisoraw`) và so với lúc không có option. Đã chạy 92 option đang có trên đồ.
- **Option chiến đấu:** 2 nhân vật đánh nhau thật (lệnh admin `pkall`), đọc sát thương từng đòn trong gói tin.
- **Set đứng gần:** 2 nhân vật mặc 2 cải trang khác loại đứng cạnh nhau.

**Lỗi đã sửa:**
| Lỗi | Trước | Sau |
|---|---|---|
| Nội tại tải trùng 8 lần | 7/87 lần mở nội tại ra "Chưa kích hoạt" | Mỗi nội tại 1 lần |
| Nội tại 23 (+% vàng từ quái) | Không có tác dụng | Cộng vàng rơi từ quái |
| Nội tại 3 (Thái Dương Hạ San −% KI) | Không giảm KI | Giảm KI tiêu hao |
| Nội tại 9 Masenko | Cộng sát thương 2 lần, còn giảm nhầm hồi chiêu | Chỉ cộng sát thương 1 lần |
| Nội tại 24 / 25 | Dùng nội tại cũ: đòn đầu tiên hoặc vừa đổi nội tại thì sai | Lấy nội tại hiện tại |
| Set Picolo 5 món (Makankosappo ×1,5) | Viết `*= 3 / 2` = nhân 1, không tăng | Tăng 50% |
| Bùa trí tuệ x4 | Bị cộng 2 lần (thành x8) | x4 |
| Option 117 / 226 Sức đánh đẹp | Bị cộng 2 lần (+10% thành +21%) | +10% |
| Option 155 "Giảm 50% SĐ, HP, KI…" | Chỉ giảm SĐ | Giảm cả HP, KI |
| Option 100 / 155 +% vàng | Không có tác dụng | Cộng vàng rơi |
| Option 114 / 148 +% tốc độ | Không có tác dụng | Tăng tốc chạy |
| Option 191 Né chí mạng | Khi né vẫn hiện chữ chí mạng | Hiện đòn thường |

**Kết quả test chiến đấu** (B tộc Namếc đánh A, mỗi kịch bản 8 đòn):
| Option trên A | Sát thương / đòn | Kết quả |
|---|---|---|
| không có | ~96.000 | gốc |
| 199 giảm 50% ST từ Namếc | ~47.000 | đúng |
| 198 giảm 50% ST từ Trái Đất | ~96.000 | đúng (không giảm đòn Namếc) |
| 157 giảm 50% ST khi KI < 20% | ~48.000 khi KI thấp, ~96.000 khi KI đầy | đúng |
| 4 hồi 10% KI khi bị đánh | KI +88 triệu sau 8 đòn | đúng |
| 15 phản 100.000 ST cận chiến | B mất đúng 800.000 HP | đúng |
| B chí mạng 100%, A không có 191 | ~188.000 | gốc chí mạng |
| B chí mạng 100%, A có 191 100% | ~96.000 | đúng |

**Kết quả set đứng gần:** đúng hết, gồm:
- 145/146 Dr Slum: +20% SĐ, +66% tốc độ.
- 150 Póc–Píc–King Kong: +10% SĐ/HP/KI cho cả 2 người.
- 177 Gohan Bư gần Mabư: +2%.
- 179 Demons Frost: +2%.
- 193 Cầu thủ, 196 Thỏ.
- 256 Hầu gái: +5%; cùng loại thì không cộng.

**Option chỉ để hiện chữ** (hạn dùng, không giao dịch, sách kỹ năng…), không cần code: 1, 9, 30, 93, 210, 231…

## Đợt 27: Tối ưu cho VPS yếu, effect chính thức, tool tải repo
- **Rò rỉ luồng (lỗi nặng):** mỗi lần đăng nhập, hiện hiệu ứng (LogicEffect), ghi log, hẹn giờ (setTimeout), Mabư ăn người, luyện tập, trả thù, phó bản… server tạo 1 luồng không bao giờ đóng.
  - Test cũ: 15 lần đăng nhập để lại 13 luồng.
  - Nay dùng chung `Util.POOL` (luồng rảnh 60 giây tự đóng) và `Util.TIMER`. Test lại: không còn luồng thừa.
- Khu Ngọc Rồng Sao Đen không có người: cập nhật 1 lần/giây thay vì 7 lần/giây.
- `run_vps_yeu.bat` / `run_vps_yeu.sh`: dùng SerialGC, giới hạn RAM. Lúc không có người chơi tốn khoảng 160 MB RAM, so với khoảng 420 MB khi chạy mặc định.
- Hướng dẫn chọn JDK / NetBeans và chỉnh MySQL: xem `HUONG_DAN_VPS_YEU.md`.
- **Effect chính thức:** thêm effect 500–519; cập nhật 19 effect khác bản chính thức (ảnh x4 gốc, x1–x3 thu nhỏ từ x4).
  - Danh hiệu 300–303 không có trên server chính thức nên vẫn thiếu ảnh.
- **`tools/NroRepoDownloader.exe`:** tải toàn bộ file server từ GitHub, mặc định bỏ `tools/` và `client/` (LÂU CỒ MOD).
  - Lần đầu tải 1 file zip (khoảng 690 MB) rồi giải nén: test được 79.325 file trong 55 giây.
  - Lần sau so mã hash từng file, chỉ tải file mới / đã sửa.
  - Không ghi đè `Config.properties` và `maintenanceConfig.txt` nếu trên máy đã có.
  - Mã nguồn: `tools/repo-downloader`.

## Đợt 28: NroResTool v8 dò danh hiệu
- **Vì sao tool cũ không tìm được danh hiệu:**
  - Danh hiệu dùng số effect của server chính thức. Số này khác bản server lậu (218–303), nên đoán theo dải 0–1200 không trúng.
  - Server gửi danh hiệu bằng lệnh 24 loại 2 (id người chơi, giây, id effect), cứ 10 giây gửi lại. Tool cũ không đọc lệnh này.
- **Mục mới "Dò danh hiệu":**
  - Tool đăng nhập rồi đi qua các khu (vd `0-15`) của map nhân vật đang đứng, mỗi khu chờ 12 giây.
  - Ghi lại số effect người khác đang đeo: danh hiệu (lệnh 24) và hiệu ứng set / cải trang (lệnh -128). Sau đó tự tải đúng các effect đó.
  - Danh sách tìm được lưu ở `danh_hieu_do_duoc.txt`.
  - Test trên server thử: nhân vật khác đeo danh hiệu 222 ở khu 3, tool tìm thấy và tải đúng ảnh và effdata.

## Đợt 29: NroResTool v9 thử các kiểu hỏi danh hiệu
- Bản v8 dò được danh hiệu server chính thức đang dùng (224, 228), nhưng server không trả ảnh khi hỏi effect kiểu thường.
- **Tải không được thì tool tự thử thêm các kiểu hỏi:**
  - effect + 1 byte loại (1, 2, 3, 0);
  - id dạng int;
  - ảnh theo tên (`danhhieu_`, `danh_hieu_`, `title_`, `Title_`, `dh_`, `ImgEffect_` + id).
- Tool ghi mọi gói server trả về trong lúc thử vào cuối `danh_hieu_do_duoc.txt` (bỏ gói di chuyển / chiến đấu) để phân tích.
- **Gói effect định dạng lạ:** tool lấy ảnh PNG nằm cuối gói. Không đọc được thì lưu nguyên gói vào `effect_loi/goi_<id>.bin`.

## Đợt 30: NroResTool v10 giả client 2.5.2
- **Đã kiểm tra client chính thức Dragonboy252** (đóng gói Enigma Virtual Box, đã bung ra):
  - Trong client không có sẵn ảnh danh hiệu, chỉ có effect cũ e_0–28.
  - Client không tải ảnh qua web.
  - Danh sách lệnh -111 lúc đăng nhập chỉ là các file res (ô gạch bản đồ), repo đã có đủ.
- **Server đọc phiên bản client từ chuỗi platform** lúc đăng nhập (`pc|2.3.3` → 233). Tool trước báo là 2.3.3, nên server chính thức có thể không gửi effect / danh hiệu mới.
  - Nay mặc định `pc|2.5.2` / `2.5.2` cho khớp client chính thức (sửa được ở mục Nâng cao).
- **Dự phòng:** client chính thức lưu dữ liệu đã tải ở `%USERPROFILE%\AppData\LocalLow\Team\Dragonboy252`. Nén thư mục này gửi lên để lấy ảnh danh hiệu client đã tải.

## Đợt 31: NroResTool v11 thử zoom x2 cho danh hiệu
- **Dữ liệu AppData của client chính thức:**
  - Client không lưu effect / danh hiệu xuống ổ đĩa, chỉ giữ trong RAM.
  - File `lastZoomlevel` = 2: client chính thức chạy zoom x2, còn tool hỏi x4.
- Hỏi x4 không được thì tool tự kết nối lại với zoom x2, x1, x3 rồi hỏi lại. Ảnh lưu ở `effect/x<zoom>`.
- Test trên server thử: danh hiệu chỉ có ảnh x2 → tool không tải được ở x4, tự chuyển sang x2 và tải được.

## Đợt 32: NroResTool v12 sửa lỗi không vào game
- **Lỗi của v10–v11:** khi tool báo phiên bản `pc|2.5.2`, server chính thức không cho vào game (bản 2.5.2 thật đăng nhập bằng gói khác). Vì vậy dò 50 khu đều không thấy gì.
- **Sửa:**
  - Trả mặc định về `pc|2.3.3`.
  - Nếu với phiên bản đã chọn mà không vào được game, tool tự kết nối lại bằng 2.3.3.
  - Phần thử zoom x2 / x1 / x3 cho danh hiệu (v11) vẫn giữ.

## Đợt 33: Kiểm tra icon, NPC, map
**Icon item** (đối chiếu 2.112 item với file ảnh 4 mức zoom):
- 125 item thiếu icon x1, khoảng 15 item có icon x1/x3 là ảnh trống 1x1. Đã tạo lại từ bản lớn nhất. Làm cho mọi icon trên ổ: thêm 1.566 file. Part cải trang / pet không còn thiếu file nào.
- Icon thật cho Trực thăng thỏ đế, Pet hươu, Pet hổ (trước là ảnh trống).
- Còn 13 item trống icon ở mọi zoom (abcd, TT, Đai lưng, CT Sida/Heles/Rumsshi/Mosco/Champa/Belmod/Fide chai/Test). Bản chính thức cũng trống; không có trong shop, hộp quà hay rơi đồ nên người chơi không gặp.

**NPC:** test tự động 176 lượt NPC trên 91 map: mở menu, bấm từng lựa chọn cấp 1, theo dõi lỗi server.
- **Boss luyện tập tự động:** `leaveMap` gây NullPointer lặp vô hạn (khoảng 1.200 lần trong một lần test). Server chậm hẳn, nhiều NPC bấm không phản hồi. Đã sửa.
- **Quả trứng:** bấm khi chưa có trứng Mabư thì lỗi NullPointer. Nay báo "Bạn chưa có trứng Ma Bư!".
- **Đại Thiên Sứ** (NPC 64, Võ Đài Siêu Cấp / Lãnh địa Fize): lớp rỗng, bấm không có gì, mà map không có cổng ra. Thêm menu "Về nhà".
- **Whis:** ở map 164, nút "Từ chối" mở nhầm menu học tuyệt kỹ. Map khác chưa có menu nay có "Về nhà".
- **Bà Hạt Mít, nút "Top 100" ở Võ đài:** trước không làm gì. Nay hiện top 100 lượt thách đấu võ đài trong ngày.
- **NPC chưa có chức năng** (chỉ "Từ chối"): Rương Sưu Tầm (Nhà Bunma), Berry (Khu hang động); Ngộ Không, Đường Tăng ở map không vào được.

**Map:** kiểm tra cổng, điểm đến, ô gạch; test thoát game ở từng map (170 map) rồi đăng nhập lại.
- Phòng thí nghiệm Myuu (166) và Rừng nguyên sinh (186): đăng nhập lại vẫn ở đó, mà map không có lối ra → kẹt. Nay đưa về nhà.
- 7 map thiếu dữ liệu ô gạch (78, 95, 101, 121, 125, 130, 134): vào là kẹt. Nay không cho dịch chuyển vào (báo "Bản đồ này chưa mở"); đăng nhập ở đó thì đưa về nhà.
- Các map còn lại (phó bản, doanh trại, Ngọc Rồng Sao Đen, Bụng Mabư, kho báu…) đăng nhập lại đều được đưa ra đúng chỗ. Mọi cổng dẫn tới map có thật và có đất để đứng.

## Đợt 34: Rương sưu tập (NPC Rương Sưu Tầm, Nhà Bunma)
Theo ảnh bản chính thức:
- **Menu:** "Vàng bạc châu báu gì cứ yên tâm giao hết cho tôi" — **Mở rương** / **Nâng cấp rương** / **Từ chối**.
- **Mở rương:** mở giao diện rương đồ của client, nhưng là một rương riêng, mặc định 20 ô.
  - Chỉ cất được: cải trang, đeo lưng, thú cưỡi (ván bay, cân đẩu…), pet (đồ loại 27 có hình đi theo), có hạn sử dụng hay không đều được.
  - Đồ khác bị từ chối: "Rương sưu tập chỉ cất được cải trang, đeo lưng, pet và thú cưỡi".
  - Cất từ hành trang hoặc cởi thẳng từ trên người; lấy về hành trang.
- **Nâng cấp rương:** hỏi "Bạn có chắc muốn mở thêm ô 21 với giá 1,5 Tỷ vàng?", mỗi lần +1 ô.
  - Ô sau đắt thêm 10% giá gốc (ô 22: 1,65 tỷ…), tối đa 100 ô. Giá ô 22 trở đi tự đặt; sửa ở `CollectionService.BASE_PRICE`.
- **Lưu dữ liệu:** cột mới `player_extra.collection`, không đụng rương đồ thường. Mở lại rương đồ ở nhà hoặc chuyển map là quay về rương đồ thường.
- **Test trên server:** mở 20 ô → cất cải trang, pet được, đồ ăn bị từ chối → nâng cấp lên 21 ô → đăng nhập lại vẫn còn đồ → lấy về hành trang được. Rương đồ thường vẫn đúng 30 ô.

## Đợt 35: Danh hiệu Perfect (effect 302) làm lại từ video
- **Nguồn:** video quay danh hiệu "PERRFECT" (server ngocrong.net). Gồm chữ PERRFECT trên bệ tối, Xên hoàn thiện, 3 đầu Xên đỏ / xanh lá / xanh dương, kén tím lăn và nhịp chớp trắng.
- **Cách làm:**
  - Dựng ảnh nền sạch từ những lúc danh hiệu ẩn, rồi tách danh hiệu ra bằng cách so với nền.
  - 1 vòng chuyển động khoảng 0,74 giây.
  - Chia 3 vùng (trên / chữ + bệ / Xên phải), mỗi vùng chỉ lưu các trạng thái khác nhau.
  - Canh lưới 1 px game để các mảnh ghép khít.
- **Giới hạn:** tấm effect x1 tối đa 255x255 (toạ độ mảnh là 1 byte), nên dùng 11 khung, mỗi khung giữ 2 nhịp (vòng lặp vẫn dài như video). Tỉ lệ ước tính 2,5 px màn hình = 1 px game; ảnh x3/x4 phóng từ video nên hơi mềm.
- **File:** `data/effdata/DataEffect_302`, `data/effect/x1..x4/ImgEffect_302.png` (26 mảnh, ảnh 256 màu). Đã test server gửi effect 302 cho client.

## Đợt 36: Danh hiệu tạm, đập sao 10, nâng cấp +13
**Danh hiệu tạm** (đến khi có effect thật):
- Vẽ bảng tên đúng tên cho 300 Phá Cổ Trung Thu (cam), 301 Phá Cổ Trung Thu VIP (tím), 303 Đón Xuân (đỏ), cùng kiểu bảng chữ danh hiệu sẵn có.
- Có đủ x1–x4; muốn thay chỉ cần chép đè file `ImgEffect_<số>` và `DataEffect_<số>`.
- Perfect (302) dùng bản làm từ video.

**Pha lê hóa tối đa 10 sao** (trước là 9):
- Sao thứ 10: tỉ lệ 0,05% (hiện 1%), 500 triệu vàng, 280 ngọc (giá ngọc có sẵn trong bảng cũ).
- **Cường hóa lỗ (Hematite + Dùi đục):** viết lại theo bậc 8 → 9 → 10, không vượt quá số sao đang có.
  - Tỉ lệ lỗ 8: 100%, lỗ 9: 25%, lỗ 10: 15%.
  - Thông tin hiện đúng lỗ sắp cường hóa và tỉ lệ thật (trước ghi chung "50%").
- **Ép sao:** cho ép vào lỗ 10 (đá ở lỗ 8–10 thành dòng riêng như lỗ 8, 9), 10 ngọc.

**Nâng cấp vật phẩm tối đa +13** (trước +8): bảng cho +8 → +13.
| Cấp | Đá | Vàng | Tỉ lệ |
|---|---|---|---|
| +8 → +9 | 90 | 300 Tr | 0,3% |
| +9 → +10 | 110 | 400 Tr | 0,25% |
| +10 → +11 | 130 | 500 Tr | 0,2% |
| +11 → +12 | 160 | 700 Tr | 0,15% |
| +12 → +13 | 200 | 900 Tr | 0,1% |

- Bảng cũ có sẵn tỉ lệ vô lý cho +8 trở lên (5%, 1%), đã đặt lại cho giảm dần.
- Thất bại ở cấp chẵn (+2, +4, … +12) rớt 1 cấp như +2/+4/+6 cũ, trừ khi có đá bảo vệ.
- Mỗi cấp vẫn +10% chỉ số.
- Hào quang trọn bộ vẫn tính tới +8 (client chỉ có hiệu ứng tới +8).

**Test qua NPC Bà Hạt Mít:**
- Áo +12 hiện "Sau khi nâng cấp (+13)", thất bại rớt +11.
- Pha lê hóa từ 9 sao hiện đúng giá và tỉ lệ.
- Cường hóa lỗ 10 thành công sau 2 lần.
- Ép sao lỗ 10 → 10/10 sao (+2% né đòn).
- Không có lỗi server.

## Đợt 37: Sao pha lê 10 theo bài cập nhật chính thức (Đá cấp 9, Ngọc rồng xanh)
Code mới: `services/SaoPhaLe10Service.java`.

**Chi phí**
- Pha lê hóa 9 → 10 sao: 500 Tr vàng + 50 ngọc bất kỳ. Trừ ngọc xanh trước, thiếu thì trừ hồng ngọc. Trước đây là 280 ngọc xanh.
- Cường hóa lỗ 10: 1 Hematite + 1 Dùi đục + 100 ngọc bất kỳ, không tốn vàng. Lỗ 8 và 9 vẫn tốn 500 Tr vàng.

**Luật khảm theo lỗ** (Ép sao, Bà Hạt Mít)

| Lỗ | Chỉ nhận |
|---|---|
| 1 – 8 | Đá pha lê thường. Không nhận Đá cấp 9 hay Ngọc rồng xanh |
| 9 | Đá cấp 9 (2000 – 2004) |
| 10 | Ngọc rồng xanh 1 – 7 sao (2064 – 2070) |

Chỉ số Đá cấp 9 (cố định):

| Đá | Chỉ số |
|---|---|
| Linh Hồn | Chí mạng +2% |
| Hiện Thực | Giảm 5% sát thương |
| Sức Mạnh | Sức đánh +5% |
| Không Gian | HP +10% |
| Thời Gian | KI +10% |

Chỉ số Ngọc rồng xanh (ngẫu nhiên mỗi lần khảm):

| Viên | Chỉ số |
|---|---|
| 1 sao | Sát thương cuối 1 – 3% |
| 2 sao | Sức đánh 3 – 10% |
| 3 sao | HP 5 – 20% |
| 4 sao | KI 5 – 20% |
| 5 sao | Chí mạng 1 – 5% |
| 6 sao | Giảm sát thương 3 – 10% |
| 7 sao | Biến tấn công thành HP và KI 5 – 10% (2 dòng cùng giá trị) |

**Hộp**
- Hộp đá cấp 9 (2034, đổi tên từ "Hộp đá nâng cấp +9"): mở ra chọn 1 trong 5 đá cấp 9.
- Hộp ngọc rồng xanh (2071): chọn 1 trong 7 viên.

**Nguồn rơi từ boss**
- Boss từ 50 Tr HP: 5% rơi Hộp đá cấp 9.
- Boss từ 500 Tr HP: 5% rơi 1 Ngọc rồng xanh ngẫu nhiên.

**Nhiệm vụ Sao pha lê 10** (Bà Hạt Mít đảo Kame, nút mới)
- Đếm số món đủ 10 sao ở người, hành trang và rương.
- Phần thưởng theo mốc:

| Số món | Phần thưởng |
|---|---|
| 1 | 1 Hộp ngọc rồng xanh |
| 2 | 2 Hộp ngọc rồng xanh |
| 3 | 1 Capsule vỡ |
| 5 | 2 Capsule vỡ |
| 7 | Capsule kích hoạt tự chọn + Bóng mờ hoa hồng (bước chân) |

- Mỗi mốc nhận 1 lần, lưu ở `player_extra.spl10_reward`. Cột này đã thêm vào cả 2 DB.

**Đã thử trên server**
- Khảm sai đá bị chặn đúng thông báo.
- Lỗ 9 + Đá Sức Mạnh → Sức đánh +5%.
- Lỗ 10 + NRX 7 sao → 2 dòng Biến 5% thành HP và KI.
- Còn 0 ngọc xanh, 1000 hồng ngọc: sau pha lê hóa và cường hóa lỗ 10 còn 850 hồng ngọc.
- Mở 2 hộp chọn đúng viên.
- Nhận mốc 1 món, lưu lại sau khi thoát.

## Đợt 38: Nâng cấp vật phẩm chỉ tới +9
- `MAX_LEVEL_ITEM` từ 13 về 9 theo yêu cầu.
- Đồ +8 vẫn nâng được lên +9. Đồ +9 báo "đã đạt cấp tối đa".
- Kiểm tra DB: không có người chơi nào giữ đồ trên +9.
- Bảng giá và tỉ lệ cho +10 trở lên vẫn để trong code nhưng không còn dùng tới.

## Đợt 39: Đá cấp 9 dùng nâng cấp +8 lên +9 (thay cho khảm lỗ 9)
- Nâng đồ từ +8 lên +9 phải dùng đá cấp 9 đúng loại đồ, mỗi lần 10 viên, 300 Tr vàng. Tỉ lệ và rớt cấp như cũ.

| Đồ | Đá |
|---|---|
| Áo | Đá Hiện Thực (2001) |
| Quần | Đá Không Gian (2003) |
| Găng | Đá Sức Mạnh (2002) |
| Giày | Đá Thời Gian (2004) |
| Rada | Đá Linh Hồn (2000) |

- Cấp khác vẫn dùng đá thường (Titan, Ruby, Thạch anh tím, Saphia, Lục bảo). Đá cấp 9 không dùng được ở cấp khác.
- Bỏ sai đá ở +8, Bà Hạt Mít báo đúng tên đá cần.
- Item 2034 lấy lại tên "Hộp đá nâng cấp +9", vẫn chọn 1 trong 5 đá.
- Lỗ sao pha lê 9 trở về khảm đá pha lê thường. Đá cấp 9 không còn khảm được.
- Lỗ 10 vẫn chỉ khảm Ngọc rồng xanh.
- Đã thử:
  - +8 với Titan hoặc Sức Mạnh bị chặn.
  - +8 với Hiện Thực hiện "Cần 10 Đá Hiện Thực". Bấm thất bại thì trừ đúng 10 viên và rớt về +7.
  - +7 với Hiện Thực bị chặn.
  - Lỗ 9 khảm đá thường được.

## Đợt 40: Capsule Vỡ, phân rã kích hoạt theo video; kiểm tra hành tinh thực vật, Kilis, đổi đệ
**Tính năng theo video** ("Nhận quà top 10, chế tạo Capsule Vỡ, làm phôi SKH")
- **Mảnh Cápsule Vỡ (1909):** đủ 999 mảnh thì bấm Sử dụng. Hỏi "Ngươi có muốn đổi 999 Mảnh Cápsule Vỡ lấy 1 Cápsule Vỡ không?", Đồng ý thì nhận Cápsule Vỡ (1634). Chưa đủ thì báo số đang có.
- **Phiếu đổi Cápsule (1690):** đủ 99 phiếu thì đổi 1 Cápsule kích hoạt 1 món tự chọn (1655), hỏi như trên.
- **Phân rã trang bị kích hoạt (Bà Hạt Mít):** chọn một hay nhiều món cùng lúc như video, mỗi món ra 1 Khoáng tái chế, 2 tỷ vàng/món. Trước chỉ được 1 món. Món không phải đồ kích hoạt thì báo tên món đó.
- **Tái tạo Capsule kích hoạt:** nhận cả "Capsule vỡ" bản sự kiện (2111) ngoài Cápsule Vỡ (1634). Trước 2111 không dùng được gì.

**Hành tinh thực vật (156 – 166)**
- Quái **Cadic M** (quái duy nhất ở Sa mạc hoang vu 165, map úp Kilis): ảnh zoom x3 là hình con thỏ của quái khác. Ai chơi zoom x3 sẽ thấy quái lỗi. Đã dựng lại x3 từ bản x2. Quét 108 quái, không còn con nào bị như vậy.
- Thử 12 cổng của Thánh địa (156 – 159) và Khu hang động → Làng Plant (160 – 163): đều đến đúng map, đúng chỗ.
- Nhẫn thời không đi và về đúng.

**Kilis / bình hút năng lượng / đổi đệ**
- **Bùa hỗ trợ của Osin (5 ngọc, 10 phút):**
  - Trước đây luôn tắt sau đúng 60 phút, không theo thời gian đã mua. Nay mua 10 phút được 10 phút, cộng dồn tối đa 60 phút.
  - Trước đây thoát game là mất bùa. Nay lưu đúng thời gian còn lại.
- **Lưu thời gian item (data_item_time):** server lưu 32 ô nhưng đọc lệch từ ô 12. Sau khi đăng nhập lại, thời gian bị tráo:
  - Bùa Santa vào thức ăn.
  - Thức ăn vào tự động luyện tập.
  - Bùa Kilis vào nước mía.
  - Nhiều bùa khác cũng bị lệch.
  
  Nay đọc đúng thứ tự lưu.
- **Map tích Kilis:** trước là mọi map có id ≥ 165 (cả Thành cổ, Rừng nguyên sinh…). Nay chỉ Sa mạc hoang vu (165).
- **Đã thử:**
  - Mua bùa nhận icon 10 phút. Thoát game còn đúng thời gian.
  - Đánh Cadic tích Kilis vào bình.
  - Bình 3000 Kilis + đệ Mabư 40 tỷ sức mạnh: đổi được đệ (ra Uub), bình bị trừ. Bình 2999 Kilis bị từ chối.

## Đợt 41: Broly Super (SSJ Broly) tách khỏi Super Broly; Baby rơi Set Champa và nhiều sao pha lê
**Hai boss khác nhau**

| | Super Broly | Broly Super (mới, theo video "Boss mới SSJ Broly") |
|---|---|---|
| Xuất hiện | Hóa từ Broly ngay khu Broly chết | Các map tuyết của Cooler: Cánh đồng tuyết, Rừng tuyết, Núi tuyết, Dòng sông băng, Rừng băng, Hang băng (105 – 110) |
| HP | Như bản gốc (1,5 – 16 triệu) | 2 tỷ |
| Ngoại hình | Super Broly cũ (part 294 – 296) | Broly tóc xanh lá (part 2099 – 2101) |
| Phần thưởng | Như bản gốc: chưa có đệ thì nhận Đệ tử | Theo bảng phần thưởng trong video, xem dưới |
| Hồi sinh | Theo Broly | 15 phút |

Bảng rơi của Super Broly ở đợt trước đã chuyển hết sang Broly Super.

Phần thưởng Broly Super:
- Hộp đá nâng cấp +9
- Cuồng bạo (item tăng chí mạng mới)
- Đồ thần linh (cả găng)
- Ngọc rồng 1 – 4 sao
- Item cấp 2 (Cuồng nộ, Bổ khí, Bổ huyết, Giáp Xên, Ẩn danh cấp 2)
- Đồ 1 – 3 sao
- Đồ cấp 8 – 11
- Vàng ngẫu nhiên
- Mảnh khỉ Oozaru
- Đồ cấp 11 – 12 từ 5 – 8 sao
- 1% Set Beerus

Sát thương mỗi đòn tối đa 1% HP, giáp boss 65%.

**Baby**
- 10% rơi 1 món **Set Thần hủy diệt Champa**: hành tinh, loại đồ (áo / quần / găng / giày / rada) và cấp 1 – 8 đều ngẫu nhiên.
- Mỗi lần hạ rơi 3 – 6 sao pha lê, 30% thêm sao pha lê cấp 2.

**Set Thần hủy diệt Champa** (option mới 257 – 260, đã thêm vào cả 2 DB):

| Số món | Hiệu lực |
|---|---|
| 2 | +10% sát thương lên boss |
| 4 | +20% sát thương lên boss |
| 5 | +35% sát thương lên boss, +20% sức đánh chí mạng |

Bảng "chiso" có dòng "Set Thần hủy diệt Champa: N món".

**Lệnh admin mới** (dùng để thử boss):
- `goiboss <BossID>`: gọi boss.
- `den boss <tên>`: dịch chuyển tới boss.
- `giet boss`: hạ mọi boss trong khu, tính như mình giết, có nhận thưởng.

**Đã thử trên server**
- Broly Super ở Hang băng / Rừng tuyết / Dòng sông băng, HP 2.000.000.000. Hạ 3 lần ra đồ theo bảng.
- Broly → Super Broly cùng khu, HP khoảng 9,8 triệu, hạ thì nhận Đệ tử.
- 25 lần hạ Baby: 2 món Champa, 200 sao pha lê.
- Mặc 0 / 2 / 4 / 5 món Champa: sát thương lên boss 0 / 10 / 20 / 35%, chí mạng +20% khi đủ 5 món.

## Đợt 42: Thông báo top 1 đăng nhập, nhặt set kích hoạt riêng, mở hộp ra cải trang
Code mới: `services/AnnounceService.java`.

**Top 1 đăng nhập** (chat thế giới, tên người đó hiện ở đầu dòng)
- Áp dụng cho các bảng top ở Chi Chi: Hộp quà thiếu nhi, Nước mía, Kem trái cây, Hộp đen bí ẩn.
- Ví dụ: "Top 1 sự kiện Nước mía, Hộp đen bí ẩn vừa đăng nhập!"
- Top đang 0 điểm thì không báo. Mỗi lần đăng nhập chỉ báo 1 lần.

**Đánh quái nhặt được set kích hoạt riêng** (thông báo server, dòng chạy chữ)
- Chỉ áp dụng cho 3 set riêng: Thần Vũ Trụ Kaio, Nail chiến binh Namếc, Cađic M. Đây là 30% đồ kích hoạt rơi từ quái ở map up SKH và map riêng tư.
- Ví dụ: "admin vừa đánh quái may mắn nhận được 1 trang bị Set kích hoạt Set Thần Vũ Trụ Kaio".
- Báo lúc nhặt lên, không báo lúc rơi.

**Mở hộp được cải trang, pet, đeo lưng, thú cưỡi** (thông báo server)
- Áp dụng cho mọi hộp quà dữ liệu (`item_box_reward`, 38 loại hộp) và đổi quà sự kiện theo mùa.
- Ví dụ: "Chúc mừng admin đã may mắn mở Hộp quà 20/10 nhận được CT Frieren hạn dùng 15 ngày", hoặc "... hạn dùng vĩnh viễn".

**Sửa thêm:** danh sách thông báo server (`ServerNotify`) không an toàn khi nhiều người cùng thêm. Đã đổi sang danh sách đồng bộ.

**Lệnh admin `roiskh`:** thả 1 món set kích hoạt riêng như quái rơi, để thử thông báo nhặt.

**Đã thử trên server**
- Đăng nhập khi đứng top 1 Nước mía và Hộp đen thì có chat thế giới.
- Nhặt món set Thần Vũ Trụ Kaio thì có thông báo server.
- Mở 40 Hộp quà 20/10 ra 13 lần cải trang / thú cưỡi, lần nào cũng có thông báo kèm hạn dùng.

## Đợt 43: Thông báo top 1 – 10 và set kích hoạt cũ
- **Đăng nhập:** đứng top 1 – 10 bảng nào ở Chi Chi cũng có chat thế giới. Trước đây chỉ top 1. Ví dụ: "Top 2 sự kiện Nước mía, Top 1 sự kiện Hộp đen bí ẩn vừa đăng nhập!"
- **Nhặt set kích hoạt từ quái:**
  - Set cũ (Sôngôku, Kakarot, Kirin, Picolo, Ốc tiêu, Pikkoro Daimao, Ca Đíc, Nappa, Gohan…): báo chung "admin vừa đánh quái may mắn nhận được 1 trang bị Set kích hoạt", không kèm tên set.
  - 3 set riêng (Thần Vũ Trụ Kaio, Nail chiến binh Namếc, Cađic M): vẫn báo kèm tên set.
- **Lệnh admin:** `roiskh cu` thả 1 món set cũ để thử (`roiskh` thả set riêng như trước).
- **Đã thử:** top 2 Nước mía + top 1 Hộp đen báo đúng hạng. Nhặt set cũ báo câu chung, nhặt set Kaio báo kèm tên set.

## Đợt 44: Panel web quản lý
- Panel chạy cùng server, mặc định cổng 8088. Code ở `src/nro/models/panel/`, giao diện ở `data/panel/index.html`, hướng dẫn ở `HUONG_DAN_PANEL.md`.
- Các mục: tổng quan, người chơi, tài khoản, bảng xếp hạng, bang hội, ký gửi, nạp thẻ, vật phẩm, hộp quà, giftcode, cửa hàng, bản đồ & NPC, sự kiện, boss, quái, thông báo & quà toàn server, bảo trì, nhật ký console, nhật ký thao tác, cấu hình, truy vấn SQL chỉ đọc.
- Bảo mật:
  - Đăng nhập bằng tài khoản / mật khẩu trong Config.properties, phiên lưu 12 giờ.
  - Sai 5 lần khóa IP 5 phút.
  - Yêu cầu sửa đổi phải có header riêng của panel (chống CSRF).
  - Ghi nhật ký thao tác.
- **Đã thử:**
  - 30 API và 21 trang trên trình duyệt thật, không có lỗi JavaScript.
  - Người chơi offline: cộng tiền, gửi đồ thẳng vào database.
  - Người chơi online: cộng tiền, gửi đồ, thông báo, dịch chuyển, kick. Người chơi nhận được ngay.
  - Tạo / xóa giftcode và thêm / xóa quà trong hộp: server nạp lại ngay.
  - Đặt / xóa NPC ở Đảo Kamê, bật / tắt sự kiện, gọi boss, tỉ lệ EXP, nạp lại shop.
  - SQL chặn câu sửa dữ liệu.

## Đợt 45: Client J2ME (Dragonboy247 v7.0) cho server riêng
- Thư mục `tools/j2me-client/`:
  - `NRO_J2ME.jar`: đã trỏ về 36.50.135.16:14445.
  - `patch_client.py`: đổi IP / port / tên server.
  - `HUONG_DAN.md`: hướng dẫn cài và cấu hình J2ME Loader cho mượt.
- Chuỗi trong client mã hóa bằng khóa `hunghero` (ký tự − i%9 − khóa[i%8]). Công cụ thay 8 chuỗi danh sách server / IP mặc định, không biên dịch lại class. 755/759 file giữ nguyên.
- **Đã đối chiếu tĩnh với server:**
  - Gói `setClientType` gửi đúng thứ tự trường, phiên bản 2.4.6 (server tính 246).
  - Gói đăng nhập khớp.
- **Chưa chạy thử client thật:** không được phép chạy jar của bên thứ ba trong môi trường làm việc.
- Tính năng Firebase "Điều khiển từ xa" của mod mặc định tắt.

## Đợt 46: Server tương thích giao thức client J2ME (Dragonboy247 / 2.4.6)
Lỗi khi vào game bằng client J2ME (theo video):
- Danh sách đồ trống ở bảng Bà Hạt Mít.
- HP / KI sai.
- "Xin chờ" treo ở mục Đệ tử.
- Ảnh quái sai.

Nguyên nhân: client J2ME 2.4.6 đọc một số trường khác client PC.

**Cách làm:** server tự nhận client J2ME qua gói khai báo (`typeClient == 1`, phiên bản ≥ 240 → `MySession.longNumbers`). Mỗi gói tin ghi song song 2 bản: bản cũ cho client PC (giữ nguyên từng byte như trước) và bản J2ME. `MessageSendCollect` chọn bản theo phiên.
- `Message`: thêm `writeNum`, `writeItemOption`, `writeDual`, `writeOnlyLong`, `getData(longNumbers)`.

**Server → client (J2ME):**
- HP / KI / sát thương / phòng thủ: long. Khoảng 70 chỗ: nhân vật, đệ tử, quái, quái của người chơi, boss lớn, bot.
- Chỉ số đồ (option): short id + int param, 18 chỗ (túi, rương, người, shop, ký gửi, giao dịch).
- Ô túi trống gửi `-1` thay vì bỏ qua (client đọc đủ số ô).
- Id quái trong map, cờ / túi, biểu tượng bang, số lượng map / quái (-28/6): short.
- Gói -42 (chỉ số), -107 (đệ tử), 87, -9 / -10 / -11 (quái): thêm hoặc đổi trường theo đúng client.
- Dữ liệu item (-28/8): gửi theo định dạng J2ME. Tên item gói trong giới hạn 65535 byte của client: bỏ dấu bớt ở tên dài, mô tả để trống.
- Gói -30/7 (bản thứ 2) thiếu trường, sai với mọi client: đã sửa đủ.
- Gói -8: id người chơi ghi `int` (trước ghi `short`, sai với mọi client).
- Giao dịch -86: kiểm tra phiên bản theo người nhận.
- Gấu Tượng Cướp (-13/5): không gửi cho client J2ME, vì client này hiểu thành "hồi sinh quái thường" và làm hỏng quái.

**Client J2ME → server:**
- 11 (xin dữ liệu quái): client gửi short, server trước đọc byte → trả sai quái (ảnh quái lỗi). Đã đọc short.
- -63 (xin ảnh cờ / túi): đọc short.
- -46 tạo bang / đổi thông tin bang: id biểu tượng đọc short.

**Đã đối chiếu tĩnh** toàn bộ gói của client J2ME (giải mã bytecode) với server: hai chiều, gói chính, -28 / -29 / -30. Các gói còn lại khớp.

**Ghi chú:** thông báo "Máy chủ sẽ bảo trì trong … phút" là bảo trì tự động hằng ngày lúc 04:30 (`AutoMaintenance`), không phải lỗi client.

## Đợt 47: Client APK / PC không hiện hành trang, đệ tử "Xin chờ" (bảng option quá 255 dòng)
**Triệu chứng** (APK "Dragon Ball" 4.8.4, video):
- Bảng ghép đồ Bà Hạt Mít không có hành trang.
- Mở đệ tử bị treo "Xin chờ".

**Nguyên nhân:** client APK / PC (Unity) đọc số dòng bảng option và id option bằng 1 byte không dấu, tối đa 255 dòng (id 0 – 254).
- Bản gốc có 255 dòng.
- Sau đó thêm option 255, 256, rồi Set Champa 257 – 260 (Đợt 41) → 261 dòng. Server ghi `writeByte(261)` = 5, nên client chỉ nhận 5 option.
- Món đồ nào có option id ≥ 5 làm client lỗi khi đọc thông tin nhân vật (-30/0), đệ tử (-107), hành trang (-36). Kết quả: hành trang trống, đệ tử treo.

Đã kiểm tra bằng cách đọc mã máy của APK (IL2CPP): mọi gói khác (-30/0, -36, -107, -81, -28/8) khớp định dạng server gửi.

**Cách sửa** (không đổi database, không đổi id thật trên server):
- `OptionIdMap`: khi gửi cho client byte, bảng option chỉ 255 dòng. Option id ≥ 255 được đặt vào các ô id không có item / shop / code nào dùng: 255→201, 256→202, 257→203, 258→205, 259→195, 260→197.
- `Message.writeItemOption` đổi id theo bảng đó cho client byte. Client J2ME vẫn nhận id thật (short).
- `DataGame.vsItem` 15 → 16 để mọi client tải lại bảng item / option (bảng lỗi đã lưu trong máy bị thay).

**Lưu ý khi thêm option mới:** id từ 255 trở lên cần còn ô trống trong `OptionIdMap.SPARE` (hiện còn 4 ô: 192, 229, 62, 208). Hết ô thì server báo lỗi trong log.

**Ghi chú:** APK 4.8.4 là client Unity, không phải J2ME. Phần tương thích J2ME (Đợt 46) chỉ áp dụng cho client J2ME (typeClient 1), không ảnh hưởng APK / PC.

## Đợt 48: Sự kiện theo source V10 (ENZEEFXNROxBARCOLL V10), danh hiệu mới, sự kiện chạy lẫn lộn
### Sự kiện chạy lẫn lộn (trồng dưa hấu đang có giữa Halloween)
- **Nguyên nhân:**
  - Hùng Vương ở Thành cổ có sẵn trong bản đồ và cho trồng dưa / đổi dưa / dâng lễ quanh năm, chỉ xét phút 25 / 55.
  - Xe nước mía (Sự kiện Hè) đặt cứng ở Làng Aru / Mori / Kakarot.
  - Lịch theo ngày cho nhiều sự kiện trùng nhau (Halloween 29/10 – 19/11 trùng 20/11 từ 15/11).
- **Sửa:**
  - Trồng dưa, đổi dưa hấu, dâng sính lễ / bánh chỉ có khi sự kiện **Giỗ tổ Hùng Vương** đang chạy. Dưa đã trồng vẫn thu hoạch được.
  - Xe nước mía chỉ hiện trong **Sự kiện Hè**.
  - Giống V10: **mỗi lúc chỉ 1 sự kiện**. Khi lịch trùng, sự kiện bắt đầu muộn nhất được chạy. Ví dụ 15/11 Nhà giáo 20/11 thay Halloween.
  - `Config.properties`: `event.active=` để trống thì chạy theo lịch. Ghi id (ví dụ `event.active=7`) thì chỉ chạy đúng sự kiện đó, giống `active_event.txt` của V10. Sửa file có hiệu lực sau ≤ 30 giây, không cần khởi động lại.
  - Bật / tắt tay trên panel vẫn được ưu tiên.

### Sự kiện lấy từ V10
- **Halloween:** thêm boss **Xương khô** (`BoXuong`, BossID -352, 10 con).
  - Đánh trúng biến người chơi thành bộ xương 30 phút.
  - Hạ rơi Bí ngô + 3 – 5 Kẹo bàn tay.
- **Nhà giáo 20/11:** NPC **Hoa Hồng** (npc_template 111) mọc ở Làng Aru, Mori, Kakarot, Đảo Kamê, Đảo Guru.
  - Dùng Kéo tỉa hoa → 1 – 5 Bông hoa hồng. Dùng Kéo tỉa hoa đặc biệt → 1 – 3 Bó hoa hồng.
  - Mỗi cây hái 7 lần thì tàn 5 phút.
  - Santa bán kéo (2 / 10 ngọc), đổi 20 Bông hoa hồng lấy Hộp quà, 10 Bó hoa hồng lấy danh hiệu Gõ đầu trẻ (7 ngày).

### Danh hiệu mới
Hiệu ứng lấy từ V10: `data/effdata` + `data/effect/x1..x4` cho 215, 255, 505, 506, 1099.

| Danh hiệu | Chỉ số | Cách nhận |
|---|---|---|
| Chiến binh sao đen (1904) | SĐ +10%, HP +10%, KI +10%, +5% vàng từ quái, 7 ngày | Thắng Ngọc rồng 1 sao đen ở khu 1 ngày chủ nhật (người giữ ngọc), có thông báo server |
| Tay nhanh hơn não (1673) | SĐ / HP / KI +11%, Đẹp +5%, 7 ngày | Halloween: Santa đổi 199 Kẹo bàn tay |
| Mùa đông không lạnh (1765) | Né 5%, HP / KI +12%, 7 ngày | Giáng sinh: Santa đổi 50 Chuông + 50 Kẹo giáng sinh + 3 Tất |
| Kẻ thao túng sói (1286) | SĐ +10%, chí mạng +5% | Đã bán ở tab Danh hiệu nhưng thiếu dòng `data_badges` nên mua không ra: đã thêm |

Dùng vật phẩm danh hiệu trong hành trang để kích hoạt (theo hạn sử dụng).

### Database
- `Dot48Migration` tự thêm dữ liệu khi khởi động server (chạy lại không sao): `data_badges`, `item_template` (part / mô tả), `npc_template` 111, `season_event_npc`, `season_event_exchange` 201 – 206, boss và rơi đồ Halloween.
- Không cần chạy file SQL.
- `vsData` 19 → 20, `vsItem` 16 → 17 để client tải lại NPC và item.

### Chưa làm
Boss sự kiện khác của V10: Tuần Lộc (Giáng sinh), Bé Na (Tết), Thỏ Đại Ca / Meowth (Trung Thu), Rồng nhí 1 – 7 sao (Giỗ tổ). Chúng dùng hệ boss riêng của V10, sẽ làm khi gần tới các sự kiện đó.

## Đợt 49: Lỗi NullPointerException khi mở hộp quà sự kiện
- Log: `NullPointerException: Cannot read field "name" because "<parameter2>.template" is null` tại `Event2026Service.openBox` (dòng 151).
- **Nguyên nhân:** mở hộp cuối cùng thì `subQuantityItemsBag` xóa hộp khỏi hành trang (template = null), sau đó thông báo lại đọc `box.template.name`.
- Quà vẫn được cộng, chỉ có thông báo lỗi và log dài.
- **Sửa:** lấy tên hộp trước khi trừ số lượng.

## Đợt 50: Boss Tuần Lộc, Bé Na; thông báo mở hộp; hiệu ứng mở hộp; danh hiệu mới ở Santa
### Boss Tuần Lộc (Giáng sinh) và Bé Na (Tết) – theo source V10
- Cùng cơ chế với Lân con:
  - Đánh hết máu thì con vật đi theo người hạ.
  - Dẫn về Quy Lão Kame, bấm **Giao Tuần Lộc / Giao Bé Na**.
  - Đi xa quá 300px thì mất lượt giao.
- **Phần thưởng:**
  - Tuần Lộc: như Lân con (thú cưỡi ngẫu nhiên có chỉ số).
  - Bé Na: 1 Capsule Tết 2026.
- Mỗi sự kiện 10 con: Giáng sinh `-372:10`, Tết `-373:10`.
- Bỏ cách gian lận "chat thang là con vật đi theo luôn" ở 2 boss mới.
- Câu chat thô tục của Tuần Lộc trong V10 đã thay.

### Mở hộp quà sự kiện
- Thông báo toàn server chỉ khi nhận đồ **vĩnh viễn**. Đồ có hạn dùng (ngày) không thông báo nữa.
- Thêm **hiệu ứng mở hộp giống Capsule kì bí** (gói -81 OPEN_ITEM: icon hộp → icon quà, vàng / ngọc / hồng ngọc có icon riêng).
- Áp dụng khi dùng hộp và khi đổi quà "mở ngay" từng cái một.

### Danh hiệu mới trong tab Danh hiệu (Santa)
- Đã thêm vào tab (đánh dấu NEW), có chỉ số theo `data_badges`: Chiến binh sao đen, Tay nhanh hơn não, Mùa đông không lạnh, Perfect, Phá cỗ trung thu VIP, Phá cỗ trung thu, Đón Xuân.
- Danh hiệu có nhiệm vụ: vẫn hiện % hoàn thành và mua khi đủ 100%.
- Danh hiệu sự kiện / phần thưởng (không có nhiệm vụ):
  - Không hiện dòng "Hoàn thành #%".
  - Bấm mua thì báo **cách nhận** (lấy từ mô tả vật phẩm), không bán bằng ngọc.
- Mô tả cách nhận đã ghi cho Perfect, Phá cỗ trung thu (VIP), Đón Xuân.

### Database
- Tự cập nhật khi khởi động (`Dot48Migration.runDot50`): `season_event.bosses`, `item_template`, `item_shop` + `item_shop_option` tab 44.
- `vsItem` 17 → 18.

## Đợt 51: Boss Thỏ đại ca, Meowth, Rồng nhí; danh hiệu chỉ qua đua top; Chi Chi / Quy Lão theo sự kiện (như video)
### Boss mới (theo source V10)
- **Trung Thu:**
  - **Thỏ đại ca** (-374): đánh trúng biến người chơi thành củ cà rốt 30 phút (outfit 406/407/408); mỗi đòn mất tối đa 1.000 HP; hạ rơi 10 Củ cà rốt.
  - **Meowth** (-377): mỗi đòn chỉ mất 1 HP (HP 2.000). Hạ rơi 30% Ván bay cá đuối VIP (1 – 5 ngày); không thì 90% Pet rồng Pikachu (SĐ / HP / KI +22%); còn lại vật phẩm 1099 – 1103.
- **Giỗ tổ:** **Rồng nhí 1 – 7 sao** (-390 … -396): không đánh, chỉ chạy theo người chơi; mỗi đòn tối đa 25.000 HP. Hạ rơi 90% 1 – 3 Mảnh trứng rồng nhí, 10% Trứng rồng nhí.
- Mỗi loại 5 – 10 con, chỉ khi sự kiện đó chạy.

### Danh hiệu chỉ nhận qua đua top
| Danh hiệu | Đua top |
|---|---|
| Đón Xuân | Tết – top Lì xì (1–10: 30 ngày, 11–100: 7 ngày); vẫn còn top 1 Hộp đen |
| Phá cỗ trung thu VIP / Phá cỗ trung thu | Trung Thu – top Hộp bánh (top 1–3 VIP 30 ngày, top 4–100 thường 15 ngày) |
| Tay nhanh hơn não | Halloween – top Túi mù |
| Mùa đông không lạnh | Giáng sinh – top mới **Hộp quà Giáng sinh** |

- Đã xóa mọi công thức đổi lấy danh hiệu.
- Bỏ Phá cỗ trung thu khỏi vòng quay ngọc.
- Thêm đua top **Hộp quà 20-11** (quà như các top khác).
- Cần ít nhất **10 điểm** mới được xếp top. Quà nhận trong **7 ngày** sau khi chốt.

### Chi Chi (Đảo Kamê) theo sự kiện đang chạy, như video
- Menu gồm:
  - Các **Top** của sự kiện. Chọn top: thông tin (kết thúc sau N ngày, hạn chót nhận giải), **Top 100** (bảng xếp hạng), **Xem điểm** ("Chưa đủ điểm vào TOP (ít nhất là 10 điểm)").
  - **Nhận giải đua top** (khi có quà chờ).
  - **Cửa hàng** (shop sự kiện dạng cửa hàng thật).
  - **Hủy bỏ trang bị có hsd**, **Hủy bỏ bóng mờ hsd dưới 35 ngày** (có hỏi xác nhận).
- Đã bỏ các top cứng chạy quanh năm (Hộp quà thiếu nhi 2025, Nước mía, Kem trái cây) và nút Hộp đen bí ẩn.
- Cửa hàng lấy từ nhóm "Cửa hàng" của sự kiện đang chạy. Mua vẫn tính hạn dùng, điểm top, điểm sự kiện.
- Halloween thêm như video: Lưỡi hái hồng, CT Bill Bí Ngô, CT Caufila Dơi, Ma trơi, Hồn ma Goku / Ca đíc / Pôcôlô, Capsule Squid Game, Cánh thiên thần 1 – 2.

### Quy Lão: Đổi điểm sự kiện
- Mở cửa hàng **Đổi thưởng** trả bằng **điểm sự kiện** (trước đây là shop trả ngọc).
- Danh sách như video (Hộp quà Cađíc VIP, Goku Day VIP, Ván bay cung trăng, CT Android 21 Evil, Cánh Thiên thần – Ác quỷ, Gohan Beast, Black Goku Rose, …, Bóng mờ sao may mắn, Bư tập tạ). Có cho mọi sự kiện, sửa được trong bảng `season_event_exchange` (nhóm "Đổi điểm").
- **Đổi sự kiện thì cửa hàng Chi Chi và Quy Lão đổi theo.**
- "Đua top ngọc rồng" và "Quà Trung Thu" chỉ hiện trong Trung Thu.
- Nhóm "Cửa hàng" / "Đổi điểm" không còn hiện trong menu sự kiện của Santa (đã thành cửa hàng).

### Database
- Tự cập nhật khi khởi động (`Dot48Migration.runDot51`).
- `vsItem` 18 → 19.

## Đợt 52: Item mặc không hiện, Xe nước mía, gộp thẻ Oozaru, chữ ký số "Việt"
### Item mặc vào không hiện (gán đúng part)
- So item_template với source V10:
  - 34 pet (Thỏ xám / trắng, Hổ mặp, Lính bảo vệ, Pet mèo, Pet Voi / Gà / Ngựa chín…, Pikachu thanh long, Thỏ Ú…) và 9 cải trang (Goku / Pôcôlô / Cađíc võ sĩ, Gohan / Biđen / Bản Cô Nương Siêu Nhân, Chan Xư, Arale, Xuka): part giống hệt, gán thẳng.
  - 9 cải trang (Android 21 Evil / Thân Thiện, Đặng Văn Chiến, Bunma Rider, Kochiro, Musashi, Kirin chú cuội, Ác Ma Pico, Janemba): part V10 khác part cùng id của mình nên chép sang **part mới 2352 – 2378**.
  - 13 đeo lưng (bạch tuộc, Bông cúc, Cờ Pokemon, Thùng rác, Lồng đèn 2 lon, Cú con cưng, Bó hoa hồng 2, Ba lô ngọc rồng, Bông tuyết, Cúp vàng, Hỏa tiêm thương): thêm **flag_bag 162 – 173**.
- Ảnh:
  - 60 ảnh thiếu chép từ V10 vào `data/icon/x1..x4`.
  - 21 ảnh trùng id nhưng khác hình (Cờ Pokemon, chân Android 21) chép sang id mới 32278 – 32298.
- **Xe nước mía** (NPC 84) đang dùng part của *Quán phở anh 2* (sự kiện Phở Anh Hai trong V10). Đổi sang part xe nước mía đúng (part mới 2349 – 2351).
- `Dot52Migration` tự chạy khi khởi động:
  - Thêm part / flag_bag nếu chưa có, cập nhật item chỉ khi đang trống.
  - Bảng `part` không có khóa chính nên dùng `insert ... where not exists`.
  - Nếu id mới đã có dữ liệu khác thì bỏ qua và báo lỗi, không ghi đè.

### Gộp 3 thẻ Oozaru
- Thẻ Oozaru / Oozarun 1 / Oozarun 2 → 1 thẻ **Thẻ Oozaru (1771)**, chỉ số và aura của Oozarun 2.
- Mảnh khỉ Oorazu 1 / 2 dùng vẫn tính vào Thẻ Oozaru.
- Người chơi đã có thẻ cũ được gộp khi vào game (giữ cấp cao nhất).

### Bỏ "Mr Blue", chữ ký "Việt"
- 196 dòng `@author ... Mr Blue` → `@author Việt`.
- Lớp `MrBlue` (nạp dữ liệu người chơi) đổi tên `VietLoader`.
- Tên bot `mrblue1, 2…` → `viet1, 2…`.
- `20.jar` được **ký số bằng chứng chỉ riêng "Việt"** (RSA 3072, SHA-256, hạn đến 2036).
  - Chứng chỉ công khai và script ký lại: `tools/codesign/`.
  - Khóa riêng `viet-codesign.p12` **không đưa lên git** (đã gửi riêng cho chủ server).
- `vsData` 20 → 21, `vsItem` 19 → 20.

## Đợt 53: Kiểm tra chữ ký Việt khi khởi động, aura Set kích hoạt / Beerus
### Kiểm tra chữ ký khi server khởi động (`JarSignatureCheck`)
- Trước khi chạy, server đọc toàn bộ file trong `20.jar` và kiểm tra:
  - Mã băm của từng file.
  - Mọi file đều phải được ký bằng chứng chỉ **CN=Việt** (so vân tay SHA-256).
- Không cho chạy (thoát, báo "KIỂM TRA CHỮ KÝ VIỆT THẤT BẠI") khi: jar chưa ký, bị sửa file, bị thêm file lạ, hoặc ký bằng khóa khác.
- Đã thử cả 4 trường hợp: jar đã ký chạy bình thường (3.434 file hợp lệ); jar chưa ký, sửa 1 byte class, thêm file lạ đều bị chặn.
- Chạy từ thư mục class (lập trình trong NetBeans) chỉ cảnh báo, vẫn chạy.
- **Từ bản này, mọi bản build phải ký bằng `tools/codesign/sign_jar.*` mới chạy được.**

### Hiệu ứng Set kích hoạt / Beerus
- Mặc đủ **5 món cùng một Set kích hoạt** (mọi set, kể cả Champa) → **hiệu ứng 504** quanh nhân vật.
- Đủ **5 món set Beerus** → **hiệu ứng 512**.
- 504 / 512 là mã **hiệu ứng** (`data/effdata`), không phải aura. Aura là ảnh riêng `aura_<id>_0/1` trong `data/img_by_name`, chỉ có các id 0 – 94, 120 – 122.
- Hệ hiệu ứng set có sẵn trong source dùng mã 1200 / 10277 / 5017 / 1202 **không tồn tại** nên chưa bao giờ hiện. Hàm gửi hiệu ứng của người khác cho người mới vào map cũng rỗng. Đã viết lại cả hai.
- Mặc / tháo đồ thì hiệu ứng bật / tắt ngay cho cả map.
- Đếm thêm set Champa (option 257) và Beerus (option 115).
- **Sửa lỗi cũ:** số món set kích hoạt chỉ được đếm khi vào game, thay đồ phải thoát ra vào lại mới có hiệu lực. Giờ đếm lại mỗi lần tính chỉ số.
- `getAura()` đổi từ byte sang short.

## Đợt 54: Aura thẻ radar theo cấp
| Thẻ | Lv.1 | Lv.2 | Lv.3 |
|---|---|---|---|
| Thẻ Đội Trưởng Vàng | aura 0 | aura 0 | aura 0 |
| Thẻ Rồng Thần Namek | aura 1 | aura 1 | aura 1 |
| Thẻ Oozaru (đã gộp) | aura 2 | aura 3 | aura 4 |
| Thẻ khỉ con | aura 120 | aura 121 | aura 122 |

- **Lv.1:** đã sưu tầm thẻ (Level -1). **Lv.2:** Level 1. **Lv.3:** Level 2, giống dòng "Sử dụng Lv.x" trong bảng thẻ.
- Trước đây phải đạt Lv.3 mới có aura, giờ có từ Lv.1.
- Có nhiều thẻ thì hiện aura của **thẻ có chỉ số cao nhất**: tổng chỉ số các dòng đã mở tới cấp hiện tại, bằng nhau thì thẻ cấp cao hơn.
- Aura thưởng đua top chỉ hiện khi không có aura thẻ.
- Sưu tầm / lên cấp thẻ làm đổi aura thì cập nhật ngay cho mình và người trong map, không cần đổi map.
- Bỏ thẻ 1142 khỏi danh sách aura (không có trong bảng radar).

## Đợt 55: Hiệu ứng bóng mờ, số mảnh thẻ radar, panel admin v2
### Bóng mờ có hiệu ứng
| Bóng mờ | Hiệu ứng |
|---|---|
| Tuyết rơi (1960) | 508 |
| NRO (1971) | 509 |
| Sao may mắn (2023) | 513 |
| Trung thu (2054) | 514 |
| Hoa hồng đỏ (2096) | 518 |
| Hoa hồng băng (2097) | 519 |

- Cả 6 bóng mờ mang ở ô **bóng bước chân** (trước chỉ 2 bóng hoa hồng), không chiếm ô pet / đeo lưng.
- Mang / tháo (chat "thao buoc chan") là bật / tắt hiệu ứng ngay. Người vào map sau cũng thấy.

### Thẻ radar: số mảnh và điều kiện
- Cấp thẻ: -1 = đang sưu tầm (chưa có chỉ số), 1 / 2 / 3 = Lv.1 / Lv.2 / Lv.3 (tối đa).
- Dòng chỉ số Lv.1 có tác dụng từ Lv.1. Dòng Lv.2 / Lv.3 khi đang dùng thẻ ở đúng cấp đó.

| Thẻ | Mảnh lên Lv.1 / Lv.2 / Lv.3 | Điều kiện dùng mảnh |
|---|---|---|
| Đội Trưởng Vàng | 3 / 30 / 30 | — |
| Rồng Thần Namek | 3 / 30 / 30 | — |
| Oozaru | 10 / 125 / 125 | Rồng Thần Namek Lv.1 |
| Khỉ con | 10 / 125 / 125 | Oozaru Lv.2 |
| Thẻ khác | 1 / tối đa của thẻ / tối đa của thẻ | như cũ |

- Dữ liệu cũ (-1 / 1 / 2) đổi **1 lần** sang (1 / 2 / 3), người chơi giữ nguyên cấp và chỉ số. Đánh dấu ở bảng mới `server_migration`.
- Thẻ chưa đạt Lv.1 không bật dùng được, không có aura.

### Panel admin v2
- **Giao diện:**
  - Thanh trên luôn hiện: tên server, số online, sự kiện đang chạy, trạng thái chữ ký Việt, RAM, phiên bản Java / dữ liệu.
  - Menu có biểu tượng.
  - Điện thoại: menu thu gọn, mở bằng nút ☰.
- **Tổng quan:** biểu đồ người online, thẻ sự kiện, thao tác nhanh (lưu tất cả, nạp lại sự kiện / shop, thông báo nhanh, bảo trì), Top 10 sức mạnh.
- **Trang mới:**
  - **Đua top sự kiện:** danh sách top, Top 100, chốt top.
  - **Thẻ radar:** số mảnh, điều kiện, aura từng cấp, sửa tên / chỉ số và nạp lại ngay.
  - **Danh hiệu:** ảnh hiệu ứng, chỉ số, cách nhận, sửa và nạp lại ngay.
  - **Hiệu ứng & aura:** bảng mã đang dùng và thư viện ảnh 174 hiệu ứng, 71 aura (cắt khung đầu), có tìm theo mã.
- API mới: `server`, `eventtops`, `eventtop`, `radar`, `radar/save`, `badges`, `badge/save`, `fx`; ảnh `/fx/eff/<id>`, `/fx/aura/<id>`.

## Đợt 56
- Bà Hạt Mít (sự kiện Nâng cấp trang bị lên Sao Pha Lê 10, 31/10 - hết 20/11/2026): mốc 7 món thưởng Hộp Set kích hoạt tùy chọn + **Hào quang chân Sao Pha Lê 10** vĩnh viễn (item 2112, thay cho bóng mờ hoa hồng).
- Đồ đập lên 10 sao trong thời gian sự kiện được gắn dòng "Hào quang chân Sao Pha Lê 10" (option 261). Mốc nhiệm vụ chỉ đếm đồ có dòng này.
- Mặc đồ có dòng này hoặc mang Hào quang chân SPL10: hiệu ứng 515 + SĐ 6%, HP 6%, KI 6%, sát thương cuối 2% (cộng 1 lần, không cộng dồn).
- 4 cải trang mới (2113 - 2116, part 2379 - 2390) và 123 icon mới / cập nhật từ gói 31/10. data v22, item v21.
- Panel: giao diện riêng cho điện thoại (thanh dưới, menu dạng lưới ô, bảng thành thẻ, hộp thoại trượt từ dưới).
- Halloween theo ảnh sự kiện:
  - Đổi tên: 2113 Cải trang Goku Ninja, 2114 Pet Đầu Quỷ (pet đi theo), 2115 Cải trang Melee, 2116 Tiểu thư Malfoy.
  - Vật phẩm mới: 2117 Cờ đeo lưng 3 con ma (cờ 174, 18 khung), 2118 Hộp Ngọc Rồng Bí (Bí ngô 1 - 7 sao), 2119 Hộp Halloween (Chi Chi bán 10 triệu vàng).
  - Chỉ số theo ảnh "Chỉ số vật phẩm HLW" (item_template_option). Option mới 262 biến người xung quanh thành Zombi; làm thêm option 221 (hồi HP/KI cho mình và đồng bang mỗi 30 giây) và 161 (ngọc mỗi ngày khi hạ quái).
  - Nội dung Túi mù, Kẹo mù, Thiệp Ma, Hộp Halloween, Hộp NR Bí, quà Zombi; quà đua top Túi mù và Hộp Kẹo Ma Quỷ (top 1 Túi mù đạt 21000 điểm nhận thêm Set kích hoạt Beerus + Trứng Xên Vàng).
  - Hộp quà có "bảo hiểm" (cột pity, bảng box_pity): mở đủ số lần chưa ra thì lần đó chắc chắn ra. Sửa được trên panel.
  - Hộp / top / chỉ số chỉ ghi 1 lần (server_migration 'dot56_halloween'), sau đó sửa trên panel không bị ghi đè.

## Đợt 57
- **Sửa lỗi NullPointerException khi mở shop (Quy Lão "Đổi điểm", Chi Chi, mọi shop)**: vật phẩm tạo từ chỉ số mặc định / mua shop / nhặt dưới đất dùng chung đối tượng chỉ số với shop; khi vật phẩm bị hủy, chỉ số của shop bị xóa theo (optionTemplate = null) và lần mở shop sau bị lỗi. Giờ mọi nơi đều sao chép chỉ số (ItemOption.copyAll), hủy vật phẩm không xóa mẫu chỉ số, shop bỏ qua chỉ số hỏng, tra mẫu chỉ số theo đúng id (không theo vị trí).
- Shop Chi Chi mùa Halloween giống ảnh: Hòm Halloween 10 triệu vàng (hạn 30 ngày, chứa quà "Hộp Halloween"), Giỏ đựng kẹo trái bí 5 triệu vàng, giỏ / giấy hạn 35 ngày, thêm Cánh thiên sứ hắc ám 999 ngọc (SĐ 15%, HP 13%, KI 13%, giảm 3% sát thương, phản 5%, không giao dịch). Bỏ Hộp Halloween 2119 (trùng Hòm Halloween; ai đã có vẫn mở được).
- Thẻ sưu tầm thường (2120, icon 17638): mở ra 1 mảnh Đội trưởng Vàng / Rồng thần Namếc / Khỉ Oozaru; có trong Thiệp Ma.
- Kẹo mù Halloween thêm Avatar mang kính đo sức mạnh (894 / 895 / 896, SĐ 20%, HP 17%).

## Đợt 58
- Chỉ số đúng theo ảnh (trước đây các món này mua / nhận về không có dòng chỉ số nào): Ma trơi giảm 14% sát thương, Hồn ma Goku HP 24%, Hồn ma Ca đíc SĐ 14%, Hồn ma Pôcôlô KI 24%, Capsule Halloween / Squid Game không giao dịch (Capsule Halloween hạn 30 ngày), Bill Bí Ngô SĐ/HP/KI 20% + biến người xung quanh thành Bí Ngô (option mới 263) + không bị hóa Xương + không thể bán lại, Pet Cerberus SĐ 16% HP 15% KI 15% chống lạnh, Cánh thiên thần sa ngã +30% tấn công lên boss, biến 15% tấn công thành HP, giảm 10% sát thương, không giao dịch, Cánh thiên thần 1 / 2 SĐ 11%.
- Option 32 "Không bị hóa Xương" giờ có tác dụng (miễn mọi biến hình Halloween).
- Đeo lưng trỏ tới cờ chưa có trong flag_bag (21 món) trước đây đeo vào không hiện: thêm cờ Labubu (141) theo V10, các cờ còn thiếu tạm hiện icon vật phẩm.
- Một dòng hạn dùng sai định dạng trong item_box_reward không còn làm hỏng cả bảng hộp quà.
- Panel: sửa chỉ số mặc định của vật phẩm (Vật phẩm → Chi tiết → Sửa chỉ số), trang Cửa hàng sự kiện (sửa giá, hạn, quà, thêm / xóa), trang Kiểm tra dữ liệu (vật phẩm không tồn tại, chỉ số hỏng, đồ sự kiện thiếu chỉ số, part / cờ thiếu, icon thiếu, số ô option >= 255).

## Đợt 59
- "Cải trang Melee" (2115) chính là Cải trang Công Chúa Quỷ: đổi tên, chỉ số HP 20%, KI 20%, SĐ 25%, TNSM 150%; có trong Thiệp Ma (bảo hiểm 100 lần mở).
- Thẻ radar Oozaru và Khỉ con đổi icon + hình theo cấp (Oozaru: Oozaru → Oozarun 1 → Oozarun 2; Khỉ con: Goku GT Kid → Goku GT → Goku cận vô cực). Lên cấp thì gửi lại bảng thẻ. Mã chỉ số thẻ gửi client qua OptionIdMap.
- Chỉ số theo ảnh tham khảo: Caufila Dơi, Lưỡi hái hồng, Rađa dò Zombie, CT Bunma Rider, Poc Bikini, Hắc Mị Nương, Janemba, Máy bay giấy, Đeo lưng dụng cụ học tập, Pikkon, Thú cưỡi rồng Đen / Băng, Pet Minion, CT Frieren, Pet ngựa KPI, Đeo lưng Mãi Dell thành công. Thiệp Ma 50 ngọc hạn 30 ngày, Rađa dò Zombie hạn 30 ngày.

## Đợt 60
- Chỉ số ngẫu nhiên theo ảnh: Pet Xên con / hoàn hảo / Max, Bóng bước chân hoa hồng xanh (đi cùng Xên hoàn hảo +6% STCM) và đỏ (đi cùng Xên Max +5% giảm sát thương).
- Pháo hoa NRO thành cờ đeo lưng (flag 175, hoạt ảnh pháo hoa) SĐ 20% + SĐ 10.000 - 25.000; Đeo lưng bông tuyết HP 20% + HP 10.000 - 25.000.
- Trứng Xên Vàng: mở ra 1 trong 3 Pet Xên, chỉ số tối đa, vĩnh viễn.
- Option mới 264 (Lưỡi hái hồng: đi cùng CT Black Goku +4% SĐ, HP, KI) và 265 (Poc Bikini: giảm 5% SĐ của 10 người gần nhất, mặc CT hè Pic / Poc / King Kong không bị ảnh hưởng).
- OptionIdMap thêm 4 ô trống (67, 68, 69, 225) cho mã chỉ số >= 255 (đang dùng 11 / 14).

## Đợt 61
- Hợp thể bông tai (cấp 1 / 2 / 3):
  - Sửa NullPointerException khi đang hợp thể mà không có đệ (thiếu ngoặc trong getHead / getBody / getLeg; cộng chỉ số đệ khi đệ null).
  - Đệ Vegeta / Kid Beer (typePet 3) bị cộng +20% SĐ hai lần (khối "pet pic" trùng "pet beer").
  - levelBT không được đặt lại khi tính chỉ số.
  - Bông tai không còn trong hành trang (bán, cất rương, giao dịch) mà vẫn hợp thể, chỉ số bông tai mất: giờ tự tách hợp thể. Đệ bị xóa khi đang hợp thể: tự bỏ trạng thái hợp thể.
- Thưởng boss theo bậc (BossRewardService, bảng boss_reward): thay "20.000 - 30.000 vàng + 5 điểm" như nhau cho mọi boss bằng thưởng theo máu tối đa (5 bậc); quà riêng của từng boss giữ nguyên. Sửa trên panel trang "Thưởng boss".
- Panel trang "Database": xem mọi bảng, tìm, sắp xếp, phân trang, sửa ô (đặt NULL), thêm / xóa dòng (dòng cũ ghi vào nhật ký thao tác), xem cấu trúc, xuất SQL / CSV.

## Đợt 62
- Boss chỉ rơi vàng theo bậc: bỏ điểm sự kiện (kể cả Ăn Trộm) và bỏ quà thêm (Đá bảo vệ, Capsule, Thỏi vàng, Rương SPL VIP... là đồ đua top).

## Đợt 63
- Boss không rơi vàng (bỏ cả vàng của Broly Super). Quà thêm theo bậc là nguyên liệu farm: đá nâng cấp, ngọc rồng 4 - 7 sao, sao pha lê, đậu thần 7 - 9, mảnh vỡ / mảnh hồn bông tai, cuồng nộ / bổ huyết / bổ khí, viên capsule kì bí, đá may mắn cấp 1 - 2. Không có đồ đua top.
- Broly Super không rơi Mảnh khỉ Oozaru (đồ đua top).

## Đợt 64
- Boss "Broly Super" đổi tên thành "SSJ Broly". Quà giữ nguyên như cũ (vàng 100.000 - 2.000.000, Mảnh khỉ Oozaru, trang bị cấp 11 - 12...), không nhận quà theo bậc của BossRewardService.

## Đợt 65
- Phiếu sao vàng may mắn (2021): dùng để vào Ngục tù may mắn (map 187, bản sao Hành tinh ngục tù) trong thời gian ghi trên phiếu (1440 phút). Còn hạn dùng lại phiếu để quay lại (không mất phiếu); hết hạn tự đưa về nhà. Hạn lưu ở player_extra.lucky_map_until.
- Ngục tù may mắn: quái rơi 300.000 - 600.000 vàng (50%), nhân theo may mắn; vẫn rơi đồ của Hành tinh ngục tù.
- Option 236 "May mắn" trước đây gần như không có tác dụng: chỉ đếm đồ đang mặc (bỏ sót bóng mờ, danh hiệu, thẻ), chỉ dùng ở Map riêng tư / map đồ kích hoạt, và công thức làm 10% may mắn chỉ tăng 0,6% tỉ lệ. Giờ tính từ mọi nguồn (NPoint.tlMayMan), +1% may mắn = +1% tỉ lệ rơi đồ hiếm (tối đa +200%), áp dụng thêm cho đồ hiếm ở Ngục tù và vàng ở Ngục tù may mắn.
- Chỉ số theo ảnh shop Quy Lão: Bư tập tạ, Bóng mờ sao may mắn, Ván bay sao băng, Pet Mèo thần tài, Cân Đẩu Vân / Khủng Long Thơ Mộng, Pet Hải Ly, CT Urôn Trư Bát Giới, Cánh thiên thần sa ngã (Đệ), Pet Ma tốc độ, Ván bay Sọ Dừa, Cánh Thiên thần - Ác quỷ, CT Android 21 Evil, Ván bay cung trăng.

## Đợt 66
- Lỗi hành trang (đang xem thì đứng, văng về màn hình đăng nhập) sau khi mở Trứng Xên Vàng: client APK / PC đọc chỉ số 2 byte có dấu, Pet Xên chỉ số tối đa có HP +150.000, KI +35.000, Giáp +35.000 vượt 32.767 thành số âm làm client lỗi. Giờ gửi tối đa 32.767; HP+# / KI+# lớn hiện dạng HP+#K / KI+#K. Server và client J2ME vẫn dùng số thật.
- Quái không rơi ngọc nữa (bỏ ngọc miễn phí mỗi ngày và ngọc rơi ngẫu nhiên). Ngọc nhận từ nhiệm vụ hàng ngày: dễ 2, thường 4, khó 6, rất khó 10, địa ngục 15.
- Chặn qua map khi chưa làm tới nhiệm vụ: thêm Rừng xương, Vực Maima, Rừng thông Xayda, Đảo Kamê, Đảo Guru, Vách núi đen, Nam Kamê, Nam Guru, Thung lũng đen, Vách núi Aru / Moori, Vực Plant (trước đây đi capsule tới được). Sửa thiếu break ở Núi khỉ vàng.
- Dòng "đi cùng" / "đủ món": 4 món sao may mắn +10% vàng rơi; Cân Đẩu Vân + Chi Chi Võ Đài, Khủng Long + Cân Đẩu Vân +5% giảm sát thương; Urôn + Bồ cào 9 răng +5% SĐ +10% SĐCM; Ván bay Sọ Dừa + Cánh Thiên thần - Ác quỷ +3% SĐCM, +10% HP; Rồng Đen + Pet Rồng nhí +5% né +5% chí mạng; đủ 4 thú cưỡi rồng +2% SĐ HP KI; Android 21 Evil ở gần Android 21 Thân Thiện +10% chí mạng.
- Pet Ma tốc độ: bất tử 3 giây khi HP dưới 5% (hồi 120 giây), 10% có dòng chống lạnh. Năng lượng E10: 10 phút KI +10%, SĐ +5%, giảm 5% HP. Cánh thiên thần sa ngã (Đệ), Bư tập tạ, Cánh thiên thần 3 (Đệ) chỉ đệ tử mặc được.

## Đợt 67
- Lỗi "lần đầu bình thường, thoát ra vào lại thì không mở được shop Đảo Kamê và hành trang lỗi": client APK / PC lưu bộ nhớ đệm danh sách item theo 2 gói (loại 1 và loại 2), gói loại 2 chỉ giữ gói cuối. Phần item sau id 750 đã vượt 65535 byte nên bị chia nhiều gói loại 2 -> vào lại game client mất các item ở giữa (shop / hành trang có item đó bị lỗi). Giờ luôn gửi đúng 2 gói: dời điểm chia và rút gọn 77 mô tả dài nhất (còn khoảng 80 byte) cho vừa. Tăng vsItem lên 22 để client tải lại.

## Đợt 68
- Quái chỉ rơi sao pha lê (441 - 447) khi người đánh mặc cải trang có dòng "Dò pha lê" (option 110, trước đây có dòng nhưng không có tác dụng). Tỉ lệ 10% (cỏ bốn lá +15%).

## Đợt 69
- Đệ tử 2: đệ tử đạt 40 tỷ (săn Super Broly) -> đánh Hirudegarn (22h) có 20% rơi Quả trứng Mabư (của riêng người đánh). Trứng nở xong (đếm về 0:00) hoặc đã có đệ Mabư -> đánh Hirudegarn lần nữa có 20% rơi Bình hút năng lượng. Tích 3000 Kilis ở Sa mạc hoang vu rồi dùng Bình hút: đổi ngẫu nhiên Uub / Kid Beer / Jiren (khởi đầu 40 tỷ, chỉ số gốc cao hơn đệ thường, 9 ô trang bị, có chiêu 5). Trước đây Hirudegarn rơi trứng / bình cho bất kỳ ai và đổi đệ đòi đệ Mabư 40 tỷ.
- Tapion chỉ đưa vào map Hirudegarn lúc 22h - 23h.
- Cánh thiên thần sa ngã (Đệ), Bư tập tạ, Cánh thiên thần 3 (Đệ) chỉ đệ tử 2 mặc được.

## Đợt 70
- Trả lại ngọc miễn phí mỗi ngày (con quái đầu tiên rơi 2 ngọc) và thông báo khi vào game. Ngọc từ nhiệm vụ hàng ngày vẫn giữ.
- Hirudegarn rơi Quả trứng Mabư 20%, ai cũng nhặt được. Bình hút năng lượng vẫn chỉ rơi cho người có trứng đã nở (hoặc đệ Mabư).

## Đợt 71
- Hiệu ứng danh hiệu động: Đón Xuân (303) dùng ảnh 511 (chữ ĐÓN XUÂN đổi màu + hoa mai, hoa đào rơi), Phá Cỗ Trung Thu / VIP (300, 301): bảng tên sáng nhấp nháy + đèn lồng rơi. Giữ nguyên mã hiệu ứng nên người đã có danh hiệu không bị ảnh hưởng. Công cụ tạo lại: tools/effect/build_title_effects.py.
- Ảnh thú cưỡi 48 - 79 (trước đây thiếu ảnh nên các thú cưỡi mã part 48 - 73 như Ván bay Cây Thông, Magikarp, Rồng Thiêng, Lugia, rồng Băng / Đen / Vô Cực, Chổi bay, Máy bay giấy, Ván bay sao băng... cưỡi vào không hiện), thêm dòng img_by_name (số frame tự đo theo ảnh).
- 7 thú cưỡi mới (2121 - 2127): Rồng Hắc Ám, Bạch Điểu, Ván bay Bóng Đá 2026, Kim Phượng, Kỳ Lân, Hỏa Điểu, Rồng Lửa (SĐ / HP / KI 6 - 10%, bay và phục hồi KI).
- Thêm ảnh Skills / aura còn thiếu từ gói data. vsData 23, vsItem 23.

## Đợt 72
- Thú cưỡi mới theo ảnh: 2122 Đại bàng đất sét nổ (SĐ 10%, HP 13%, KI 10%, giảm ST 10%, né 10%, HP +12.000, hồi 7% HP/30s, bay hồi KI), 2121 Đại bàng đất sét nổ VIP (SĐ 10%, HP 15%, KI 10%, giảm ST 11%, né 12%, HP +25.000, hồi 7% HP/30s, bay hồi KI), 2123 Thú cưỡi Kỳ Lân (giữ tạm). Bỏ Rồng Hắc Ám, Kim Phượng, Hỏa Điểu, Rồng Lửa; dồn mã cho liền (client dùng thứ tự = mã).
- Ván bay World Cup 2026 (2044) đã có sẵn với đúng chỉ số trong ảnh nhưng gán sai hình (tàu ngầm số 19, part 33): đổi sang hình ván bay 2026 (part 75). Bỏ bản trùng 2123 cũ.
- vsData 24, vsItem 24.

## Đợt 73
- Client Unity 2.4.7 (Thời Đại Ngọc Rồng): sửa IP máy chủ thành 160.191.243.165:14445, bỏ logo trên màn hình trong game, gửi "|long" để server dùng giao thức số lớn (client này đọc mã chỉ số 2 byte + giá trị 4 byte như J2ME 2.4.x). File sửa và hướng dẫn: tools/client-2.4.7.
- Server: nhận "|long" trong chuỗi phiên bản -> session.longNumbers. Config server.ip = 160.191.243.165 (server gửi IP này cho client).

## Đợt 74
- Santa > Danh hiệu > tab "Sở hữu": tên tab đếm đúng số danh hiệu nhưng bấm xem trống với các danh hiệu không nằm sẵn trong tab 45 (danh hiệu đua top / sự kiện mới). Giờ dựng thẳng từ danh hiệu người chơi đang có (kèm chỉ số và hạn dùng), bấm để đổi sang dùng.
- Thẻ radar: aura chỉ hiện khi thẻ đang được bấm "Sử dụng" (trước đây đạt Lv.1 là tự hiện); bật / tắt thẻ thì aura hiện / ẩn ngay. Lên cấp thẻ hiện bảng thông báo (kèm nhắc bấm Sử dụng nếu chưa dùng).

## Đợt 75
- Nâng giới hạn server cho bằng client 2.4.7 (đọc số 8 byte):
  - HP, KI, SĐ (cả chỉ số gốc) đổi sang số long, trần mới 900 nghìn tỷ (trước 2,1 tỷ). Đọc / lưu database bằng long. Sát thương mỗi đòn vẫn tối đa 2,1 tỷ.
  - Vàng tối đa 999 nghìn tỷ (trước 200 tỷ).
  - Giao dịch vàng mỗi lần tối đa 2 tỷ (trước 10 triệu); ký gửi giá tối đa 2 tỷ, tối đa x999 (trước 100.000 thỏi vàng / 1.000.000 ngọc, x99).
  - Ngọc / hồng ngọc giữ 2,1 tỷ (client đọc 4 byte). Client cũ (byte) vẫn nhận số đã giới hạn như trước.

## Đợt 76
- Client Android DragonBoy249 (bản chính thức 2.4.8, Java thường, không mã hoá) sửa IP sang 160.191.243.165:14445 (cả 2 danh sách máy chủ trong classes.dex), đổi đuôi phiên bản gửi lên thành 2.4.9 rồi ký lại APK (v1 + v2, khoá mới).
- Server nhận client phiên bản 2.4.9 dùng giao thức số lớn (long) như J2ME 2.4.x, để HP / KI / SĐ / vàng hiển thị đúng với giới hạn mới của Đợt 75.
- Đối chiếu bytecode client DragonBoy249 với server: mọi gói server gửi (chỉ số long, dòng chỉ số đồ short/int, id short, dữ liệu item 1 gói) và gói client gửi lên đều khớp chế độ long, không cần nhánh riêng. Thêm tools/client-db249 (đổi IP + ký lại APK, ghi chú giao thức).

## Đợt 77
- Đeo lưng hiện nhầm hình Ngọc Rồng Super: Cánh thiên thần sa ngã, bản đệ tử và Cánh hào khí trỏ vào cờ 154 - 156 (NR Super 5 - 7 sao). Tạo cờ riêng 176 - 178 đúng khung hình cánh. Sửa thêm Diều rồng băng (đang dùng hình Rìu Sơn Tinh, nay cờ 179) và Cá Koi zombie (đang dùng hình rồng hồng).
- Bóng mờ:
  - Hiện ở ô cuối danh sách trang bị (sau ô Sách tuyệt kỹ). Bấm "Mặc" từ hành trang là vào ô này (trước đây có thể vào nhầm ô pet), bấm vào ô để tháo ra.
  - Đứng im không có hiệu ứng; chạy / bay mới có vệt kéo theo sau lưng, đúng hướng di chuyển (hiệu ứng 1201 - 1212). Dừng khoảng 0,75 giây thì tắt.
- Ván bay / thú cưỡi mới làm lỗi hành trang:
  - Phiên bản dữ liệu item (vsItem) tự tăng khi bảng vật phẩm đổi (lưu ở bảng data_version), lần chạy này tăng lên 26 để mọi client tải lại danh sách item. Trước đây phải sửa tay, quên tăng thì client giữ danh sách cũ và gặp vật phẩm mới (Kỳ Lân, Đại bàng đất sét nổ...) là lỗi hành trang.
  - Vật phẩm đã xóa khỏi item_template (thú cưỡi 2124 - 2127) còn trong hành trang / rương không còn làm lỗi tải nhân vật, được bỏ đi.
  - Nạp item_template theo thứ tự mã (ORDER BY id).
- Panel: thêm trang "Đeo lưng & thú cưỡi" (xem khung hình, vật phẩm dùng cờ nào, cảnh báo trùng khung / thiếu icon, sửa khung, gán cờ khác; kiểm tra ảnh thú cưỡi; xem phiên bản item và nút buộc client tải lại) và trang "Lệnh admin trong game".
- Admin trong game: menu "a" thêm "Công cụ admin": thông báo toàn server, tặng vàng / ngọc / hồng ngọc, hồi đầy HP KI, dịch chuyển map, danh sách online, đổi EXP server, nạp lại dữ liệu (cửa hàng, chỉ số, thưởng boss, hộp quà, sự kiện), khóa / mở khóa tài khoản theo tên (cả khi offline). Mọi thao tác ghi log [ADMIN].
- Sửa nút "Ban" trong game: trước đây ghi ban = 0 (không khóa thật), nay ghi ban = 1.

## Đợt 78
- APK DragonBoy249 sửa IP mở lên bị văng: chuỗi danh sách server mới ("NRO:...") làm sai thứ tự bảng chuỗi trong classes.dex, Android từ chối nạp. Giữ tên "Vũ trụ 1" để chuỗi nằm đúng chỗ, chuỗi thứ hai đệm số 0 ở tham số cuối; công cụ mod_apk.py kiểm tra thứ tự trước khi ký.
- Bóng mờ kiểu "bóng bước chân" (theo video): khi chạy / bay, cứ vài nhịp một vật (hoa hồng, bông tuyết, ngôi sao, ngọc rồng, lồng đèn) rơi xuống ngay chân, nằm yên trên mặt đất phía sau rồi nhỏ dần và biến mất; đứng im không có.
- Đeo lưng chưa có hình sau lưng nay có hoạt ảnh: Lưỡi hái (cờ 300 không tồn tại), Lồng đèn Thỏ trắng, Lồng đèn Tết, Bao lì xì, Bư tập tạ, Cờ cớt hồng / VIP, Yêu quái núi lãng lãng, Kẹo Noel, Cờ sao may mắn, Bóng sinh nhật NRO, Đuôi rắn xanh / rực lửa (cờ 180 - 192); Bạch tuộc Xanh cáu kỉnh dùng hình bạch tuộc xanh, Cánh thiên thần 3 (Đệ) dùng hình bản thường.
- Đeo lưng hiệu ứng (cờ 201 - 254, vd Thanh Long Yển Nguyệt đao): không vẽ thêm icon vật phẩm tĩnh sau lưng nữa (client vẽ bằng hiệu ứng), bỏ cảnh báo trên panel.
- Panel → Đeo lưng & thú cưỡi: thêm tab "Đeo lưng hiệu ứng" (xem trước động các hiệu ứng 201 - 254 khi đứng / chạy / bay, vật phẩm nào đang dùng) và nút "Chọn hiệu ứng" cho từng đeo lưng: bấm hiệu ứng là gán cho vật phẩm.
- Panel: thư viện hình động dùng chung, bấm để chọn:
  - Đeo lưng & thú cưỡi: "Chọn hình" cho đeo lưng (mọi cờ, khung chạy vòng) và cho thú cưỡi (ảnh mount lớp sau / trước, đổi part + bảng thú cưỡi ngay).
  - Thẻ radar: chọn aura từng cấp Lv.1 / 2 / 3 (bảng radar_aura, ưu tiên hơn aura mặc định trong code; có "Dùng mặc định" / "Không có aura").
  - Danh hiệu: "Xem hiệu ứng động" (danh hiệu trên đầu nhân vật). Chỉ xem, vì mã hiệu ứng cũng là mã danh hiệu người chơi đang sở hữu.
  - Hiệu ứng & aura: tab "Aura động".

## Đợt 79
- Bóng mờ làm lại đúng kiểu "chạy để lại bóng rồi mờ dần": trước đây hiệu ứng gắn theo người nên vật chạy theo nhân vật và đứng lại là tắt hết. Giờ server gửi lệnh 113 (hiệu ứng cố định trên map) tại các điểm nhân vật đi qua, cách nhau 30 px: vật nằm yên tại chỗ khoảng 1,5 giây, nhỏ dần rồi biến mất; điểm đi qua lâu hơn dùng bản đã mờ hơn (hiệu ứng 1221 - 1244, bỏ 1201 - 1212). Đứng im không rơi thêm.
- Sửa lệnh hiệu ứng trên map (113): mã hiệu ứng ghi 1 byte trong khi client đọc 2 byte làm lệch tọa độ (hiệu ứng bẫy map, kỹ năng đặt hiệu ứng trên map).

## Đợt 80
- Vào game không thấy quái (client long: DragonBoy249, Unity 2.4.7, J2ME 2.4.x): gói dữ liệu quái (lệnh 11) gửi mã quái 1 byte trong khi client đọc 2 byte, client đọc lệch nên không dựng được quái. Nay gửi 2 byte cho client long, client cũ vẫn 1 byte.
- Tăng tốc tải khi vào game: file dữ liệu gửi cho client (icon, quái, hiệu ứng, map, ảnh theo tên) giữ trong bộ nhớ đệm (tối đa 192 MB, file sửa trên đĩa thì tự đọc lại), không đọc đĩa mỗi lần; đọc file đủ toàn bộ (trước đây đọc một lần có thể thiếu).
- Đứa bé (#78, nhiệm vụ phi thuyền) cõng sau lưng: cờ 193 dùng icon 736, hết cảnh báo trên panel.

## Đợt 81
- Bảng kỹ năng đệ tử: đệ thường (săn Broly) không mở được kỹ năng 5 nên bỏ hẳn ô "Không thể mở kỹ năng này", chỉ hiện 4 ô; đệ VIP (Uub, Kid Beer, Jiren) vẫn 5 ô ("Cần đạt sức mạnh 40tỷ để mở").
- Cửa hàng Đổi thưởng của Quy Lão trừ điểm sự kiện nhưng giá hiện kèm icon đồng vàng: chuyển sang kiểu cửa hàng có icon riêng, giá hiện kèm icon ngôi sao (điểm sự kiện).

## Đợt 82
- Rađa trống (client DragonBoy249 / J2ME / Unity): dòng chỉ số thẻ gửi (byte mã, short giá trị) trong khi client số lớn đọc (short, int), client đọc lệch cả gói nên không hiện thẻ nào. Nay ghi theo từng loại client (Message.writeItemOption).
- Bỏ bảng "Thẻ ... đạt Lv.1 / Bấm nút Sử dụng..." bắt bấm OK: chỉ hiện dòng chữ nhỏ "Thẻ ... đạt Lv.N".
- Bóng mờ: vật hiện ngang lưng nhân vật (không ở chân), mờ dần mượt (8 mức nhỏ dần + trong dần, bay lên rất nhẹ), điểm cách nhau 22 px, 8 bản theo thời gian đi qua nên vệt liền mạch không khựng (hiệu ứng 1221 - 1268).
- Menu game (Chức năng → Mini game / Lý Tiểu Nương): thêm "Điểm Quy Lão" (điểm sự kiện đổi thưởng + điểm đua top tặng ngọc rồng, nút "Đổi thưởng" mở thẳng cửa hàng Quy Lão). Menu game mở được ở mọi map kể cả khi không đặt NPC Lý Tiểu Nương trên map nào (trước đây không mở được).

## Đợt 83
- Bóng mờ về lại dưới chân (đáy vật chạm đất), mượt hơn: điểm cách nhau 16 px, 12 bản theo thời gian đi qua, 10 mức nhỏ dần + trong dần; bỏ chấm lẻ cạnh bông tuyết làm vật lơ lửng (hiệu ứng 1221 - 1292).
- Lên cấp thẻ radar: bỏ cả dòng thông báo.
- NroRepoDownloader 1.1: nhớ mã hash từng file trong .nro_manifest.json (file trên máy không đổi thì khỏi tính lại, kiểm tra gần như tức thì); kiểm tra song song; chỉ tải file mới / đã đổi, báo rõ bao nhiêu file mới và bao nhiêu file đã đổi; chỉ tải file zip cả nhánh khi máy gần như chưa có gì (trước đây thiếu hơn 3000 file là tải lại cả zip); tham số -force để tính lại mã từ đầu.

## Đợt 84
- Giảm RAM cho máy 2 GB: dữ liệu thật của server chỉ khoảng 40 - 60 MB, trước đây chạy không giới hạn nên Java giữ heap tới ~420 MB và không trả lại. run.bat / run_vps_yeu.* giờ đặt RAM tối đa (mặc định 448m / 384m, sửa RAM_MAX), G1 tự trả RAM thừa cho máy khi rảnh (G1PeriodicGCInterval), giới hạn metaspace / code cache / stack luồng. Đo thử: ~420 MB còn ~180 MB.
- Bộ nhớ đệm file (Đợt 80) giảm còn tối đa 48 MB (1/10 RAM Java), không giữ file > 512 KB.
- Menu admin (gõ "a"): dòng RAM tách "Máy" (RAM cả máy, gồm Windows / MySQL) và "Server" (dữ liệu game / phần Java đang giữ / tối đa) để biết server dùng bao nhiêu thật.
- Công cụ admin: thêm "Tặng điểm Quy Lão" (tên, điểm sự kiện đổi thưởng, điểm đua top ngọc rồng; nhập số âm để trừ, không cho âm).

## Đợt 85
- Dọn NPC không hợp bản đồ: bỏ Bò Mộng trong 3 nhà (Gôhan / Moori / Broly; vẫn còn ở Rừng Karin, Siêu Thị). Sự kiện không thả thêm Chi Chi, Santa, Bà Hạt Mít ra các làng nữa (đã có sẵn ở Đảo Kamê, Đảo Guru, Vách núi đen, các vách núi).
- Bật / tắt sự kiện tự ý: bật tay / tắt tay / tự động giờ lưu vào database (bảng season_event_force), khởi động lại server vẫn giữ. Menu admin trong game ("a" → Công cụ admin → "Bật / tắt sự kiện") hiện danh sách sự kiện, trạng thái và đổi theo mã; panel → Sự kiện vẫn dùng được.
- Sự kiện mới:
  - Ngũ Hành Sơn (12, tự chạy 1/5 - 26/5): quái rơi Quả Hồng Đào (150/ngày); boss mới Tề Thiên Đại Thánh (3 con, đồi núi 3 hành tinh, nghỉ 15 phút) rơi Vòng Kim Cô + Quả Hồng Đào Chín; Chi Chi nhóm "Tây Du" đổi Như Ý Kim Cô Bổng, Cửu Xỉ Đinh Ba, Nguyệt Nha Sản, Cửu Hoàn Thiên Tích Trượng, CT Urôn Trư Bát Giới, Bồ cào 9 răng, Quạt ba tiêu (có tỉ lệ ra vĩnh viễn); đua top Ngũ Hành Sơn (top 1 CT Trư Bát Giới + Kim Cô Bổng vĩnh viễn).
  - Chỉ bật tay: Mưa vàng (13, quái rơi thỏi vàng, 20/ngày), Lễ hội Ngọc Rồng (14, ngọc rồng 4 - 7 sao, 3 viên mỗi loại / ngày), Đá Ngũ Sắc (15, đá nâng cấp 5 loại, 30 viên mỗi loại / ngày).
- Panel → Boss: hiện cả boss sự kiện (Halloween, Trung Thu, Ngũ Hành Sơn, Giáng sinh, Hùng Vương, Tết).

## Đợt 86
- Ngũ Hành Sơn làm lại theo bản chính thức (video), bỏ boss Tề Thiên Đại Thánh:
  - Trong sự kiện, Đường Tăng xuất hiện ở Làng Aru / Làng Mori / Làng Kakarot: "Đồng ý" đưa vào bản đồ Ngũ Hành Sơn (122 - 124, có sẵn Đường Tăng và Ngộ Không bị phong ấn), trong Ngũ Hành Sơn bấm "Về làng" để về nhà.
  - Quái ở Ngũ Hành Sơn rơi Quả Hồng Đào (300/ngày); Chi Chi bán Quả Hồng Đào (1 triệu vàng) và Quả Hồng Đào Chín (5 ngọc).
  - Ngộ Không: "Tặng quả Hồng Đào" (1 điểm công đức / quả), "Tặng quả Hồng Đào Chín" (5 điểm / quả). Điểm công đức lưu ở player_extra.cong_duc.
  - Đường Tăng "Nhận thưởng": 500 cộng 7 ngày cải trang đang dùng, 600 cộng 9 ngày cải trang đệ tử, 700 CT Bát Giới (Đệ), 1.000 CT Tôn Ngộ Không (Đệ), 1.300 CT Sa Tăng (Đệ), 800 Yêu quái núi Lãng Lãng (30 ngày).
  - 3 cải trang mới chỉ dành cho đệ tử (2128 - 2130), người chơi không mặc được.
- Chỉ số tạm (sửa trên panel khi có chỉ số thật): CT Bát Giới / Ngộ Không / Sa Tăng (Đệ), Như Ý Kim Cô Bổng, Cửu Xỉ Đinh Ba, Nguyệt Nha Sản, Cửu Hoàn Thiên Tích Trượng, Quạt ba tiêu, Bồ cào 9 răng.

## Đợt 87
- Cổng game chuyển sang Netty (thư viện Netty 4.1.114 trong lib/, mã SHA-1 khớp Maven Central): vài luồng mạng phục vụ mọi kết nối thay vì 2 luồng / người chơi (kiểu cũ dễ sập khi bị mở hàng nghìn kết nối rác). Gói tin mỗi phiên vẫn xử lý tuần tự như cũ. Muốn quay về kiểu cũ: `server.netty=false` trong Config.properties.
- Chống DDoS tầng ứng dụng (AntiDdos): giới hạn tổng kết nối (3000), kết nối / IP, kết nối mới / IP trong 10 giây (20), phải bắt tay trong 10 giây, gói tin tối đa 16 KB, tối đa 120 gói / giây mỗi kết nối, chưa vào game mà im lặng 120 giây thì ngắt, máy khách không nhận dữ liệu (dồn > 32 MB) thì ngắt. Vi phạm bị chặn IP 2 phút, tái phạm gấp đôi (tối đa 1 ngày). Chỉnh bằng server.ddos.maxconn / maxperip / newconn10s / msgpersec. Menu admin hiện số kết nối, IP đang bị chặn.
  - Tấn công băng thông lớn (UDP / SYN flood vài Gbps) phải chặn ở nhà mạng / VPS (tường lửa nhà cung cấp, Cloudflare Spectrum...), phần mềm trên máy chủ không chặn được lưu lượng chưa tới máy.
- Chống kéo res (ResGuard): tool kéo res vẫn nhận đủ file nhưng mọi ảnh là ảnh cùng kích thước ghi "Good, but not today." (icon, ảnh theo tên, nền vật phẩm, hiệu ứng, ảnh trong gói res; dữ liệu quái không gửi). Bị coi là tool khi xin ảnh lúc chưa đăng nhập tài khoản, xin quá 900 ảnh trong 2 phút, quét icon theo dãy số liên tiếp (150 mã), hoặc 1 IP tải gói res của từ 2 cỡ màn hình trở lên trong 6 giờ.

## Đợt 88
- Ngũ Hành Sơn theo luật mới, dùng vật phẩm có sẵn trong game (không tạo item mới):
  - Tặng Quả Hồng Đào (541) cho Ngộ Không: mỗi quả nhận ngẫu nhiên 1 chữ Giải / Khai / Phong / Ấn (537 - 540), Quả Hồng Đào Chín (542): 3 chữ. Tối đa 99 quả mỗi lần bấm.
  - Đủ 10 chữ mỗi loại, gặp Đường Tăng bấm "Đổi CT Tôn Ngộ Không" nhận Cải trang Tôn Ngộ Không theo hành tinh (544 Trái Đất / 545 Namếc / 546 Xayda).
  - Chi Chi bán Vòng Kim Cô (543) giá 10 ngọc. Mặc CT Tôn Ngộ Không + có Vòng Kim Cô trong hành trang, đánh quái ở Ngũ Hành Sơn (122 - 124) được +1 công đức / con (tối đa 500 / ngày).
  - Nhiệm vụ hộ tống ở Đường Tăng: đi qua 3 vùng Ngũ Hành Sơn rồi quay lại gặp Ngộ Không trong 15 phút, thưởng 20 - 40 công đức (tối đa 5 lần / ngày).
  - Cải trang sự kiện (CT Tôn Ngộ Không 544 - 546, CT đệ 547 / 548 / Sa Tăng 2130): chỉ số ngẫu nhiên sức đánh 15 - 20%, HP / KI 50 - 100%, tiềm năng 30 - 100%, vàng 70 - 130%, tốc độ chạy 50 - 120%, 20% có "Không ảnh hưởng bởi cái lạnh", hạn 5 - 15 ngày, không bao giờ vĩnh viễn. Không mặc được trong Ngọc Rồng Sao Đen (đang mặc thì tự cất vào hành trang / rương đồ khi vào, đầy cả hai thì đưa về nhà).
- Chống kéo res siết chặt hơn: bỏ giới hạn "chỉ giữ tới hết phiên" - tài khoản bị đánh dấu lưu vào bảng res_guard_flag, nhận ảnh giả 7 ngày kể cả đăng nhập lại (gỡ: `delete from res_guard_flag where account_id = ...`). Thêm luật: xin quá 200 ảnh khi chưa vào nhân vật, xin quá 60 mã ảnh không tồn tại (dò mã), tài khoản quá 6.000 ảnh / ngày, IP quá 12.000 ảnh / ngày, tải gói res quá 8 lần / ngày / IP. Tài khoản admin không bị giới hạn.
- Chống DDoS chặn nhầm chính IP người chơi / admin ("chặn ... 2 phút: không bắt tay trong 10 giây"): kết nối chậm gửi gói bắt tay (mạng điện thoại yếu, client mở sẵn kết nối, nhiều máy chung IP) giờ chỉ bị ngắt kết nối đó; chỉ chặn IP khi 1 IP có từ 10 kết nối như vậy trong 1 phút (server.ddos.strikes). IP đã đăng nhập thành công được tin cậy 24 giờ: không bao giờ bị chặn cả IP (vi phạm chỉ ngắt đúng kết nối đó), giới hạn kết nối / IP nới gấp 4; đăng nhập thành công cũng gỡ chặn IP đó. Thêm server.ddos.whitelist trong Config.properties (IP không bao giờ bị chặn, cách nhau dấu phẩy).

## Đợt 89
- Sự kiện mới "Quán phở Anh 2" (id 16, mặc định tắt, bật trên web panel mục Sự kiện): đánh quái nhặt Bột gạo (120 / ngày), Thịt tươi (80 / ngày), Cục xương (50 / ngày), mang tới Chi Chi (nút "Quán phở Anh 2") nấu Tô phở tái nhiều hành (10 bột + 5 thịt + 3 xương + 5 triệu vàng), Tô phở tái bò viên (15 + 10 + 5 + 10 triệu vàng), Tô phở nạm 2 quẩy (20 + 15 + 10 + 10 ngọc). Tô phở là vật phẩm có sẵn: ăn vào đổi chiêu 2 / 3 / 4 cho đệ tử. Mỗi tô được 1 / 2 / 3 điểm đua top Quán phở; quà top: top 1 20 tô nạm 2 quẩy, top 2 - 3 10 tô nạm, top 4 - 10 10 tô bò viên, top 11 - 100 5 tô tái. Công thức, tỉ lệ rơi, quà top sửa được trên panel.
- Sao Pha Lê 10 thành sự kiện riêng (id 18) thay cho ngày cố định trong code: tự chạy 31/10 - 20/11 hằng năm, admin bật / tắt / tự động trên web panel.
- Đua top tặng bộ ngọc rồng 1 - 7 sao cho Quy Lão tách khỏi Trung Thu thành sự kiện riêng (id 17): tự chạy cùng lịch Trung Thu, admin bật / tắt / tự động trên web panel; tắt thì Quy Lão ẩn nút "Đua top ngọc rồng" (điểm đã có vẫn giữ, chốt top bằng lệnh chottopql như cũ).
- Tab cửa hàng sự kiện hiện nguyên chữ "Shop<>sự kiện" / "Đổi<>thưởng": sửa thành xuống dòng đúng.
- Ngũ Hành Sơn đủ 3 vùng: map 122 / 124 / 123 đặt tên Ngũ Hành Sơn 1 / 2 / 3 (cả tên cổng dịch chuyển). Đường Tăng ở làng có 3 nút chọn thẳng vùng 1 / 2 / 3, trong map vẫn đi bộ qua cổng 1 <-> 2 <-> 3. Nhiệm vụ hộ tống: đi qua đủ Ngũ Hành Sơn 2 và 3 (thứ tự nào cũng được) rồi về gặp Ngộ Không ở Ngũ Hành Sơn 1.
- Yêu quái núi Lãng Lãng (1988): chỉ số mới sức đánh 21%, HP 17%, KI 17%, tiềm năng sức mạnh ngẫu nhiên 50 - 120% (áp cho món nhận mới: đổi công đức ở Đường Tăng, Sơn Tinh / Thủy Tinh rơi, admin tặng).

## Đợt 90
- **Chống DDoS chặn nhầm chính mình (lần 2):** mọi lỗi của 1 kết nối (gói tin lớn, gửi dồn nhiều gói khi mạng lag, chậm bắt tay, sai giao thức) giờ chỉ ngắt kết nối đó và tính 1 "lỗi". Chỉ chặn cả IP khi 1 IP có từ 30 lỗi trong 1 phút (server.ddos.strikes) hoặc mở quá 60 kết nối / 10 giây. Nới giới hạn: 20 kết nối / IP, 300 gói / giây, gói tối đa 64 KB. Mạng LAN (192.168.x, 10.x, 172.16 - 31.x) coi như máy chủ, không bao giờ bị chặn. IP đã đăng nhập vẫn được tin cậy 24 giờ. Thêm `server.ddos.ban=false` để tắt hẳn chặn IP, nút "🛡️ Gỡ chặn IP" trên web panel, lịch sử chặn ghi vào logs/ddos.log (gửi file này nếu còn bị chặn nhầm).
- **Túi mù Halloween / hộp quà / capsule mở ra quà bị tách lẻ:** các vật phẩm trong bảng item_box_reward (Túi mù Halloween, Kẹo mù, Hòm / Thiệp Halloween, Hộp quà 20/10, 20/11, Lì xì, Hộp hào khí...) giờ gộp chung 1 ô và không có hạn sử dụng (hạn khác nhau mỗi ngày làm chúng không gộp được). Hộp cũ đang có hạn trong hành trang tự gộp vào khi nhận thêm hộp cùng loại.
- **Vật phẩm bấm "Sử dụng" không có phản hồi** (dò tự động 260 vật phẩm bán ở shop / phần thưởng / hộp quà):
  - Gói 30 đậu thần cấp 1 - 9: nhận 30 hạt đậu thần cấp tương ứng.
  - Capsule hồng, Capsule Trung Thu: mở ra quà (vàng, ngọc, hồng ngọc, thỏi vàng, đá bảo vệ, đá nâng cấp / Hộp bánh Trung Thu 2026, Bóng mờ trung thu...). Nội dung sửa được trên panel (bảng item_box_reward).
  - Bánh tét, Bánh chưng, kẹo / súp / hamburger Halloween, Mì thanh long, Cơm gà quay, Gà tây nướng, Bánh sinh nhật NRO, Chai Cuke: ăn được (+10% sức đánh như thức ăn).
  - Pet cậu vàng, Hộp quà Goku Day VIP 2025 bị gán loại 75 (không dùng được): về loại 27 như DataNRO.
  - Vật phẩm chỉ là nguyên liệu (Quả Hồng Đào, Cápsule Vỡ, Bánh trung thu hạt sen...): bấm dùng báo "là nguyên liệu sự kiện, mang tới <NPC> để đổi <quà>" hoặc công dụng của nó, thay vì im lặng.
- **Tên / icon chuẩn theo DataNRO:** sửa icon Đổi đệ tử, Phá cỗ trung thu; "Capsule vỡ" (2111) đổi tên / icon thành Cápsule Vỡ chuẩn; 749 mô tả "VPSK" / bị cắt ngắn thay bằng mô tả DataNRO (file sql/dot89_datanro.sql, chạy 1 lần).
- **Shop sự kiện mất dòng hạn sử dụng / vĩnh viễn:** mỗi món hiện "Hạn sử dụng X ngày", "Hạn sử dụng hoặc vĩnh viễn" (có tỉ lệ vĩnh viễn) hoặc "Vĩnh viễn".
- **Danh hiệu:** Đón Xuân có chỉ số như bản chính thức (+50% TN SM, +25% TN SM đệ tử khi sư phụ mặc, +5% vàng rơi, SĐ 11%, chí mạng 5%, 30 ngày); vật phẩm Phá cỗ trung thu / Phá cỗ trung thu VIP / Đón Xuân hiện đủ chỉ số.
- **CT Android 21 Evil gây lỗi hành trang:** thiếu ảnh đại diện (head_avatar). Bổ sung ảnh đại diện cho 13 cải trang còn thiếu (Android 21 Evil / Thân Thiện, Bunma Rider, Kochiro, Musashi, Ác Ma Pico, Janemba, Trâu Nâu, Bản Cô Nương, Broly, Đặng Văn Chiến, Kirin chú cuội).
- **Shop đổi điểm Quy Lão:** giá hiện kèm icon đồng xu NRO như bản chính thức (trước là ngôi sao).
- **Đua top kết thúc:** Chi Chi vẫn hiện mục đua top đó trong 7 ngày, nút "Xem điểm" đổi thành "Nhận quà đua top"; Top 100 vẫn xem được (lấy bảng xếp hạng lúc chốt).

## Đợt 91
- **Dùng Mảnh Khỉ Oozaru làm rađa nhảy lung tung / trống:** mỗi mảnh dùng server gửi lại cả bảng rađa (vì Thẻ Oozaru đổi hình theo cấp), client dựng lại rađa nên nhảy về thẻ đầu (Thẻ Quỷ đất) hoặc hiện trống. Giờ chỉ gửi lại cả bảng khi thẻ lên cấp (đúng lúc đổi hình), các mảnh khác chỉ cập nhật số mảnh của thẻ.
- **Rađa dùng cùng lúc tối đa 5 thẻ** (trước chỉ 1, phải tắt thẻ này mới bật được thẻ khác). Chỉ số "Sử dụng Lv.x" của từng thẻ đang bật đều cộng; aura lấy theo thẻ mạnh nhất.
- **Nhận quà đua top như bản chính thức (video):** Chi Chi → chọn đua top → 3 nút "Top 100", "Xem điểm", "Nhận thưởng Sự kiện Đua Top" (cả khi đang đua lẫn sau khi kết thúc, giữ 7 ngày). "Nhận thưởng" mở bảng "N Vật phẩm": mỗi món ghi "Cách nhận: Đua Top nhận" và "TOP #hạng tên đua top (xSL)", có nút Nhận / Xoá / Nhận tất (vàng, ngọc, hồng ngọc cũng là 1 món). Không còn tự phát quà khi đăng nhập / khi chốt top; đăng nhập chỉ nhắc ra Chi Chi nhận. "Xem điểm" sau khi chốt hiện hạng / điểm lúc chốt.

## Đợt 92
- **Bấm "Sử dụng" mảnh thẻ (Mảnh Khỉ Oozaru...) tự bật bảng rađa:** server không gửi lại cả bảng rađa khi dùng mảnh nữa (client nhận gói đó là mở rađa và nhảy về thẻ đầu); hình thẻ theo cấp cập nhật khi tự mở rađa.
- **Hộp / capsule / rương bấm dùng không ra gì:** dò tự động ~150 hộp, thêm quà cho 76 hộp (file sql/dot92_boxes.sql, chỉ thêm cho hộp chưa có quà, sửa tiếp trên panel): Capsule Bạc / Vàng / 1 - 3 sao / quà tặng / Squid Game / SEA games / World Cup, Rương Bạc / Vàng / Siêu Hạng / Rồng Thần (mảnh Rồng thần Namếc + ngọc rồng) / Mảnh Thiên Sứ / cải trang Hải Tặc / sao pha lê, các Hộp quà Tết / 20-10 / FA / Black Friday / Cađíc / tháng 9 / thường / cao cấp / Trung Thu, Hộp mù Pet, Hộp Pet Tết, Hộp Nón Tết, Capsule cải trang VIP (vĩnh viễn) / HSD, Capsule Trang Sức VIP, Hộp nâng cấp đồ thiên sứ, Hộp hoa hồng, Túi ngọc rồng băng, Hộp quà thiếu nhi, Rương mùa hè, Hộp quà tháng 9...
  - Rương áo / quần / găng / giày / rađa: 1 món đồ thường ngẫu nhiên đúng hành tinh; Rương bí kíp tuyệt kỹ: 1 sách kỹ năng đúng hành tinh; Hộp trang bị Thần: 1 món đồ Thần Linh đúng hành tinh.
  - Quà theo hành tinh (Nón Chuột / Trâu may mắn, Nón mèo...) tự đổi sang bản đúng hành tinh của người mở.
- **Tên item không dấu, thiếu dòng trên client 2.4.x (J2ME):** client này nhận cả danh sách ~2100 item trong 1 gói 64 KB nên không đủ chỗ cho dấu của mọi tên. Giờ ưu tiên giữ dấu cho quà đổi sự kiện / đua top (toàn bộ), item bán trong shop (611 / 655), rồi quà trong hộp; item cũ không còn dùng mới bị bỏ dấu ("Cai trang" rút thành "CT"). Phiên bản dữ liệu item tăng để client tải lại. Thêm dòng "Dành cho đệ tử 2" cho Cánh thiên thần sa ngã (Đệ), Cánh thiên thần 3 (Đệ), Bư tập tạ (client 2.4.x không nhận mô tả item).
  - Capsule 1 món kích hoạt: 1 món set kích hoạt ngẫu nhiên đúng hành tinh. Hộp quà Set kích hoạt / 3 sao / 5 sao: đủ 5 món (áo, quần, găng, giày, rađa) cùng 1 set kích hoạt, bản 3 / 5 sao có sẵn 3 / 5 lỗ sao pha lê.
- Hộp Ngọc Rồng Bí đổi icon sang 16895 (hộp bí ẩn), trước dùng trùng icon Hộp ngọc rồng xanh. Rương Rồng Thần / VIP có thêm pet hình rồng (Pet rồng Pikachu thanh long, Pet rồng con thần tài, Pet Rồng xương, Pet Rồng Xanh, Rồng nhí 2 - 7 sao) và ván bay / thú cưỡi hình rồng (Rồng PiLong, Cá chép rồng, Rồng Thiêng, Ván bay Rồng Thiêng VIP, rồng Băng, rồng Đen, rồng Vô Cực, Ván Rồng 7 sao hiếm), hạn 7 / 15 / 30 ngày, tỉ lệ vĩnh viễn 2% (VIP 10%).
- **Đổi điểm Quy Lão như bản chính thức:** trang bị / pet / ván bay / cải trang đổi bằng điểm sự kiện trước luôn vĩnh viễn (shop hiện "Vĩnh viễn"), giờ là "Hạn sử dụng hoặc vĩnh viễn" thật sự: hạn 30 ngày, 10% ra vĩnh viễn (chỉnh từng món trên panel: reward_days / perm_rate). Shop sự kiện và bảng quà đua top không còn hiện dòng "Vĩnh viễn" tự chế; món vĩnh viễn không hiện dòng hạn như bản chính thức.
- **Thẻ rađa theo bản chính thức:** Đội trưởng Vàng: Mở khóa SĐ +1%, Sử dụng Lv.1 SĐ +2%, Lv.2 SĐ +3%. Rồng thần Namếc: Mở khóa HP +1%, Lv.1 chí mạng +1%, Lv.2 chí mạng +2%. Oozaru: Mở khóa chí mạng +1%, Lv.1 chí mạng +4%, Lv.2 SĐ +5%, Lv.3 HP +10%, KI +5%, giảm 3% sát thương. Dòng "Sử dụng Lv.k" giờ có tác dụng đúng khi bật thẻ ở Lv.k (trước lệch 1 cấp) và chỉ số luôn lấy theo bảng rađa hiện tại (sửa trên panel là có tác dụng).
- **Bật nhiều thẻ rađa:** thẻ bật sau cùng được ưu tiên hiện aura (vd đang bật Đội trưởng Vàng, bật thêm Rồng thần Namếc thì hiện Rồng thần); vừa vào game thì ưu tiên thẻ số lớn hơn.
- **Bộ sao may mắn như bản chính thức:** Pet Mèo thần tài, Ván bay sao băng, Bóng mờ sao may mắn dùng dòng "+#% vàng rơi" (option 268, tác dụng như vàng từ quái); Ván bay sao băng bỏ dòng "Dùng để bay và phục hồi KI".

## Đợt 93
- **Web panel → Người chơi → mở 1 nhân vật:** thêm
  - sửa chỉ số gốc: sức mạnh, tiềm năng, HP / KI / sức đánh / giáp / chí mạng gốc, thể lực, giới hạn sức mạnh;
  - sửa nhiệm vụ: chọn nhiệm vụ, bước, số đã làm (danh sách lấy từ bảng nhiệm vụ của server);
  - đổi tên nhân vật (kiểm tra trùng tên);
  - nút ✕ xoá từng vật phẩm ở Đang mặc / Hành trang / Rương;
  - xoá nhân vật (phải gõ XOA để xác nhận; đang online thì kick, đợi lưu xong mới xoá; tài khoản giữ lại).
  Người chơi đang online được sửa trực tiếp và lưu ngay, offline thì sửa trong database.
- **Bot cho đỡ trống server (web panel → Bot):** luôn giữ đủ số bot đặt trên panel (mặc định 15, 0 = tắt), vào / ra dần. Mỗi bot có tên giống người chơi, hành tinh và cải trang ngẫu nhiên, lưu tiến độ (bảng bot_profile) nên sức mạnh tăng dần qua các lần khởi động. Bot luyện tập đánh quái map hợp sức mạnh, nhặt đồ, săn boss, săn đệ (ưu tiên Broly), cộng điểm đua top sự kiện: hiện trong Top 100 nhưng chốt top bỏ qua bot nên không chiếm quà của người chơi thật. Bỏ các câu chat chửi bậy của bot cũ, bot chat câu bình thường thỉnh thoảng.
- **Log lỗi bot:** lỗi của từng bot ghi vào logs/bot.log (tên bot, đang làm gì, map nào, chỗ lỗi), xem nhanh 50 lỗi gần nhất trên trang Bot của panel; lỗi 1 bot không làm dừng bot khác, bot lỗi liên tục tự thoát rồi vào lại. Sửa lỗi khi bot nhặt vàng (không có kết nối) và đánh quái ở map thiếu dữ liệu ô gạch.
- **Boss không dùng chiêu khống chế:** bỏ thôi miên, dịch chuyển tức thời, trói, socola, thái dương hạ san, ma phong ba khỏi mọi boss; bỏ hoá đá / biến kẹo của Mabư, Drabura, Cadic, Goku (sự kiện Mabư).
- **SSJ Broly HP 15 tỷ.** Sửa lỗi HP tối đa bị ép về số int (> 2,1 tỷ bị cắt sai), gửi client vẫn tự xử lý số lớn.
- **Dr. Myuu và boss Baby:** Dr. Myuu xuất hiện ngẫu nhiên ở 1 bản đồ thường (cứ 30 - 45 phút đổi chỗ, báo kênh thế giới), không xuất hiện ở Tương lai, hành tinh Cold, hành tinh ngục tù, hành tinh thực vật, nhà, phó bản và các map đặc biệt. Nói chuyện với Dr. Myuu → Đồng ý để vào Phòng thí nghiệm Myuu (map 166); boss Baby giờ xuất hiện trong phòng thí nghiệm (trước ở Làng Kakarot). Trong phòng thí nghiệm có Dr. Myuu để về nhà (map không có lối ra).
- **Bot thêm hoạt động:** vào Ngọc Rồng Sao Đen lúc mở (cắm cờ ngẫu nhiên, đánh quái, người chơi đánh được bot; bot không nhặt ngọc rồng sao đen / đồ trong NRSD), đi doanh trại / Đại hội võ thuật (rời map 5 - 10 phút, về thì mạnh hơn và chat kể lại; 2 nơi này là phó bản / giải đấu riêng nên bot không vào thật), PK với bot khác ~3 phút (bật PK, chỉ đánh bot, không tấn công người chơi thật), đập đồ / úp vàng (chat + tăng sức mạnh), đem đồ thường lên ký gửi (sao pha lê, đá nâng cấp, mảnh Cápsule vỡ, thỏi vàng; tối đa 20 món của bot cùng lúc, món đã bán tự gỡ), đua top kịch liệt: mỗi bot có sức đua 50% - 140% điểm của người chơi thật đứng đầu và cộng dần về mức đó (vài bot vượt cả người đứng đầu) nhưng vẫn không nhận quà.
- Sửa boss Ăn Trộm (và boss nào chỉ có chiêu khống chế) bị lỗi khi bị đánh sau khi bỏ chiêu khống chế: cho 1 chiêu đánh thường (lỗi do log bot phát hiện).

## Đợt 94
- Drabura và Mabư được giữ lại chiêu khống chế (hoá đá, biến kẹo); các boss khác vẫn bỏ.
- **Mô tả vật phẩm trên client J2ME (Dragonboy247 v7.0):** ngoài gói danh sách item rút gọn (1 gói 64 KB: không mô tả, một phần tên không dấu), server gửi thêm gói lớn lệnh 12 (độ dài 3 byte, như bản chính thức) có đủ tên có dấu và mô tả. Client hiểu lệnh 12 sẽ thay danh sách bằng bản đầy đủ; client không hiểu thì bỏ qua, giữ bản rút gọn.
- **Dòng "5 món ..." của bộ đồ:** chữ màu đen khi chưa mặc đủ, xanh khi mặc đủ (đếm áo, quần, găng, giày, rađa có cùng dòng; dòng "[2] / [4] / [5]" theo đúng số món ghi trong ngoặc). Cập nhật mỗi khi gửi trang bị / hành trang / rương.
- **Chỉ số cờ:** Pháo hoa NRO thêm chí mạng 10 - 20%, sức đánh chí mạng 10 - 20%; Đeo lưng bông tuyết thêm giảm sát thương 10 - 15%, né đòn 20 - 25%, không ảnh hưởng bởi cái lạnh (ngẫu nhiên trong khoảng khi nhận món mới).
- **Mở hộp quà / capsule:** bỏ dòng thông báo "Mở ... nhận được ...", chỉ còn hiệu ứng mở hộp hiện món nhận được (cả Capsule 1 món kích hoạt, Hộp quà Set kích hoạt).
- **Bóng mờ:** điểm dày hơn (12 px), luôn có 1 điểm ngay dưới chân nên vệt bám sát người chơi, bớt khựng khi chạy nhanh.

## Đợt 95
- **Bot là tài khoản thật do admin chọn:** panel → Người chơi → nút "🤖 Đặt làm bot" (hoặc trang Bot → thêm theo tên / id). Khi chủ tài khoản không online, server nạp chính nhân vật đó vào game và cho tự chơi: đánh quái theo sức mạnh, săn boss, săn đệ, vào NRSD, PK. Bot dùng đúng đồ đang mặc, hành trang, vàng ngọc của nhân vật và lưu lại vào nhân vật như người chơi thật.
- Bot tài khoản không được cộng sức mạnh / điểm đua top / đồ ký gửi "ảo" như bot giả lập: chỉ có những gì tự đánh, tự nhặt được. Nhận thưởng boss, đua top như người chơi thật.
- **NRSD:** bot tài khoản nhặt ngọc rồng sao đen và thủ ngọc (đánh người chơi khác cờ ở gần), không phân biệt người thật hay bot.
- Chủ tài khoản đăng nhập: bot thoát ngay, lưu nhân vật rồi mới nạp cho chủ. Chủ thoát game: sau khoảng 30 giây bot vào lại.
- Panel → Bot: bảng tài khoản bot (trạng thái, đang làm gì, map), nút "Xem hành trang" (đồ đang mặc, hành trang, rương: lấy dữ liệu trực tiếp khi bot đang chơi), "Bỏ bot".
- Bot giả lập mặc định 0 con (chỉ bật khi admin đặt số lượng trên panel).
- Sửa chọn map luyện tập theo sức mạnh của bot: trước đây ngưỡng sức mạnh bị trùng nên bot từ 100 triệu trở lên phần lớn đứng ở Nhà Gôhan (không có quái); nay < 2 tỷ đi map Fide / Nappa, < 40 tỷ đi map tương lai, trên 40 tỷ đi map Cold.
- Bot tài khoản chết 3 lần trong 5 phút (map Cold / tương lai bị trừ sức mạnh khi chết như người thật) thì tự chuyển xuống vùng map dễ hơn.

## Đợt 96
- **Tài khoản bot tách biệt với người chơi:** panel → Bot → "Tạo bot" (tên, hành tinh, sức mạnh / nhiệm vụ ban đầu, số lượng). Mỗi bot là 1 tài khoản + nhân vật riêng với mật khẩu ngẫu nhiên; đăng nhập bằng tài khoản bot bị từ chối. Nút Bật / Tắt / Xoá (bot đang làm bang chủ thì không xoá được, bot thành viên bị xoá sẽ rời bang). Không còn kiểu đặt nhân vật của người chơi làm bot.
- **Bang hội:** bot đủ nhiệm vụ vào bang thì xin vào bang có bang chủ đang online (bang của bot được duyệt ngay, bang của người chơi do bang chủ duyệt), không có thì tự lập bang (trả vàng mua cờ như người chơi). Bot làm bang chủ tự duyệt đơn xin vào, kể cả của người chơi thật. Người chơi mời bot vào bang thì bot đồng ý.
- **Doanh trại:** bang đủ 5 người, thành viên vào bang trên 1 ngày: bang chủ bot gom bot cùng bang tới Lính canh (Rừng Bamboo) rồi mở doanh trại; trong trại bot đánh hết quái, đánh boss, sang map tiếp theo, nhặt ngọc. Bang (kể cả người chơi thật) đang đánh doanh trại thì bot cùng bang vào đánh cùng.
- **Buôn bán:** bot đem đồ thật trong hành trang lên ký gửi (phí 1 thỏi vàng như người chơi), đồ bán được tự nhận tiền, săn đồ ký gửi rẻ. Giá theo chợ nhưng giới hạn 0.5 - 2 lần giá thường (người chơi không ép giá được).
- **Giao dịch với bot:** người chơi mời giao dịch → bot nhận (đang bận thì từ chối). Đưa đồ: bot trả vàng 80% giá; chỉ đưa vàng: bot bán lại đồ vừa giá. Bot không đủ vàng thì từ chối, không vét sạch túi. Trong lúc giao dịch bot đứng yên (không nhặt đồ làm huỷ giao dịch); bang hội / ký gửi của bot không chạy khi bot đang giao dịch (tránh nhân bản đồ). Đã kiểm tra bằng client thật: 10 lần giao dịch, vàng và đồ 2 bên khớp.
- Bot chọn map luyện tập theo chỉ số thật (HP, sức đánh), cộng tiềm năng nhanh khi còn nhiều, chỉ dùng chiêu đã học.
- Panel → dịch chuyển: thêm "tới chỗ người chơi / bot" (cùng khu).

## Đợt 97
- **Thỏi vàng:** bán cho cửa hàng 37 triệu vàng / thỏi (một giá cho bán từng thỏi, bán nhiều và cho bot; trước đây bán từng thỏi qua gói bán trực tiếp vẫn được 500 triệu). **Thỏi vàng NRJ** (id 2032) bán được 150 triệu, bán nhiều một lúc như thỏi vàng thường. Giá lấy từ item_template.gold (Đợt 96 migration).
- **Giá đập đồ theo mức vàng mới** (100 triệu cũ ≈ 1 thỏi vàng = 37 triệu, làm tròn lên): nâng cấp +6: 37tr, +7/+8: 111tr, +9: 148tr, +10: 185tr, +11: 259tr, +12: 333tr; pha lê hoá 3-5 sao: 37tr, 6-7: 74tr, 8: 111tr, 9: 185tr. Mức thấp giữ nguyên.
- **Mô tả vật phẩm trên client J2ME:** mỗi mô tả item thành 1 dòng chỉ số riêng, hiện ở cuối các dòng chỉ số của món (hành trang, trang bị, rương, cửa hàng, ký gửi, giao dịch). Chỉ gửi cho client J2ME (loại 1); client Unity / APK đã hiện mô tả sẵn nên không bị lặp. Tự kiểm tra: tools/tests/run.sh, tools/tests/t_desc_j2me.py.
- **Bot tài khoản tự làm nhiệm vụ chính từ đầu** (mũi tên chỉ dẫn, về nhà, mở rương, hái đậu, mộc nhân, đùi gà, em bé, quái mẹ, ngọc 7 sao, Quy Lão, bang hội, heo rừng...): bước gặp NPC thì tới nói chuyện; bước đánh quái thì tới map có con quái đó yếu nhất và ưu tiên đánh đúng con đó; bước tới map / nhặt vật phẩm thì tới đó nhặt. Bot mới (dưới nhiệm vụ 10) chỉ luyện tập + làm nhiệm vụ. Kẹt 1 bước quá 30 phút thì chơi bình thường 30 phút rồi thử lại. Đã chạy thử: bot mới làm liền từ nhiệm vụ 0 tới nhiệm vụ 15.
- **Úp SKH:** bot rảnh đi map úp set kích hoạt của hành tinh mình (1/2/3, 8/9/11, 15/16/17), nhặt đồ kích hoạt rơi ra và tự mặc nếu ô đó trống hoặc đang mặc đồ thường.
- **Nâng chiêu:** bot đủ sức mạnh và ngọc thì học chiêu mới / cấp tiếp theo, trả ngọc bằng giá sách (mỗi lượt 1 cấp).
- Bot tài khoản lên sức mạnh / tiềm năng như người chơi (có giới hạn sức mạnh, ghi nhiệm vụ, cộng điểm bang); trước đây cộng thẳng, không bao giờ xong nhiệm vụ "Đạt ... sức mạnh".
- **Sửa lỗi bot làm mất vật phẩm nhiệm vụ của người chơi:** bot nhặt em bé (Vách núi) / vật phẩm nhà rồi tự xoá khỏi map, người chơi vào khu đó không làm được nhiệm vụ; bot còn nhặt cả vật phẩm nhiệm vụ đang ẩn. Giờ bot chỉ thấy và nhặt như người chơi.
- Bot chỉ làm nhiệm vụ ở map người chơi đi tới được bằng cổng (bỏ Tây Karin và các map không có cổng, bỏ phó bản bang: doanh trại, kho báu, rắn độc, khí gas). Quái nhiệm vụ có ở nhiều map thì chọn map con quái đó yếu nhất.
- Bot không tự học chiêu đặc biệt (khiên năng lượng, dịch chuyển tức thời, thôi miên, trói, huýt sáo, siêu chiêu): người chơi phải có sách đặc biệt thì bot cũng vậy.
- Bot chạm giới hạn sức mạnh thì tự mở giới hạn miễn phí (chờ như người chơi); bước "Đạt ... sức mạnh" được kiểm tra lại mỗi lượt (trước đây chạm giới hạn thì kẹt bước này mãi).

## Đợt 98
- **Danh hiệu Phá Cỗ Trung Thu VIP / Perfect:** dùng hiệu ứng mới anh gửi (data/effect 516 = Phá Cỗ Trung Thu VIP đèn lồng, 517 = Perfect Cell); hiệu ứng cũ 301 chỉ là khung chữ, 302 bị vỡ hình. Mã mới nên client tải lại ảnh, không dùng ảnh cũ đã lưu. Hiệu ứng 515 / 518 / 519 trong gói trùng mã với hiệu ứng đang dùng (hình khác) nên không chép đè.
- **Bot không vào map chưa mở theo nhiệm vụ:** dùng chung luật người chơi (ChangeMapService.checkMapCanJoin) khi luyện tập, làm nhiệm vụ, săn boss; kèm chặn theo chỉ số HP / sức đánh đã có. Bot tài khoản không bị gỡ ngang khỏi game khi map hết khu.
- **Bot học chiêu bằng tiềm năng** (1/10 sức mạnh yêu cầu của cấp đó) vì ngọc chỉ kiếm qua nhiệm vụ hằng ngày; vẫn không tự học chiêu cần sách đặc biệt.
- **Nội tại:** bot từ 10 tỷ sức mạnh mở nội tại bằng vàng (giá như người chơi, chừa 50tr dự trữ) tới khi được nội tại tăng sát thương cho 1 chiêu đánh bot đang dùng.
- **Bot phần sau:** đệ đi đánh cùng để lên sức mạnh; 22h đánh Hirudegarn (Thành phố Santa), nhặt Quả trứng Mabư thì dùng (nở sau 10 ngày như người chơi, sau đó Hirudegarn rơi Bình hút); có Bình hút thì đi Sa mạc hoang vu tích Kilis, đủ 3000 Kilis và đệ 40 tỷ thì đổi đệ tử 2; mua Bông tai Porata cấp 1, lên Thánh địa nhặt mảnh vỡ, đủ 9999 mảnh ghép bông tai cấp 2 (200tr vàng, 50%); có đủ 20000 mảnh BT3 thì ghép cấp 3. Chỗ cần ngọc bot trả bằng tiềm năng (1 ngọc = 1 triệu tiềm năng). Các việc này chỉ đi khi chỉ số đủ đánh map Cold.

## Đợt 99
- **Nhiệm vụ theo source V10:** nhiệm vụ 29 (trước là "..") thành chuỗi Bardock như V10: nhặt Nhẫn thời không khi hạ Black Goku → dùng nhẫn (tới hành tinh thực vật quá khứ) → tìm Bardock → hạ 5.000 Tobi / Cabira → nói chuyện Bardock → tìm Berry → đưa Berry về hang → nhặt 99 thức ăn ở Bìa rừng nguyên thủy → hạ 10.000 Tobi / Cabira → báo Bardock (thưởng 10 Tr sức mạnh / tiềm năng). Source V10 chỉ làm 2 bước đầu, các bước sau không có code (người chơi V10 kẹt ở bước 3): đã làm đủ. Nhiệm vụ 30: chờ nhiệm vụ mới.
- Bỏ NPC đặt sai trong dữ liệu nhiệm vụ 11 (Lý Tiểu Nương ở bước đạt sức mạnh, Tiên Bry ở bước về nhà): bước về nhà gặp ông nội.
- **Boss đánh toàn "Xí hụt":** Goku, Cadic (Mabư 12h), Tào Pai Pai tính né đòn chia 1 thay vì 1000, có chút né đòn là né mọi đòn. Đã sửa như các boss khác.
- **Máy đo sức mạnh:** đứng yên, bị đấm từ phía bên kia thì quay mặt về người đấm.
- **Bot ép sao pha lê / ngọc rồng** vào lỗ trống (1 - 7) của đồ đang mặc, theo món: áo quần chống chịu (ngọc 7 sao HP, ngọc 2 sao giảm sát thương), găng và rada sức đánh (ngọc 3 sao, xuyên giáp), giày né đòn / KI (ngọc 1 sao, ngọc 6 sao). Thiếu ngọc thì đổi tiềm năng.
- **Bot tự rút kinh nghiệm chọn map** (thay cho model AI): mỗi lần rời map, chấm điểm map = sức mạnh kiếm được mỗi phút (chết thì giảm điểm), điểm dùng chung mọi bot, lưu bảng bot_map_stat; khi chọn map luyện, rút 3 map hợp sức lấy map điểm cao nhất, map chưa thử được ưu tiên, 1/5 số lần chọn ngẫu nhiên để khám phá.
- **Đấm Black Goku / SSJ Broly toàn hụt:** chiêu đánh gần bị tính trượt khi khoảng cách trên server > 100 px, mà boss di chuyển / bay liên tục nên vị trí trên server lệch so với màn hình người chơi (đứng sát đấm vẫn bị tính xa). Mục tiêu là boss nay cho 200 px. Black Goku còn trừ ngẫu nhiên tới 100.000 sát thương mỗi đòn (người đánh dưới 100k chỉ ra 1): nay trừ tối đa 10% sát thương.
- **Sức đánh lớn (1,4 tỷ) đánh boss vẫn báo hụt:** sát thương sau giáp và số trả về khi bị đánh bị ép kiểu int, đòn > 2,1 tỷ (chí mạng) tràn thành số âm, client hiện hụt. Hàm trừ giáp trả long, mọi chỗ trả / hiện sát thương giới hạn ở 2,1 tỷ thay vì tràn số.
- **Có đủ 3 Pet Xên mà không nhận danh hiệu Perfect:** lỗi do Đợt 96 đổi mã hiệu ứng danh hiệu (302 -> 517) nhưng code cấp danh hiệu và vật phẩm danh hiệu vẫn dùng 302. Đã đổi đồng bộ (PERFECT_EFFECT, vật phẩm danh hiệu 2081 / 2076, danh hiệu người chơi đã có 301 / 302 -> 516 / 517). Đã thử: đủ 3 pet vĩnh viễn, vào game nhận ngay danh hiệu Perfect.
- **Bot như người chơi thêm:** chat thế giới (tốn ngọc như người chơi, ~40 phút / lần), mở hộp quà / capsule / túi mù trong hành trang, mua và bật máy dò khi làm nhiệm vụ Capsule kì bí (đánh Xên con), thỉnh thoảng bật / tắt cờ khi luyện tập, mặc đồ thừa cho đệ.

## Đợt 100
- **EXP server x1** (Config.properties server.expserver=1; panel / menu admin vẫn đổi tạm được).
- **Vàng rơi:** mỗi cục tối đa 9.999 vàng; trong khu riêng tư vào bằng Vé vàng (map 164) tối đa 500.000 / cục (áp cho mọi nguồn rơi vàng: quái, boss, phó bản).
- **Vé vàng** bán ở Santa (tab Hỗ trợ) giá 19.999 ngọc.
- **Dòng "Không giao dịch"** cho đồ đổi / mua ở sự kiện (cả hiện trong shop sự kiện), trừ cải trang, đeo lưng, ván bay, danh hiệu, pet.
- **Ký gửi đồ sự kiện ở Siêu thị:** Cửa hàng ký gửi (Siêu thị) nhận nguyên liệu sự kiện (bí ngô, kẹo...), người chơi bán cho nhau bằng ngọc / thỏi vàng.
- **Bot:** bỏ bot giả lập (chỉ còn tài khoản bot chơi thật); tạo tới 1.000 bot một lần (panel / menu admin), tên ngẫu nhiên như người chơi (hunter2k5, gauconboy, kazephu2k6...); bot vào game 20 con / 30 giây.
- **Panel Bot mới:** ô tổng hợp (tổng, đang chơi, chờ, đã tắt, tổng sức mạnh, số bang), biểu đồ đang làm gì, tìm / lọc / sắp xếp, thanh sức mạnh, bật / tắt tất cả, tự làm mới 10 giây.
- **Menu Bot trong game** (công cụ admin → Bot): xem tổng hợp, tạo bot (1 - 1000), bật / tắt tất cả, tới chỗ 1 bot đang chơi.
- **Sửa "Không giao dịch" hiện nhầm** trên CT (Gohan Beast, Android 21...), đeo lưng (Cánh thiên sứ hắc ám, Cánh thiên thần sa ngã, Bư tập tạ, Lưỡi hái hồng...): dòng này đến từ option mặc định của món, nay bị gỡ cho các loại được miễn, cả trong shop lẫn khi nhận.

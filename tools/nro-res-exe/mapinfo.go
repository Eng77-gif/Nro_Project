package main

// Lấy cả bản đồ: gói -24 (thông tin bản đồ) gửi khi nhân vật vào map gồm tên, hành tinh, bộ ô gạch, nền, cổng, quái,
// NPC, vật trang trí nền (item_bg_map_data) và hiệu ứng (eff_map). Ô gạch tải riêng bằng lệnh -28 loại 10.
// "Dò bản đồ" cho nhân vật đi qua các cổng (di chuyển vào khung cổng rồi gửi -23) để thăm lần lượt nhiều map.

import (
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strconv"
	"strings"
	"time"
)

type waypoint struct {
	Name                   string `json:"name"`
	MinX, MinY, MaxX, MaxY int    `json:"-"`
	Rect                   [4]int `json:"rect"`
	Enter                  bool   `json:"enter"`
	Offline                bool   `json:"offline"`
	GoMap                  int    `json:"goMap"` // -1: chưa đi qua nên chưa biết dẫn tới đâu
	GoX                    int    `json:"goX"`
	GoY                    int    `json:"goY"`
}

type mapMob struct {
	Temp, Level int
	Hp          int64
	X, Y        int
}

type mapNpc struct {
	Temp, X, Y, Status, Avatar int
}

type mapInfo struct {
	ID        int        `json:"id"`
	Name      string     `json:"name"`
	Planet    int        `json:"planet"`
	Tile      int        `json:"tile"`
	Bg        int        `json:"bg"`
	Type      int        `json:"type"`
	BgType    int        `json:"bgType"`
	Zone      int        `json:"zone"`
	X, Y      int        `json:"-"`
	Waypoints []waypoint `json:"waypoints"`
	Mobs      []mapMob   `json:"-"`
	Npcs      []mapNpc   `json:"-"`
	bgItems   []byte
	effects   []byte
}

// parseMapInfo đọc gói -24. Server lậu / chính thức và từng bản client khác nhau ở cách ghi quái (mã quái 1 hay 2 byte,
// máu 4 hay 8 byte) nên thử các kiểu và lấy kiểu đọc hết gói.
func parseMapInfo(data []byte) (*mapInfo, error) {
	var last error
	for _, v := range [][2]bool{{true, true}, {false, false}, {true, false}, {false, true}} {
		mi, err := parseMapInfoAs(data, v[0], v[1])
		if err == nil {
			return mi, nil
		}
		last = err
	}
	return nil, last
}

func parseMapInfoAs(data []byte, tempShort, hpLong bool) (*mapInfo, error) {
	r := &rd{d: data}
	u8 := func() int {
		b, _ := r.take(1)
		if len(b) == 0 {
			return 0
		}
		return int(b[0])
	}
	i8 := func() int { v, _ := r.i8(); return int(v) }
	i16 := func() int { v, _ := r.i16(); return int(v) }
	i32 := func() int64 { v, _ := r.i32(); return int64(v) }
	num := func() int64 {
		if hpLong {
			b, err := r.take(8)
			if err != nil {
				return 0
			}
			var v int64
			for _, c := range b {
				v = v<<8 | int64(c)
			}
			return v
		}
		return i32()
	}
	bo := func() bool { return i8() != 0 }
	mi := &mapInfo{}
	mi.ID = u8()
	mi.Planet, mi.Tile, mi.Bg, mi.Type = i8(), i8(), i8(), i8()
	name, err := r.utf()
	if err != nil {
		return nil, err
	}
	mi.Name = name
	mi.Zone = i8()
	mi.X, mi.Y = i16(), i16()
	for n := u8(); n > 0; n-- {
		w := waypoint{GoMap: -1}
		w.MinX, w.MinY, w.MaxX, w.MaxY = i16(), i16(), i16(), i16()
		w.Rect = [4]int{w.MinX, w.MinY, w.MaxX, w.MaxY}
		w.Enter, w.Offline = bo(), bo()
		if w.Name, err = r.utf(); err != nil {
			return nil, err
		}
		mi.Waypoints = append(mi.Waypoints, w)
	}
	for n := u8(); n > 0; n-- {
		r.take(5) // khoá, đứng yên, lửa, băng, gió
		m := mapMob{}
		if tempShort {
			m.Temp = i16()
		} else {
			m.Temp = u8()
		}
		i8() // sys
		m.Hp = num()
		m.Level = i8()
		num() // máu tối đa
		m.X, m.Y = i16(), i16()
		i8()
		i8()
		bo() // quái lớn
		mi.Mobs = append(mi.Mobs, m)
	}
	if extra := u8(); extra != 0 {
		return nil, errors.New("định dạng quái không khớp")
	}
	for n := u8(); n > 0; n-- {
		s, x, y, t, a := i8(), i16(), i16(), u8(), i16()
		mi.Npcs = append(mi.Npcs, mapNpc{Temp: t, X: x, Y: y, Status: s, Avatar: a})
	}
	for n := u8(); n > 0; n-- {
		i16()
		i16()
		i16()
		i16()
		if i32() == -2 {
			i16()
		}
	}
	start := r.p
	nb := i16()
	if nb < 0 || nb > 3000 {
		return nil, errors.New("số vật nền không hợp lệ")
	}
	if _, err := r.take(nb * 6); err != nil {
		return nil, err
	}
	mi.bgItems = append([]byte(nil), data[start:r.p]...)
	start = r.p
	ne := i16()
	if ne < 0 || ne > 1000 {
		return nil, errors.New("số hiệu ứng không hợp lệ")
	}
	for ; ne > 0; ne-- {
		if _, err := r.utf(); err != nil {
			return nil, err
		}
		if _, err := r.utf(); err != nil {
			return nil, err
		}
	}
	mi.effects = append([]byte(nil), data[start:r.p]...)
	b, err := r.take(2) // kiểu nền, tàu
	if err != nil {
		return nil, err
	}
	mi.BgType = int(int8(b[0]))
	if rest := len(data) - r.p; rest > 3 {
		return nil, fmt.Errorf("thừa %d byte", rest)
	}
	return mi, nil
}

// onMapInfo: gói -24 tới (vào map, kể cả lúc đăng nhập).
func (d *downloader) onMapInfo(data []byte) {
	mi, err := parseMapInfo(data)
	if err != nil {
		if d.o.Debug {
			d.j.log("    không đọc được thông tin bản đồ: %v", err)
		}
		return
	}
	d.curMap = mi
	d.mapSeq++
}

// saveMap lưu đủ một map đang đứng: ô gạch (hỏi -28), vật nền, hiệu ứng, thông tin.
func (d *downloader) saveMap(mi *mapInfo) {
	id := strconv.Itoa(mi.ID)
	if d.full == nil {
		d.full = map[int]*mapInfo{}
	}
	if old := d.full[mi.ID]; old != nil {
		for i := range mi.Waypoints { // giữ chỗ đến của cổng đã biết
			if i < len(old.Waypoints) && old.Waypoints[i].GoMap >= 0 {
				mi.Waypoints[i].GoMap, mi.Waypoints[i].GoX, mi.Waypoints[i].GoY = old.Waypoints[i].GoMap, old.Waypoints[i].GoX, old.Waypoints[i].GoY
			}
		}
	}
	d.full[mi.ID] = mi
	d.write("map/item_bg_map_data/"+id, mi.bgItems)
	d.write("map/eff_map/"+id, mi.effects)
	if !d.have("map/tile_map_data/" + id) {
		d.mapData = nil
		d.c.send(-28, []byte{10, byte(mi.ID)})
		for end := time.Now().Add(4 * time.Second); d.mapData == nil && !d.j.isStopped() && time.Now().Before(end); {
			d.pump(0.3)
		}
		if d.mapData != nil {
			d.write("map/tile_map_data/"+id, d.mapData)
		}
	}
	d.writeMapJSON(mi)
	d.stats["bản đồ (đủ)"] = len(d.full)
}

func (d *downloader) writeMapJSON(mi *mapInfo) {
	out := map[string]any{"id": mi.ID, "name": mi.Name, "planet": mi.Planet, "tile": mi.Tile, "bg": mi.Bg, "type": mi.Type,
		"bgType": mi.BgType, "waypoints": mi.Waypoints, "mobs": mi.Mobs, "npcs": mi.Npcs}
	b, _ := json.MarshalIndent(out, "", " ")
	d.write("map/info/"+strconv.Itoa(mi.ID)+".json", b)
}

// writeMapSQL: map_template (thêm map mới hoặc cập nhật map lỗi). Cổng chỉ ghi khi đã biết hết chỗ đến (đã đi qua).
func (d *downloader) writeMapSQL() {
	if len(d.full) == 0 {
		return
	}
	q := func(s string) string {
		return "'" + strings.ReplaceAll(strings.ReplaceAll(s, `\`, `\\`), "'", `\'`) + "'"
	}
	ids := make([]int, 0, len(d.full))
	for id := range d.full {
		ids = append(ids, id)
	}
	sort.Ints(ids)
	var sb strings.Builder
	sb.WriteString("-- NroResTool: bản đồ lấy từ server (nhập sau khi chép thư mục map/ vào data/map/)\n")
	for _, id := range ids {
		mi := d.full[id]
		var mobs, npcs, wps []string
		for _, m := range mi.Mobs {
			mobs = append(mobs, fmt.Sprintf("[%d,%d,%d,%d,%d]", m.Temp, m.Level, m.Hp, m.X, m.Y))
		}
		for _, n := range mi.Npcs {
			npcs = append(npcs, fmt.Sprintf("[%d,%d,%d]", n.Temp, n.X, n.Y))
		}
		known := true
		for _, w := range mi.Waypoints {
			if w.GoMap < 0 {
				known = false
			}
			name, _ := json.Marshal(w.Name)
			inner := fmt.Sprintf("[%s,%d,%d,%d,%d,%d,%d,%d,%d,%d]", name, w.MinX, w.MinY, w.MaxX, w.MaxY, b2i(w.Enter), b2i(w.Offline), w.GoMap, w.GoX, w.GoY)
			js, _ := json.Marshal(inner)
			wps = append(wps, string(js))
		}
		mobsJ, _ := json.Marshal(mobsStr(mobs))
		wpUpd := ""
		if known {
			wpUpd = ", waypoints = VALUES(waypoints)"
		}
		fmt.Fprintf(&sb, "INSERT INTO map_template (id, NAME, zones, max_player, data, type, planet_id, bg_type, tile_id, bg_id, waypoints, mobs, npcs) VALUES "+
			"(%d, %s, 15, 15, '[0,0,0,%d,%d]', %d, %d, %d, %d, %d, %s, %s, %s) ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), type = VALUES(type), "+
			"planet_id = VALUES(planet_id), bg_type = VALUES(bg_type), tile_id = VALUES(tile_id), bg_id = VALUES(bg_id), mobs = VALUES(mobs), npcs = VALUES(npcs)%s;\n",
			id, q(mi.Name), mi.Tile, mi.Bg, mi.Type, mi.Planet, mi.BgType, mi.Tile, mi.Bg, q("["+strings.Join(wps, ",")+"]"), q(string(mobsJ)), q("["+strings.Join(npcs, ",")+"]"), wpUpd)
	}
	d.write("map/maps.sql", []byte(sb.String()))
	d.j.log("  map/maps.sql: %d bản đồ (cập nhật map_template)", len(ids))
}

// mobsStr: cột mobs lưu mảng chuỗi ["[temp,level,hp,x,y]", ...].
func mobsStr(m []string) []string {
	if m == nil {
		return []string{}
	}
	return m
}

func b2i(b bool) int {
	if b {
		return 1
	}
	return 0
}

// crawl: đi qua các cổng để thăm tối đa limit bản đồ, lưu đủ từng map. Cần tài khoản (nhân vật trong game).
func (d *downloader) crawl(limit int) {
	if d.o.User == "" {
		d.j.log("Bỏ qua dò bản đồ: cần tài khoản để vào game")
		return
	}
	for end := time.Now().Add(15 * time.Second); d.curMap == nil && !d.j.isStopped() && time.Now().Before(end); {
		d.pump(0.5)
	}
	if d.curMap == nil {
		d.j.log("Dò bản đồ: không nhận được thông tin bản đồ đang đứng")
		return
	}
	d.j.log("Dò bản đồ: bắt đầu ở %s (%d), tối đa %d map...", d.curMap.Name, d.curMap.ID, limit)
	failed := map[string]bool{} // map:cổng đi không được
	d.saveMap(d.curMap)
	for len(d.full) < limit && !d.j.isStopped() {
		cur := d.full[d.curMap.ID]
		// cổng chưa đi ở map đang đứng
		next := -1
		for i, w := range cur.Waypoints {
			if w.GoMap < 0 && !w.Offline && !failed[fmt.Sprintf("%d:%d", cur.ID, i)] {
				next = i
				break
			}
		}
		if next < 0 {
			path := d.pathToUnexplored(cur.ID, failed)
			if path == nil {
				d.j.log("  Không còn cổng nào chưa đi")
				break
			}
			ok := true
			for _, step := range path { // đi qua các cổng đã biết tới map còn cổng chưa đi
				if !d.walkThrough(step) {
					ok = false
					break
				}
			}
			if !ok {
				d.j.log("  Không quay lại được map còn cổng chưa đi, dừng")
				break
			}
			continue
		}
		from := cur.ID
		w := cur.Waypoints[next]
		if !d.walkThrough(next) {
			failed[fmt.Sprintf("%d:%d", from, next)] = true
			continue
		}
		mi := d.curMap
		d.full[from].Waypoints[next].GoMap, d.full[from].Waypoints[next].GoX, d.full[from].Waypoints[next].GoY = mi.ID, mi.X, mi.Y
		if _, seen := d.full[mi.ID]; !seen {
			d.saveMap(mi)
			d.j.log("  %d. %s (%d) qua cổng %q", len(d.full), mi.Name, mi.ID, w.Name)
		} else {
			d.full[mi.ID].X, d.full[mi.ID].Y = mi.X, mi.Y
		}
		d.writeMapJSON(d.full[from])
	}
	d.j.log("Dò bản đồ xong: %d map", len(d.full))
}

// walkThrough: đi vào khung cổng thứ i của map đang đứng rồi xin chuyển map; true khi đã sang map khác.
func (d *downloader) walkThrough(i int) bool {
	cur := d.curMap
	if cur == nil || i >= len(cur.Waypoints) {
		return false
	}
	w := cur.Waypoints[i]
	tx, ty := (w.MinX+w.MaxX)/2, w.MaxY
	x, y := cur.X, cur.Y
	for steps := 0; steps < 60 && (x != tx || y != ty); steps++ { // đi từng đoạn 80 px như người chơi
		x += clamp(tx-x, 80)
		y += clamp(ty-y, 80)
		d.c.send(-7, (&pkt{}).b(0).s(int16(x)).s(int16(y)).Bytes())
		d.pump(0.15)
	}
	seq := d.mapSeq
	d.c.send(-23, nil)
	for end := time.Now().Add(6 * time.Second); d.mapSeq == seq && !d.j.isStopped() && time.Now().Before(end); {
		d.pump(0.3)
	}
	if d.mapSeq == seq || d.curMap == nil || d.curMap.ID == cur.ID {
		d.curMap = cur // không sang được: vẫn ở map cũ
		return false
	}
	return true
}

func clamp(v, m int) int {
	if v > m {
		return m
	}
	if v < -m {
		return -m
	}
	return v
}

// pathToUnexplored: danh sách cổng (theo thứ tự) để đi từ map from tới map gần nhất còn cổng chưa đi.
func (d *downloader) pathToUnexplored(from int, failed map[string]bool) []int {
	type node struct {
		id   int
		path []int
	}
	seen := map[int]bool{from: true}
	queue := []node{{from, nil}}
	for len(queue) > 0 {
		n := queue[0]
		queue = queue[1:]
		mi := d.full[n.id]
		if mi == nil {
			continue
		}
		for i, w := range mi.Waypoints {
			if w.GoMap < 0 && !w.Offline && !failed[fmt.Sprintf("%d:%d", n.id, i)] && n.id != from {
				return n.path
			}
		}
		for i, w := range mi.Waypoints {
			if w.GoMap >= 0 && !seen[w.GoMap] {
				seen[w.GoMap] = true
				p := append(append([]int(nil), n.path...), i)
				queue = append(queue, node{w.GoMap, p})
			}
		}
	}
	return nil
}

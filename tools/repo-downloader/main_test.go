package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestMatchesSuffix(t *testing.T) {
	l := splitList("src,*.sh")
	for p, want := range map[string]bool{"src/a.java": true, "run.sh": true, "x/run.SH": true, "run.bat": false, "data/src.png": false} {
		if matches(p, l) != want {
			t.Errorf("%s: want %v", p, want)
		}
	}
}

func TestRemoveDroppedOnlySafeList(t *testing.T) {
	base := t.TempDir()
	for _, p := range []string{"src/a.java", "data/icon/1.png", "Config.properties"} {
		os.MkdirAll(filepath.Dir(filepath.Join(base, p)), 0o755)
		os.WriteFile(filepath.Join(base, p), []byte("x"), 0o644)
	}
	drop := []entry{{Path: "src/a.java"}, {Path: "data/icon/1.png"}, {Path: "Config.properties"}}
	removeDropped(base, drop, splitList("Config.properties"), splitList("src"))
	if _, err := os.Stat(filepath.Join(base, "src")); !os.IsNotExist(err) {
		t.Error("src phải bị xoá cả thư mục rỗng")
	}
	for _, p := range []string{"data/icon/1.png", "Config.properties"} {
		if _, err := os.Stat(filepath.Join(base, p)); err != nil {
			t.Error(p + " không được xoá")
		}
	}
}

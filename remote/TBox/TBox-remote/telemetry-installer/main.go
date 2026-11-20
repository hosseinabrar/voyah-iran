package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

func main() {
	root := flag.String("root", ".", "package root")
	flag.Parse()
	platform, name := "macos", "adb"
	if runtime.GOOS == "windows" {
		platform, name = "windows", "adb.exe"
	}
	adb := filepath.Join(*root, "..", "TBox-antenna", "tools", platform, name)
	run := func(args ...string) (string, error) {
		ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
		defer cancel()
		b, e := exec.CommandContext(ctx, adb, args...).CombinedOutput()
		return string(b), e
	}
	fail := func(s string) { fmt.Println("ERROR:", s); os.Exit(1) }
	out, e := run("devices")
	if e != nil {
		fail(out)
	}
	serial := ""
	for _, line := range strings.Split(out, "\n") {
		f := strings.Fields(line)
		if len(f) != 2 || f[1] != "device" {
			continue
		}
		if s, e := run("-s", f[0], "shell", "test -f /mnt/hirain_app/etc/software_versions.xml && echo VI_TBOX"); e == nil && strings.Contains(s, "VI_TBOX") {
			if serial != "" {
				fail("Connect only one T-box")
			}
			serial = f[0]
		}
	}
	if serial == "" {
		fail("No authorized T-box connected")
	}
	fmt.Println("DONE: T-box detected")
	if s, e := run("-s", serial, "shell", "mkdir -p /tmp/vi-remote-install"); e != nil {
		fail(s)
	}
	files, e := os.ReadDir(filepath.Join(*root, "telemetry-payload"))
	if e != nil {
		fail(e.Error())
	}
	for _, f := range files {
		if !f.IsDir() && !strings.HasPrefix(f.Name(), ".") {
			if s, e := run("-s", serial, "push", filepath.Join(*root, "telemetry-payload", f.Name()), "/tmp/vi-remote-install/"+f.Name()); e != nil {
				fail(s)
			}
		}
	}
	fmt.Println("DONE: Package transferred")
	s, e := run("-s", serial, "shell", "sh /tmp/vi-remote-install/install.sh")
	fmt.Print(s)
	if e != nil || !strings.Contains(s, "SUCCESS: Installation complete;") {
		fail("Installation failed; inspect the error above")
	}
}

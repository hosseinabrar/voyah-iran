package main

import (
	"bytes"
	"context"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

func main() {
	root := flag.String("root", ".", "TBox-remote folder")
	uninstall := flag.Bool("uninstall", false, "disable remote only")
	flag.Parse()
	abs, e := filepath.Abs(*root)
	if e != nil {
		log.Fatal(e)
	}
	*root = abs
	platform := "macos"
	name := "adb"
	if runtime.GOOS == "windows" {
		platform = "windows"
		name = "adb.exe"
	}
	adb := filepath.Join(*root, "..", "TBox-antenna", "tools", platform, name)
	logs := filepath.Join(*root, "logs")
	os.MkdirAll(logs, 0700)
	lf, e := os.OpenFile(filepath.Join(logs, "install-"+time.Now().Format("20060102-150405")+".log"), os.O_CREATE|os.O_WRONLY, 0600)
	if e != nil {
		log.Fatal(e)
	}
	defer lf.Close()
	log.SetOutput(io.MultiWriter(os.Stderr, lf))
	log.SetPrefix("FAILED: ")
	fmt.Println("Installation log:", lf.Name())
	run := func(args ...string) (string, error) {
		ctx, cancel := context.WithTimeout(context.Background(), 45*time.Second)
		defer cancel()
		c := exec.CommandContext(ctx, adb, args...)
		var b bytes.Buffer
		c.Stdout = &b
		c.Stderr = &b
		e := c.Run()
		lf.Write(b.Bytes())
		return b.String(), e
	}
	devices, e := run("devices")
	if e != nil {
		log.Fatal(e)
	}
	serial := ""
	for _, line := range strings.Split(devices, "\n") {
		f := strings.Fields(line)
		if len(f) < 2 || f[1] != "device" {
			continue
		}
		if identity, err := run("-s", f[0], "shell", "test -f /mnt/hirain_app/etc/software_versions.xml && echo TBOX_FOUND"); err == nil && strings.Contains(identity, "TBOX_FOUND") {
			if serial != "" {
				log.Fatal("Connect only one T-box")
			}
			serial = f[0]
		}
	}
	if serial == "" {
		log.Fatal("No authorized T-box connected")
	}
	shell := func(cmd string) (string, error) { return run("-s", serial, "shell", cmd) }
	if *uninstall {
		out, e := shell("/bin/sh /usr/local/lib/TBox-remote/uninstall.sh")
		fmt.Print(out)
		if e != nil {
			log.Fatal(e)
		}
		return
	}
	version, e := shell("cat /mnt/hirain_app/etc/software_versions.xml")
	if e != nil || !strings.Contains(version, `value="2.0.7"`) {
		log.Fatal("Expected T-box 2.0.7")
	}
	fmt.Println("DONE: T-box 2.0.7 connected")
	radio, e := shell("sha256sum /lib64/libvendor-ril.so")
	if e != nil {
		log.Fatal(e)
	}
	if !strings.HasPrefix(radio, "1ec44b63fd7dd2a5d0cc88a06fe82c802f2ec415aff87861611b6ed95c6ef201") {
		if !strings.HasPrefix(radio, "247c82094dfdb47756aedce77a99fe06cf95b1941cfdd315713e408756306d85") {
			log.Fatal("Unknown radio firmware")
		}
		fmt.Println("Installing bundled antenna prerequisite...")
		kit := filepath.Join(*root, "..", "TBox-antenna")
		var cmd *exec.Cmd
		if runtime.GOOS == "windows" {
			cmd = exec.Command("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", filepath.Join(kit, "host_win.ps1"), "-Action", "install")
		} else {
			cmd = exec.Command("/bin/bash", filepath.Join(kit, "host_mac.sh"), "install")
		}
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		if e = cmd.Run(); e != nil {
			log.Fatal("Antenna prerequisite failed: ", e)
		}
		radio, e = shell("sha256sum /lib64/libvendor-ril.so")
		if e != nil || !strings.HasPrefix(radio, "1ec44b63fd7dd2a5d0cc88a06fe82c802f2ec415aff87861611b6ed95c6ef201") {
			log.Fatal("Antenna patch verification failed")
		}

	}
	if _, e = shell("mkdir -p /tmp/TBox-remote-stage"); e != nil {
		log.Fatal(e)
	}
	if _, e = run("-s", serial, "push", filepath.Join(*root, "payload")+"/.", "/tmp/TBox-remote-stage/"); e != nil {
		log.Fatal(e)
	}
	out, e := shell("/bin/sh /tmp/TBox-remote-stage/install.sh && echo VOYAH_INSTALL_OK")
	fmt.Print(out)
	if e != nil || !strings.Contains(out, "VOYAH_INSTALL_OK") {
		log.Fatal("Installation failed before startup; see installation log")
	}
	for i := 0; i < 40; i++ {
		state, _ := shell("systemctl is-active TBox-remote.service")
		if strings.TrimSpace(state) == "active" {
			fmt.Println("SUCCESS: Remote agent is running. Send an SMS from the paired phone to open a 60-second window.")
			return
		}
		if strings.TrimSpace(state) == "failed" {
			break
		}
		time.Sleep(2 * time.Second)
	}
	out, _ = shell("tail -n 20 /tmp/TBox-remote-prepare.log /tmp/TBox-remote-agent.log /tmp/TBox-remote-oem.log")
	fmt.Print(out)
	log.Fatal("Remote startup failed; see log. OEM runtime overlays can be removed with uninstall.")
}

package main

import (
	"fmt"
	"os"
	"syscall"
	"unsafe"
)

// PTY makes libc line-buffer CAN output instead of holding cached frames in a pipe.
func terminal() (*os.File, *os.File, error) {
	m, e := os.OpenFile("/dev/ptmx", os.O_RDWR|syscall.O_NOCTTY, 0)
	if e != nil {
		return nil, nil, e
	}
	var unlock int32
	_, _, errno := syscall.Syscall(syscall.SYS_IOCTL, m.Fd(), 0x40045431, uintptr(unsafe.Pointer(&unlock)))
	if errno != 0 {
		m.Close()
		return nil, nil, errno
	}
	var n uint32
	_, _, errno = syscall.Syscall(syscall.SYS_IOCTL, m.Fd(), 0x80045430, uintptr(unsafe.Pointer(&n)))
	if errno != 0 {
		m.Close()
		return nil, nil, errno
	}
	s, e := os.OpenFile(fmt.Sprintf("/dev/pts/%d", n), os.O_RDWR|syscall.O_NOCTTY, 0)
	if e != nil {
		m.Close()
	}
	return m, s, e
}

//go:build !linux

package main

import (
	"fmt"
	"os"
)

func terminal() (*os.File, *os.File, error) {
	return nil, nil, fmt.Errorf("OEM collector requires Linux; use -stdin for fixtures")
}

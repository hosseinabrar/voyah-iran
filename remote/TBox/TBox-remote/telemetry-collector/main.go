// Linux collector uses the installed OEM receive-only monitor, never CAN injection.
package main

import (
	"bufio"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"encoding/xml"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"time"
)

type signal struct {
	Name   string  `xml:"name,attr"`
	Start  uint    `xml:"startbit,attr"`
	Bits   uint    `xml:"bitlen,attr"`
	Factor float64 `xml:"factor,attr"`
	Offset float64 `xml:"offset,attr"`
	Signed int     `xml:"signed,attr"`
}
type message struct {
	ID      string   `xml:"id,attr"`
	Signals []signal `xml:"signal"`
}
type config struct {
	Order    string `xml:"order,attr"`
	Channels []struct {
		Index    int       `xml:"index,attr"`
		Messages []message `xml:"message"`
	} `xml:"channel"`
}
type sample struct {
	LastCANAt   int64              `json:"lastCANAt,omitempty"`
	CANMessages uint64             `json:"canMessages,omitempty"`
	ObservedAt  int64              `json:"observedAt"`
	Values      map[string]any     `json:"values"`
	FieldTimes  map[string]int64   `json:"fieldTimes"`
	Raw         map[string]float64 `json:"raw,omitempty"`
}
type mapping struct {
	Key             string
	Min, Max, Scale float64
	Bool            bool
}

var mappings = map[string]mapping{
	// OEM 10-bit EV cruising range; 1023 denotes unavailable.
	"vcu_remainingMileage": {"batteryRange", 0, 1022, 1, false},
	// OEM TPMS physical values are bar; the remote client contract uses PSI.
	"bcm_tyreLFPressure":            {"tireFLPsi", 0, 25.4, 14.5037738, false},
	"bcm_tyreRFPressure":            {"tireFRPsi", 0, 25.4, 14.5037738, false},
	"bcm_tyreLRPressure":            {"tireRLPsi", 0, 25.4, 14.5037738, false},
	"bcm_tyreRRPressure":            {"tireRRPsi", 0, 25.4, 14.5037738, false},
	"bms_maxACChargeAllowedCurrent": {"chargeCurrentLimit", 5, 68, 1, false},
	"ivi_chargeFormSet":             {"chargeForm", 0, 3, 1, false},
	"bms_vPackBms":                  {"batteryVoltage", 0, 800, 1, false},
	"bms_iPack":                     {"batteryCurrent", -600, 1000, 1, false},
	"bms_packTempAvg":               {"batteryTemperature", -40, 150, 1, false},
	"bms_SOH":                       {"batteryHealth", 0, 100, 1, false},
	"bms_maxChargeSOC":              {"chargeLimit", 50, 100, 1, false},
	"bcm_ebs_U_BATT":                {"auxVoltage", 0, 25, 1, false},
	"bcm_12VBatterySOC":             {"auxSOC", 0, 100, 1, false},

	"bms_SOC": {"battery", 0, 100, 1, false}, "ac_ambientTemp": {"outside", -40, 80, 1, false}, "ac_cabinTemp": {"cabin", -40, 85, 1, false},
	"ic_oilPercent": {"fuel", 0, 100, 1, false}, "IC_OilRemainingMileage": {"fuelRange", 0, 1022, 1, false},
}
var header = regexp.MustCompile(`CAN\[([0-9a-fA-F]+)\] channel\[(\d+)\] data:`)

func decode(data []byte, s signal) (float64, bool) {
	if s.Start < 1 || s.Bits < 1 || s.Bits > 63 || s.Start-1+s.Bits > uint(len(data)*8) {
		return 0, false
	}
	var padded [8]byte
	copy(padded[:], data)
	n := (binary.LittleEndian.Uint64(padded[:]) >> (s.Start - 1)) & ((uint64(1) << s.Bits) - 1)
	v := int64(n)
	if s.Signed != 0 && n&(1<<(s.Bits-1)) != 0 {
		v -= 1 << s.Bits
	}
	return float64(v)*s.Factor + s.Offset, true
}
func main() {
	input := flag.String("xml", "/etc/vns/dbc/can-in.xml", "OEM CAN schema")
	output := flag.String("output", "/data/vi-remote/snapshot.json", "snapshot")
	stdin := flag.Bool("stdin", false, "fixture input instead of OEM monitor")
	flag.Parse()
	b, e := os.ReadFile(*input)
	if e != nil {
		log.Fatal(e)
	}
	var cfg config
	if e = xml.Unmarshal(b, &cfg); e != nil || cfg.Order != "intel" {
		log.Fatal("Unsupported CAN schema")
	}
	schemas := map[uint64][]signal{}
	for _, ch := range cfg.Channels {
		if ch.Index == 1 {
			for _, m := range ch.Messages {
				id, e := strconv.ParseUint(m.ID, 16, 32)
				if e == nil {
					for i := range m.Signals {
						// X suffixes identify the same signal, not a different bit layout.
						m.Signals[i].Name = regexp.MustCompile(`_0x[0-9a-fA-F]+_[0-9]+$`).ReplaceAllString(m.Signals[i].Name, "")
					}
					schemas[id] = m.Signals
				}
			}
		}
	}
	// Only unambiguous boolean/physical signals are exposed. Enum mappings require
	// recorded vehicle verification; raw values remain in the local diagnostic file.
	for _, side := range []struct{ Oem, UI string }{{"LF", "FL"}, {"RF", "FR"}, {"LR", "RL"}, {"RR", "RR"}} {
		mappings["bcm_tyre"+side.Oem+"Temp"] = mapping{"tire" + side.UI + "C", -40, 125, 1, false}
	}
	for _, side := range []string{"FL", "FR", "RL", "RR"} {
		mappings["bcm_door"+side+"Sts"] = mapping{"door" + side, 0, 1, 1, true}
	}
	mappings["bcm_hoodSts"] = mapping{"hood", 0, 1, 1, true}
	mappings["bcm_chrgCapSts"] = mapping{"chargeFlap", 0, 1, 1, true}
	var reader io.Reader = os.Stdin
	if !*stdin {
		master, slave, e := terminal()
		if e != nil {
			log.Fatal(e)
		}
		defer master.Close()
		defer slave.Close()
		cmd := exec.Command("vnsMonitorCan", "-all")
		cmd.Stdout = slave
		cmd.Stderr = slave
		if e = cmd.Start(); e != nil {
			log.Fatal(e)
		}
		defer cmd.Process.Kill()
		reader = master
	}
	scanner := bufio.NewScanner(reader)
	scanner.Buffer(make([]byte, 4096), 65536)
	snapshot := sample{Values: map[string]any{}, FieldTimes: map[string]int64{}, Raw: map[string]float64{}}
	rawTimes := map[string]int64{}
	var id uint64
	active := false
	var lastWrite time.Time
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if h := header.FindStringSubmatch(line); h != nil {
			id, _ = strconv.ParseUint(h[1], 16, 32)
			active = h[2] == "1"
			continue
		}
		if !active {
			continue
		}
		active = false
		words := strings.Fields(line)
		if len(words) < 8 {
			continue
		}
		raw, e := hex.DecodeString(strings.Join(words[:8], ""))
		if e != nil {
			continue
		}
		now := time.Now()
		snapshot.LastCANAt = now.UnixMilli()
		snapshot.CANMessages++
		changed := false
		for _, s := range schemas[id] {
			v, ok := decode(raw, s)
			if !ok {
				continue
			}
			snapshot.Raw[s.Name] = v
			rawTimes[s.Name] = now.UnixMilli()
			if m, ok := mappings[s.Name]; ok && v >= m.Min && v <= m.Max {
				if m.Bool {
					snapshot.Values[m.Key] = v == 1
				} else {
					snapshot.Values[m.Key] = v * m.Scale
				}
				snapshot.FieldTimes[m.Key] = now.UnixMilli()
				changed = true
			}
		}
		normalize(snapshot.Raw, rawTimes, &snapshot, now.UnixMilli())
		if len(schemas[id]) > 0 {
			changed = true
		}
		if changed {
			snapshot.ObservedAt = now.UnixMilli()
		}
		if now.Sub(lastWrite) >= time.Second {
			data, _ := json.Marshal(snapshot)
			if e = os.WriteFile(*output+".new", data, 0600); e == nil {
				e = os.Rename(*output+".new", *output)
			}
			if e != nil {
				log.Print("Snapshot write failed: ", e)
			}
			lastWrite = now
		}
	}
	if e = scanner.Err(); e != nil {
		log.Fatal(e)
	}
	fmt.Fprintln(os.Stderr, "OEM monitor ended")
}

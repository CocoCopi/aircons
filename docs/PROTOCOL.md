# AirCons protocol notes

These notes describe the subset of Apple's Accessory Protocol (AAP) used by AirCons.
They are derived from public reverse-engineering, especially LibrePods. Behavior is
firmware-dependent and can change.

## Transport

- Bluetooth Classic L2CAP, PSM `4097` (`0x1001`).
- Service UUID `74ec2172-0bad-4d01-8f77-997b2be0722a`.
- A rootless app cannot use the public `createL2capSocket` API for this PSM, so AirCons
  reflects on the hidden `BluetoothSocket` constructor with socket type `3`:
  `BluetoothSocket(BluetoothDevice, int type, boolean auth, boolean encrypt, int port, ParcelUuid uuid)`.
- The pods must already be paired/connected to the phone.

## Framing

AAP packets begin with `04 00 04 00`. The next byte is the opcode. AirCons scans the
stream for that header and handles known opcodes.

## Handshake

```
00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00   handshake
04 00 04 00 4D 00 D7 00 00 00 00 00 00 00 00 00   feature flags
04 00 04 00 0F 00 FF FF FF FF                        request notifications
04 00 04 00 09 00 39 0F 00 00 00                     stem customization
```

AirCons sends the handshake/feature/notification sequence again after starting head
tracking, then starts a watchdog that re-sends the start packet when motion stops.

## Head tracking

Opcode `0x17`. The start and stop packets are 24–25 bytes and differ between firmware
families; AirCons ships both variants and lets the user toggle the alternate flavor.

Incoming head-tracking packets are 70 bytes. AirCons reads little-endian signed 16-bit
orientation values at offsets 43, 45 and 47 (relative to the packet start) and
accelerometer values at 51 and 53.

The raw values are relative to the pod's own frame. AirCons calibrates a neutral
orientation by averaging 18 samples after the link opens, then subtracts that neutral
before mapping yaw/pitch/roll.

## Battery, ears, stems

- `0x04` — battery. Entry count is at offset 6; entries are 5 bytes starting at offset 7:
  component id, status, level, status, flags. Component `0x02` = right, `0x04` = left,
  `0x08` = case. Status `0x01` and `0x05` mean charging.
- `0x06` — ear detection. Bytes 6 and 7 are primary/secondary; `0x00` means in ear.
- `0x19` — stem press. Byte 6 is the press type (`0x05` single, `0x06` double,
  `0x07` triple, `0x08` long); byte 7 is the bud (`0x01` left, `0x02` right).
- `0x09` — control/ack traffic; currently skipped.

## Privacy / ownership

AirCons does not attempt a takeover of the pods' ownership. That requires privileged
hooks (for example Xposed in LibrePods) and is intentionally out of scope. As a result,
the pods must remain connected to the Android phone, and some multi-device handoff
behavior may interrupt the stream.

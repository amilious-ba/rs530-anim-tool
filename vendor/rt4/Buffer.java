// Reference copy from the Amilious / RT4 client (rt4.Buffer / client!wa).
// NOT compiled by this project. JagString / Node / RSA / XTEA / pool methods omitted.
// Kotlin port of the methods we call: src/main/kotlin/rs530anim/model/Rs2Buffer.kt
//
// Verified against the pasted source:
//   g1      = data[offset++] & 0xFF
//   g2      = ((b0 & 0xFF) << 8) + (b1 & 0xFF)
//   g2b     = g2, then if > 32767 subtract 0x10000
//   gsmart  = peek < 128 ? g1() - 64 : g2() - 0xC000     // -16384..16383
//   gsmarts = peek >= 128 ? g2() - 0x8000 : g1()
//   psmarts = value < 128 ? p1(value) : p2(value + 0x8000)

package rt4;

public class Buffer {
    public byte[] data;
    public int offset;

    public Buffer(byte[] src) {
        this.data = src;
        this.offset = 0;
    }

    public final int g1() {
        return this.data[this.offset++] & 0xFF;
    }

    public final byte g1b() {
        return this.data[this.offset++];
    }

    public final int g2() {
        this.offset += 2;
        return ((this.data[this.offset - 2] & 0xFF) << 8) + (this.data[this.offset - 1] & 0xFF);
    }

    public final int g2b() {
        this.offset += 2;
        int value = ((this.data[this.offset - 2] & 0xFF) << 8) + (this.data[this.offset - 1] & 0xFF);
        if (value > 32767) {
            value -= 0x10000;
        }
        return value;
    }

    public final int gsmart() {
        int value = this.data[this.offset] & 0xFF;
        return value < 128 ? this.g1() - 64 : this.g2() - 0xc000;
    }

    public final int gsmarts() {
        int value = this.data[this.offset] & 0xFF;
        return value >= 128 ? this.g2() - 0x8000 : this.g1();
    }

    public final void p1(int value) {
        this.data[this.offset++] = (byte) value;
    }

    public final void p2(int value) {
        this.data[this.offset++] = (byte) (value >> 8);
        this.data[this.offset++] = (byte) value;
    }

    public final void psmarts(int value) {
        if (value >= 0 && value < 128) {
            this.p1(value);
        } else if (value >= 0 && value < 0x8000) {
            this.p2(value + 0x8000);
        } else {
            throw new IllegalArgumentException("psmarts out of range: " + value);
        }
    }
}

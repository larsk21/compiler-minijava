/* Base 64 encoder reading from stdin */

class Main {

	public int[] base64;

	public void init() {
		this.base64 = new int[64];
		base64[0] = 65;
		base64[1] = 66;
		base64[2] = 67;
		base64[3] = 68;
		base64[4] = 69;
		base64[5] = 70;
		base64[6] = 71;
		base64[7] = 72;
		base64[8] = 73;
		base64[9] = 74;
		base64[10] = 75;
		base64[11] = 76;
		base64[12] = 77;
		base64[13] = 78;
		base64[14] = 79;
		base64[15] = 80;
		base64[16] = 81;
		base64[17] = 82;
		base64[18] = 83;
		base64[19] = 84;
		base64[20] = 85;
		base64[21] = 86;
		base64[22] = 87;
		base64[23] = 88;
		base64[24] = 89;
		base64[25] = 90;
		base64[26] = 97;
		base64[27] = 98;
		base64[28] = 99;
		base64[29] = 100;
		base64[30] = 101;
		base64[31] = 102;
		base64[32] = 103;
		base64[33] = 104;
		base64[34] = 105;
		base64[35] = 106;
		base64[36] = 107;
		base64[37] = 108;
		base64[38] = 109;
		base64[39] = 110;
		base64[40] = 111;
		base64[41] = 112;
		base64[42] = 113;
		base64[43] = 114;
		base64[44] = 115;
		base64[45] = 116;
		base64[46] = 117;
		base64[47] = 118;
		base64[48] = 119;
		base64[49] = 120;
		base64[50] = 121;
		base64[51] = 122;
		base64[52] = 48;
		base64[53] = 49;
		base64[54] = 50;
		base64[55] = 51;
		base64[56] = 52;
		base64[57] = 53;
		base64[58] = 54;
		base64[59] = 55;
		base64[60] = 56;
		base64[61] = 57;
		base64[62] = 43;
		base64[63] = 47;
	}

	public void printFromBuf(Buffer buf) {
		CharResult result = buf.getFourChars();
		if (result.paddingLen == 3) {
			System.out.write(10);
			System.out.flush();
			return;
		}

		int i = 0;
		while (i < 4 - result.paddingLen) {
			System.out.write(base64[result.chars[i]]);
			i=i+1;
		}
		if (result.endReached) {
			int j = 0;
			while (j < result.paddingLen) {
				System.out.write(61);
				j=j+1;
			}
			System.out.write(10);
			System.out.flush();
		}
	}

	public void run() throws Exception {
		int input;
		Buffer buf = new Buffer();
		buf.init();

		int counter = 0;

		while ((input = System.in.read()) != -1) {
			buf.pushByte(input);
			counter=counter+1;
				
			if (counter == 3) {
				printFromBuf(buf);
				counter = 0;
			}
		}
		printFromBuf(buf);
	}

	public static void main(String[] args) throws Exception {
		Main main = new Main();
		main.init();
		main.run();
	}
}

class Buffer {

	public ByteArray data;
	public int firstIdx;
	public int freeIdx;

	public Operations op;
	public int[] patterns;

	public void init() {
		this.patterns = new int[4];
		patterns[0] = -67108864;
		patterns[1] = 66060288;
		patterns[2] = 1032192;
		patterns[3] = 16128;
		this.op = new Operations();
		op.init();

		data = new ByteArray(); data.init(12);
		firstIdx = 0;
		freeIdx = 0;
	}

	/*public void printArr(int[] arr) {
		String output = "[";
		for (int entry : arr) { 
			output = output + bin(entry);
			output = output + ",";
		}
		output = output + "]";
		System.out.println(output);
	}

	public String bin(int input) {
		return String.format("%32s", Integer.toBinaryString(input)).replace(' ', '0');
	}*/

	public void pushByte(int byt) {
		if (freeIdx == data.length) {
			data.shiftLeft(firstIdx);	
			freeIdx = freeIdx - firstIdx;
			firstIdx = 0;
		} else if (freeIdx > data.length) {
			/* System.out.println("Error: Free index exceeds Buffer length!!!");*/
			return;
		}

		data.set(freeIdx, byt);
		freeIdx=freeIdx+1;
	}

	public CharResult getFourChars() {
		int contiguous = 0;
		int byt = op.shl_i(data.get(firstIdx), 24);
		contiguous = op.or_i(contiguous, byt);
		byt = op.shl_i(data.get(firstIdx+1), 16);
		contiguous = op.or_i(contiguous, byt);
		byt = op.shl_i(data.get(firstIdx+2), 8);
		contiguous = op.or_i(contiguous, byt);
		

		firstIdx = firstIdx + 3;

		int[] chars = new int[4];
		chars[0] = op.shr_i(op.and_i(patterns[0], contiguous), 26);
		chars[1] = op.shr_i(op.and_i(patterns[1], contiguous), 20);
		chars[2] = op.shr_i(op.and_i(patterns[2], contiguous), 14);
		chars[3] = op.shr_i(op.and_i(patterns[3], contiguous), 8);

		CharResult result = new CharResult();

		if (firstIdx > freeIdx) {
			result.init(chars, true, firstIdx - freeIdx);
		} else {
			result.init(chars, false, 0);
		}

		return result;
	}
}

class CharResult {
	
	public int[] chars;
	public boolean endReached;
	public int paddingLen;

	public void init(int[] chars, boolean endReached, int paddingLen) {
		this.chars = chars;
		this.endReached = endReached;
		this.paddingLen = paddingLen;
	}
}

class ByteArray {
	
	public Operations op;
	public int[] storage;
	public int numPadding;
	public int length;

	/*public void printArr(int[] arr) {
		String output = "[";
		for (int entry : arr) { 
			output = output + bin(entry);
			output = output + ",";
		}
		output = output + "]";
		System.out.println(output);
	}

	public String bin(int input) {
		return String.format("%32s", Integer.toBinaryString(input)).replace(' ', '0');
	}*/

	public void init(int sizeInBytes) {
		this.numPadding = 4 - (sizeInBytes % 4);
		int numInts = sizeInBytes / 4;
		if (numPadding > 0) {
			numInts=numInts+1;
		}
		this.storage = new int[numInts];
		this.length = sizeInBytes;
		this.op = new Operations();
		op.init();
	}

	public int get(int byteIndex) {
		int index = byteIndex / 4;
		int bitOffset = (byteIndex % 4) * 8;

		int mask = op.shl_i(255, bitOffset);
		int byt = op.and_i(storage[index], mask);
		return op.shr_i(byt, bitOffset);
	}

	public void set(int byteIndex, int value) {
		int index = byteIndex / 4;
		int bitOffset = (byteIndex % 4) * 8;

		int byt = op.shl_i(value, bitOffset);
		storage[index] = op.or_i(storage[index], byt);
	}
	
	public void shiftLeft(int numBytes) {
		this.storage = op.shl(storage, length, numBytes * 8);	
	}
}

class Operations {

	public int[] powers;

	public void init() {
		powers = new int[32];
		powers[0] = 1;
		powers[1] = 2;
		powers[2] = 4;
		powers[3] = 8;
		powers[4] = 16;
		powers[5] = 32;
		powers[6] = 64;
		powers[7] = 128;
		powers[8] = 256;
		powers[9] = 512;
		powers[10] = 1024;
		powers[11] = 2048;
		powers[12] = 4096;
		powers[13] = 8192;
		powers[14] = 16384;
		powers[15] = 32768;
		powers[16] = 65536;
		powers[17] = 131072;
		powers[18] = 262144;
		powers[19] = 524288;
		powers[20] = 1048576;
		powers[21] = 2097152;
		powers[22] = 4194304;
		powers[23] = 8388608;
		powers[24] = 16777216;
		powers[25] = 33554432;
		powers[26] = 67108864;
		powers[27] = 134217728;
		powers[28] = 268435456;
		powers[29] = 536870912;
		powers[30] = 1073741824;
		powers[31] =-2147483648;
	}

	public int pow(int base, int exponent) {
		int result = 1;
		int i = 0;
		while (i < exponent) {
			result = result * base;
			i=i+1;
		}
		return result;
	}

	public int not_i(int a) {
		return -1 - a;
	}

	public int shr_i(int a, int b) {
		if (b > 31) {
			return 0;
		}
		if (a < 0) {
			a = a + powers[31];
			a = a / powers[b];
			a = a + powers[31 - b];
			return a;
		} else {
			return a / powers[b];
		}
	}

	public int shl_i(int a, int b) {
		if (b > 31) {
			return 0;
		}
		return a * powers[b];
	}

	public int and_i(int a, int b) {
		int c = 0;
		int x = 0;
		while (x <= 31) {
			c = c + c;
			if (a < 0) {
				if (b < 0) {
					c = c + 1;
				}
			}
			a = a + a;
			b = b + b;
			x=x+1;
		}
		return c;
	}

	public int or_i(int a, int b) {
		int c = 0;
		int x = 0;
		while (x <= 31) {
			c = c + c;
			if (a < 0) {
				c = c + 1;
			} else if (b < 0) {
				c = c + 1;
			}
			a = a + a;
			b = b + b;
			x=x+1;
		}
		return c;
	}

	public int xor_i(int a, int b) {
		int c = 0;
		int x = 0;
		while (x <= 31) {
			c = c + c;
			if (a < 0) {
				if (b >= 0) {
					c = c + 1;
				}
			} else if (b < 0) {
				c = c + 1;
			}
			a = a + a;
			b = b + b;
			x=x+1;
		}
		return c;
	}

	public int[] shl(int[] a, int length, int b) {
		int[] result = new int[length];
		if (b >= length) {
			return result;
		}
		int new_carry = 0;
		int old_carry = 0;
		int i = length-1;
		while (i >= 0) {
			new_carry = shr_i(a[i], 32 - b);

			result[i] = shl_i(a[i], b);
			result[i] = or_i(result[i], old_carry);
			old_carry = new_carry;
			i=i-1;
		}
		return result;
	}

	public int[] shr(int[] a, int length, int b) {
		int[] result = new int[length];
		int new_carry = 0;
		int old_carry = 0;
		int i = length-1;
		while (i >= 0) {
			new_carry = shl_i(and_i(a[i], powers[b]-1), 32-b);

			result[i] = shr_i(a[i], b);
			result[i] = or_i(result[i], old_carry);
			old_carry = new_carry;
			i=i-1;
		}
		return result;
	}

	public int[] and(int[] a, int length, int[] b) {
		int[] result = new int[length];
		int i = 0;
		while (i < length) {
			result[i] = and_i(a[i], b[i]);
			i=i+1;
		}
		return result;
	}

	public int[] or(int[] a, int length, int[] b) {
		int[] result = new int[length];
		int i = 0;
		while (i < length) {
			result[i] = or_i(a[i], b[i]);
			i=i+1;
		}
		return result;
	}

	public int[] not(int[] a, int length) {
		int[] result = new int[length];
		int i = 0;
		while (i < length) {
			result[i] = not_i(a[i]);
			i=i+1;
		}
		return result;
	}

	public int[] xor(int[] a, int length, int[] b) {
		int[] result = new int[length];
		int i = 0;
		while (i < length) {
			result[i] = xor_i(a[i], b[i]);
			i=i+1;
		}
		return result;
	}
}

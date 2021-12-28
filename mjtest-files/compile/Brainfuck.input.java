/*
Brainfuck [1] interpreter.
Reads program from STDIN optionally followed by a 0 byte and additional input.
Try for example program [2].

[1]: https://esolangs.org/wiki/Brainfuck
[2]: https://raw.githubusercontent.com/libfirm/jFirm/master/bf_examples/bockbeer.bf
*/
class Brainfuck {
    public static void main(String[] args) throws Exception {
        Brainfuck bf = new Brainfuck().init();
        bf.read();
        bf.run();
    }

    public int insts_cap;
    public int insts_len;
    public int[] insts;

    public Brainfuck init() {
        this.insts = new int[1024];
        this.insts_cap = 1024;
        this.insts_len = 0;
        return this;
    }

    public void run() throws Exception {
        int[] mem = new int[30000];
        /* {Inst,Mem} Pointer */
        int ip = 0;
        int mp = 0;

        while (ip < insts_len) {
            if (insts[ip] == 43) {
                mem[mp] = mem[mp] + 1;
            }
            if (insts[ip] == 45) {
                mem[mp] = mem[mp] - 1;
            }
            if (insts[ip] == 60) {
                mp = mp - 1;
            }
            if (insts[ip] == 62) {
                mp = mp + 1;
            }
            if (insts[ip] == 44) {
                mem[mp] = System.in.read();
            }
            if (insts[ip] == 46) {
                System.out.write(mem[mp]);
            }
            if (insts[ip] == 91 && mem[mp] == 0) {
                /* Read forward until matching ] */
                int lvl = 0;
                while (!(insts[ip = ip + 1] == 93 && lvl == 0)) {
                    if (insts[ip] == 91) lvl = lvl + 1;
                    if (insts[ip] == 93) lvl = lvl - 1;
                }
            }
            if (insts[ip] == 93 && mem[mp] != 0) {
                /* Read backward until matching [ */
                int lvl = 0;
                while (!(insts[ip = ip - 1] == 91 && lvl == 0)) {
                    if (insts[ip] == 93) lvl = lvl + 1;
                    if (insts[ip] == 91) lvl = lvl - 1;
                }
            }
            ip = ip + 1;
        }
        System.out.flush();
    }

    /* Read instructions until either EOF or NUL is encountered. */
    public void read() throws Exception {
        while (readChar()) {}
    }

    public boolean readChar() throws Exception {
        int inp = System.in.read();

        /* EOF or NUL: stop reading */
        if (inp == -1 || inp == 0) {
            return false;
        }

        /* Valid instruction: Add to array */
        /*  +,-.                        <            >            [            ] */
        if ((inp >= 43 && inp <= 46) || inp == 60 || inp == 62 || inp == 91 || inp == 93) {
            if (insts_len == insts_cap) {
                insts_cap = insts_cap * 2;
                int[] new_insts = new int[insts_cap];
                int i = 0;
                while (i < insts_len) {
                    new_insts[i] = insts[i];
                    i = i + 1;
                }
                insts = new_insts;
            }

            insts[insts_len] = inp;
            insts_len = insts_len + 1;
        }

        /* Invalid instruction: Ignore, keep reading */
        return true;
    }
}

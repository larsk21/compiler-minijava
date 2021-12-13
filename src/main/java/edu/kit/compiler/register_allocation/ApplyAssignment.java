package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.InstructionType;
import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import edu.kit.compiler.logger.Logger;
import lombok.Getter;

import java.util.*;

/**
 * For an already calculated assignment of vRegisters to concrete registers or stack slots,
 * this class is responsible for actually generating the concrete instructions that
 * implement the assignment.
 *
 * Additionally, it uses lifetime information of the vRegisters and an internal state
 * tracker to verify the validity of the input assignment (at construction and at the
 * different steps of instruction creation).
 */
public class ApplyAssignment {
    private RegisterAssignment[] assignment;
    private RegisterSize[] sizes;
    private Lifetime[] lifetimes;
    private List<Instruction> ir;
    private CallingConvention cconv;
    private List<String> result;
    private Optional<Deque<Register>> savedRegisters;

    public ApplyAssignment(RegisterAssignment[] assignment, RegisterSize[] sizes,
                           Lifetime[] lifetimes, List<Instruction> ir, CallingConvention cconv) {
        assert assignment.length == sizes.length && assignment.length == lifetimes.length;
        this.assignment = assignment;
        this.sizes = sizes;
        this.lifetimes = lifetimes;
        this.ir = ir;
        this.cconv = cconv;
        this.result = new ArrayList<>();
        this.savedRegisters = Optional.empty();

        assertRegistersDontInterfere(assignment, sizes, lifetimes);
    }

    public ApplyAssignment(RegisterAssignment[] assignment, RegisterSize[] sizes,
                           Lifetime[] lifetimes, List<Instruction> ir) {
        this(assignment, sizes, lifetimes, ir, CallingConvention.X86_64);
    }

    /**
     * Assumes that all register lifetimes interfere.
     */
    public ApplyAssignment(RegisterAssignment[] assignment, RegisterSize[] sizes, List<Instruction> ir) {
        this(assignment, sizes, completeLifetimes(assignment.length, ir.size()), ir);
    }

    public AssignmentResult doApply() {
        result = new ArrayList<>();
        LifetimeTracker tracker = new LifetimeTracker();
        Map<Integer, String> replace = new HashMap<>();

        for (int i = 0; i < ir.size(); i++) {
            replace.clear();
            switch (ir.get(i).getType()) {
                case GENERAL -> handleGeneralInstruction(tracker, replace, i);
                case DIV, MOD -> handleDivOrMod(tracker, i);
                case CALL -> handleCall(tracker, i);
                default -> throw new UnsupportedOperationException();
            }
        }
        tracker.assertFinallyEmpty(ir.size());

        return new AssignmentResult(result, tracker.getRegisters().getUsedRegisters());
    }

    private void handleGeneralInstruction(LifetimeTracker tracker, Map<Integer, String> replace, int index) {
        Instruction instr = ir.get(index);
        assert instr.getType() == InstructionType.GENERAL;
        tracker.enterInstruction(index);
        List<Register> tmpRegisters = tracker.getTmpRegisters(countRequiredTmps(instr));

        // handle spilled input registers and set names
        int tmpIdx = 0;
        for (int vRegister: instr.getInputRegisters()) {
            RegisterSize size = sizes[vRegister];
            String registerName;
            if (assignment[vRegister].isSpilled()) {
                int stackSlot = assignment[vRegister].getStackSlot().get();
                registerName = tmpRegisters.get(tmpIdx).asSize(size);
                output("mov%c %d(%%rbp), %s # reload for @%s",
                        size.getSuffix(), stackSlot, registerName, vRegister);
                tmpIdx++;
            } else {
                Register r = assignment[vRegister].getRegister().get();
                tracker.assertMapping(vRegister, r);
                registerName = r.asSize(size);
            }
            replace.put(vRegister, registerName);
        }

        if (!instr.getTargetRegister().isPresent()) {
            // output the instruction itself
            output(instr.mapRegisters(replace));
            tracker.leaveInstruction(index);
        } else {
            int target = instr.getTargetRegister().get();
            RegisterSize size = sizes[target];
            Register tRegister;
            if (assignment[target].isSpilled()) {
                tRegister = tmpRegisters.get(tmpRegisters.size() - 1);
            } else {
                tRegister = assignment[target].getRegister().get();
            }
            String targetName = tRegister.asSize(size);
            replace.put(target, targetName);

            // handle the overwrite register
            if (instr.getOverwriteRegister().isPresent()) {
                int overwrite = instr.getOverwriteRegister().get();
                if (assignment[overwrite].isSpilled()) {
                    int stackSlot = assignment[overwrite].getStackSlot().get();
                    output("mov%c %d(%%rbp), %s # reload for @%s [overwrite]",
                            size.getSuffix(), stackSlot, targetName, overwrite);
                } else {
                    Register ovRegister = assignment[overwrite].getRegister().get();
                    if (ovRegister != tRegister) {
                        String ovName = assignment[overwrite].getRegister().get().asSize(size);
                        output("mov %s, %s # move for @%s [overwrite]",
                                ovName, targetName, overwrite);
                    }
                }
            }

            // output the instruction itself
            output(instr.mapRegisters(replace));
            tracker.leaveInstruction(index);

            // possibly spill the target register
            if (assignment[target].isSpilled()) {
                int stackSlot = assignment[target].getStackSlot().get();
                output("mov%c %s, %d(%%rbp) # spill for @%s",
                        size.getSuffix(), targetName, stackSlot, target);
            } else {
                tracker.assertMapping(target, assignment[target].getRegister().get());
            }
        }
    }

    private void handleDivOrMod(LifetimeTracker tracker, int index) {
        Instruction instr = ir.get(index);
        int dividend = instr.getInputRegisters().get(0);
        int divisor = instr.getInputRegisters().get(1);
        int target = instr.getTargetRegister().get();
        assert sizes[dividend] == RegisterSize.DOUBLE && sizes[divisor] == RegisterSize.DOUBLE &&
                sizes[target] == RegisterSize.DOUBLE;

        tracker.enterInstruction(index);
        tracker.assertFreeOrEqual(Register.RAX, dividend);
        Register divisorRegister;
        if (!assignment[divisor].isSpilled() && lifetimes[divisor].isLastInstructionAndInput(index)) {
            Register r = assignment[divisor].getRegister().get();
            tracker.assertMapping(divisor, r);
            divisorRegister = r;
        } else {
            divisorRegister = tracker.getDivRegister();
        }

        // move dividend to %rax
        String getDividend = getVRegisterValue(dividend, RegisterSize.DOUBLE);
        output("movslq %s, %%rax # get dividend", getDividend);
        output("cqto # sign extension to octoword");

        // move divisor to temporary register
        String getDivisor = getVRegisterValue(divisor, RegisterSize.DOUBLE);
        output("movslq %s, %s # get divisor", getDivisor, divisorRegister.getAsQuad());

        // output the instruction itself
        output("idivq %s", divisorRegister.getAsQuad());
        tracker.registers.markUsed(Register.RAX);
        tracker.registers.markUsed(Register.RDX);
        tracker.leaveInstruction(index);

        // move the result to the target register
        String getResult = switch (instr.getType()) {
            case DIV -> "%rax";
            case MOD -> "%rdx";
            default -> throw new IllegalStateException();
        };

        if (assignment[target].isSpilled()) {
            output("leal 0(%s), %%eax # get result of division", getResult);
            int stackSlot = assignment[target].getStackSlot().get();
            output("movl %%eax, %d(%%rbp) # spill for @%s", stackSlot, target);
        } else {
            Register r = assignment[target].getRegister().get();
            tracker.assertMapping(target, r);
            output("leal 0(%s), %s # get result of division", getResult, r.getAsDouble());
        }
    }

    private void handleCall(LifetimeTracker tracker, int index) {
        Instruction instr = ir.get(index);
        // TODO: differentiate external function call (-> stack alignment)
        tracker.enterInstruction(index);

        // handle caller-saved registers
        int savedOffset = 0;
        ArrayDeque<Register> saved = new ArrayDeque<>();
        EnumMap<Register, Integer> offsets = new EnumMap<>(Register.class);
        for (Register r: cconv.getCallerSaved()) {
            if (!tracker.getRegisters().isFree(r)) {
                // TODO: input registers that don't survive the call?!
                savedOffset += 8;
                offsets.put(r, savedOffset);
                saved.push(r);
                output("pushq %s # push caller-saved register", r.getAsQuad());
            }
        }

        // handle args
        int numArgsOnStack = 0;
        List<Integer> args = instr.getInputRegisters();
        for (int i = 0; i < args.size(); i++) {
            int vRegister = args.get(i);
            if (cconv.getArgRegister(i).isPresent()) {
                // the argument is passed within a register
                Register argReg = cconv.getArgRegister(i).get();
                if (assignment[vRegister].isSpilled()) {
                    int stackSlot = assignment[vRegister].getStackSlot().get();
                    RegisterSize size = sizes[vRegister];
                    output("mov%c %d(%%rbp), %s # load @%d as arg %d",
                            size.getSuffix(), stackSlot, argReg.asSize(size), vRegister, i);
                } else {
                    Register r = assignment[vRegister].getRegister().get();
                    tracker.assertMapping(vRegister, r);
                    if (cconv.isCallerSaved(r)) {
                        // load value from stack
                        int offset = savedOffset - offsets.get(r);
                        output("movq %d(%%rsp), %s # reload @%d as arg %d",
                                offset, argReg.getAsQuad(), vRegister, i);
                    } else {
                        assert argReg != r;
                        output("mov %s, %s # move @%d into arg %d",
                                r.getAsQuad(), argReg.getAsQuad(), vRegister, i);
                    }
                }
            } else {
                // the argument is passed on the stack
                numArgsOnStack++;
                if (assignment[vRegister].isSpilled()) {
                    int stackSlot = assignment[vRegister].getStackSlot().get();
                    RegisterSize size = sizes[vRegister];
                    // to ensure that we read with correct size, we first move into a register
                    output("mov%c %d(%%rbp), %s # reload @%d ...",
                            size.getSuffix(), stackSlot, cconv.getReturnRegister().getAsQuad(), vRegister);
                    output("pushq %s # ... and pass it as arg %d",
                            cconv.getReturnRegister().getAsQuad(), i);
                } else {
                    Register r = assignment[vRegister].getRegister().get();
                    tracker.assertMapping(vRegister, r);
                    if (cconv.isCallerSaved(r)) {
                        // load value from stack
                        int offset = savedOffset - offsets.get(r);
                        output("pushq %d(%%rsp) # reload @%d as arg %d", offset, vRegister, i);
                    } else {
                        output("pushq %s # pass @%d as arg %d", r.getAsQuad(), vRegister, i);
                    }
                }
            }
        }

        // output the instruction itself
        output("call %s", instr.getCallReference().get());
        tracker.leaveInstruction(index);

        // remove arguments
        if (numArgsOnStack > 0) {
            output("addq $%d, %%rsp # remove args from stack", 8 * numArgsOnStack);
        }

        // TODO: handle external calls here
        // restore caller-saved registers
        while (!saved.isEmpty()) {
            Register r = saved.pop();
            output("popq %s # restore caller-saved register", r.getAsQuad());
        }

        // read return value
        if (instr.getTargetRegister().isPresent()) {
            int target = instr.getTargetRegister().get();
            // TODO: with tmp pass-through: set tmp in return register
            if (assignment[target].isSpilled()) {
                int stackSlot = assignment[target].getStackSlot().get();
                RegisterSize size = sizes[target];
                output("mov%c %s, %d(%%rbp) # spill return value for @%s",
                        size.getSuffix(), cconv.getReturnRegister().asSize(size), stackSlot, target);
            } else {
                Register r = assignment[target].getRegister().get();
                tracker.assertMapping(target, r);
                if (cconv.getReturnRegister() != r) {
                    output("mov %s, %s # move return value into @%s",
                            cconv.getReturnRegister().getAsQuad(), r.getAsQuad(), target);
                }
            }
        }
    }

    /**
     * Can only be called after applying the register allocation,
     * because the used registers must be known.
     */
    public List<String> createFunctionProlog(int nArgs, EnumSet<Register> usedRegisters) {
        this.savedRegisters = Optional.of(new ArrayDeque<>());
        this.result = new ArrayList<>();

        output("pushq %rbp");
        output("movq %rsp, %rbp");

        // allocate activation record
        output("subq $%d, %%rsp # allocate activation record", calculateActivationRecordSize());

        // save registers
        for (Register r: usedRegisters) {
            if (!cconv.isCallerSaved(r)) {
                savedRegisters.get().push(r);
                output("pushq %s # push callee-saved register", r.getAsQuad());
            }
        }

        // initialize vRegisters that are function arguments
        for (int vRegister = 0; vRegister < nArgs; vRegister++) {
            RegisterSize size = sizes[vRegister];
            String toVRegister = getVRegisterValue(vRegister, size);
            if (cconv.getArgRegister(vRegister).isPresent()) {
                // the argument is passed within a register
                Register argReg = cconv.getArgRegister(vRegister).get();
                if (assignment[vRegister].isSpilled() || assignment[vRegister].getRegister().get() != argReg) {
                    output("mov%c %s, %s # initialize @%d from arg",
                            size.getSuffix(), argReg.asSize(size), toVRegister, vRegister);
                }
            } else {
                // the argument is passed on the stack
                // TODO: special case for spilled registers?
                int offset = 16 + 8 * (nArgs - vRegister - 1);
                output("mov%c %d(%%rbp), %s # initialize @%d from arg",
                        size.getSuffix(), offset, toVRegister, vRegister);
            }
        }
        return result;
    }

    /**
     * Can only be called after the prolog is already created.
     */
    public List<String> createFunctionEpilog() {
        // TODO: create as separate block with special label
        assert savedRegisters.isPresent();
        this.result = new ArrayList<>();

        // restore registers
        while (!savedRegisters.get().isEmpty()) {
            Register r = savedRegisters.get().pop();
            output("popq %s # restore callee-saved register", r.getAsQuad());
        }

        output("leave");
        output("ret");
        return result;
    }

    private int countRequiredTmps(Instruction instr) {
        assert instr.getType() == InstructionType.GENERAL;
        int n = 0;
        for (int vRegister: instr.getInputRegisters()) {
            if (assignment[vRegister].isSpilled()) {
                n++;
            }
        }
        if (instr.getTargetRegister().isPresent() &&
                assignment[instr.getTargetRegister().get()].isSpilled()) {
            n++;
        }
        return n;
    }

    private String getVRegisterValue(int vRegister, RegisterSize size) {
        if (assignment[vRegister].isSpilled()) {
            int stackSlot = assignment[vRegister].getStackSlot().get();
            return String.format("%d(%%rbp)", stackSlot);
        } else {
            Register r = assignment[vRegister].getRegister().get();
            return r.asSize(size);
        }
    }

    private int calculateActivationRecordSize() {
        int stackSize = 0;
        for (RegisterAssignment ra: assignment) {
            if (ra.isSpilled()) {
                stackSize = Math.max(stackSize, -ra.getStackSlot().get());
            }
        }

        // align to 8 byte
        int sizeOld = stackSize;
        stackSize += 7;
        stackSize = stackSize - (stackSize % 8);
        assert stackSize >= sizeOld && stackSize <= sizeOld + 8 && stackSize % 8 == 0;
        return stackSize;
    }

    private void output(String instr) {
        result.add(instr);
    }

    private void output(String format, Object... args) {
        result.add(String.format(format, args));
    }

    // debugging
    public void testRun(Logger logger) {
        LifetimeTracker tracker = new LifetimeTracker();
        tracker.printState(logger);
        for (int i = 0; i < ir.size(); i++) {
            tracker.enterInstruction(i);
            tracker.printState(logger);
            logger.debug("%d: %s", i, ir.get(i));
            tracker.leaveInstruction(i);
            tracker.printState(logger);
        }
    }

    private static void assertRegistersDontInterfere(RegisterAssignment[] assignment,
                                                     RegisterSize[] sizes, Lifetime[] lifetimes) {
        for(int i = 0; i < lifetimes.length; i++) {
            for(int j = i + 1; j < lifetimes.length; j++) {
                if (lifetimes[i].interferes(lifetimes[j])) {
                    if (!assignment[i].isSpilled() && !assignment[j].isSpilled()) {
                        assert assignment[i].getRegister().get() != assignment[j].getRegister().get();
                    } else if (assignment[i].isSpilled() && assignment[j].isSpilled()) {
                        int slotA = assignment[i].getStackSlot().get();
                        int slotB = assignment[j].getStackSlot().get();
                        // assert that stack slots are disjoint
                        assert slotA >= slotB + sizes[j].getBytes() || slotB >= slotA + sizes[i].getBytes();
                    }
                }
            }
        }
    }

    /**
     * Returns lifetimes that contain all instructions.
     */
    private static Lifetime[] completeLifetimes(int nRegisters, int nInstructions) {
        Lifetime[] lifetimes = new Lifetime[nRegisters];
        for(int i = 0; i < nRegisters; i++) {
            lifetimes[i] = new Lifetime(-1, nInstructions, false);
        }
        return  lifetimes;
    }

    /**
     * Tracks the mapping of hardware registers to virtual registers during the
     * allocation in order to correctly provide free registers when needed
     */
    private class LifetimeTracker {
        /**
         * directly modifying `registers` should be avoided
         */
        @Getter
        private RegisterTracker registers;
        private List<Integer> lifetimeStarts;
        private List<Integer> lifetimeEnds;

        // prevents erroneously requesting temporary registers twice
        private boolean tmpRequested;

        LifetimeTracker() {
            this.registers = new RegisterTracker();
            this.lifetimeStarts = sortByLifetime((l1, l2) -> l1.getBegin() - l2.getBegin());
            this.lifetimeEnds = sortByLifetime((l1, l2) -> {
                int val = l1.getEnd() - l2.getEnd();
                if (val != 0) {
                    return val;
                }
                if (l1.isLastInstrIsInput() && !l2.isLastInstrIsInput()) {
                    return -1;
                } else if (!l1.isLastInstrIsInput() && l2.isLastInstrIsInput()) {
                    return 1;
                }
                return 0;
            });
            this.tmpRequested = false;
            while (!lifetimeStarts.isEmpty() && lifetimes[peek(lifetimeStarts)].getBegin() < 0) {
                setRegister(pop(lifetimeStarts));
            }
        }

        /**
         * Temporary register do _not_ outlive the execution of an original instruction
         * (unless special care is taken).
         */
        public List<Register> getTmpRegisters(int num) {
            assert !tmpRequested;
            tmpRequested = true;
            return registers.getFreeRegisters(num);
        }

        public Register getDivRegister() {
            assert !tmpRequested;
            tmpRequested = true;
            return registers.getFreeRegisters(1, RegisterPreference.PREFER_CALLEE_SAVED_NO_DIV).get(0);
        }

        /**
         * sets the state before the instruction is executed
         */
        public void enterInstruction(int index) {
            assert lifetimeStarts.isEmpty() || lifetimes[peek(lifetimeStarts)].getBegin() >= index;

            tmpRequested = false;
            while (!lifetimeEnds.isEmpty() && lifetimes[peek(lifetimeEnds)].getEnd() <= index) {
                clearRegister(pop(lifetimeEnds));
            }
        }

        /**
         * sets the state after the instruction is executed
         */
        public void leaveInstruction(int index) {
            assert lifetimeEnds.isEmpty() || lifetimes[peek(lifetimeEnds)].getEnd() > index;

            tmpRequested = false;
            while (!lifetimeEnds.isEmpty() && lifetimes[peek(lifetimeEnds)].getEnd() == index + 1 &&
                    // `isLastInstrIsInput` must be checked here, otherwise the state could be invalid at the end of a loop
                    lifetimes[peek(lifetimeEnds)].isLastInstrIsInput()) {
                clearRegister(pop(lifetimeEnds));
            }
            while (!lifetimeStarts.isEmpty() && lifetimes[peek(lifetimeStarts)].getBegin() <= index) {
                setRegister(pop(lifetimeStarts));
            }
        }

        public void assertMapping(int vRegister, Register r) {
            assert registers.get(r).isPresent();
            assert registers.get(r).get() == vRegister;
        }

        public void assertFreeOrEqual(Register r, int expectedVReg) {
            assert registers.isFree(r) || registers.get(r).get() == expectedVReg;
        }

        public void assertFinallyEmpty(int numInstrs) {
            enterInstruction(numInstrs);
            assert registers.isEmpty();
        }

        private List<Integer> sortByLifetime(Comparator<Lifetime> compare) {
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < lifetimes.length; i++) {
                if (!lifetimes[i].isTrivial() && !assignment[i].isSpilled()) {
                    result.add(i);
                }
            }
            // We want to sort in descending order, so we can pop the last elements
            result.sort((j, k) -> compare.compare(lifetimes[k],lifetimes[j]));
            return result;
        }

        private void clearRegister(int vRegister) {
            Register r = assignment[vRegister].getRegister().get();
            registers.clear(r);
        }

        private void setRegister(int vRegister) {
            Register r = assignment[vRegister].getRegister().get();
            registers.set(r, vRegister);
        }

        private int pop(List<Integer> l) {
            return l.remove(l.size()- 1);
        }

        private int peek(List<Integer> l) {
            return l.get(l.size()- 1);
        }

        // debugging
        public void printState(Logger logger) {
            logger.debug("== Register tracking state ==");
            if (!lifetimeStarts.isEmpty()) {
                int vR = peek(lifetimeStarts);
                logger.debug("- starting lifetime for vRegister %d at index %d", vR, lifetimes[vR].getBegin());
            }
            if (!lifetimeEnds.isEmpty()) {
                int vR = peek(lifetimeEnds);
                logger.debug("- ending lifetime for vRegister %d at index %d", vR, lifetimes[vR].getEnd());
            }
            registers.printState(logger);
        }
    }
}

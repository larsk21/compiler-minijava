package edu.kit.compiler.register_allocation;

import edu.kit.compiler.codegen.PermutationSolver;
import edu.kit.compiler.intermediate_lang.*;
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
    public static final String FINAL_BLOCK_LABEL = ".L_final";

    private RegisterAssignment[] assignment;
    private RegisterSize[] sizes;
    private Lifetime[] lifetimes;
    private List<Block> ir;
    private CallingConvention cconv;
    private List<String> result;
    private Optional<Deque<Register>> savedRegisters;
    private int numInstructions;

    /**
     * Creates the whole function body at once.
     *
     * This applies all steps of the assignment by first handling the
     * input instructions and adding a function prolog and epilog afterwards.
     */
    public static List<String> createFunctionBody(RegisterAssignment[] assignment, RegisterSize[] sizes,
                                                  Lifetime[] lifetimes, List<Block> ir, int numInstructions,
                                                  int nArgs, CallingConvention cconv) {
        ApplyAssignment apply = new ApplyAssignment(
                assignment, sizes, lifetimes, ir, numInstructions, cconv
        );
        AssignmentResult result = apply.doApply();
        List<String> output = apply.createFunctionProlog(nArgs, result.getUsedRegisters());
        output.addAll(result.getInstructions());
        output.addAll(apply.createFunctionEpilog());
        return output;
    }

    /**
     * Creates the whole function body at once.
     *
     * This applies all steps of the assignment by first handling the
     * input instructions and adding a function prolog and epilog afterwards.
     */
    public static List<String> createFunctionBody(RegisterAssignment[] assignment, RegisterSize[] sizes,
                                                  Lifetime[] lifetimes, List<Block> ir, int numInstructions,
                                                  int nArgs) {
        return createFunctionBody(assignment, sizes, lifetimes, ir, numInstructions, nArgs, CallingConvention.X86_64);
    }

    public ApplyAssignment(RegisterAssignment[] assignment, RegisterSize[] sizes,
                           Lifetime[] lifetimes, List<Block> ir, int numInstructions,
                           CallingConvention cconv) {
        assert assignment.length == sizes.length && assignment.length == lifetimes.length;
        this.assignment = assignment;
        this.sizes = sizes;
        this.lifetimes = lifetimes;
        this.ir = ir;
        this.cconv = cconv;
        this.result = new ArrayList<>();
        this.savedRegisters = Optional.empty();
        this.numInstructions = numInstructions;

        assertRegistersDontInterfere(assignment, sizes, lifetimes);
    }

    public ApplyAssignment(RegisterAssignment[] assignment, RegisterSize[] sizes,
                           Lifetime[] lifetimes, List<Block> ir, int numInstructions) {
        this(assignment, sizes, lifetimes, ir, numInstructions, CallingConvention.X86_64);
    }

    /**
     * Creates concrete instructions for the given input and the necessary information
     * (used registers) for creating the function prolog and epilog.
     */
    public AssignmentResult doApply() {
        result = new ArrayList<>();
        LifetimeTracker tracker = new LifetimeTracker();
        Map<Integer, String> replace = new HashMap<>();

        int i = 0;
        for (Block b: ir) {
            output(".L%d:", b.getBlockId());
            tracker.getRegisters().clearAllTmps();

            for (Instruction instr: b.getInstructions()) {
                replace.clear();
                switch (instr.getType()) {
                    case GENERAL -> handleGeneralInstruction(tracker, replace, instr, i);
                    case DIV, MOD -> handleDivOrMod(tracker, instr, i);
                    case MOV_S, MOV_U -> handleMov(tracker, instr, i);
                    case CALL -> handleCall(tracker, instr, i);
                    case RET -> handleRet(tracker, instr, i);
                }
                i++;
            }
        }
        assert numInstructions == i;
        tracker.assertFinallyEmpty(i);

        return new AssignmentResult(result, tracker.getRegisters().getUsedRegisters());
    }

    private void handleGeneralInstruction(LifetimeTracker tracker, Map<Integer, String> replace,
                                          Instruction instr, int index) {
        assert instr.getType() == InstructionType.GENERAL;
        tracker.enterInstruction(index);

        List<Register> tmpRegisters = tracker.getTmpRegisters(
                countRequiredTmps(tracker, instr, index),
                determineExcludedTmps(tracker, instr)
        );

        // handle spilled input registers and set names
        int tmpIdx = 0;
        for (int vRegister: instr.getInputRegisters()) {
            RegisterSize size = sizes[vRegister];
            String registerName;
            if (isOnStack(tracker, vRegister)) {
                registerName = tmpRegisters.get(tmpIdx).asSize(size);
                output("mov%c %d(%%rbp), %s # reload for @%s",
                        size.getSuffix(), getStackSlot(vRegister), registerName, vRegister);
                tracker.getRegisters().setTmp(tmpRegisters.get(tmpIdx), vRegister);
                tmpIdx++;
            } else {
                registerName = getRegisterOrTmp(tracker, vRegister).asSize(size);
            }
            replace.put(vRegister, registerName);
        }

        if (instr.getTargetRegister().isEmpty()) {
            // output the instruction itself
            output(instr.mapRegisters(replace));
            tracker.leaveInstruction(index);
        } else {
            int target = instr.getTargetRegister().get();
            RegisterSize size = sizes[target];
            Register tRegister;
            if (isOnStack(tracker, target)) {
                Optional<Register> oRegister = getRegisterToOverwrite(tracker, instr, index);
                if (oRegister.isPresent()) {
                    // we can use the overwrite register as a temporary register for target
                    tRegister = oRegister.get();
                } else {
                    // we need a new temporary register
                    tRegister = tmpRegisters.get(tmpRegisters.size() - 1);
                }
            } else {
                tRegister = getRegisterOrTmp(tracker, target, false);
            }
            String targetName = tRegister.asSize(size);
            replace.put(target, targetName);

            // handle the overwrite register
            if (instr.getOverwriteRegister().isPresent()) {
                Optional<Register> targetRegister = assignment[instr.getTargetRegister().get()].getRegister();
                if (targetRegister.isPresent()) {
                    for (int iRegister: instr.getInputRegisters()) {
                        assert !assignment[iRegister].getRegister().equals(targetRegister);
                    }
                }

                int overwrite = instr.getOverwriteRegister().get();
                replace.put(overwrite, targetName);
                if (isOnStack(tracker, overwrite)) {
                    int stackSlot = assignment[overwrite].getStackSlot().get();
                    output("mov%c %d(%%rbp), %s # reload for @%s [overwrite]",
                            size.getSuffix(), stackSlot, targetName, overwrite);
                } else {
                    Register ovRegister = getRegisterOrTmp(tracker, overwrite);
                    if (ovRegister != tRegister) {
                        output("mov%c %s, %s # move for @%s [overwrite]",
                                size.getSuffix(), ovRegister.asSize(size), targetName, overwrite);
                    }
                }
            }

            // output the instruction itself
            output(instr.mapRegisters(replace));
            tracker.leaveInstruction(index);

            // possibly spill the target register
            if (assignment[target].isSpilled()) {
                output("mov%c %s, %d(%%rbp) # spill for @%s",
                        size.getSuffix(), targetName, getStackSlot(target), target);
                tracker.getRegisters().setTmp(tRegister, target);
            } else {
                tracker.assertMapping(target, getRegister(target));
            }
        }
    }

    private void handleDivOrMod(LifetimeTracker tracker, Instruction instr, int index) {
        int dividend = instr.inputRegister(0);
        int divisor = instr.inputRegister(1);
        int target = instr.getTargetRegister().get();
        assert sizes[dividend] == RegisterSize.QUAD && sizes[divisor] == RegisterSize.QUAD;

        tracker.enterInstruction(index);
        tracker.assertFree(Register.RDX);

        // handle the dividend
        if (assignment[dividend].isSpilled() || getRegister(dividend) != Register.RAX) {
            tracker.assertFree(Register.RAX);
            String getDividend = getVRegisterValue(tracker, dividend, RegisterSize.QUAD);
            output("movq %s, %%rax # get dividend", getDividend);
        } else {
            assert lifetimes[dividend].isLastInstructionAndInput(index);
            tracker.assertMapping(dividend, Register.RAX);
        }

        // handle the divisor
        Register divisorRegister;
        if (assignment[divisor].isSpilled()) {
            // move divisor to temporary register
            divisorRegister = tracker.getDivRegister();
            String getDivisor = getVRegisterValue(tracker, divisor, RegisterSize.QUAD);
            output("movq %s, %s # get divisor", getDivisor, divisorRegister.getAsQuad());
        } else {
            tracker.assertMapping(divisor, getRegister(divisor));
            divisorRegister = getRegister(divisor);
        }

        // output the instruction itself
        output("cqto # sign extension to octoword");
        output("idivq %s", divisorRegister.getAsQuad());
        tracker.registers.markUsed(Register.RAX);
        tracker.registers.markUsed(Register.RDX);
        tracker.getRegisters().clearTmp(Register.RDX);
        tracker.leaveInstruction(index);

        // move the result to the target register
        Register result = switch (instr.getType()) {
            case DIV -> Register.RAX;
            case MOD -> Register.RDX;
            default -> throw new IllegalStateException();
        };

        RegisterSize size = sizes[target];
        if (assignment[target].isSpilled()) {
            output("mov%c %s, %d(%%rbp) # spill for @%s",
                    size.getSuffix(), result.asSize(size), getStackSlot(target), target);
            tracker.getRegisters().setTmp(result, target);
        } else {
            tracker.assertMapping(target, getRegister(target));
            if (result != getRegister(target)) {
                output("mov%c %s, %s # move result to @%s",
                        size.getSuffix(), result.asSize(size), getRegister(target).asSize(size), target);
            }
        }
    }

    private void handleMov(LifetimeTracker tracker, Instruction instr, int index) {
        int source = instr.inputRegister(0);
        int target = instr.getTargetRegister().get();
        tracker.enterInstruction(index);

        boolean isUpcast = sizes[target].getBytes() > sizes[source].getBytes();
        boolean isSignedUpcast = isUpcast && instr.getType() == InstructionType.MOV_S;
        if (!isSignedUpcast && assignment[source].isEquivalent(assignment[target]) ) {
            // eliminate unnecessary move
            tracker.leaveInstruction(index);
            return;
        }

        // downcast uses same size for both operands (a downcast is a plain `movl`)
        RegisterSize sourceSize = isUpcast ? sizes[source] : sizes[target];
        String getSource = getVRegisterValue(tracker, source, sourceSize);
        RegisterSize targetSize = isUpcast && !isSignedUpcast ? sizes[source] : sizes[target];
        String getTarget;
        if (assignment[target].isSpilled()) {
            getTarget = String.format("%d(%%rbp)", getStackSlot(target));
        } else {
            getTarget = getRegister(target).asSize(targetSize);
        }

        String cmd;
        if (isSignedUpcast) {
            // currently, only conversions from double to quad are supported
            assert sourceSize == RegisterSize.DOUBLE && targetSize == RegisterSize.QUAD;
            cmd = "movslq";
        } else {
            cmd = String.format("mov%c", targetSize.getSuffix());
        }
        if (isUpcast && !isSignedUpcast && assignment[target].isSpilled()) {
            // edge case: stack slot needs to be zeroed in case of unsigned upcast
            output("mov%c $0, %s # zero the target slot for upcast",
                    targetSize.getSuffix(), getTarget);
        }

        // output the instruction itself
        if ((isOnStack(tracker, source) || isSignedUpcast) && assignment[target].isSpilled()) {
            Register tmp = tracker.getTmpRegisters(1, Set.of()).get(0);
            output("%s %s, %s # load to temporary...",
                    cmd, getSource, tmp.asSize(targetSize));
            output("mov%c %s, %s # ...and spill to target",
                    targetSize.getSuffix(), tmp.asSize(targetSize), getTarget);
            tracker.getRegisters().setTmp(tmp, target);
        } else {
            output("%s %s, %s", cmd, getSource, getTarget);
            tracker.getRegisters().clearTmp(target);
        }
        tracker.leaveInstruction(index);
    }

    private void handleCall(LifetimeTracker tracker, Instruction instr, int index) {
        tracker.enterInstruction(index);

        // handle caller-saved registers
        int savedOffset = 0;
        ArrayDeque<Register> saved = new ArrayDeque<>();
        for (Register r: cconv.getCallerSaved()) {
            if (!tracker.getRegisters().isFree(r)) {
                int vRegister = tracker.getRegisters().get(r).get();
                if (!lifetimes[vRegister].isLastInstructionAndInput(index)) {
                    savedOffset += 8;
                    saved.push(r);
                    output("pushq %s # push caller-saved register", r.getAsQuad());
                }
            }
        }

        // handle args
        int numArgsOnStack = 0;
        List<Integer> args = instr.getInputRegisters();
        Permuter permuter = new Permuter();
        for (int i = 0; i < args.size(); i++) {
            int vRegister = args.get(i);
            RegisterSize size = sizes[vRegister];

            if (cconv.isPassedInRegister(i)) {
                Register argReg = cconv.getArgRegister(i).get();
                if (isOnStack(tracker, vRegister)) {
                    permuter.stackToRegister("mov%c %d(%%rbp), %s # load @%d as arg %d",
                            size.getSuffix(), getStackSlot(vRegister), argReg.asSize(size), vRegister, i);
                } else {
                    permuter.registerToRegister(getRegisterOrTmp(tracker, vRegister), argReg);
                }
            } else {
                numArgsOnStack++;
                if (isOnStack(tracker, vRegister)) {
                    permuter.stackToStack("mov%c %d(%%rbp), %s # reload @%d ...",
                            size.getSuffix(), getStackSlot(vRegister), cconv.getReturnRegister().asSize(size), vRegister);
                    permuter.stackToStack("pushq %s # ... and pass it as arg %d",
                            cconv.getReturnRegister().getAsQuad(), i);
                } else {
                    permuter.registerToStack("pushq %s # pass @%d as arg %d",
                            getRegisterOrTmp(tracker, vRegister).getAsQuad(), vRegister, i);
                }
            }
        }
        permuter.outputAll(cconv.getReturnRegister(), "assign arg registers");

        // align to 16 byte (only required for external functions, which take all args in registers)
        int alignmentOffset = 0;
        if (numArgsOnStack == 0 && (savedOffset % 16 != 0)) {
            alignmentOffset = 8;
            output("subq $8, %rsp # align stack to 16 byte");
        }

        // output the instruction itself
        output("call %s", instr.getCallReference().get());
        tracker.leaveInstruction(index);
        for (Register r: cconv.getCallerSaved()) {
            tracker.getRegisters().clearTmp(r);
        }

        // remove arguments
        if (numArgsOnStack > 0 || alignmentOffset > 0) {
            output("addq $%d, %%rsp # remove args from stack", 8 * numArgsOnStack + alignmentOffset);
        }

        // read return value
        if (instr.getTargetRegister().isPresent()) {
            int target = instr.getTargetRegister().get();
            RegisterSize size = sizes[target];
            if (assignment[target].isSpilled()) {
                output("mov%c %s, %d(%%rbp) # spill return value for @%s",
                        size.getSuffix(), cconv.getReturnRegister().asSize(size), getStackSlot(target), target);
                tracker.getRegisters().clearTmp(target);
                tracker.getRegisters().setTmp(cconv.getReturnRegister(), target);
            } else {
                tracker.assertMapping(target, getRegister(target));
                if (cconv.getReturnRegister() != getRegister(target)) {
                    output("mov%c %s, %s # move return value into @%s",
                            size.getSuffix(), cconv.getReturnRegister().asSize(size),
                            getRegister(target).asSize(size), target);
                }
            }
        }

        // restore caller-saved registers
        while (!saved.isEmpty()) {
            Register r = saved.pop();
            assert !tracker.getRegisters().isFree(r);
            output("popq %s # restore caller-saved register", r.getAsQuad());
        }
    }

    private void handleRet(LifetimeTracker tracker, Instruction instr, int index) {
        if (!instr.getInputRegisters().isEmpty()) {
            int returnVal = instr.inputRegister(0);
            if (!isAlreadyInRegister(tracker, returnVal, cconv.getReturnRegister())) {
                RegisterSize size = sizes[returnVal];
                String getVal = getVRegisterValue(tracker, returnVal, size);
                output("mov%c %s, %s # set return value",
                        size.getSuffix(), getVal, cconv.getReturnRegister().asSize(size));
            }
        }
        if (index + 1 < numInstructions) {
            output("jmp %s", FINAL_BLOCK_LABEL);
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
        int arSize = calculateActivationRecordSize();
        int totalSize = arSize;
        for (Register r: usedRegisters) {
            if (!cconv.isCallerSaved(r)) {
                totalSize += 8;
            }
        }
        if (totalSize % 16 != 0) {
            // align to 16 byte
            assert (totalSize + 8) % 16 == 0;
            arSize += 8;
        }
        if (arSize > 0) {
            output("subq $%d, %%rsp # allocate activation record", arSize);
        }

        // save registers
        for (Register r: usedRegisters) {
            if (!cconv.isCallerSaved(r)) {
                savedRegisters.get().push(r);
                output("pushq %s # push callee-saved register", r.getAsQuad());
            }
        }

        // initialize all args
        Permuter permuter = new Permuter();
        Set<Register> targets = new HashSet<>();
        for (int vRegister = 0; vRegister < nArgs; vRegister++) {
            if (!lifetimes[vRegister].isTrivial()) {
                RegisterSize size = sizes[vRegister];

                if (cconv.isPassedInRegister(vRegister)) {
                    Register argReg = cconv.getArgRegister(vRegister).get();
                    if (assignment[vRegister].isSpilled()) {
                        permuter.registerToStack("mov%c %s, %d(%%rbp) # initialize @%d from arg",
                                size.getSuffix(), argReg.asSize(size), getStackSlot(vRegister), vRegister);
                    } else {
                        permuter.registerToRegister(argReg, getRegister(vRegister));
                        targets.add(getRegister(vRegister));
                    }
                } else {
                    if (assignment[vRegister].isSpilled()) {
                        int offset = argOffsetOnStack(nArgs, vRegister);
                        if (offset != getStackSlot(vRegister)) {
                            String tmpRegister = cconv.getReturnRegister().asSize(size);
                            permuter.stackToStack("mov%c %d(%%rbp), %s # load to temporary...",
                                    size.getSuffix(), offset, tmpRegister);
                            permuter.stackToStack("mov%c %s, %d(%%rbp) # ...initialize @%d from arg",
                                    size.getSuffix(), tmpRegister, getStackSlot(vRegister), vRegister);
                        }
                    } else {
                        permuter.stackToRegister("mov%c %d(%%rbp), %s # initialize @%d from arg",
                                size.getSuffix(), argOffsetOnStack(nArgs, vRegister),
                                getRegister(vRegister).asSize(size), vRegister);
                    }
                }
            }
        }
        Register freeRegister = null;
        for (Register r: cconv.getCallerSaved()) {
            if (!targets.contains(r)) {
                freeRegister = r;
                break;
            }
        }
        permuter.outputAll(freeRegister, "assign args to registers");

        return result;
    }

    /**
     * Can only be called after the prolog is already created.
     */
    public List<String> createFunctionEpilog() {
        assert savedRegisters.isPresent();
        this.result = new ArrayList<>();

        output(FINAL_BLOCK_LABEL + ":");

        // restore registers
        while (!savedRegisters.get().isEmpty()) {
            Register r = savedRegisters.get().pop();
            output("popq %s # restore callee-saved register", r.getAsQuad());
        }

        output("leave");
        output("ret");
        return result;
    }

    private int countRequiredTmps(LifetimeTracker tracker, Instruction instr, int index) {
        assert instr.getType() == InstructionType.GENERAL;
        int n = 0;
        for (int vRegister: instr.getInputRegisters()) {
            if (assignment[vRegister].isSpilled() &&
                    !tracker.getRegisters().hasTmp(vRegister)) {
                n++;
            }
        }
        if (instr.getTargetRegister().isPresent()) {
            int target = instr.getTargetRegister().get();
            if (assignment[target].isSpilled()
                    // we already have a tmp register for target
                    && !tracker.getRegisters().hasTmp(target)
                    // we can already use the overwrite register
                    && !getRegisterToOverwrite(tracker, instr, index).isPresent()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Calculates registers that can not be used as new temporary registers for the current
     * instruction because they are already used otherwise.
     */
    private Set<Register> determineExcludedTmps(LifetimeTracker tracker, Instruction instr) {
        Set<Register> excluded = new HashSet<>();
        if (instr.getOverwriteRegister().isPresent()) {
            int target = instr.getTargetRegister().get();
            int overwrite = instr.getOverwriteRegister().get();
            assignment[target].getRegister().ifPresent(excluded::add);
            tracker.getRegisters().getTmp(target).ifPresent(excluded::add);
            tracker.getRegisters().getTmp(overwrite).ifPresent(excluded::add);
        }
        for (int vRegister: instr.getInputRegisters()) {
            tracker.getRegisters().getTmp(vRegister).ifPresent(excluded::add);
        }
        return excluded;
    }

    /**
     * Tries to determine whether the overwrite register of the current instruction can be reused as
     * a temporary register for the target and returns the according register, if available.
     */
    private Optional<Register> getRegisterToOverwrite(LifetimeTracker tracker, Instruction instr, int index) {
        Optional<Integer> overwrite = instr.getOverwriteRegister();
        if (overwrite.isPresent() && lifetimes[overwrite.get()].isLastInstructionAndInput(index)
                && !isOnStack(tracker, overwrite.get())) {
            return Optional.of(getRegisterOrTmp(tracker, overwrite.get()));
        }
        return Optional.empty();
    }

    private String getVRegisterValue(LifetimeTracker tracker, int vRegister, RegisterSize size) {
        if (isOnStack(tracker, vRegister)) {
            return String.format("%d(%%rbp)", getStackSlot(vRegister));
        } else {
            return getRegisterOrTmp(tracker, vRegister).asSize(size);
        }
    }

    private boolean isOnStack(LifetimeTracker tracker, int vRegister) {
        return assignment[vRegister].isSpilled() && !tracker.getRegisters().hasTmp(vRegister);
    }

    private Register getRegisterOrTmp(LifetimeTracker tracker, int vRegister) {
        return getRegisterOrTmp(tracker, vRegister, true);
    }

    private Register getRegisterOrTmp(LifetimeTracker tracker, int vRegister, boolean assertMapping) {
        if (assignment[vRegister].isSpilled()) {
            return tracker.getRegisters().getTmp(vRegister).get();
        } else {
            if (assertMapping) {
                tracker.assertMapping(vRegister, getRegister(vRegister));
            }
            return getRegister(vRegister);
        }
    }

    private Register getRegister(int vRegister) {
        assert assignment[vRegister].isInRegister();
        return assignment[vRegister].getRegister().get();
    }

    private int getStackSlot(int vRegister) {
        assert assignment[vRegister].isSpilled();
        return assignment[vRegister].getStackSlot().get();
    }

    private boolean isAlreadyInRegister(LifetimeTracker tracker, int vRegister, Register target) {
        if (assignment[vRegister].isSpilled()) {
            Optional<Register> tmp = tracker.getRegisters().getTmp(vRegister);
            return tmp.isPresent() && tmp.get() == target;
        } else {
            return getRegister(vRegister) == target;
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

    private static int argOffsetOnStack(int nArgs, int vRegister) {
        return 16 + 8 * (nArgs - vRegister - 1);
    }

    private void output(String instr) {
        result.add(instr);
    }

    private void output(String format, Object... args) {
        result.add(String.format(format, args));
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
            this.lifetimeStarts = sortByLifetime(Comparator.comparingInt(Lifetime::getBegin), true);
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
            }, false);
            this.tmpRequested = false;
            while (!lifetimeStarts.isEmpty() && lifetimes[peek(lifetimeStarts)].getBegin() < 0) {
                setRegister(pop(lifetimeStarts));
            }
        }

        /**
         * Temporary register do _not_ outlive the execution of an original instruction
         * (unless special care is taken).
         */
        public List<Register> getTmpRegisters(int num, Set<Register> excluded) {
            assert !tmpRequested;
            tmpRequested = true;
            return registers.getTmpRegisters(num, excluded);
        }

        public Register getDivRegister() {
            assert !tmpRequested;
            tmpRequested = true;
            return registers.getTmpRegisters(
                    1, RegisterPreference.PREFER_CALLEE_SAVED_NO_DIV, Set.of()
            ).get(0);
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
            if (!registers.get(r).isPresent() || !(registers.get(r).get().equals(vRegister))) {
                throw new IllegalStateException(String.format(
                        "Expected that %s is mapped to @%d", r.getAsQuad(), vRegister));
            }
        }

        public void assertFree(Register r) {
            assert registers.isFree(r);
        }

        public void assertFinallyEmpty(int numInstrs) {
            enterInstruction(numInstrs);
            assert registers.isEmpty();
        }

        private List<Integer> sortByLifetime(Comparator<Lifetime> compare, boolean noSpilled) {
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < lifetimes.length; i++) {
                if (!lifetimes[i].isTrivial() && (!noSpilled || !assignment[i].isSpilled())) {
                    result.add(i);
                }
            }
            // We want to sort in descending order, so we can pop the last elements
            result.sort((j, k) -> compare.compare(lifetimes[k],lifetimes[j]));
            return result;
        }

        private void clearRegister(int vRegister) {
            if (assignment[vRegister].isSpilled()) {
                registers.clearTmp(vRegister);
            } else {
                registers.clear(getRegister(vRegister));
            }
        }

        private void setRegister(int vRegister) {
            registers.set(getRegister(vRegister), vRegister);
        }

        private int pop(List<Integer> l) {
            return l.remove(l.size()- 1);
        }

        private int peek(List<Integer> l) {
            return l.get(l.size()- 1);
        }
    }

    /**
     * Helper class for creating permutations where both source and target can be
     * either a register or on the stack.
     */
    private class Permuter {
        private PermutationSolver solver = new PermutationSolver();
        private List<String> stackAssignments = new ArrayList<>();
        private List<String> stackToRegisterAssignments = new ArrayList<>();

        public void stackToStack(String format, Object... args) {
            stackAssignments.add(String.format(format, args));
        }

        public void registerToStack(String format, Object... args) {
            stackAssignments.add(String.format(format, args));
        }

        public void registerToRegister(Register source, Register target) {
            solver.addMapping(source.ordinal(), target.ordinal());
        }

        public void stackToRegister(String format, Object... args) {
            stackToRegisterAssignments.add(String.format(format, args));
        }

        public void outputAll(Register freeRegister, String comment) {
            // the correct order is crucial for not accidentally
            // overwriting a value that needs to be used later
            for (String line: stackAssignments) {
                output(line);
            }
            for (var move: solver.solveFromNonCycle(freeRegister.ordinal())) {
                Register from = Register.fromOrdinal(move.getInput());
                Register to = Register.fromOrdinal(move.getTarget());
                output("mov %s, %s # %s", from.getAsQuad(), to.getAsQuad(), comment);
            }
            for (String line: stackToRegisterAssignments) {
                output(line);
            }
        }
    }
}

package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import edu.kit.compiler.logger.Logger;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

public class ApplyAssignmentTest {
    private Logger logger = new Logger(Logger.Verbosity.DEBUG, true);

    @Test
    public void testTrivialInit() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
          new RegisterAssignment(Register.RAX)
        };
        RegisterSize[] sizes = new RegisterSize[] {
          RegisterSize.QUAD
        };
        Lifetime[] lifetimes = new Lifetime[] {
          new Lifetime(0, 1)
        };
        Instruction[] ir = new Instruction[] {
            Instruction.newOp("xorl @0, @0", new int[] {}, Optional.empty(), 0)
        };
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir));
        ass.testRun(logger);
    }

    @Test
    public void testTrivialRun() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
                new RegisterAssignment(Register.RAX)
        };
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.QUAD
        };
        Lifetime[] lifetimes = new Lifetime[] {
                new Lifetime(0, 1)
        };
        Instruction[] ir = new Instruction[] {
                Instruction.newInput("movq $0, 0(@0)", new int[] { 0 })
        };
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir));
        var result = ass.doApply(logger);
        for (var line: result.getInstructions()) {
            logger.info("%s", line);
        }
    }
}

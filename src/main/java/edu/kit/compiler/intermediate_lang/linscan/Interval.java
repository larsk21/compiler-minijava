package edu.kit.compiler.intermediate_lang.linscan;

import edu.kit.compiler.intermediate_lang.data.IL_Repr;
import lombok.Getter;

import java.util.List;

@Getter
public class Interval {
    private IL_Repr repr;

    /**
     * register number that this interval is assigned to
     */
    private int reg_num;
    /**
     * streaming or general purpose register
     */
    private RegisterType type;
    /**
     * start and endpoints for this interval
     */
    private List<Range> ranges;
    /**
     * positions in the interval where register is used.
     */
    private List<UsePosition> positions;
}

package edu.kit.compiler.intermediate_lang.linscan;

import edu.kit.compiler.intermediate_lang.RegisterType;
import edu.kit.compiler.intermediate_lang.data.IL_Repr;
import lombok.Getter;

import java.util.List;

@Getter
public class Interval {
    private IL_Repr repr;

    private int reg_num;
    private RegisterType type;
    private List<Range> ranges;
    private List<UsePosition> positions;
}

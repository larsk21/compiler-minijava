package edu.kit.compiler.intermediate_lang;

import lombok.Data;

import java.util.List;

@Data
public class Block {

    private List<Instruction> instructions;


}

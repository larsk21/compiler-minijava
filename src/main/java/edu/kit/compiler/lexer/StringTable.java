package edu.kit.compiler.lexer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class StringTable {

    private final LinkedHashMap<String, Integer> table = new LinkedHashMap<>();
    private int index = -1;
    private final ArrayList<String> strings = new ArrayList<>();

    public int insert(String str) {
        Integer i = table.get(str);
        if(i != null) {
            return i;
        }

        index++;
        table.put(str, index);
        strings.add(str);

        return index;
    }

    /**
     *
     * @param hash index of the string value to be received
     * @return String representation of this hash inside the String table.
     * @throws IllegalArgumentException if no such string exists.
     */
    public String retrieve(int hash) {
        if(hash < size() && hash >= 0) {
            return strings.get(hash);
        }
        throw new IllegalArgumentException("No string present for this value.");
    }

    /**
     * @param hash index of the requested string value
     */
    public boolean contains(int hash) {
        return hash < size() && hash >= 0;
    }

    public int size() {
        return table.size();
    }

    public List<String> toList() {
        return List.copyOf(strings);
    }

    @Override
    public String toString() {
        return table.toString();
    }

}

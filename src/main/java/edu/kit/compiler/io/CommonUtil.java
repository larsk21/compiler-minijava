package edu.kit.compiler.io;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommonUtil {

    /**
     * Convert the given iterable to a non-parallel stream.
     */
    public static final <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Convert the given iterable to a list.
     */
    public static final <T> List<T> toList(Iterable<T> iterable) {
        return stream(iterable).collect(Collectors.toList());
    }

}

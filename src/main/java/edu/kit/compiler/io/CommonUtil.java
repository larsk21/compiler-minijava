package edu.kit.compiler.io;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
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
     * Concatenate the given streams using Stream::concat.
     */
    @SafeVarargs
    public static final <T> Stream<T> concat(Stream<T>... streams) {
        return Arrays.stream(streams).reduce(Stream.of(), (result, stream) -> Stream.concat(result, stream));
    }

    /**
     * Convert the given iterable to a list.
     */
    public static final <T> List<T> toList(Iterable<T> iterable) {
        return stream(iterable).collect(Collectors.toList());
    }

    /**
     * Convert the given iterable to an array.
     */
    public static final <T> T[] toArray(Iterable<T> iterable, IntFunction<T[]> constructor) {
        return (T[]) toList(iterable).toArray(constructor);
    }

}

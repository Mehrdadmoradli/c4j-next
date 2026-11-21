package io.github.Mehrdadmoradli.c4jnext;

/** Entry points used by contract methods. Day 1 has no active evaluation phase. */
public class Condition {
    public static boolean preCondition() {
        return false;
    }

    public static boolean postCondition() {
        return false;
    }

    public static <T> T result() {
        return null;
    }

    public static <T> T result(Class<T> returnType) {
        return result();
    }
}

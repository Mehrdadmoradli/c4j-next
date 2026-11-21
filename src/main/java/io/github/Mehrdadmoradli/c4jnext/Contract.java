package io.github.Mehrdadmoradli.c4jnext;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Documented
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.TYPE)
public @interface Contract {
    Class<?> forTarget() default InheritedType.class;

    interface InheritedType {}
}

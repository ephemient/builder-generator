package com.github.ephemient.builder_generator.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The GenerateBuilder annotation is used to request generation of a builder class.
 * <p>
 * It may be used on non-private non-inner classes, constructors, and static methods.
 * When a class is annotated, it behaves as if the annotation were on the longest constructer.
 */
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface GenerateBuilder {
    /**
     * @return Name of the generated class.
     * If empty, the generated class will be named like the target with _Builder appended.
     */
    String className() default "";
    /**
     * @return Package of the generated class.
     * If empty, the generated class will belong to the same package as the target.
     */
    String packageName() default "";
    /**
     * @return Whether the generated class will be public or package-private.
     */
    boolean isPublic() default true;
}

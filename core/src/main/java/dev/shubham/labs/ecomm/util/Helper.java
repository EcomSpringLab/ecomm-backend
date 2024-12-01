package dev.shubham.labs.ecomm.util;

import lombok.experimental.UtilityClass;
import org.springframework.core.NestedExceptionUtils;

import java.util.function.Function;

@UtilityClass
public class Helper {

    public static final Function<Throwable, String> getMostSpecificCauseMessage = Helper::apply;

    private static String apply(Throwable throwable) {
        return NestedExceptionUtils.getMostSpecificCause(throwable).getMessage();
    }
}

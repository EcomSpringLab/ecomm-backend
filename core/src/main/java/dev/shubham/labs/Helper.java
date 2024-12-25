package dev.shubham.labs;

import lombok.experimental.UtilityClass;
import org.springframework.core.NestedExceptionUtils;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@UtilityClass
public class Helper {

    public static final Function<Throwable, String> getMostSpecificCauseMessage = Helper::apply;
    public static final Supplier<String> uuid = () -> UUID.randomUUID().toString();

    private static String apply(Throwable throwable) {
        return NestedExceptionUtils.getMostSpecificCause(throwable).getMessage();
    }
}

package com.jn.sqlhelper.common.ddl.model.internal;

import com.jn.langx.text.StringTemplates;
import com.jn.langx.util.reflect.Reflects;
import com.jn.sqlhelper.common.exception.ValueConvertException;
import com.jn.sqlhelper.common.utils.Converter;

public class BooleanFlagConverter implements Converter<String, BooleanFlag> {
    @Override
    public BooleanFlag apply(String str) {
        if (str == null || str.length() > 3) {
            throw new ValueConvertException(StringTemplates.formatWithPlaceholder("Can't convert {} to {}", str, Reflects.getFQNClassName(BooleanFlag.class)));
        }
        return BooleanFlag.of(str);
    }
}
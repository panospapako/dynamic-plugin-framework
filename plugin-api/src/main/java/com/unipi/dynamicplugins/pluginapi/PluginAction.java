package com.unipi.dynamicplugins.pluginapi;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PluginAction {
    String description() default "";
}
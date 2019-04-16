package com.dj.mvcframework.demo;

public class DemoService implements IDemoService{
    public String get(String name) {
        return "My Name is "  + name;
    }
}

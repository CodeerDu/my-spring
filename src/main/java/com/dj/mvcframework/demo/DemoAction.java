package com.dj.mvcframework.demo;

import com.dj.mvcframework.annotation.MyAutowired;
import com.dj.mvcframework.annotation.MyController;
import com.dj.mvcframework.annotation.MyRequestMapping;
import com.dj.mvcframework.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/demo")
public class DemoAction {
    @MyAutowired
    private IDemoService demoService;

    @MyRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp, @MyRequestParam("name") String name){
            String result = demoService.get(name);
        try {
            resp.getWriter().write(name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @MyRequestMapping("/add")
    public  void add(HttpServletRequest request,HttpServletResponse resp,@MyRequestParam("a") Integer a,@MyRequestParam("b") Integer b){
        try {
            resp.getWriter().write(a+"+"+b+"="+(a+b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

package com.dj.mvcframework.v1.servlet;

import com.dj.mvcframework.annotation.MyController;
import com.dj.mvcframework.annotation.MyRequestMapping;
import com.dj.mvcframework.annotation.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 1.根据扫描路径获取所有路径下的文件
 * 2.对class文件进行剪处理，拼接位class名，在mapping中进行保存
 * 3.对class对象进行处理，根据注解对class进行不同处理，把所有类实例化保存
 */
public class MyDispatcherServlet extends HttpServlet {
    private Map<String,Object> mapping = new HashMap<String, Object>();
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    private void doDispatch(HttpServletRequest req,HttpServletResponse resp) throws Exception{
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        System.out.println(contextPath);
        url = url.replace(contextPath,"").replace("/+","/");
        if(! this.mapping.containsKey(url)){
            resp.getWriter().write("404 Not Found");
            return;
        }
        Method method = (Method)this.mapping.get(url);
        Map<String,String[]> params = req.getParameterMap();
        method.invoke(this.mapping.get(method.getDeclaringClass().getName()),new Object[]{req,resp,params.get("name")[0]});

    }

    @Override
    public void init(ServletConfig config) throws ServletException{
        InputStream input = null;
        Properties configContext = new Properties();
        input = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("contextConfigLocation"));
        try {
            configContext.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(input == null){
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        String scanPackage = configContext.getProperty("scanPackage");
        doScanner(scanPackage);

        for(String className : mapping.keySet()){
            if(! className.contains(".")) {continue;};
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            if(clazz.isAnnotationPresent(MyController.class)){
                try {
                    mapping.put(className,clazz.newInstance());
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                String basrUrl  = "";
                if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                    MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                    basrUrl = requestMapping.value();
                }
                Method[] methods = clazz.getMethods();
                for(Method method:methods){
                    if(! method.isAnnotationPresent(MyRequestMapping.class)){ continue;};
                    MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                    String url = (basrUrl+"/"+requestMapping.value()).replaceAll("/+","/");
                    mapping.put(url,method);
                    System.out.println("Mapped "+url+","+method);
                }
            }else if(clazz.isAnnotationPresent(MyService.class)){
                    MyService service = clazz.getAnnotation(MyService.class);
                    String beanName = service.value();
                    if("".equals(beanName)){
                            beanName = clazz.getName();
                    }
                Object instance = null;
                try {
                    instance = clazz.newInstance();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                mapping.put(beanName,instance);

                    for(Class<?> i : clazz.getInterfaces()){
                        mapping.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            }
        }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for(File file : classDir.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage+"."+file.getName());
            }
            String clazzName = (scanPackage+"."+file.getName().replaceAll(".class",""));
            mapping.put(clazzName,null);
        }
    }
}

package com.dj.mvcframework.v2.servlet;

import com.dj.mvcframework.annotation.*;


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDispatcherServlet extends HttpServlet {
    //存储 aplication.properties 的配置内容
    private Properties contextConfig = new Properties();
    //存储所有扫描到的类
    private List<String> classNames = new ArrayList<String>();
    //IOC 容器，保存所有实例化对象
    private Map<String,Object> ioc = new HashMap<String,Object>();
    //保存 Contrller 中所有 Mapping 的对应关系
//    private Map<String, Method> handlerMapping = new HashMap<String,Method>();
    //保存所有的 Url 和方法的映射关系
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    public void init(ServletConfig config) throws ServletException {
//1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
//2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
//3、初始化所有相关的类的实例，并且放入到 IOC 容器之中
        doInstance();
//4、完成依赖注入
        doAutowired();
//5、初始化 HandlerMapping
        initHandlerMapping();
        System.out.println("My Spring framework is init.");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
/**
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(! this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404");
            return;
        }

        Method method = this.handlerMapping.get(url);
        Map<String,String[]> params = req.getParameterMap();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Map<String,String[]> parameterMap = req.getParameterMap();
        Object[] paramValues = new Object[parameterTypes.length];
        for(int i=0;i<parameterTypes.length;i++){
            Class parameterType = parameterTypes[i];
            if(parameterType == HttpServletRequest.class){
                paramValues[i] = req;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = resp;
                continue;
            }else if(parameterType == String.class){
                MyRequestParam requestParam = (MyRequestParam) parameterType.getAnnotation(MyRequestParam.class);
                if(parameterMap.containsKey(requestParam.value())){
                    for (Map.Entry<String,String[]> param : parameterMap.entrySet()){
                        String value = Arrays.toString(param.getValue())
                                .replaceAll("\\[|\\]","")
                                .replaceAll("\\s",",");
                        paramValues[i] = value;
                    }
                }
            }
        }
        //投机取巧的方式
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),new Object[]{req,resp,params.get("name")[0]});
    }
**/

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    private void doLoadConfig(String contextConfigLocation){
        InputStream fis = null;
        try {
            fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
//1、读取配置文件
            contextConfig.load(fis);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            try {
                if(null != fis){fis.close();}
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void doScanner(String scanPackage) {
//包传过来包下面的所有的类全部扫描进来的
        URL url = this.getClass().getClassLoader()
                .getResource("/" + scanPackage.replaceAll("\\.","/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            }else {
                if(!file.getName().endsWith(".class")){ continue; }
                String className = (scanPackage + "." + file.getName()).replace(".class","");
                classNames.add(className);
            }
        }
    }
    private void doInstance() {
        if(classNames.isEmpty()){return;}
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(MyController.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                }else if(clazz.isAnnotationPresent(MyService.class)){
//1、默认的类名首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
//2、自定义命名
                    MyService service = clazz.getAnnotation(MyService.class);
                    if(!"".equals(service.value())){
                        beanName = service.value();
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
//3、根据类型注入实现类，投机取巧的方式
                    for (Class<?> i : clazz.getInterfaces()) {
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("The beanName is exists!!");
                        }
                        ioc.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void doAutowired() {
        if(ioc.isEmpty()){ return; }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
//拿到实例对象中的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(!field.isAnnotationPresent(MyAutowired.class)){ continue; }
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
//不管你愿不愿意，强吻
                field.setAccessible(true); //设置私有属性的访问权限
                try {
//执行注入动作
                field.set(entry.getValue(), ioc.get(beanName));
                } catch (Exception e) {
                    e.printStackTrace();
                    continue ;
                }
            }
        }
    }
    /**
    private void initHandlerMapping() {
        if(ioc.isEmpty()){ return; }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)){ continue; }
            String baseUrl = "";
//获取 Controller 的 url 配置
            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = requestMapping.value();
            }
//获取 Method 的 url 配置
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
//没有加 RequestMapping 注解的直接忽略
                if(!method.isAnnotationPresent(MyRequestMapping.class)){ continue; }
//映射 URL
                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                String url = ("/" + baseUrl + "/" + requestMapping.value())
                        .replaceAll("/+", "/");
                handlerMapping.put(url,method);
                System.out.println("Mapped " + url + "," + method);
            }
        }
    }
     **/
    private void initHandlerMapping(){
        if(ioc.isEmpty()){ return; }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)){ continue; }
            String url = "";
//获取 Controller 的 url 配置
            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                url = requestMapping.value();
            }
//获取 Method 的 url 配置
            Method [] methods = clazz.getMethods();
            for (Method method : methods) {
//没有加 RequestMapping 注解的直接忽略
                if(!method.isAnnotationPresent(MyRequestMapping.class)){ continue; }
//映射 URL
                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                String regex = ("/" + url + requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern,entry.getValue(),method));
                System.out.println("mapping " + regex + "," + method);
            }
        }
    }

    private void doDispatch(HttpServletRequest req,HttpServletResponse resp) throws Exception{
        try{
            Handler handler = getHandler(req);
            if(handler == null){
//如果没有匹配上，返回 404 错误
                resp.getWriter().write("404 Not Found");
                return;
            }
//获取方法的参数列表
            Class<?> [] paramTypes = handler.method.getParameterTypes();
//保存所有需要自动赋值的参数值
            Object [] paramValues = new Object[paramTypes.length];
            Map<String,String[]> params = req.getParameterMap();
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]",
                        "").replaceAll(",\\s", ",");
//如果找到匹配的对象，则开始填充参数值
                if(!handler.paramIndexMapping.containsKey(param.getKey())){continue;}
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index],value);
            }
//设置方法中的 request 和 response 对象
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
            handler.method.invoke(handler.controller, paramValues);
        }catch(Exception e){
            throw e;
        }
    }
    private Handler getHandler(HttpServletRequest req) throws Exception{
        if(handlerMapping.isEmpty()){ return null; }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        for (Handler handler : handlerMapping) {
            try{
                Matcher matcher = handler.pattern.matcher(url);
//如果没有匹配上继续下一个匹配
                if(!matcher.matches()){ continue; }
                return handler;
            }catch(Exception e){
                throw e;
            }
        }
        return null;
    }
    private Object convert(Class<?> type,String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }
    
    private String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
}

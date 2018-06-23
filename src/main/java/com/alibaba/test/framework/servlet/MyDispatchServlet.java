package com.alibaba.test.framework.servlet;


import com.alibaba.test.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDispatchServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> iocMap = new HashMap<>();

    private List<BeanHandler> handlerMapping = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            doDispatcher(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("404 Not Found!");
        }
    }

    /**
     * 根据请求映射方法
     *
     * @param req
     * @param resp
     */
    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        BeanHandler handler = getBeanHandler(req);
        if (null == handler) {
            throw new Exception("404 Not Found");
        }

        Method method = handler.method;
        Class<?>[] paramsTypes = method.getParameterTypes();
        Object[] paramValues = new Object[paramsTypes.length];
        Map<String, String[]> params = req.getParameterMap();

        String value = null;
        int index = 0;
        for (Map.Entry<String, String[]> param : params.entrySet()) {
            value = Arrays.toString(param.getValue());
            if (!handler.paramIndexMap.containsKey(param.getKey())) {
                continue;
            }

            index = handler.paramIndexMap.get(param.getKey());
            paramValues[index] = covert(paramsTypes[index], value);
        }

        if (handler.paramIndexMap.containsKey(HttpServletRequest.class.getSimpleName())) {
            int reqIndex = handler.paramIndexMap.get(HttpServletRequest.class.getSimpleName());
            paramValues[reqIndex] = req;
        }
        if (handler.paramIndexMap.containsKey(HttpServletResponse.class.getSimpleName())) {
            int respIndex = handler.paramIndexMap.get(HttpServletResponse.class.getSimpleName());
            paramValues[respIndex] = resp;
        }

        method.invoke(handler.controller, paramValues);
    }

    /**
     * 封装参数
     *
     * @param paramsType
     * @param value
     * @return
     */
    private Object covert(Class<?> paramsType, String value) {
        if (paramsType == String.class) {
            return value;
        }
        return null;
    }

    /**
     * 根据请求获取对应的beanHandler
     *
     * @param req
     * @return
     */
    private BeanHandler getBeanHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty()) {
            return null;
        }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        for (BeanHandler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        }
        return null;
    }

    /**
     * spring初始化
     *
     * @param config
     */
    @Override
    public void init(ServletConfig config) {

        //加载配置文件
        loadConfig(config);

        //扫描所有相关的类
        String packageName = contextConfig.getProperty("scanPackage");
        scanClass(packageName);

        //初始化所有相关的类
        initClass();

        //自动装配
        initAutowired();

        //初始化HandlerMapping
        initHandlerMapping();
    }

    /**
     * 初始化HandlerMapping
     */
    private void initHandlerMapping() {
        if (iocMap.isEmpty()) {
            return;
        }

        try {
            for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
                Class<?> clazz = entry.getValue().getClass();
                if (!clazz.isAnnotationPresent(MyController.class)) {
                    continue;
                }

                String baseUrl = "";
                if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                    baseUrl = "/" + clazz.getAnnotation(MyRequestMapping.class).value();
                }

                for (Method method : clazz.getMethods()) {
                    if (!method.isAnnotationPresent(MyRequestMapping.class)) {
                        continue;
                    }

                    String methodUrl = baseUrl + "/" + method.getAnnotation(MyRequestMapping.class).value();
                    methodUrl = methodUrl.replaceAll("/+", "/");
                    Pattern pattern = Pattern.compile(methodUrl);
                    handlerMapping.add(new BeanHandler(pattern, clazz.newInstance(), method));

                    System.out.println("Mapping: " + methodUrl + "; Method: " + method);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 自动装配
     */
    private void initAutowired() {
        if (iocMap.isEmpty()) {
            return;
        }

        //循环IOC中所有的类，对需要装配的字段自动赋值
        try {
            Object beanObj = null;
            Field[] fields = null;
            String beanName = null;
            for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
                beanObj = entry.getValue();
                fields = beanObj.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if (!field.isAnnotationPresent(MyAutowired.class)) {
                        continue;
                    }

                    field.setAccessible(true);

                    MyAutowired myAutowired = field.getAnnotation(MyAutowired.class);
                    beanName = myAutowired.value();
                    if ("".equals(beanName)) {
                        beanName = field.getName();
                    }

                    field.set(beanObj, iocMap.get(beanName));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化所有相关的类
     */
    private void initClass() {
        if (classNames.isEmpty()) {
            return;
        }

        try {
            Class<?> clzz = null;
            String beanName = null;
            for (String className : classNames) {
                clzz = Class.forName(className);

                if (clzz.isAnnotationPresent(MyController.class) || clzz.isAnnotationPresent(MyService.class)) {
                    beanName = lowerStr(clzz.getSimpleName());
                    MyService myService = clzz.getAnnotation(MyService.class);
                    if (null != myService && !"".equals(myService.value())) {
                        beanName = myService.value();
                    }
                    iocMap.put(beanName, clzz.newInstance());

                    for (Class<?> c : clzz.getInterfaces()) {
                        beanName = lowerStr(c.getSimpleName());
                        if (iocMap.containsKey(beanName)) {
                            throw new Exception("bean: " + c.getSimpleName() + " has be init!");
                        }
                        iocMap.put(beanName, clzz.newInstance());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描所有相关的类
     *
     * @param packageName
     */
    private void scanClass(String packageName) {
        try {
            URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));

            File classDir = new File(URLDecoder.decode(url.getFile(), "UTF-8"));
            for (File file : classDir.listFiles()) {
                if (file.isDirectory()) {
                    scanClass(packageName + "." + file.getName());
                } else {
                    String className = packageName + "." + file.getName().replaceAll(".class", "");
                    classNames.add(className);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载配置文件
     *
     * @param config
     */
    private void loadConfig(ServletConfig config) {
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);//用dom4j自带的reader读取去读返回一个Document
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String lowerStr(String str) {
        if (null == str || "".equals(str)) {
            return "";
        }

        return str.substring(0, 1).toLowerCase() + str.substring(1, str.length());
    }

    private class BeanHandler {

        protected Pattern pattern;

        protected Object controller;

        protected Method method;

        protected Map<String, Integer> paramIndexMap;

        public BeanHandler(Pattern pattern, Object controller, Method method) {
            this.pattern = pattern;
            this.controller = controller;
            this.method = method;
            this.paramIndexMap = new HashMap<>();
            initParamIndex();
        }

        private void initParamIndex() {
            Map<String, Integer> map = new HashMap<>();

            //方法中含有注解的参数
            Annotation[][] annotation = method.getParameterAnnotations();
            String paramName = null;
            for (int i = 0, len = annotation.length; i < len; i++) {
                for (Annotation a : annotation[i]) {
                    if (a instanceof MyRequestParam) {
                        paramName = ((MyRequestParam) a).value();
                        if (!"".equals(paramName)) {
                            paramIndexMap.put(paramName, i);
                        }
                    }
                }
            }

            //方法中的request、response参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i = 0, len = paramsTypes.length; i < len; i++) {
                if (paramsTypes[i] == HttpServletRequest.class || paramsTypes[i] == HttpServletResponse.class) {
                    paramIndexMap.put(paramsTypes[i].getName(), i);
                }
            }
        }
    }
}
